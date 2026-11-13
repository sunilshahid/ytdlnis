package com.deniscerri.ytdl.vault.crypto

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.crypto.AEADBadTagException

/**
 * 84-byte binary header for `YTVLT2` vault video containers:
 * - Magic: 6B (`"YTVLT2"`)
 * - Version: 2B (`0x0002` = 64 KiB AES-256-GCM chunks [default]; `0x0003` = AES-256-CTR fallback)
 * - File Salt: 16B
 * - Base Nonce: 16B
 * - Chunk Size: 4B (`65536`)
 * - Payload Length: 8B (plaintext payload byte length)
 * - Header MAC: 32B (`HMAC-SHA256(headerKey, headerBytes[0..51])`)
 */
data class VaultHeader(
    val version: Int,
    val fileSalt: ByteArray,
    val baseNonce: ByteArray,
    val chunkSize: Int,
    val payloadLength: Long,
    val headerMac: ByteArray
) {
    fun deriveItemKey(rootKey: ByteArray, uuid: String): ByteArray =
        VaultCryptoPrimitives.hkdfSha256(
            ikm = rootKey,
            salt = fileSalt,
            info = "ytdlnis-vault-item-v1:$uuid"
        )

    fun expectedFileLength(): Long = expectedFileLength(version, chunkSize, payloadLength)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultHeader) return false
        return version == other.version &&
            chunkSize == other.chunkSize &&
            payloadLength == other.payloadLength &&
            fileSalt.contentEquals(other.fileSalt) &&
            baseNonce.contentEquals(other.baseNonce) &&
            headerMac.contentEquals(other.headerMac)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + fileSalt.contentHashCode()
        result = 31 * result + baseNonce.contentHashCode()
        result = 31 * result + chunkSize
        result = 31 * result + payloadLength.hashCode()
        result = 31 * result + headerMac.contentHashCode()
        return result
    }

    companion object {
        val MAGIC: ByteArray = "YTVLT2".toByteArray(StandardCharsets.US_ASCII)
        const val HEADER_SIZE = 84
        const val MAC_INPUT_LENGTH = 52
        const val VERSION_GCM_CHUNKS = 0x0002
        const val VERSION_CTR = 0x0003
        const val DEFAULT_CHUNK_SIZE = 65_536

        fun expectedFileLength(version: Int, chunkSize: Int, payloadLength: Long): Long {
            require(payloadLength >= 0L)
            return when (version) {
                VERSION_CTR -> HEADER_SIZE + payloadLength
                VERSION_GCM_CHUNKS -> {
                    val chunks = if (payloadLength == 0L) {
                        0L
                    } else {
                        (payloadLength + chunkSize - 1L) / chunkSize.toLong()
                    }
                    HEADER_SIZE + payloadLength + (chunks * VaultCryptoPrimitives.GCM_TAG_BYTES)
                }
                else -> throw IllegalArgumentException("Unsupported vault version: $version")
            }
        }

        fun serializeWithMac(
            rootKey: ByteArray,
            version: Int = VERSION_GCM_CHUNKS,
            fileSalt: ByteArray,
            baseNonce: ByteArray,
            chunkSize: Int = DEFAULT_CHUNK_SIZE,
            payloadLength: Long
        ): ByteArray {
            require(fileSalt.size == 16)
            require(baseNonce.size == 16)
            require(chunkSize == DEFAULT_CHUNK_SIZE)
            require(payloadLength >= 0L)
            require(version == VERSION_GCM_CHUNKS || version == VERSION_CTR)

            val buf = ByteBuffer.allocate(HEADER_SIZE)
            buf.put(MAGIC)
            buf.putShort(version.toShort())
            buf.put(fileSalt)
            buf.put(baseNonce)
            buf.putInt(chunkSize)
            buf.putLong(payloadLength)

            val bytes = buf.array()
            val headerKey = VaultCryptoPrimitives.hkdfSha256(
                ikm = rootKey,
                salt = fileSalt,
                info = "ytdlnis-vault-header-v1"
            )
            try {
                val mac = VaultCryptoPrimitives.hmacSha256(headerKey, bytes, 0, MAC_INPUT_LENGTH)
                System.arraycopy(mac, 0, bytes, MAC_INPUT_LENGTH, 32)
                return bytes
            } finally {
                headerKey.fill(0)
            }
        }

        @Throws(IOException::class, SecurityException::class)
        fun readAndVerify(file: File, rootKey: ByteArray): VaultHeader {
            if (!file.exists()) throw IOException("Vault file does not exist: ${file.name}")
            val actualLength = file.length()
            if (actualLength < HEADER_SIZE) {
                throw SecurityException("Corrupt vault container (truncated header)")
            }

            val headerBytes = ByteArray(HEADER_SIZE)
            RandomAccessFile(file, "r").use { raf ->
                raf.readFully(headerBytes)
            }
            return parseAndVerify(headerBytes, actualLength, rootKey)
        }

        @Throws(SecurityException::class)
        fun parseAndVerify(headerBytes: ByteArray, fileLength: Long?, rootKey: ByteArray): VaultHeader {
            if (headerBytes.size != HEADER_SIZE) {
                throw SecurityException("Invalid vault header length")
            }
            for (i in MAGIC.indices) {
                if (headerBytes[i] != MAGIC[i]) {
                    throw SecurityException("Invalid vault container magic")
                }
            }
            val buf = ByteBuffer.wrap(headerBytes)
            buf.position(MAGIC.size)
            val version = buf.short.toInt() and 0xFFFF
            if (version != VERSION_GCM_CHUNKS && version != VERSION_CTR) {
                throw SecurityException("Unsupported vault format version: $version")
            }
            val fileSalt = ByteArray(16).also { buf.get(it) }
            val baseNonce = ByteArray(16).also { buf.get(it) }
            val chunkSize = buf.int
            val payloadLength = buf.long
            val storedMac = ByteArray(32).also { buf.get(it) }

            // Authenticate before trusting chunkSize or payloadLength.
            val headerKey = VaultCryptoPrimitives.hkdfSha256(
                ikm = rootKey,
                salt = fileSalt,
                info = "ytdlnis-vault-header-v1"
            )
            val computedMac = try {
                VaultCryptoPrimitives.hmacSha256(headerKey, headerBytes, 0, MAC_INPUT_LENGTH)
            } finally {
                headerKey.fill(0)
            }
            if (!VaultCryptoPrimitives.constantTimeEquals(storedMac, computedMac)) {
                throw SecurityException("Vault header MAC verification failed (wrong key or tampered file)")
            }

            if (chunkSize != DEFAULT_CHUNK_SIZE) {
                throw SecurityException("Unsupported vault chunk size: $chunkSize")
            }
            if (payloadLength < 0L) {
                throw SecurityException("Negative payload length in header")
            }
            if (fileLength != null) {
                val expected = expectedFileLength(version, chunkSize, payloadLength)
                if (fileLength != expected) {
                    throw AEADBadTagException("Vault payload size mismatch: expected=$expected actual=$fileLength")
                }
            }
            return VaultHeader(
                version = version,
                fileSalt = fileSalt,
                baseNonce = baseNonce,
                chunkSize = chunkSize,
                payloadLength = payloadLength,
                headerMac = storedMac
            )
        }
    }
}
