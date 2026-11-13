package com.deniscerri.ytdl.vault.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Asymmetric public-key sealing for background Flow A vault downloads queued or completed while the
 * vault is locked.
 *
 * The RSA-2048 public key is stored in the vault envelope so background workers can seal a fresh
 * 32-byte per-item secret (`{uuid}.wrap.enc`) without ever touching `vaultDEK` while locked. The
 * matching private key is encrypted under `vaultDEK` and only decrypted after the user unlocks.
 */
object VaultIngestSeal {

    private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private val OAEP_SPEC = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA1,
        PSource.PSpecified.DEFAULT
    )

    data class KeyPairEncoded(
        val publicKeyDer: ByteArray,
        val privateKeyDer: ByteArray
    )

    fun generateKeyPair(): KeyPairEncoded {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val pair = gen.generateKeyPair()
        return KeyPairEncoded(
            publicKeyDer = pair.public.encoded,
            privateKeyDer = pair.private.encoded
        )
    }

    fun sealSecret(publicKeyDer: ByteArray, secretBytes: ByteArray): ByteArray {
        val pub: PublicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(publicKeyDer))
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, pub, OAEP_SPEC)
        return cipher.doFinal(secretBytes)
    }

    fun unsealSecret(privateKeyDer: ByteArray, sealedBytes: ByteArray): ByteArray {
        val priv: PrivateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(privateKeyDer))
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, priv, OAEP_SPEC)
        return cipher.doFinal(sealedBytes)
    }

    fun encodeBase64(bytes: ByteArray): String =
        VaultCryptoPrimitives.encodeBase64(bytes)

    fun decodeBase64(str: String): ByteArray =
        VaultCryptoPrimitives.decodeBase64(str)
}
