package com.deniscerri.ytdl.vault.crypto

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streams large video files into and out of `YTVLT2` containers using constant (< 192 KiB) RAM.
 *
 * Writes atomically via a `.tmp` staging file in the same vault directory and verifies chunk 0 +
 * header MAC before callers delete the plaintext source.
 */
object VaultFileCipher {

    @Throws(IOException::class)
    fun encryptStream(
        input: InputStream,
        plaintextLength: Long,
        outputFile: File,
        rootKey: ByteArray,
        uuid: String,
        cipherVersion: Int = VaultHeader.VERSION_GCM_CHUNKS,
        onProgress: ((writtenBytes: Long, totalBytes: Long) -> Unit)? = null
    ): VaultHeader {
        require(plaintextLength >= 0L) { "Plaintext length must be known and >= 0" }
        outputFile.parentFile?.mkdirs()

        val tempFile = File(outputFile.parentFile, "${outputFile.name}.writing")
        tempFile.delete()

        val fileSalt = VaultCryptoPrimitives.randomBytes(16)
        val baseNonce = VaultCryptoPrimitives.randomBytes(16)
        val chunkSize = VaultHeader.DEFAULT_CHUNK_SIZE
        val headerBytes = VaultHeader.serializeWithMac(
            rootKey = rootKey,
            version = cipherVersion,
            fileSalt = fileSalt,
            baseNonce = baseNonce,
            chunkSize = chunkSize,
            payloadLength = plaintextLength
        )
        val header = VaultHeader.parseAndVerify(headerBytes, fileLength = null, rootKey = rootKey)
        val itemKey = header.deriveItemKey(rootKey, uuid)
        val keySpec = SecretKeySpec(itemKey, "AES")

        val plainBuf = ByteArray(chunkSize)
        var totalWritten = 0L

        try {
            BufferedOutputStream(FileOutputStream(tempFile), chunkSize * 2).use { out ->
                out.write(headerBytes)
                when (cipherVersion) {
                    VaultHeader.VERSION_GCM_CHUNKS -> {
                        val gcmCipher = Cipher.getInstance("AES/GCM/NoPadding")
                        var chunkIndex = 0L
                        while (totalWritten < plaintextLength) {
                            val toRead = minOf(chunkSize.toLong(), plaintextLength - totalWritten).toInt()
                            val filled = readFullyUpTo(input, plainBuf, toRead)
                            if (filled != toRead) {
                                throw IOException("Unexpected EOF while encrypting video: expected $plaintextLength, read ${totalWritten + filled}")
                            }
                            val nonce = VaultCryptoPrimitives.gcmChunkNonce(baseNonce, chunkIndex)
                            val aad = VaultCryptoPrimitives.gcmChunkAad(uuid, chunkIndex)
                            gcmCipher.init(
                                Cipher.ENCRYPT_MODE,
                                keySpec,
                                GCMParameterSpec(VaultCryptoPrimitives.GCM_TAG_BITS, nonce)
                            )
                            gcmCipher.updateAAD(aad)
                            val cipherChunk = gcmCipher.doFinal(plainBuf, 0, filled)
                            out.write(cipherChunk)
                            totalWritten += filled
                            chunkIndex++
                            onProgress?.invoke(totalWritten, plaintextLength)
                        }
                    }

                    VaultHeader.VERSION_CTR -> {
                        val ctrCipher = Cipher.getInstance("AES/CTR/NoPadding")
                        ctrCipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(baseNonce))
                        val cipherBuf = ByteArray(chunkSize)
                        while (totalWritten < plaintextLength) {
                            val toRead = minOf(chunkSize.toLong(), plaintextLength - totalWritten).toInt()
                            val filled = readFullyUpTo(input, plainBuf, toRead)
                            if (filled != toRead) {
                                throw IOException("Unexpected EOF while encrypting video: expected $plaintextLength, read ${totalWritten + filled}")
                            }
                            val encryptedLen = ctrCipher.update(plainBuf, 0, filled, cipherBuf, 0)
                            if (encryptedLen > 0) out.write(cipherBuf, 0, encryptedLen)
                            totalWritten += filled
                            onProgress?.invoke(totalWritten, plaintextLength)
                        }
                        val tail = ctrCipher.doFinal()
                        if (tail.isNotEmpty()) out.write(tail)
                    }

                    else -> throw IllegalArgumentException("Unsupported cipher version: $cipherVersion")
                }
                out.flush()
            }

            if (outputFile.exists() && !outputFile.delete()) {
                throw IOException("Failed to replace existing vault file: ${outputFile.name}")
            }
            if (!tempFile.renameTo(outputFile)) {
                throw IOException("Failed to finalize vault file: ${outputFile.name}")
            }
            return header
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        } finally {
            plainBuf.fill(0)
            itemKey.fill(0)
        }
    }

    /**
     * Verifies header MAC, container size, and test-decrypts chunk 0 (if non-empty) so the caller
     * knows the written vault file is readable before deleting the unencrypted source.
     */
    @Throws(IOException::class, SecurityException::class)
    fun verifyFirstChunkAndHeader(file: File, rootKey: ByteArray, uuid: String) {
        val header = VaultHeader.readAndVerify(file, rootKey)
        if (header.payloadLength == 0L) return
        val itemKey = header.deriveItemKey(rootKey, uuid)
        val keySpec = SecretKeySpec(itemKey, "AES")
        val firstChunkPlainLen = minOf(header.chunkSize.toLong(), header.payloadLength).toInt()
        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(VaultHeader.HEADER_SIZE.toLong())
                when (header.version) {
                    VaultHeader.VERSION_GCM_CHUNKS -> {
                        val enc = ByteArray(firstChunkPlainLen + VaultCryptoPrimitives.GCM_TAG_BYTES)
                        raf.readFully(enc)
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(
                            Cipher.DECRYPT_MODE,
                            keySpec,
                            GCMParameterSpec(
                                VaultCryptoPrimitives.GCM_TAG_BITS,
                                VaultCryptoPrimitives.gcmChunkNonce(header.baseNonce, 0L)
                            )
                        )
                        cipher.updateAAD(VaultCryptoPrimitives.gcmChunkAad(uuid, 0L))
                        val plain = cipher.doFinal(enc)
                        plain.fill(0)
                    }
                    VaultHeader.VERSION_CTR -> {
                        val enc = ByteArray(firstChunkPlainLen)
                        raf.readFully(enc)
                        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(header.baseNonce))
                        val plain = cipher.doFinal(enc)
                        plain.fill(0)
                    }
                }
            }
        } finally {
            itemKey.fill(0)
        }
    }

    /**
     * Streams decrypted plaintext chunks out of a vault video container (used only on explicit user
     * "Export / Restore to Public Storage").
     */
    @Throws(IOException::class, SecurityException::class)
    fun decryptToStream(
        file: File,
        rootKey: ByteArray,
        uuid: String,
        output: OutputStream,
        onProgress: ((writtenBytes: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val header = VaultHeader.readAndVerify(file, rootKey)
        if (header.payloadLength == 0L) return
        val itemKey = header.deriveItemKey(rootKey, uuid)
        val keySpec = SecretKeySpec(itemKey, "AES")
        var totalRead = 0L

        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(VaultHeader.HEADER_SIZE.toLong())
                when (header.version) {
                    VaultHeader.VERSION_GCM_CHUNKS -> {
                        val gcmCipher = Cipher.getInstance("AES/GCM/NoPadding")
                        var chunkIndex = 0L
                        while (totalRead < header.payloadLength) {
                            val plainLen = minOf(header.chunkSize.toLong(), header.payloadLength - totalRead).toInt()
                            val enc = ByteArray(plainLen + VaultCryptoPrimitives.GCM_TAG_BYTES)
                            raf.readFully(enc)
                            gcmCipher.init(
                                Cipher.DECRYPT_MODE,
                                keySpec,
                                GCMParameterSpec(
                                    VaultCryptoPrimitives.GCM_TAG_BITS,
                                    VaultCryptoPrimitives.gcmChunkNonce(header.baseNonce, chunkIndex)
                                )
                            )
                            gcmCipher.updateAAD(VaultCryptoPrimitives.gcmChunkAad(uuid, chunkIndex))
                            val plain = gcmCipher.doFinal(enc)
                            try {
                                output.write(plain)
                            } finally {
                                plain.fill(0)
                            }
                            totalRead += plainLen
                            chunkIndex++
                            onProgress?.invoke(totalRead, header.payloadLength)
                        }
                    }

                    VaultHeader.VERSION_CTR -> {
                        val ctrCipher = Cipher.getInstance("AES/CTR/NoPadding")
                        ctrCipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(header.baseNonce))
                        val buf = ByteArray(header.chunkSize)
                        val plain = ByteArray(header.chunkSize)
                        try {
                            while (totalRead < header.payloadLength) {
                                val toRead = minOf(header.chunkSize.toLong(), header.payloadLength - totalRead).toInt()
                                raf.readFully(buf, 0, toRead)
                                val decLen = ctrCipher.update(buf, 0, toRead, plain, 0)
                                if (decLen > 0) output.write(plain, 0, decLen)
                                totalRead += toRead
                                onProgress?.invoke(totalRead, header.payloadLength)
                            }
                            val tail = ctrCipher.doFinal()
                            if (tail.isNotEmpty()) output.write(tail)
                        } finally {
                            buf.fill(0)
                            plain.fill(0)
                        }
                    }
                }
            }
            output.flush()
        } finally {
            itemKey.fill(0)
        }
    }

    private fun readFullyUpTo(input: InputStream, buffer: ByteArray, targetBytes: Int): Int {
        var total = 0
        while (total < targetBytes) {
            val r = input.read(buffer, total, targetBytes - total)
            if (r < 0) break
            total += r
        }
        return total
    }
}
