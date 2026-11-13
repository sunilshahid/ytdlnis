package com.deniscerri.ytdl.vault.crypto

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Low-level cryptographic primitives for the Private Vault.
 *
 * Implemented with standard JCE/Conscrypt primitives (`HmacSHA256`, `AES/GCM/NoPadding`,
 * `SecureRandom`) so it runs identically on Android API 24–36 and local JVM unit tests.
 */
object VaultCryptoPrimitives {

    const val AES_KEY_BYTES = 32
    const val GCM_IV_BYTES = 12
    const val GCM_TAG_BITS = 128
    const val GCM_TAG_BYTES = 16
    const val PBKDF2_DEFAULT_ITERATIONS = 600_000

    private val secureRandom = SecureRandom()

    fun randomBytes(length: Int): ByteArray = ByteArray(length).also { secureRandom.nextBytes(it) }

    /**
     * Converts a passphrase [CharArray] to UTF-8 bytes without allocating an immutable [String],
     * wiping intermediate [ByteBuffer] memory after copying.
     */
    fun charsToUtf8Bytes(chars: CharArray): ByteArray {
        val bb = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val out = ByteArray(bb.remaining())
        bb.get(out)
        if (bb.hasArray()) {
            bb.array().fill(0)
        }
        return out
    }

    /**
     * RFC 2898 PBKDF2-HMAC-SHA256.
     * Implemented over [Mac] ("HmacSHA256") rather than [javax.crypto.SecretKeyFactory] because
     * `PBKDF2WithHmacSHA256` was not present in Android Conscrypt prior to API 26 (project minSdk is 24).
     */
    fun pbkdf2HmacSha256(
        passwordChars: CharArray,
        salt: ByteArray,
        iterations: Int,
        outputBytes: Int = AES_KEY_BYTES
    ): ByteArray {
        require(iterations > 0) { "PBKDF2 iterations must be positive" }
        require(outputBytes > 0) { "outputBytes must be positive" }

        val passwordBytes = charsToUtf8Bytes(passwordChars)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
        passwordBytes.fill(0)

        val hashLen = 32
        val blocks = (outputBytes + hashLen - 1) / hashLen
        val derived = ByteArray(blocks * hashLen)
        val u = ByteArray(hashLen)
        val blockSalt = ByteArray(salt.size + 4)
        System.arraycopy(salt, 0, blockSalt, 0, salt.size)

        try {
            for (blockIndex in 1..blocks) {
                blockSalt[salt.size] = (blockIndex ushr 24).toByte()
                blockSalt[salt.size + 1] = (blockIndex ushr 16).toByte()
                blockSalt[salt.size + 2] = (blockIndex ushr 8).toByte()
                blockSalt[salt.size + 3] = blockIndex.toByte()

                var digest = mac.doFinal(blockSalt)
                System.arraycopy(digest, 0, u, 0, hashLen)
                val destOffset = (blockIndex - 1) * hashLen
                System.arraycopy(u, 0, derived, destOffset, hashLen)

                for (i in 2..iterations) {
                    digest = mac.doFinal(u)
                    System.arraycopy(digest, 0, u, 0, hashLen)
                    for (j in 0 until hashLen) {
                        derived[destOffset + j] = (derived[destOffset + j].toInt() xor u[j].toInt()).toByte()
                    }
                }
            }
            return derived.copyOf(outputBytes)
        } finally {
            derived.fill(0)
            u.fill(0)
            blockSalt.fill(0)
        }
    }

    /** RFC 5869 HKDF-SHA256 (Extract + Expand). */
    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputBytes: Int = AES_KEY_BYTES
    ): ByteArray {
        require(outputBytes in 1..(255 * 32)) { "Invalid HKDF output length: $outputBytes" }
        val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val extractMac = Mac.getInstance("HmacSHA256")
        extractMac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        val prk = extractMac.doFinal(ikm)

        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))

        val out = ByteArray(outputBytes)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        try {
            while (pos < outputBytes) {
                expandMac.update(t)
                expandMac.update(info)
                expandMac.update(counter.toByte())
                t = expandMac.doFinal()
                val copyLen = minOf(t.size, outputBytes - pos)
                System.arraycopy(t, 0, out, pos, copyLen)
                pos += copyLen
                counter++
            }
            return out
        } finally {
            prk.fill(0)
            t.fill(0)
        }
    }

    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: String,
        outputBytes: Int = AES_KEY_BYTES
    ): ByteArray = hkdfSha256(ikm, salt, info.toByteArray(StandardCharsets.UTF_8), outputBytes)

    fun hmacSha256(key: ByteArray, data: ByteArray, offset: Int = 0, length: Int = data.size): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update(data, offset, length)
        return mac.doFinal()
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun encryptGcm(
        key: ByteArray,
        iv: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        require(key.size == AES_KEY_BYTES) { "Expected 32-byte AES key" }
        require(iv.size == GCM_IV_BYTES) { "Expected 12-byte GCM IV" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun decryptGcm(
        key: ByteArray,
        iv: ByteArray,
        ciphertextWithTag: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        require(key.size == AES_KEY_BYTES) { "Expected 32-byte AES key" }
        require(iv.size == GCM_IV_BYTES) { "Expected 12-byte GCM IV" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertextWithTag)
    }

    fun uint64Be(value: Long): ByteArray {
        val buf = ByteBuffer.allocate(8)
        buf.putLong(value)
        return buf.array()
    }

    /**
     * Derives the 12-byte nonce for chunk `chunkIndex` by taking [baseNonce16]`[0..11]` and
     * XORing bytes `4..11` with `uint64be(chunkIndex)`.
     */
    fun gcmChunkNonce(baseNonce16: ByteArray, chunkIndex: Long): ByteArray {
        require(baseNonce16.size >= GCM_IV_BYTES)
        val out = baseNonce16.copyOfRange(0, GCM_IV_BYTES)
        for (b in 0 until 8) {
            val shift = (7 - b) * 8
            val idxByte = ((chunkIndex ushr shift) and 0xFFL).toInt()
            out[4 + b] = (out[4 + b].toInt() xor idxByte).toByte()
        }
        return out
    }

    fun gcmChunkAad(uuid: String, chunkIndex: Long): ByteArray {
        val uuidBytes = uuid.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArray(uuidBytes.size + 1 + 8)
        System.arraycopy(uuidBytes, 0, out, 0, uuidBytes.size)
        out[uuidBytes.size] = 0x00
        val idxBytes = uint64Be(chunkIndex)
        System.arraycopy(idxBytes, 0, out, uuidBytes.size + 1, 8)
        return out
    }

    /**
     * 128-bit big-endian addition: `(baseNonce16 + floor(position / 16)) mod 2^128`.
     * Used exclusively by CTR fallback streams.
     */
    fun counterBlockAt(baseNonce16: ByteArray, position: Long): ByteArray {
        require(baseNonce16.size == 16)
        require(position >= 0L)
        val out = baseNonce16.copyOf()
        var carry = position ushr 4 // position / 16
        for (i in 15 downTo 0) {
            if (carry == 0L) break
            val sum = (out[i].toLong() and 0xFFL) + (carry and 0xFFL)
            out[i] = sum.toByte()
            carry = (carry ushr 8) + (sum ushr 8)
        }
        return out
    }

    private const val B64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val B64_INV = IntArray(128) { -1 }.apply {
        for (i in B64_ALPHABET.indices) this[B64_ALPHABET[i].code] = i
    }

    /** Dependency-free Base64 encoder that runs identically on API 24+ and JVM unit tests. */
    fun encodeBase64(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i++].toInt() and 0xFF
            val hasB1 = i < data.size
            val b1 = if (hasB1) (data[i++].toInt() and 0xFF) else 0
            val hasB2 = i < data.size
            val b2 = if (hasB2) (data[i++].toInt() and 0xFF) else 0
            val triple = (b0 shl 16) or (b1 shl 8) or b2
            sb.append(B64_ALPHABET[(triple ushr 18) and 0x3F])
            sb.append(B64_ALPHABET[(triple ushr 12) and 0x3F])
            sb.append(if (hasB1) B64_ALPHABET[(triple ushr 6) and 0x3F] else '=')
            sb.append(if (hasB2) B64_ALPHABET[triple and 0x3F] else '=')
        }
        return sb.toString()
    }

    fun decodeBase64(input: String): ByteArray {
        val clean = input.trim()
        if (clean.isEmpty()) return ByteArray(0)
        var validCount = 0
        for (ch in clean) {
            if (!ch.isWhitespace()) validCount++
        }
        require(validCount % 4 == 0) { "Invalid Base64 length" }
        val padding = when {
            clean.endsWith("==") -> 2
            clean.endsWith("=") -> 1
            else -> 0
        }
        val out = ByteArray((validCount / 4) * 3 - padding)
        var outIdx = 0
        var acc = 0
        var bits = 0
        for (ch in clean) {
            if (ch.isWhitespace()) continue
            if (ch == '=') break
            val code = ch.code
            val val6 = if (code in 0..127) B64_INV[code] else -1
            require(val6 >= 0) { "Invalid Base64 character" }
            acc = (acc shl 6) or val6
            bits += 6
            if (bits >= 8) {
                bits -= 8
                if (outIdx < out.size) {
                    out[outIdx++] = ((acc ushr bits) and 0xFF).toByte()
                }
            }
        }
        return out
    }
}
