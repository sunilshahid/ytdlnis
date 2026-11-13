package com.deniscerri.ytdl.vault.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.deniscerri.ytdl.vault.VaultRepository
import com.deniscerri.ytdl.vault.VaultStorage
import com.deniscerri.ytdl.vault.crypto.VaultCryptoPrimitives
import com.deniscerri.ytdl.vault.crypto.VaultHeader
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Media3 [DataSource.Factory] serving `vault://{uuid}` URIs directly from encrypted `.video.enc`
 * containers via chunk-level random access decryption (< 128 KiB RAM).
 */
@UnstableApi
class VaultDataSourceFactory(
    private val itemsDir: File,
    private val vaultKeyProvider: () -> ByteArray?
) : DataSource.Factory {

    override fun createDataSource(): DataSource = VaultDataSource(itemsDir, vaultKeyProvider)
}

/**
 * Pull-based Media3 [DataSource] implementing O(1) seek decryption over `YTVLT2` containers:
 * - Version `0x0002` (Default): 64 KiB AES-256-GCM authenticated chunks (caches 1 chunk for sequential reads).
 * - Version `0x0003` (Fallback): AES-256-CTR with 128-bit payload-relative counter arithmetic.
 *
 * Re-derives `itemKey` on each [open] and zeroes all key/plaintext buffers on [close] so seeking
 * (where ExoPlayer reuses one DataSource across multiple open/close cycles) works cleanly without
 * leaving keys resident in RAM.
 */
@UnstableApi
class VaultDataSource(
    private val itemsDir: File,
    private val vaultKeyProvider: () -> ByteArray?
) : BaseDataSource(/* isNetwork = */ false) {

    private var activeUri: Uri? = null
    private var activeUuid: String? = null
    private var header: VaultHeader? = null
    private var itemKey: ByteArray? = null
    private var raf: RandomAccessFile? = null

    private var currentPosition: Long = 0L
    private var bytesRemaining: Long = 0L
    private var opened = false

    // GCM chunk cache (single chunk max = 64 KiB)
    private var gcmCipher: Cipher? = null
    private var cachedChunkIndex: Long = -1L
    private var cachedChunkPlain: ByteArray? = null
    private var cachedChunkValidLen: Int = 0

    // CTR state
    private var ctrCipher: Cipher? = null
    private var ctrReadScratch: ByteArray? = null

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (!"vault".equals(uri.scheme, ignoreCase = true)) {
            throw IOException("Unsupported URI scheme for VaultDataSource: ${uri.scheme}")
        }
        val uuid = uri.host?.takeIf { it.isNotBlank() }
            ?: uri.path?.trim('/')?.takeIf { it.isNotBlank() }
            ?: throw IOException("Missing vault item UUID in URI: $uri")

        val vaultKey = vaultKeyProvider()
            ?: throw SecurityException("Vault is locked")

        val itemRootKey = try {
            VaultRepository.resolveItemRootKey(itemsDir, uuid, vaultKey)
        } finally {
            vaultKey.fill(0)
        }

        val videoFile = VaultStorage.videoFile(itemsDir, uuid)
        val verifiedHeader: VaultHeader
        val derivedKey: ByteArray
        try {
            verifiedHeader = VaultHeader.readAndVerify(videoFile, itemRootKey)
            derivedKey = verifiedHeader.deriveItemKey(itemRootKey, uuid)
        } finally {
            itemRootKey.fill(0)
        }

        if (dataSpec.position < 0L || dataSpec.position > verifiedHeader.payloadLength) {
            derivedKey.fill(0)
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }

        transferInitializing(dataSpec)

        activeUri = uri
        activeUuid = uuid
        header = verifiedHeader
        itemKey = derivedKey
        currentPosition = dataSpec.position
        val available = verifiedHeader.payloadLength - dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            available
        } else {
            minOf(dataSpec.length, available)
        }

        val randomAccess = RandomAccessFile(videoFile, "r")
        raf = randomAccess
        cachedChunkIndex = -1L
        cachedChunkValidLen = 0

        when (verifiedHeader.version) {
            VaultHeader.VERSION_GCM_CHUNKS -> {
                gcmCipher = Cipher.getInstance("AES/GCM/NoPadding")
                val cache = cachedChunkPlain
                if (cache == null || cache.size != verifiedHeader.chunkSize) {
                    cachedChunkPlain = ByteArray(verifiedHeader.chunkSize)
                }
            }
            VaultHeader.VERSION_CTR -> {
                randomAccess.seek(VaultHeader.HEADER_SIZE.toLong() + dataSpec.position)
                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(derivedKey, "AES"),
                    IvParameterSpec(VaultCryptoPrimitives.counterBlockAt(verifiedHeader.baseNonce, dataSpec.position))
                )
                val skipBytes = (dataSpec.position % 16L).toInt()
                if (skipBytes > 0) {
                    cipher.update(ByteArray(skipBytes))
                }
                ctrCipher = cipher
            }
            else -> {
                close()
                throw IOException("Unsupported vault format version: ${verifiedHeader.version}")
            }
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val hdr = header ?: throw IOException("VaultDataSource not opened")

        return when (hdr.version) {
            VaultHeader.VERSION_GCM_CHUNKS -> readGcm(hdr, buffer, offset, length)
            VaultHeader.VERSION_CTR -> readCtr(buffer, offset, length)
            else -> C.RESULT_END_OF_INPUT
        }
    }

    private fun readGcm(hdr: VaultHeader, buffer: ByteArray, offset: Int, length: Int): Int {
        val chunkSize = hdr.chunkSize.toLong()
        val chunkIndex = currentPosition / chunkSize
        val offsetInChunk = (currentPosition % chunkSize).toInt()

        if (cachedChunkIndex != chunkIndex) {
            loadAndVerifyChunk(hdr, chunkIndex)
        }

        val plainChunk = cachedChunkPlain ?: throw IOException("Chunk buffer cleared")
        val availableInChunk = cachedChunkValidLen - offsetInChunk
        if (availableInChunk <= 0) return C.RESULT_END_OF_INPUT

        val toCopy = minOf(length.toLong(), bytesRemaining, availableInChunk.toLong()).toInt()
        System.arraycopy(plainChunk, offsetInChunk, buffer, offset, toCopy)
        currentPosition += toCopy
        bytesRemaining -= toCopy
        bytesTransferred(toCopy)
        return toCopy
    }

    private fun loadAndVerifyChunk(hdr: VaultHeader, chunkIndex: Long) {
        val randomAccess = raf ?: throw IOException("Stream closed")
        val key = itemKey ?: throw SecurityException("Key zeroized")
        val uuid = activeUuid ?: throw IOException("Missing UUID")
        val cipher = gcmCipher ?: throw IOException("Cipher uninitialized")

        val chunkSizeLong = hdr.chunkSize.toLong()
        val chunkStartPlain = chunkIndex * chunkSizeLong
        val expectedPlainLen = minOf(chunkSizeLong, hdr.payloadLength - chunkStartPlain).toInt()
        val encChunkStride = chunkSizeLong + VaultCryptoPrimitives.GCM_TAG_BYTES.toLong()
        val fileOffset = VaultHeader.HEADER_SIZE.toLong() + (chunkIndex * encChunkStride)

        val encBuf = ByteArray(expectedPlainLen + VaultCryptoPrimitives.GCM_TAG_BYTES)
        randomAccess.seek(fileOffset)
        randomAccess.readFully(encBuf)

        val nonce = VaultCryptoPrimitives.gcmChunkNonce(hdr.baseNonce, chunkIndex)
        val aad = VaultCryptoPrimitives.gcmChunkAad(uuid, chunkIndex)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(VaultCryptoPrimitives.GCM_TAG_BITS, nonce)
        )
        cipher.updateAAD(aad)
        val plainOut = cachedChunkPlain ?: ByteArray(hdr.chunkSize).also { cachedChunkPlain = it }
        val decryptedLen = cipher.doFinal(encBuf, 0, encBuf.size, plainOut, 0)
        encBuf.fill(0)
        if (decryptedLen != expectedPlainLen) {
            plainOut.fill(0)
            throw IOException("Decrypted chunk length mismatch at chunk $chunkIndex")
        }
        cachedChunkIndex = chunkIndex
        cachedChunkValidLen = decryptedLen
    }

    private fun readCtr(buffer: ByteArray, offset: Int, length: Int): Int {
        val randomAccess = raf ?: throw IOException("Stream closed")
        val cipher = ctrCipher ?: throw IOException("Cipher uninitialized")
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val scratch = ctrReadScratch?.takeIf { it.size >= toRead }
            ?: ByteArray(maxOf(toRead, 8192)).also { ctrReadScratch = it }

        val readCount = randomAccess.read(scratch, 0, toRead)
        if (readCount <= 0) return C.RESULT_END_OF_INPUT
        val decrypted = cipher.update(scratch, 0, readCount, buffer, offset)
        scratch.fill(0, 0, readCount)
        currentPosition += decrypted
        bytesRemaining -= decrypted
        bytesTransferred(decrypted)
        return decrypted
    }

    override fun getUri(): Uri? = activeUri

    override fun close() {
        activeUri = null
        activeUuid = null
        header = null
        gcmCipher = null
        ctrCipher = null
        cachedChunkIndex = -1L
        cachedChunkValidLen = 0
        itemKey?.fill(0)
        itemKey = null
        cachedChunkPlain?.fill(0)
        cachedChunkPlain = null
        ctrReadScratch?.fill(0)
        ctrReadScratch = null
        runCatching { raf?.close() }
        raf = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
