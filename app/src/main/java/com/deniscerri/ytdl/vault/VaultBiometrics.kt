package com.deniscerri.ytdl.vault

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.deniscerri.ytdl.vault.crypto.VaultCryptoPrimitives
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed biometric DEK wrapping via AndroidKeyStore + `BiometricPrompt.CryptoObject`.
 *
 * Keys are generated with `setInvalidatedByBiometricEnrollment(true)` so adding a new device
 * fingerprint or face invalidates the vault's biometric unlock key and forces Master Password
 * re-verification.
 */
object VaultBiometrics {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "ytdlnis_vault_bio_v1"
    private val BIO_AAD = "ytdlnis-vault-bio-dek-v1".toByteArray(StandardCharsets.UTF_8)

    fun isSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bm = context.getSystemService(BiometricManager::class.java) ?: return false
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bm = context.getSystemService(BiometricManager::class.java) ?: return false
            @Suppress("DEPRECATION")
            bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        }
    }

    fun deleteKey(@Suppress("UNUSED_PARAMETER") context: Context) {
        runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) {
                ks.deleteEntry(KEY_ALIAS)
            }
        }
    }

    private fun generateFreshKey(): SecretKey {
        deleteKey(AppHolder)
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0, // require biometric auth on every single use
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        gen.init(builder.build())
        return gen.generateKey()
    }

    fun createEnrollCipher(): Cipher? = runCatching {
        val key = generateFreshKey()
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }.getOrNull()

    fun createUnlockCipher(iv: ByteArray): UnlockCipherResult {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val key = ks.getKey(KEY_ALIAS, null) as? SecretKey ?: return UnlockCipherResult.Invalidated
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            UnlockCipherResult.Ready(cipher)
        } catch (_: KeyPermanentlyInvalidatedException) {
            UnlockCipherResult.Invalidated
        } catch (_: UnrecoverableKeyException) {
            UnlockCipherResult.Invalidated
        } catch (_: InvalidKeyException) {
            UnlockCipherResult.Invalidated
        } catch (_: KeyStoreException) {
            UnlockCipherResult.Invalidated
        } catch (t: Throwable) {
            val msg = t.message.orEmpty().lowercase()
            if (msg.contains("invalidat") || msg.contains("biometric") || msg.contains("permanently")) {
                UnlockCipherResult.Invalidated
            } else {
                UnlockCipherResult.Error(t.message ?: "Biometric key error")
            }
        }
    }

    /** Synchronously verifies whether the enrolled hardware biometric key is still valid. */
    fun checkEnrolledKeyStillValid(context: Context, ivB64: String?): Boolean {
        if (!isSupported(context)) return false
        val iv = ivB64?.let { runCatching { VaultCryptoPrimitives.decodeBase64(it) }.getOrNull() }
            ?: return false
        return when (createUnlockCipher(iv)) {
            is UnlockCipherResult.Ready -> true
            is UnlockCipherResult.Invalidated -> {
                deleteKey(context)
                false
            }
            is UnlockCipherResult.Error -> true
        }
    }

    sealed class UnlockCipherResult {
        data class Ready(val cipher: Cipher) : UnlockCipherResult()
        data object Invalidated : UnlockCipherResult()
        data class Error(val message: String) : UnlockCipherResult()
    }

    /** Authenticate with hardware CryptoObject to wrap a fresh biometric copy of [dek]. */
    fun enrollBiometricDek(
        context: Context,
        title: String,
        subtitle: String,
        cancelText: String,
        dek: ByteArray,
        onResult: (Result<Pair<String, String>>) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onResult(Result.failure(UnsupportedOperationException("Requires Android 9+")))
            return
        }
        val cipher = createEnrollCipher()
        if (cipher == null) {
            onResult(Result.failure(IllegalStateException("Could not initialize hardware key")))
            return
        }

        val cancelSignal = CancellationSignal()
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButton(cancelText, context.mainExecutor) { _, _ ->
                cancelSignal.cancel()
                onResult(Result.failure(SecurityException("Canceled")))
            }
            .build()

        prompt.authenticate(
            BiometricPrompt.CryptoObject(cipher),
            cancelSignal,
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authedCipher = result.cryptoObject?.cipher
                    if (authedCipher == null) {
                        onResult(Result.failure(SecurityException("Missing CryptoObject cipher")))
                        return
                    }
                    runCatching {
                        authedCipher.updateAAD(BIO_AAD)
                        val iv = authedCipher.iv
                        val ct = authedCipher.doFinal(dek)
                        Pair(
                            VaultCryptoPrimitives.encodeBase64(iv),
                            VaultCryptoPrimitives.encodeBase64(ct)
                        )
                    }.fold(
                        onSuccess = { onResult(Result.success(it)) },
                        onFailure = { onResult(Result.failure(it)) }
                    )
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onResult(Result.failure(SecurityException(errString?.toString() ?: "Biometric error")))
                }
            }
        )
    }

    /** Authenticate with hardware CryptoObject to unwrap the vault DEK into volatile RAM. */
    fun unlockWithBiometrics(
        context: Context,
        title: String,
        subtitle: String,
        cancelText: String,
        ivB64: String,
        ctB64: String,
        onSuccess: (ByteArray) -> Unit,
        onInvalidated: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError("Biometrics require Android 9+")
            return
        }
        val iv = runCatching { VaultCryptoPrimitives.decodeBase64(ivB64) }.getOrNull()
        val ct = runCatching { VaultCryptoPrimitives.decodeBase64(ctB64) }.getOrNull()
        if (iv == null || ct == null) {
            onInvalidated()
            return
        }

        when (val res = createUnlockCipher(iv)) {
            is UnlockCipherResult.Invalidated -> {
                deleteKey(context)
                onInvalidated()
                return
            }
            is UnlockCipherResult.Error -> {
                onError(res.message)
                return
            }
            is UnlockCipherResult.Ready -> {
                val cancelSignal = CancellationSignal()
                val prompt = BiometricPrompt.Builder(context)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButton(cancelText, context.mainExecutor) { _, _ ->
                        cancelSignal.cancel()
                        onError("Canceled")
                    }
                    .build()

                prompt.authenticate(
                    BiometricPrompt.CryptoObject(res.cipher),
                    cancelSignal,
                    context.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val authedCipher = result.cryptoObject?.cipher
                            if (authedCipher == null) {
                                onError("Missing CryptoObject cipher")
                                return
                            }
                            try {
                                authedCipher.updateAAD(BIO_AAD)
                                val dek = authedCipher.doFinal(ct)
                                onSuccess(dek)
                            } catch (_: Throwable) {
                                deleteKey(context)
                                onInvalidated()
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS ||
                                errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE
                            ) {
                                deleteKey(context)
                                onInvalidated()
                            } else {
                                onError(errString?.toString() ?: "Biometric authentication failed")
                            }
                        }
                    }
                )
            }
        }
    }

    // Internal dummy context placeholder because deleteEntry doesn't actually need a Context.
    private val AppHolder: Context get() = com.deniscerri.ytdl.App.instance
}
