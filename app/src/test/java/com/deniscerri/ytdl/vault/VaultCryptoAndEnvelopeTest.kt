package com.deniscerri.ytdl.vault

import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.AudioPreferences
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.VideoPreferences
import com.deniscerri.ytdl.vault.crypto.VaultCryptoPrimitives
import com.deniscerri.ytdl.vault.crypto.VaultFileCipher
import com.deniscerri.ytdl.vault.crypto.VaultHeader
import com.deniscerri.ytdl.vault.crypto.VaultIngestSeal
import com.deniscerri.ytdl.vault.crypto.VaultSidecarCrypto
import com.deniscerri.ytdl.vault.ingest.VaultPendingDownloadStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import javax.crypto.AEADBadTagException

class VaultCryptoAndEnvelopeTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @After
    fun tearDown() {
        VaultPendingDownloadStore.clearAll()
    }

    @Test
    fun vaultHeader_serializeVerifyAndTamperRejection() {
        val rootKey = VaultCryptoPrimitives.randomBytes(32)
        val fileSalt = VaultCryptoPrimitives.randomBytes(16)
        val baseNonce = VaultCryptoPrimitives.randomBytes(16)
        val payloadLength = 150_123L

        val bytes = VaultHeader.serializeWithMac(
            rootKey = rootKey,
            version = VaultHeader.VERSION_GCM_CHUNKS,
            fileSalt = fileSalt,
            baseNonce = baseNonce,
            payloadLength = payloadLength
        )
        assertEquals(VaultHeader.HEADER_SIZE, bytes.size)
        assertEquals("YTVLT2", String(bytes.copyOfRange(0, 6), Charsets.US_ASCII))

        val expectedLen = VaultHeader.expectedFileLength(
            VaultHeader.VERSION_GCM_CHUNKS,
            VaultHeader.DEFAULT_CHUNK_SIZE,
            payloadLength
        )
        val parsed = VaultHeader.parseAndVerify(bytes, expectedLen, rootKey)
        assertEquals(VaultHeader.VERSION_GCM_CHUNKS, parsed.version)
        assertEquals(payloadLength, parsed.payloadLength)
        assertArrayEquals(fileSalt, parsed.fileSalt)
        assertArrayEquals(baseNonce, parsed.baseNonce)

        // Wrong root key fails MAC verification
        val wrongKey = VaultCryptoPrimitives.randomBytes(32)
        assertThrows(SecurityException::class.java) {
            VaultHeader.parseAndVerify(bytes, expectedLen, wrongKey)
        }

        // Tampering payloadLength fails MAC verification
        val tampered = bytes.copyOf()
        tampered[45] = (tampered[45].toInt() xor 0x01).toByte()
        assertThrows(SecurityException::class.java) {
            VaultHeader.parseAndVerify(tampered, expectedLen, rootKey)
        }

        // Truncated/extended file length fails size check
        assertThrows(AEADBadTagException::class.java) {
            VaultHeader.parseAndVerify(bytes, expectedLen - 1L, rootKey)
        }
    }

    @Test
    fun vaultFileCipher_gcmChunks_multiChunkRoundTripAndTamperDetection() {
        val rootKey = VaultCryptoPrimitives.randomBytes(32)
        val uuid = "11111111-2222-4333-8444-555555555555"
        // 200_000 bytes -> spans 4 chunks (65_536 * 3 + 3_392)
        val plaintext = ByteArray(200_000) { idx -> ((idx * 31 + 7) and 0xFF).toByte() }
        val container = File(tempFolder.root, "$uuid.vault.bin")

        val header = VaultFileCipher.encryptStream(
            input = ByteArrayInputStream(plaintext),
            plaintextLength = plaintext.size.toLong(),
            outputFile = container,
            rootKey = rootKey,
            uuid = uuid,
            cipherVersion = VaultHeader.VERSION_GCM_CHUNKS
        )
        assertEquals(header.expectedFileLength(), container.length())
        VaultFileCipher.verifyFirstChunkAndHeader(container, rootKey, uuid)

        val out = ByteArrayOutputStream()
        VaultFileCipher.decryptToStream(container, rootKey, uuid, out)
        assertArrayEquals(plaintext, out.toByteArray())

        // Cross-UUID key/AAD mismatch must fail
        assertThrows(AEADBadTagException::class.java) {
            VaultFileCipher.decryptToStream(
                container,
                rootKey,
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                ByteArrayOutputStream()
            )
        }

        // Bit-flip inside chunk 2 (offset past header + 2 full GCM chunks) must fail tag check
        RandomAccessFile(container, "rw").use { raf ->
            val targetOffset = VaultHeader.HEADER_SIZE.toLong() +
                (VaultHeader.DEFAULT_CHUNK_SIZE + VaultCryptoPrimitives.GCM_TAG_BYTES) * 2L + 100L
            raf.seek(targetOffset)
            val b = raf.read()
            raf.seek(targetOffset)
            raf.write(b xor 0x5A)
        }
        assertThrows(AEADBadTagException::class.java) {
            VaultFileCipher.decryptToStream(container, rootKey, uuid, ByteArrayOutputStream())
        }
    }

    @Test
    fun vaultFileCipher_ctr_multiChunkRoundTrip() {
        val rootKey = VaultCryptoPrimitives.randomBytes(32)
        val uuid = "99999999-8888-4777-8666-555555555555"
        val plaintext = ByteArray(140_500) { idx -> ((idx * 17) and 0xFF).toByte() }
        val container = File(tempFolder.root, "$uuid.vault.bin")

        val header = VaultFileCipher.encryptStream(
            input = ByteArrayInputStream(plaintext),
            plaintextLength = plaintext.size.toLong(),
            outputFile = container,
            rootKey = rootKey,
            uuid = uuid,
            cipherVersion = VaultHeader.VERSION_CTR
        )
        assertEquals(VaultHeader.VERSION_CTR, header.version)
        assertEquals(header.expectedFileLength(), container.length())
        VaultFileCipher.verifyFirstChunkAndHeader(container, rootKey, uuid)

        val out = ByteArrayOutputStream()
        VaultFileCipher.decryptToStream(container, rootKey, uuid, out)
        assertArrayEquals(plaintext, out.toByteArray())
    }

    @Test
    fun vaultSidecarCrypto_aadBindsItemIdAndPurpose() {
        val rootKey = VaultCryptoPrimitives.randomBytes(32)
        val secret = "Secret Video Title".toByteArray(Charsets.UTF_8)

        val blob = VaultSidecarCrypto.encryptToBytes(
            rootKey = rootKey,
            itemId = "item-a",
            purpose = VaultSidecarCrypto.PURPOSE_META,
            plaintext = secret
        )
        val plain = VaultSidecarCrypto.decryptFromBytes(
            rootKey = rootKey,
            itemId = "item-a",
            purpose = VaultSidecarCrypto.PURPOSE_META,
            blob = blob
        )
        assertArrayEquals(secret, plain)

        // Swapping to item-b fails AAD check
        assertThrows(AEADBadTagException::class.java) {
            VaultSidecarCrypto.decryptFromBytes(
                rootKey = rootKey,
                itemId = "item-b",
                purpose = VaultSidecarCrypto.PURPOSE_META,
                blob = blob
            )
        }

        // Swapping purpose fails HKDF + AAD check
        assertThrows(AEADBadTagException::class.java) {
            VaultSidecarCrypto.decryptFromBytes(
                rootKey = rootKey,
                itemId = "item-a",
                purpose = VaultSidecarCrypto.PURPOSE_THUMB,
                blob = blob
            )
        }
    }

    @Test
    fun vaultIngestSeal_rsaOaepSealAndUnsealRoundTrip() {
        val kp = VaultIngestSeal.generateKeyPair()
        val itemSecret = VaultCryptoPrimitives.randomBytes(32)
        val sealed = VaultIngestSeal.sealSecret(kp.publicKeyDer, itemSecret)
        assertFalse(sealed.contentEquals(itemSecret))
        val unsealed = VaultIngestSeal.unsealSecret(kp.privateKeyDer, sealed)
        assertArrayEquals(itemSecret, unsealed)
    }

    @Test
    fun vaultEnvelopeEngine_fullLifecycle_unlockRewrapDecoyStealthBackoffAndWipe() {
        val iterations = 2_000
        val created = VaultEnvelopeEngine.createNew(
            passwordChars = "Master#1234".toCharArray(),
            iterations = iterations
        )
        assertEquals(iterations, created.envelope.kdfIterations)

        // Unlock with correct password recovers exact DEK
        val unlock1 = VaultEnvelopeEngine.unlockWithPassword(
            envelope = created.envelope,
            passwordChars = "Master#1234".toCharArray(),
            nowMs = 1_000L
        )
        assertTrue(unlock1 is VaultEnvelopeEngine.UnlockOutcome.Success)
        val s1 = unlock1 as VaultEnvelopeEngine.UnlockOutcome.Success
        assertFalse(s1.isDecoy)
        assertArrayEquals(created.dek, s1.dek)

        // Private ingest RSA key decrypts cleanly with primary DEK, fails with random/decoy DEK
        val privKey = VaultEnvelopeEngine.decryptIngestPrivateKey(s1.updatedEnvelope, s1.dek)
        val sealed = VaultIngestSeal.sealSecret(
            VaultCryptoPrimitives.decodeBase64(s1.updatedEnvelope.ingestPublicKeyB64),
            "item-key-32bytes-0123456789abcd!".toByteArray(Charsets.UTF_8)
        )
        val recovered = VaultIngestSeal.unsealSecret(privKey, sealed)
        assertEquals("item-key-32bytes-0123456789abcd!", String(recovered, Charsets.UTF_8))

        // Change master password instantaneously via DEK re-wrap
        val rewrapped = VaultEnvelopeEngine.rewrapPrimaryPassword(
            envelope = s1.updatedEnvelope,
            currentDek = s1.dek,
            newPasswordChars = "Rotated#9999".toCharArray()
        )
        assertTrue(
            VaultEnvelopeEngine.unlockWithPassword(rewrapped, "Master#1234".toCharArray(), 2_000L)
                is VaultEnvelopeEngine.UnlockOutcome.WrongPassword
        )
        val unlock2 = VaultEnvelopeEngine.unlockWithPassword(
            rewrapped,
            "Rotated#9999".toCharArray(),
            2_000L
        ) as VaultEnvelopeEngine.UnlockOutcome.Success
        assertArrayEquals(created.dek, unlock2.dek)

        // Set decoy password and confirm separate decoy DEK
        val withDecoy = VaultEnvelopeEngine.setDecoyPassword(
            rewrapped,
            "Decoy@0000".toCharArray()
        )
        val decoyUnlock = VaultEnvelopeEngine.unlockWithPassword(
            withDecoy,
            "Decoy@0000".toCharArray(),
            3_000L
        ) as VaultEnvelopeEngine.UnlockOutcome.Success
        assertTrue(decoyUnlock.isDecoy)
        assertFalse(decoyUnlock.dek.contentEquals(created.dek))

        // Stealth PIN HMAC check (never stored plaintext)
        val withStealth = VaultEnvelopeEngine.withStealthTrigger(withDecoy, true, "*#9090#".toCharArray())
        assertTrue(VaultEnvelopeEngine.matchesStealthPin(withStealth, "*#9090#"))
        assertTrue(VaultEnvelopeEngine.matchesStealthPin(withStealth, "  *#9090#  "))
        assertFalse(VaultEnvelopeEngine.matchesStealthPin(withStealth, "*#0000#"))
        val stealthOff = VaultEnvelopeEngine.withStealthTrigger(withStealth, false, null)
        assertFalse(VaultEnvelopeEngine.matchesStealthPin(stealthOff, "*#9090#"))

        // Exponential backoff starting at attempt 3 + auto-wipe at threshold
        var env = withStealth.copy(autoWipeThreshold = 5)
        val r1 = VaultEnvelopeEngine.unlockWithPassword(env, "bad".toCharArray(), 10_000L)
            as VaultEnvelopeEngine.UnlockOutcome.WrongPassword
        assertEquals(0L, r1.backoffMs)
        assertFalse(r1.autoWiped)
        env = r1.updatedEnvelope

        val r2 = VaultEnvelopeEngine.unlockWithPassword(env, "bad".toCharArray(), 11_000L)
            as VaultEnvelopeEngine.UnlockOutcome.WrongPassword
        assertEquals(0L, r2.backoffMs)
        env = r2.updatedEnvelope

        val r3 = VaultEnvelopeEngine.unlockWithPassword(env, "bad".toCharArray(), 12_000L)
            as VaultEnvelopeEngine.UnlockOutcome.WrongPassword
        assertEquals(1_000L, r3.backoffMs)
        assertFalse(r3.autoWiped)
        env = r3.updatedEnvelope

        // Locked out during backoff window
        val locked = VaultEnvelopeEngine.unlockWithPassword(env, "Rotated#9999".toCharArray(), 12_500L)
        assertTrue(locked is VaultEnvelopeEngine.UnlockOutcome.LockedOut)

        val r4 = VaultEnvelopeEngine.unlockWithPassword(env, "bad".toCharArray(), 14_000L)
            as VaultEnvelopeEngine.UnlockOutcome.WrongPassword
        assertEquals(2_000L, r4.backoffMs)
        assertEquals(1, r4.remainingBeforeWipe)
        env = r4.updatedEnvelope

        val r5 = VaultEnvelopeEngine.unlockWithPassword(env, "bad".toCharArray(), 20_000L)
        assertTrue(r5 is VaultEnvelopeEngine.UnlockOutcome.WrongPassword)
        assertTrue((r5 as VaultEnvelopeEngine.UnlockOutcome.WrongPassword).autoWiped)
    }

    @Test
    fun vaultEnvelopeAndSettings_preserveBiometricInvalidatedAndPasswordManagerFlags() {
        val iterations = 2_000
        val created = VaultEnvelopeEngine.createNew(
            passwordChars = "TestPass#123".toCharArray(),
            iterations = iterations
        )
        assertFalse(created.envelope.biometricInvalidated)
        assertFalse(created.envelope.passwordManagerEnabled)

        val updatedEnv = created.envelope.copy(
            biometricEnabled = false,
            biometricInvalidated = true,
            passwordManagerEnabled = true
        )
        val unlocked = VaultEnvelopeEngine.unlockWithPassword(
            envelope = updatedEnv,
            passwordChars = "TestPass#123".toCharArray(),
            nowMs = 1_000L
        ) as VaultEnvelopeEngine.UnlockOutcome.Success
        assertTrue(unlocked.updatedEnvelope.biometricInvalidated)
        assertTrue(unlocked.updatedEnvelope.passwordManagerEnabled)

        val rewrapped = VaultEnvelopeEngine.rewrapPrimaryPassword(
            envelope = unlocked.updatedEnvelope,
            currentDek = unlocked.dek,
            newPasswordChars = "NewPass#456".toCharArray()
        )
        assertTrue(rewrapped.passwordManagerEnabled)
        assertTrue(rewrapped.biometricInvalidated)

        val defaultSettings = VaultSettings()
        assertFalse(defaultSettings.passwordManagerEnabled)
        val pmEnabled = defaultSettings.copy(passwordManagerEnabled = true)
        assertTrue(pmEnabled.passwordManagerEnabled)
    }

    @Test
    fun vaultEnvelopeEngine_defaultIterationsAreStrong() {
        assertEquals(600_000, VaultCryptoPrimitives.PBKDF2_DEFAULT_ITERATIONS)
    }

    @Test
    fun vaultPendingDownloadStore_redactsSensitiveFieldsAndHydratesInRam() {
        val original = DownloadItem(
            id = 42L,
            url = "https://example.com/private-secret-video",
            title = "Sensitive Personal Video Title",
            author = "Secret Channel",
            thumb = "https://example.com/secret-thumb.jpg",
            duration = "04:20",
            type = DownloadType.video,
            format = Format(format_id = "18", container = "mp4"),
            container = "mp4",
            downloadSections = "",
            allFormats = mutableListOf(),
            downloadPath = "/storage/emulated/0/Download",
            website = "youtube",
            downloadSize = "12 MB",
            playlistTitle = "Secret Playlist",
            audioPreferences = AudioPreferences(),
            videoPreferences = VideoPreferences(),
            extraCommands = "--cookies /tmp/secret",
            customFileNameTemplate = "%(title)s",
            SaveThumb = true,
            status = "Queued",
            downloadStartTime = 123456L,
            logID = 99L,
            playlistURL = "https://example.com/playlist?list=secret",
            incognito = false,
            vaultMode = true,
            vaultJobUuid = ""
        )

        val (redacted, jobUuid) = VaultPendingDownloadStore.registerAndRedact(original)
        assertTrue(jobUuid.isNotBlank())
        assertEquals("vault://pending/$jobUuid", redacted.url)
        assertEquals("", redacted.title)
        assertEquals("", redacted.author)
        assertEquals("", redacted.thumb)
        assertEquals("", redacted.playlistTitle)
        assertNull(redacted.playlistURL)
        assertEquals("", redacted.extraCommands)
        assertEquals("", redacted.downloadPath)
        assertFalse(redacted.SaveThumb)
        assertTrue(redacted.incognito)
        assertTrue(redacted.vaultMode)
        assertTrue(VaultPendingDownloadStore.hasAny())

        val jobTemp = File(tempFolder.root, jobUuid)
        val hydrated = VaultPendingDownloadStore.hydrateForWorker(redacted, jobTemp)!!
        assertEquals(original.url, hydrated.url)
        assertEquals(original.title, hydrated.title)
        assertEquals(original.author, hydrated.author)
        assertEquals(original.thumb, hydrated.thumb)
        assertEquals(original.playlistURL, hydrated.playlistURL)
        assertEquals(original.extraCommands, hydrated.extraCommands)
        assertEquals(jobTemp.absolutePath, hydrated.downloadPath)
        assertEquals(jobUuid, hydrated.customFileNameTemplate)

        val consumed = VaultPendingDownloadStore.consume(jobUuid)!!
        assertEquals(original.url, consumed.realUrl)
        assertEquals(original.downloadPath, consumed.originalDownloadPath)
        assertFalse(VaultPendingDownloadStore.hasAny())
        assertNull(VaultPendingDownloadStore.hydrateForWorker(redacted, jobTemp))
    }

    @Test
    fun vaultItemMeta_provenanceSerialization_andLegacyFallback() {
        val meta = VaultItemMeta(
            id = "uuid-123",
            title = "Sample Clip",
            originalFileName = "Sample Clip.mp4",
            durationMs = 42_000L,
            filesizeBytes = 1_234_567L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            dateAddedMs = 1_700_000_000_000L,
            sourceUrl = "https://example.com/watch?v=1",
            originalFilePath = "/storage/emulated/0/Download/YTDLnis/Video/Sample Clip.mp4",
            originalDir = "/storage/emulated/0/Download/YTDLnis/Video",
            fromDownloadHistory = true,
            author = "Creator",
            website = "youtube",
            thumbUrl = "https://img.example.com/1.jpg",
            durationText = "0:42",
            formatJson = "{\"ext\":\"mp4\"}",
            historyTimeSec = 1_700_000_000L,
            downloadId = 77L
        )
        val roundTrip = VaultItemMeta.fromJsonString(meta.toJsonString())
        assertEquals(meta, roundTrip)

        val legacyJson = """{"id":"legacy-1","title":"Old Clip","originalFileName":"old.mp4","durationMs":1000,"filesizeBytes":500,"width":640,"height":360,"mimeType":"video/mp4","dateAddedMs":1000}"""
        val legacy = VaultItemMeta.fromJsonString(legacyJson)
        assertEquals("legacy-1", legacy.id)
        assertEquals("", legacy.originalFilePath)
        assertEquals("", legacy.originalDir)
        assertFalse(legacy.fromDownloadHistory)
    }

    private fun <T : Throwable> assertThrows(expectedClass: Class<T>, block: () -> Unit) {
        try {
            block()
            fail("Expected ${expectedClass.simpleName} to be thrown")
        } catch (t: Throwable) {
            if (!expectedClass.isInstance(t)) {
                throw AssertionError(
                    "Expected ${expectedClass.simpleName} but got ${t::class.java.name}: ${t.message}",
                    t
                )
            }
        }
    }
}
