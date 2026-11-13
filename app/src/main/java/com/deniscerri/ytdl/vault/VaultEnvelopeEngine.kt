package com.deniscerri.ytdl.vault

import com.deniscerri.ytdl.vault.crypto.VaultCryptoPrimitives
import com.deniscerri.ytdl.vault.crypto.VaultIngestSeal
import java.nio.charset.StandardCharsets

/**
 * Pure Bitwarden-style envelope KDF engine (context-free for clean JVM unit testing).
 */
object VaultEnvelopeEngine {

    private val PRIMARY_WRAP_AAD = "ytdlnis-vault-dek-v1".toByteArray(StandardCharsets.UTF_8)
    private val DECOY_WRAP_AAD = "ytdlnis-vault-decoy-dek-v1".toByteArray(StandardCharsets.UTF_8)
    private val INGEST_PRIV_AAD = "ytdlnis-vault-ingest-priv-v1".toByteArray(StandardCharsets.UTF_8)

    data class CreatedVault(
        val envelope: VaultEnvelope,
        val dek: ByteArray
    )

    sealed class UnlockOutcome {
        data class Success(
            val dek: ByteArray,
            val isDecoy: Boolean,
            val updatedEnvelope: VaultEnvelope
        ) : UnlockOutcome()

        data class LockedOut(val remainingMs: Long) : UnlockOutcome()

        data class WrongPassword(
            val updatedEnvelope: VaultEnvelope,
            val autoWiped: Boolean,
            val backoffMs: Long,
            val remainingBeforeWipe: Int?
        ) : UnlockOutcome()
    }

    fun createNew(
        passwordChars: CharArray,
        iterations: Int = VaultCryptoPrimitives.PBKDF2_DEFAULT_ITERATIONS
    ): CreatedVault {
        val dek = VaultCryptoPrimitives.randomBytes(VaultCryptoPrimitives.AES_KEY_BYTES)
        val salt = VaultCryptoPrimitives.randomBytes(16)
        val (authHash, kek) = deriveAuthAndKek(passwordChars, salt, iterations)

        val wrapIv = VaultCryptoPrimitives.randomBytes(VaultCryptoPrimitives.GCM_IV_BYTES)
        val wrappedDekCt = VaultCryptoPrimitives.encryptGcm(kek, wrapIv, dek, PRIMARY_WRAP_AAD)
        kek.fill(0)

        val kp = VaultIngestSeal.generateKeyPair()
        val privSalt = VaultCryptoPrimitives.randomBytes(16)
        val privIv = VaultCryptoPrimitives.randomBytes(VaultCryptoPrimitives.GCM_IV_BYTES)
        val privWrapKey = VaultCryptoPrimitives.hkdfSha256(
            ikm = dek,
            salt = privSalt,
            info = "ytdlnis-vault-ingest-key-v1"
        )
        val privCt = try {
            VaultCryptoPrimitives.encryptGcm(privWrapKey, privIv, kp.privateKeyDer, INGEST_PRIV_AAD)
        } finally {
            privWrapKey.fill(0)
            kp.privateKeyDer.fill(0)
        }

        val envelope = VaultEnvelope(
            kdfIterations = iterations,
            vaultSaltB64 = VaultCryptoPrimitives.encodeBase64(salt),
            masterPasswordHashB64 = VaultCryptoPrimitives.encodeBase64(authHash),
            wrappedDekIvB64 = VaultCryptoPrimitives.encodeBase64(wrapIv),
            wrappedDekCtB64 = VaultCryptoPrimitives.encodeBase64(wrappedDekCt),
            ingestPublicKeyB64 = VaultCryptoPrimitives.encodeBase64(kp.publicKeyDer),
            ingestPrivateKeySaltB64 = VaultCryptoPrimitives.encodeBase64(privSalt),
            ingestPrivateKeyIvB64 = VaultCryptoPrimitives.encodeBase64(privIv),
            ingestPrivateKeyCtB64 = VaultCryptoPrimitives.encodeBase64(privCt)
        )
        return CreatedVault(envelope = envelope, dek = dek)
    }

    fun unlockWithPassword(
        envelope: VaultEnvelope,
        passwordChars: CharArray,
        nowMs: Long = System.currentTimeMillis()
    ): UnlockOutcome {
        if (envelope.lockoutUntilMs > nowMs) {
            return UnlockOutcome.LockedOut(envelope.lockoutUntilMs - nowMs)
        }

        // 1. Primary vault check
        val primarySalt = VaultCryptoPrimitives.decodeBase64(envelope.vaultSaltB64)
        val expectedPrimaryAuth = VaultCryptoPrimitives.decodeBase64(envelope.masterPasswordHashB64)
        val (primaryAuth, primaryKek) = deriveAuthAndKek(passwordChars, primarySalt, envelope.kdfIterations)
        val primaryMatch = VaultCryptoPrimitives.constantTimeEquals(primaryAuth, expectedPrimaryAuth)
        primaryAuth.fill(0)

        if (primaryMatch) {
            return try {
                val iv = VaultCryptoPrimitives.decodeBase64(envelope.wrappedDekIvB64)
                val ct = VaultCryptoPrimitives.decodeBase64(envelope.wrappedDekCtB64)
                val dek = VaultCryptoPrimitives.decryptGcm(primaryKek, iv, ct, PRIMARY_WRAP_AAD)
                val reset = envelope.copy(failedAttempts = 0, lockoutUntilMs = 0L)
                UnlockOutcome.Success(dek = dek, isDecoy = false, updatedEnvelope = reset)
            } finally {
                primaryKek.fill(0)
            }
        } else {
            primaryKek.fill(0)
        }

        // 2. Duress decoy vault check (if configured)
        if (envelope.hasDecoy()) {
            val decoySalt = VaultCryptoPrimitives.decodeBase64(envelope.decoySaltB64!!)
            val expectedDecoyAuth = VaultCryptoPrimitives.decodeBase64(envelope.decoyPasswordHashB64!!)
            val (decoyAuth, decoyKek) = deriveAuthAndKek(passwordChars, decoySalt, envelope.kdfIterations)
            val decoyMatch = VaultCryptoPrimitives.constantTimeEquals(decoyAuth, expectedDecoyAuth)
            decoyAuth.fill(0)
            if (decoyMatch) {
                return try {
                    val iv = VaultCryptoPrimitives.decodeBase64(envelope.decoyWrappedDekIvB64!!)
                    val ct = VaultCryptoPrimitives.decodeBase64(envelope.decoyWrappedDekCtB64!!)
                    val decoyDek = VaultCryptoPrimitives.decryptGcm(decoyKek, iv, ct, DECOY_WRAP_AAD)
                    val reset = envelope.copy(failedAttempts = 0, lockoutUntilMs = 0L)
                    UnlockOutcome.Success(dek = decoyDek, isDecoy = true, updatedEnvelope = reset)
                } finally {
                    decoyKek.fill(0)
                }
            } else {
                decoyKek.fill(0)
            }
        }

        // 3. Mismatch: exponential backoff + optional auto-wipe threshold
        val attempts = envelope.failedAttempts + 1
        val wipeThreshold = envelope.autoWipeThreshold
        if (wipeThreshold > 0 && attempts >= wipeThreshold) {
            return UnlockOutcome.WrongPassword(
                updatedEnvelope = envelope.copy(failedAttempts = attempts),
                autoWiped = true,
                backoffMs = 0L,
                remainingBeforeWipe = 0
            )
        }

        val backoffSec = if (attempts >= 3) {
            val shift = (attempts - 3).coerceAtMost(5) // up to 32s cap (30s practical)
            (1 shl shift).coerceAtMost(30)
        } else {
            0
        }
        val backoffMs = backoffSec * 1000L
        val updated = envelope.copy(
            failedAttempts = attempts,
            lockoutUntilMs = if (backoffMs > 0L) nowMs + backoffMs else 0L
        )
        val remaining = if (wipeThreshold > 0) (wipeThreshold - attempts).coerceAtLeast(0) else null
        return UnlockOutcome.WrongPassword(
            updatedEnvelope = updated,
            autoWiped = false,
            backoffMs = backoffMs,
            remainingBeforeWipe = remaining
        )
    }

    /** Instant (~20 ms KDF only) master password rotation without touching video payloads. */
    fun rewrapPrimaryPassword(
        envelope: VaultEnvelope,
        currentDek: ByteArray,
        newPasswordChars: CharArray
    ): VaultEnvelope {
        val newSalt = VaultCryptoPrimitives.randomBytes(16)
        val (newAuth, newKek) = deriveAuthAndKek(newPasswordChars, newSalt, envelope.kdfIterations)
        return try {
            val iv = VaultCryptoPrimitives.randomBytes(VaultCryptoPrimitives.GCM_IV_BYTES)
            val ct = VaultCryptoPrimitives.encryptGcm(newKek, iv, currentDek, PRIMARY_WRAP_AAD)
            envelope.copy(
                vaultSaltB64 = VaultCryptoPrimitives.encodeBase64(newSalt),
                masterPasswordHashB64 = VaultCryptoPrimitives.encodeBase64(newAuth),
                wrappedDekIvB64 = VaultCryptoPrimitives.encodeBase64(iv),
                wrappedDekCtB64 = VaultCryptoPrimitives.encodeBase64(ct),
                failedAttempts = 0,
                lockoutUntilMs = 0L
            )
        } finally {
            newAuth.fill(0)
            newKek.fill(0)
        }
    }

    fun setDecoyPassword(
        envelope: VaultEnvelope,
        decoyPasswordChars: CharArray
    ): VaultEnvelope {
        val decoyDek = VaultCryptoPrimitives.randomBytes(VaultCryptoPrimitives.AES_KEY_BYTES)
        val decoySalt = VaultCryptoPrimitives.randomBytes(16)
        val (decoyAuth, decoyKek) = deriveAuthAndKek(decoyPasswordChars, decoySalt, envelope.kdfIterations)
        return try {
            val iv = VaultCryptoPrimitives.randomBytes(VaultCryptoPrimitives.GCM_IV_BYTES)
            val ct = VaultCryptoPrimitives.encryptGcm(decoyKek, iv, decoyDek, DECOY_WRAP_AAD)
            envelope.copy(
                decoySaltB64 = VaultCryptoPrimitives.encodeBase64(decoySalt),
                decoyPasswordHashB64 = VaultCryptoPrimitives.encodeBase64(decoyAuth),
                decoyWrappedDekIvB64 = VaultCryptoPrimitives.encodeBase64(iv),
                decoyWrappedDekCtB64 = VaultCryptoPrimitives.encodeBase64(ct)
            )
        } finally {
            decoyDek.fill(0)
            decoyAuth.fill(0)
            decoyKek.fill(0)
        }
    }

    fun withStealthTrigger(
        envelope: VaultEnvelope,
        enabled: Boolean,
        pinChars: CharArray?
    ): VaultEnvelope {
        if (!enabled || pinChars == null || pinChars.isEmpty()) {
            return envelope.copy(
                stealthEnabled = false,
                stealthPinSaltB64 = null,
                stealthPinHmacB64 = null
            )
        }
        val salt = VaultCryptoPrimitives.randomBytes(16)
        val pinBytes = VaultCryptoPrimitives.charsToUtf8Bytes(pinChars)
        return try {
            val mac = VaultCryptoPrimitives.hmacSha256(salt, pinBytes)
            envelope.copy(
                stealthEnabled = true,
                stealthPinSaltB64 = VaultCryptoPrimitives.encodeBase64(salt),
                stealthPinHmacB64 = VaultCryptoPrimitives.encodeBase64(mac)
            )
        } finally {
            pinBytes.fill(0)
        }
    }

    fun matchesStealthPin(envelope: VaultEnvelope, candidate: String): Boolean {
        if (!envelope.stealthEnabled || !envelope.hasStealthPin()) return false
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return false
        val salt = VaultCryptoPrimitives.decodeBase64(envelope.stealthPinSaltB64!!)
        val expected = VaultCryptoPrimitives.decodeBase64(envelope.stealthPinHmacB64!!)
        val candBytes = trimmed.toByteArray(StandardCharsets.UTF_8)
        return try {
            val mac = VaultCryptoPrimitives.hmacSha256(salt, candBytes)
            VaultCryptoPrimitives.constantTimeEquals(expected, mac)
        } finally {
            candBytes.fill(0)
        }
    }

    fun decryptIngestPrivateKey(envelope: VaultEnvelope, primaryDek: ByteArray): ByteArray {
        val privSalt = VaultCryptoPrimitives.decodeBase64(envelope.ingestPrivateKeySaltB64)
        val privIv = VaultCryptoPrimitives.decodeBase64(envelope.ingestPrivateKeyIvB64)
        val privCt = VaultCryptoPrimitives.decodeBase64(envelope.ingestPrivateKeyCtB64)
        val privWrapKey = VaultCryptoPrimitives.hkdfSha256(
            ikm = primaryDek,
            salt = privSalt,
            info = "ytdlnis-vault-ingest-key-v1"
        )
        return try {
            VaultCryptoPrimitives.decryptGcm(privWrapKey, privIv, privCt, INGEST_PRIV_AAD)
        } finally {
            privWrapKey.fill(0)
        }
    }

    private fun deriveAuthAndKek(
        passwordChars: CharArray,
        salt: ByteArray,
        iterations: Int
    ): Pair<ByteArray, ByteArray> {
        val masterKey = VaultCryptoPrimitives.pbkdf2HmacSha256(passwordChars, salt, iterations)
        return try {
            val authHash = VaultCryptoPrimitives.hkdfSha256(
                ikm = masterKey,
                salt = salt,
                info = "ytdlnis-vault-auth-v1"
            )
            val kek = VaultCryptoPrimitives.hkdfSha256(
                ikm = masterKey,
                salt = salt,
                info = "ytdlnis-vault-kek-v1"
            )
            authHash to kek
        } finally {
            masterKey.fill(0)
        }
    }
}
