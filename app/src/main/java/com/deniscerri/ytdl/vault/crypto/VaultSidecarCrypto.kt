package com.deniscerri.ytdl.vault.crypto

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Encrypts and decrypts authenticated vault sidecars (`{uuid}.meta.enc`, `{uuid}.thumb.enc`,
 * `{uuid}.itemkey.enc`, `vault_settings.enc`).
 *
 * Binary layout: `[16B salt][12B IV][AES-256-GCM ciphertext + 16B tag]`
 * Key: `HKDF-SHA256(rootKey, salt, "ytdlnis-vault-sidecar-v1:$purpose")`
 * AAD: `"$itemId|$purpose"` so sidecars cannot be swapped between items or purposes.
 */
object VaultSidecarCrypto {

    private const val SALT_BYTES = 16
    private const val IV_BYTES = VaultCryptoPrimitives.GCM_IV_BYTES
    private const val MIN_BYTES = SALT_BYTES + IV_BYTES + VaultCryptoPrimitives.GCM_TAG_BYTES

    const val PURPOSE_META = "meta"
    const val PURPOSE_THUMB = "thumb"
    const val PURPOSE_ITEM_KEY = "itemkey"
    const val PURPOSE_SETTINGS = "settings"

    fun encryptToBytes(
        rootKey: ByteArray,
        itemId: String,
        purpose: String,
        plaintext: ByteArray
    ): ByteArray {
        val salt = VaultCryptoPrimitives.randomBytes(SALT_BYTES)
        val iv = VaultCryptoPrimitives.randomBytes(IV_BYTES)
        val sidecarKey = VaultCryptoPrimitives.hkdfSha256(
            ikm = rootKey,
            salt = salt,
            info = "ytdlnis-vault-sidecar-v1:$purpose"
        )
        val aad = "$itemId|$purpose".toByteArray(StandardCharsets.UTF_8)
        return try {
            val cipherBytes = VaultCryptoPrimitives.encryptGcm(sidecarKey, iv, plaintext, aad)
            ByteArray(SALT_BYTES + IV_BYTES + cipherBytes.size).apply {
                System.arraycopy(salt, 0, this, 0, SALT_BYTES)
                System.arraycopy(iv, 0, this, SALT_BYTES, IV_BYTES)
                System.arraycopy(cipherBytes, 0, this, SALT_BYTES + IV_BYTES, cipherBytes.size)
            }
        } finally {
            sidecarKey.fill(0)
        }
    }

    fun decryptFromBytes(
        rootKey: ByteArray,
        itemId: String,
        purpose: String,
        blob: ByteArray
    ): ByteArray {
        if (blob.size < MIN_BYTES) {
            throw SecurityException("Corrupt vault sidecar ($purpose for $itemId)")
        }
        val salt = blob.copyOfRange(0, SALT_BYTES)
        val iv = blob.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
        val cipherBytes = blob.copyOfRange(SALT_BYTES + IV_BYTES, blob.size)
        val sidecarKey = VaultCryptoPrimitives.hkdfSha256(
            ikm = rootKey,
            salt = salt,
            info = "ytdlnis-vault-sidecar-v1:$purpose"
        )
        val aad = "$itemId|$purpose".toByteArray(StandardCharsets.UTF_8)
        return try {
            VaultCryptoPrimitives.decryptGcm(sidecarKey, iv, cipherBytes, aad)
        } finally {
            sidecarKey.fill(0)
        }
    }

    fun writeEncryptedFile(
        targetFile: File,
        rootKey: ByteArray,
        itemId: String,
        purpose: String,
        plaintext: ByteArray
    ) {
        targetFile.parentFile?.mkdirs()
        val blob = encryptToBytes(rootKey, itemId, purpose, plaintext)
        val temp = File(targetFile.parentFile, "${targetFile.name}.tmp")
        FileOutputStream(temp).use { it.write(blob) }
        if (targetFile.exists() && !targetFile.delete()) {
            temp.delete()
            throw IOException("Failed to overwrite vault sidecar: ${targetFile.name}")
        }
        if (!temp.renameTo(targetFile)) {
            temp.delete()
            throw IOException("Failed to finalize vault sidecar: ${targetFile.name}")
        }
    }

    fun readEncryptedFile(
        sourceFile: File,
        rootKey: ByteArray,
        itemId: String,
        purpose: String
    ): ByteArray {
        if (!sourceFile.exists()) throw IOException("Missing vault sidecar: ${sourceFile.name}")
        return decryptFromBytes(rootKey, itemId, purpose, sourceFile.readBytes())
    }
}
