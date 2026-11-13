package com.deniscerri.ytdl.vault

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.vault.crypto.VaultCryptoPrimitives
import com.deniscerri.ytdl.vault.crypto.VaultFileCipher
import com.deniscerri.ytdl.vault.crypto.VaultIngestSeal
import com.deniscerri.ytdl.vault.crypto.VaultSidecarCrypto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Coordinates envelope persistence, key derivation/rotation, settings, `.wrap.enc` reconciliation,
 * item listing, renaming, deletion, and explicit SAF export.
 */
object VaultRepository {

    private const val SETTINGS_ITEM_ID = "vault_settings"

    fun isInitialized(context: Context): Boolean =
        VaultStorage.envelopeFile(context).exists()

    fun loadEnvelopeOrNull(context: Context): VaultEnvelope? {
        val file = VaultStorage.envelopeFile(context)
        if (!file.exists()) return null
        return runCatching { VaultEnvelope.fromJsonString(file.readText(StandardCharsets.UTF_8)) }.getOrNull()
    }

    fun isStealthEnabled(context: Context): Boolean =
        loadEnvelopeOrNull(context)?.stealthEnabled == true

    fun matchesStealthTrigger(context: Context, query: String): Boolean {
        val envelope = loadEnvelopeOrNull(context) ?: return false
        return VaultEnvelopeEngine.matchesStealthPin(envelope, query)
    }

    private fun saveEnvelope(context: Context, envelope: VaultEnvelope) {
        val file = VaultStorage.envelopeFile(context)
        file.parentFile?.let { VaultStorage.ensureNoMedia(it) }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(envelope.toJsonString(), StandardCharsets.UTF_8)
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    suspend fun createVault(
        context: Context,
        passwordChars: CharArray
    ): VaultEnvelope = withContext(Dispatchers.IO) {
        val created = VaultEnvelopeEngine.createNew(passwordChars)
        val dek = created.dek
        try {
            saveEnvelope(context, created.envelope)
            VaultStorage.itemsDir(context, decoy = false)
            VaultStorage.itemsDir(context, decoy = true)
            saveSettings(context, VaultSettings(), dek)
            VaultSessionManager.unlockWithKey(dek, decoy = false)
            created.envelope
        } finally {
            dek.fill(0)
            passwordChars.fill('\u0000')
        }
    }

    suspend fun unlockWithPassword(
        context: Context,
        passwordChars: CharArray
    ): VaultEnvelopeEngine.UnlockOutcome = withContext(Dispatchers.IO) {
        val envelope = loadEnvelopeOrNull(context)
            ?: throw IllegalStateException("Vault is not initialized")
        try {
            val outcome = VaultEnvelopeEngine.unlockWithPassword(envelope, passwordChars)
            when (outcome) {
                is VaultEnvelopeEngine.UnlockOutcome.Success -> {
                    val cleanEnvelope = if (!outcome.isDecoy && outcome.updatedEnvelope.biometricInvalidated) {
                        outcome.updatedEnvelope.copy(biometricInvalidated = false)
                    } else {
                        outcome.updatedEnvelope
                    }
                    saveEnvelope(context, cleanEnvelope)
                    if (!outcome.isDecoy) {
                        reconcilePendingSealedItems(context, cleanEnvelope, outcome.dek)
                    }
                    VaultSessionManager.unlockWithKey(outcome.dek, decoy = outcome.isDecoy)
                    outcome
                }
                is VaultEnvelopeEngine.UnlockOutcome.WrongPassword -> {
                    if (outcome.autoWiped) {
                        VaultStorage.wipeEntireVault(context)
                    } else {
                        saveEnvelope(context, outcome.updatedEnvelope)
                    }
                    outcome
                }
                is VaultEnvelopeEngine.UnlockOutcome.LockedOut -> outcome
            }
        } finally {
            passwordChars.fill('\u0000')
        }
    }

    fun clearBiometricEnrollment(context: Context, dueToChange: Boolean = false) {
        VaultBiometrics.deleteKey(context)
        val env = loadEnvelopeOrNull(context) ?: return
        saveEnvelope(
            context,
            env.copy(
                biometricEnabled = false,
                biometricInvalidated = dueToChange,
                biometricWrappedDekIvB64 = null,
                biometricWrappedDekCtB64 = null
            )
        )
        VaultSessionManager.getKeyCopyOrNull()?.let { dek ->
            try {
                if (!VaultSessionManager.isDecoySession) {
                    val settings = loadSettingsSync(context, dek).copy(biometricsEnabled = false)
                    saveSettings(context, settings, dek)
                }
            } finally {
                dek.fill(0)
            }
        }
    }

    fun finishBiometricEnrollment(context: Context, ivB64: String, ctB64: String) {
        val env = loadEnvelopeOrNull(context) ?: return
        saveEnvelope(
            context,
            env.copy(
                biometricEnabled = true,
                biometricInvalidated = false,
                biometricWrappedDekIvB64 = ivB64,
                biometricWrappedDekCtB64 = ctB64
            )
        )
        VaultSessionManager.getKeyCopyOrNull()?.let { dek ->
            try {
                if (!VaultSessionManager.isDecoySession) {
                    val settings = loadSettingsSync(context, dek).copy(biometricsEnabled = true)
                    saveSettings(context, settings, dek)
                }
            } finally {
                dek.fill(0)
            }
        }
    }

    suspend fun finishBiometricUnlock(context: Context, dek: ByteArray) = withContext(Dispatchers.IO) {
        val env = loadEnvelopeOrNull(context) ?: run {
            dek.fill(0)
            return@withContext
        }
        try {
            val resetEnv = env.copy(failedAttempts = 0, lockoutUntilMs = 0L)
            saveEnvelope(context, resetEnv)
            reconcilePendingSealedItems(context, resetEnv, dek)
            VaultSessionManager.unlockWithKey(dek, decoy = false)
        } finally {
            dek.fill(0)
        }
    }

    suspend fun changeMasterPassword(
        context: Context,
        currentPasswordChars: CharArray,
        newPasswordChars: CharArray
    ): Boolean = withContext(Dispatchers.IO) {
        val env = loadEnvelopeOrNull(context) ?: return@withContext false
        try {
            if (VaultSessionManager.isDecoySession) return@withContext false
            val verify = VaultEnvelopeEngine.unlockWithPassword(env, currentPasswordChars)
            if (verify !is VaultEnvelopeEngine.UnlockOutcome.Success || verify.isDecoy) {
                return@withContext false
            }
            val dek = verify.dek
            try {
                val updated = VaultEnvelopeEngine.rewrapPrimaryPassword(env, dek, newPasswordChars)
                saveEnvelope(context, updated)
                true
            } finally {
                dek.fill(0)
            }
        } finally {
            currentPasswordChars.fill('\u0000')
            newPasswordChars.fill('\u0000')
        }
    }

    suspend fun setDecoyPassword(context: Context, decoyPasswordChars: CharArray) =
        withContext(Dispatchers.IO) {
            val env = loadEnvelopeOrNull(context) ?: return@withContext
            try {
                val updated = VaultEnvelopeEngine.setDecoyPassword(env, decoyPasswordChars)
                saveEnvelope(context, updated)
            } finally {
                decoyPasswordChars.fill('\u0000')
            }
        }

    fun clearDecoyPassword(context: Context) {
        val env = loadEnvelopeOrNull(context) ?: return
        saveEnvelope(
            context,
            env.copy(
                decoySaltB64 = null,
                decoyPasswordHashB64 = null,
                decoyWrappedDekIvB64 = null,
                decoyWrappedDekCtB64 = null
            )
        )
        runCatching { VaultStorage.itemsDir(context, decoy = true).deleteRecursively() }
    }

    fun loadSettings(context: Context): VaultSettings {
        if (VaultSessionManager.isDecoySession) return VaultSettings()
        val dek = VaultSessionManager.getKeyCopyOrNull() ?: return VaultSettings()
        return try {
            loadSettingsSync(context, dek)
        } finally {
            dek.fill(0)
        }
    }

    private fun loadSettingsSync(context: Context, dek: ByteArray): VaultSettings {
        val file = VaultStorage.settingsFile(context)
        if (!file.exists()) return VaultSettings()
        return runCatching {
            val plain = VaultSidecarCrypto.readEncryptedFile(
                sourceFile = file,
                rootKey = dek,
                itemId = SETTINGS_ITEM_ID,
                purpose = VaultSidecarCrypto.PURPOSE_SETTINGS
            )
            try {
                VaultSettings.fromJsonString(String(plain, StandardCharsets.UTF_8))
            } finally {
                plain.fill(0)
            }
        }.getOrDefault(VaultSettings())
    }

    fun updateSettings(
        context: Context,
        newSettings: VaultSettings,
        newStealthPinChars: CharArray? = null
    ) {
        if (VaultSessionManager.isDecoySession) return
        val dek = VaultSessionManager.requireKeyCopy()
        try {
            saveSettings(context, newSettings, dek)
            val env = loadEnvelopeOrNull(context) ?: return
            var updated = env.copy(
                autoWipeThreshold = newSettings.autoWipeThreshold,
                passwordManagerEnabled = newSettings.passwordManagerEnabled
            )
            if (!newSettings.stealthMode) {
                updated = VaultEnvelopeEngine.withStealthTrigger(updated, enabled = false, pinChars = null)
            } else if (newStealthPinChars != null && newStealthPinChars.isNotEmpty()) {
                updated = VaultEnvelopeEngine.withStealthTrigger(updated, enabled = true, pinChars = newStealthPinChars)
            }
            saveEnvelope(context, updated)
        } finally {
            dek.fill(0)
            newStealthPinChars?.fill('\u0000')
        }
    }

    fun updateViewSettings(context: Context, newSettings: VaultSettings) {
        if (VaultSessionManager.isDecoySession) return
        val dek = VaultSessionManager.getKeyCopyOrNull() ?: return
        try {
            saveSettings(context, newSettings, dek)
        } finally {
            dek.fill(0)
        }
    }

    private fun saveSettings(context: Context, settings: VaultSettings, dek: ByteArray) {
        val bytes = settings.toJsonString().toByteArray(StandardCharsets.UTF_8)
        try {
            VaultSidecarCrypto.writeEncryptedFile(
                targetFile = VaultStorage.settingsFile(context),
                rootKey = dek,
                itemId = SETTINGS_ITEM_ID,
                purpose = VaultSidecarCrypto.PURPOSE_SETTINGS,
                plaintext = bytes
            )
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * Converts any Flow A items sealed with the public key while the vault was locked
     * (`{uuid}.wrap.enc`) into `{uuid}.itemkey.enc` wrapped under `vaultDEK`. Runs in < 5ms per item
     * without touching the multi-gigabyte video payload.
     */
    fun reconcilePendingSealedItems(context: Context, envelope: VaultEnvelope, primaryDek: ByteArray) {
        val itemsDir = VaultStorage.itemsDir(context, decoy = false)
        val wrapFiles = itemsDir.listFiles { _, name -> name.endsWith(".wrap.enc") } ?: return
        if (wrapFiles.isEmpty()) return

        val privateKeyDer = runCatching {
            VaultEnvelopeEngine.decryptIngestPrivateKey(envelope, primaryDek)
        }.getOrNull() ?: return

        try {
            for (wrapFile in wrapFiles) {
                val uuid = wrapFile.name.removeSuffix(".wrap.enc")
                runCatching {
                    val sealedBytes = wrapFile.readBytes()
                    val itemSeed = VaultIngestSeal.unsealSecret(privateKeyDer, sealedBytes)
                    try {
                        VaultSidecarCrypto.writeEncryptedFile(
                            targetFile = VaultStorage.itemKeyFile(itemsDir, uuid),
                            rootKey = primaryDek,
                            itemId = uuid,
                            purpose = VaultSidecarCrypto.PURPOSE_ITEM_KEY,
                            plaintext = itemSeed
                        )
                        wrapFile.delete()
                    } finally {
                        itemSeed.fill(0)
                    }
                }
            }
        } finally {
            privateKeyDer.fill(0)
        }
    }

    /** If the real vault is currently unlocked in memory, unwraps any Flow A `.wrap.enc` items now. */
    fun reconcilePendingIfUnlocked(context: Context) {
        if (!VaultSessionManager.isUnlocked.value || VaultSessionManager.isDecoySession) return
        val dek = VaultSessionManager.getKeyCopyOrNull() ?: return
        try {
            val env = loadEnvelopeOrNull(context) ?: return
            reconcilePendingSealedItems(context, env, dek)
        } finally {
            dek.fill(0)
        }
    }

    /**
     * Resolves the root key for a specific item: returns a copy of `itemSeed` from `{uuid}.itemkey.enc`
     * if present (for items downloaded while locked), otherwise a copy of `vaultKey`.
     */
    fun resolveItemRootKey(itemsDir: File, uuid: String, vaultKey: ByteArray): ByteArray {
        val keyFile = VaultStorage.itemKeyFile(itemsDir, uuid)
        if (!keyFile.exists()) return vaultKey.copyOf()
        return VaultSidecarCrypto.readEncryptedFile(
            sourceFile = keyFile,
            rootKey = vaultKey,
            itemId = uuid,
            purpose = VaultSidecarCrypto.PURPOSE_ITEM_KEY
        )
    }

    /**
     * Loads gallery items by decrypting ONLY `.meta.enc` (~1 KB) and `.thumb.enc` (~50 KB).
     * Never opens or decrypts `{uuid}.video.enc`.
     */
    suspend fun listItems(context: Context): List<VaultUiItem> = withContext(Dispatchers.IO) {
        val dek = VaultSessionManager.getKeyCopyOrNull() ?: return@withContext emptyList()
        val decoy = VaultSessionManager.isDecoySession
        val itemsDir = VaultStorage.itemsDir(context, decoy = decoy)
        try {
            if (!decoy) {
                loadEnvelopeOrNull(context)?.let { env ->
                    reconcilePendingSealedItems(context, env, dek)
                }
            }
            val metaFiles = itemsDir.listFiles { _, name -> name.endsWith(".meta.enc") }
                ?: return@withContext emptyList()

            val items = mutableListOf<VaultUiItem>()
            for (metaFile in metaFiles) {
                val uuid = metaFile.name.removeSuffix(".meta.enc")
                if (!VaultStorage.videoFile(itemsDir, uuid).exists()) continue
                val itemRoot = runCatching { resolveItemRootKey(itemsDir, uuid, dek) }.getOrNull() ?: continue
                try {
                    val metaBytes = VaultSidecarCrypto.readEncryptedFile(
                        sourceFile = metaFile,
                        rootKey = itemRoot,
                        itemId = uuid,
                        purpose = VaultSidecarCrypto.PURPOSE_META
                    )
                    val meta = try {
                        VaultItemMeta.fromJsonString(String(metaBytes, StandardCharsets.UTF_8))
                    } finally {
                        metaBytes.fill(0)
                    }

                    val thumbFile = VaultStorage.thumbFile(itemsDir, uuid)
                    val bitmap = if (thumbFile.exists()) {
                        runCatching {
                            val thumbBytes = VaultSidecarCrypto.readEncryptedFile(
                                sourceFile = thumbFile,
                                rootKey = itemRoot,
                                itemId = uuid,
                                purpose = VaultSidecarCrypto.PURPOSE_THUMB
                            )
                            try {
                                BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size)?.also {
                                    VaultSessionManager.registerBitmap(it)
                                }
                            } finally {
                                thumbBytes.fill(0)
                            }
                        }.getOrNull()
                    } else {
                        null
                    }
                    items.add(VaultUiItem(meta = meta, thumbnail = bitmap))
                } catch (_: Throwable) {
                    // Skip unreadable / tampered entry cleanly rather than crashing listing.
                } finally {
                    itemRoot.fill(0)
                }
            }
            items.sortedByDescending { it.meta.dateAddedMs }
        } finally {
            dek.fill(0)
        }
    }

    suspend fun renameItem(context: Context, uuid: String, newTitle: String): Boolean =
        withContext(Dispatchers.IO) {
            val cleanTitle = newTitle.trim()
            if (cleanTitle.isEmpty()) return@withContext false
            val dek = VaultSessionManager.getKeyCopyOrNull() ?: return@withContext false
            val itemsDir = VaultStorage.itemsDir(context, decoy = VaultSessionManager.isDecoySession)
            val itemRoot = runCatching { resolveItemRootKey(itemsDir, uuid, dek) }.getOrNull()
            try {
                if (itemRoot == null) return@withContext false
                val metaFile = VaultStorage.metaFile(itemsDir, uuid)
                val oldBytes = VaultSidecarCrypto.readEncryptedFile(
                    sourceFile = metaFile,
                    rootKey = itemRoot,
                    itemId = uuid,
                    purpose = VaultSidecarCrypto.PURPOSE_META
                )
                val updatedMeta = try {
                    VaultItemMeta.fromJsonString(String(oldBytes, StandardCharsets.UTF_8))
                        .copy(title = cleanTitle)
                } finally {
                    oldBytes.fill(0)
                }
                val newBytes = updatedMeta.toJsonString().toByteArray(StandardCharsets.UTF_8)
                try {
                    VaultSidecarCrypto.writeEncryptedFile(
                        targetFile = metaFile,
                        rootKey = itemRoot,
                        itemId = uuid,
                        purpose = VaultSidecarCrypto.PURPOSE_META,
                        plaintext = newBytes
                    )
                } finally {
                    newBytes.fill(0)
                }
                true
            } catch (_: Throwable) {
                false
            } finally {
                itemRoot?.fill(0)
                dek.fill(0)
            }
        }

    suspend fun deleteItems(context: Context, uuids: Collection<String>) = withContext(Dispatchers.IO) {
        val itemsDir = VaultStorage.itemsDir(context, decoy = VaultSessionManager.isDecoySession)
        for (uuid in uuids) {
            VaultStorage.videoFile(itemsDir, uuid).delete()
            VaultStorage.metaFile(itemsDir, uuid).delete()
            VaultStorage.thumbFile(itemsDir, uuid).delete()
            VaultStorage.itemKeyFile(itemsDir, uuid).delete()
            VaultStorage.wrappedKeyFile(itemsDir, uuid).delete()
        }
    }

    data class RestoredVaultItem(
        val uuid: String,
        val title: String,
        val destinationLabel: String,
        val restoredToDownloadsHistory: Boolean
    )

    suspend fun exportItemToUri(
        context: Context,
        uuid: String,
        destUri: Uri,
        onProgress: ((Long, Long) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val dek = VaultSessionManager.requireKeyCopy()
        val itemsDir = VaultStorage.itemsDir(context, decoy = VaultSessionManager.isDecoySession)
        val itemRoot = resolveItemRootKey(itemsDir, uuid, dek)
        try {
            val videoFile = VaultStorage.videoFile(itemsDir, uuid)
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                VaultFileCipher.decryptToStream(
                    file = videoFile,
                    rootKey = itemRoot,
                    uuid = uuid,
                    output = out,
                    onProgress = onProgress
                )
            } ?: throw IllegalStateException("Could not open export destination")
        } finally {
            itemRoot.fill(0)
            dek.fill(0)
        }
    }

    /**
     * Decrypts one or more vault videos back to their original folder (or YTDLnis default video
     * downloads directory when provenance is unavailable), scans into MediaStore, re-registers
     * YTDLnis Download History / Play Home entries when applicable, and optionally deletes the
     * encrypted vault copy ([deleteFromVaultAfter] = true for "Decrypt", false for "Decrypt a Copy").
     */
    suspend fun restoreVaultItems(
        context: Context,
        uuids: List<String>,
        deleteFromVaultAfter: Boolean,
        onProgress: ((itemIndex: Int, itemCount: Int, written: Long, total: Long) -> Unit)? = null
    ): List<RestoredVaultItem> = withContext(Dispatchers.IO) {
        if (uuids.isEmpty()) return@withContext emptyList()
        val dek = VaultSessionManager.requireKeyCopy()
        val decoy = VaultSessionManager.isDecoySession
        val itemsDir = VaultStorage.itemsDir(context, decoy = decoy)
        val results = mutableListOf<RestoredVaultItem>()

        try {
            uuids.forEachIndexed { index, uuid ->
                val itemRoot = resolveItemRootKey(itemsDir, uuid, dek)
                try {
                    val metaFile = VaultStorage.metaFile(itemsDir, uuid)
                    val metaBytes = VaultSidecarCrypto.readEncryptedFile(
                        sourceFile = metaFile,
                        rootKey = itemRoot,
                        itemId = uuid,
                        purpose = VaultSidecarCrypto.PURPOSE_META
                    )
                    val meta = try {
                        VaultItemMeta.fromJsonString(String(metaBytes, StandardCharsets.UTF_8))
                    } finally {
                        metaBytes.fill(0)
                    }

                    val videoFile = VaultStorage.videoFile(itemsDir, uuid)
                    if (!videoFile.exists()) {
                        throw IOException("Encrypted container missing for ${meta.title}")
                    }

                    val targetName = resolveRestoreFileName(meta)
                    val concreteMime = resolveConcreteVideoMime(meta.mimeType, targetName)
                    val preferredDirPath = resolvePreferredDirectoryPath(meta)

                    val restoredFilePath: String
                    val destinationLabel: String

                    val progressBridge: ((Long, Long) -> Unit) = { written, total ->
                        onProgress?.invoke(index, uuids.size, written, total)
                    }

                    // 1) If original location was a SAF tree URI, try writing back into that tree.
                    if (meta.originalDir.startsWith("content://") && meta.originalFilePath.isBlank()) {
                        val treeDoc = runCatching {
                            DocumentFile.fromTreeUri(context, Uri.parse(meta.originalDir))
                        }.getOrNull()
                        if (treeDoc != null && treeDoc.canWrite()) {
                            val uniqueDocName = uniqueDocumentName(treeDoc, targetName)
                            val created = treeDoc.createFile(concreteMime, uniqueDocName)
                                ?: throw IOException("Could not create restored file in original folder")
                            context.contentResolver.openOutputStream(created.uri)?.use { out ->
                                VaultFileCipher.decryptToStream(
                                    file = videoFile,
                                    rootKey = itemRoot,
                                    uuid = uuid,
                                    output = BufferedOutputStream(out, 64 * 1024),
                                    onProgress = progressBridge
                                )
                            } ?: throw IOException("Could not open restored document stream")
                            restoredFilePath = created.uri.toString()
                            destinationLabel = treeDoc.name ?: FileUtil.formatPath(meta.originalDir)
                        } else {
                            val written = writeToFilesystemOrMediaStore(
                                context = context,
                                videoFile = videoFile,
                                itemRoot = itemRoot,
                                uuid = uuid,
                                dir = File(FileUtil.getDefaultVideoPath()),
                                preferredName = targetName,
                                mimeType = concreteMime,
                                onProgress = progressBridge
                            )
                            restoredFilePath = written.first
                            destinationLabel = written.second
                        }
                    } else {
                        val written = writeToFilesystemOrMediaStore(
                            context = context,
                            videoFile = videoFile,
                            itemRoot = itemRoot,
                            uuid = uuid,
                            dir = File(preferredDirPath),
                            preferredName = targetName,
                            mimeType = concreteMime,
                            onProgress = progressBridge
                        )
                        restoredFilePath = written.first
                        destinationLabel = written.second
                    }

                    var restoredHistory = false
                    if (!decoy && shouldRestoreToDownloadHistory(meta, restoredFilePath)) {
                        restoredHistory = reinsertDownloadHistory(context, meta, restoredFilePath)
                    }

                    if (deleteFromVaultAfter) {
                        deleteItems(context, listOf(uuid))
                    }

                    results += RestoredVaultItem(
                        uuid = uuid,
                        title = meta.title,
                        destinationLabel = destinationLabel,
                        restoredToDownloadsHistory = restoredHistory
                    )
                } finally {
                    itemRoot.fill(0)
                }
            }
            results
        } finally {
            dek.fill(0)
        }
    }

    private fun writeToFilesystemOrMediaStore(
        context: Context,
        videoFile: File,
        itemRoot: ByteArray,
        uuid: String,
        dir: File,
        preferredName: String,
        mimeType: String,
        onProgress: (Long, Long) -> Unit
    ): Pair<String, String> {
        val fallbackDir = File(FileUtil.getDefaultVideoPath())
        val candidates = listOf(dir, fallbackDir).distinctBy { it.absolutePath }

        for (candidateDir in candidates) {
            val destFile = pickUniqueFile(candidateDir, preferredName)
            val tmpFile = File(candidateDir, ".vault_restore_${uuid}.tmp")
            val directOk = runCatching {
                candidateDir.mkdirs()
                if (!candidateDir.exists() || !candidateDir.isDirectory) {
                    throw IOException("Directory unavailable: ${candidateDir.absolutePath}")
                }
                BufferedOutputStream(FileOutputStream(tmpFile), 64 * 1024).use { out ->
                    VaultFileCipher.decryptToStream(
                        file = videoFile,
                        rootKey = itemRoot,
                        uuid = uuid,
                        output = out,
                        onProgress = onProgress
                    )
                }
                if (destFile.exists()) destFile.delete()
                if (!tmpFile.renameTo(destFile)) {
                    tmpFile.copyTo(destFile, overwrite = true)
                    tmpFile.delete()
                }
                if (!destFile.exists() || destFile.length() <= 0L) {
                    throw IOException("Restored file verification failed")
                }
                FileUtil.scanMedia(listOf(destFile.absolutePath), context)
            }.isSuccess

            if (directOk) {
                return destFile.absolutePath to candidateDir.absolutePath
            } else {
                tmpFile.delete()
            }
        }

        // Scoped storage fallback (Android 10+ when direct File API fails on external dirs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relPath = computeMediaStoreRelativePath(dir)
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, preferredName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, relPath)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = context.contentResolver.insert(collection, values)
                ?: throw IOException("Could not create destination file in MediaStore")
            try {
                context.contentResolver.openOutputStream(itemUri)?.use { out ->
                    VaultFileCipher.decryptToStream(
                        file = videoFile,
                        rootKey = itemRoot,
                        uuid = uuid,
                        output = BufferedOutputStream(out, 64 * 1024),
                        onProgress = onProgress
                    )
                } ?: throw IOException("Could not open MediaStore output stream")

                val publish = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                context.contentResolver.update(itemUri, publish, null, null)

                @Suppress("DEPRECATION")
                val resolvedPath = runCatching {
                    context.contentResolver.query(
                        itemUri,
                        arrayOf(MediaStore.Video.Media.DATA),
                        null,
                        null,
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
                    }
                }.getOrNull() ?: itemUri.toString()

                if (!resolvedPath.startsWith("content://")) {
                    runCatching { MediaScannerConnection.scanFile(context, arrayOf(resolvedPath), arrayOf(mimeType), null) }
                }
                return resolvedPath to relPath
            } catch (t: Throwable) {
                runCatching { context.contentResolver.delete(itemUri, null, null) }
                throw t
            }
        }

        throw IOException("Storage permission denied when restoring to ${dir.absolutePath}")
    }

    private fun computeMediaStoreRelativePath(dir: File): String {
        @Suppress("DEPRECATION")
        val extRoot = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        val abs = dir.absolutePath
        if (abs.startsWith(extRoot)) {
            val rel = abs.removePrefix(extRoot).trimStart('/')
            val top = rel.substringBefore('/')
            if (top.equals(Environment.DIRECTORY_MOVIES, ignoreCase = true) ||
                top.equals(Environment.DIRECTORY_DCIM, ignoreCase = true) ||
                top.equals(Environment.DIRECTORY_PICTURES, ignoreCase = true)
            ) {
                return rel
            }
        }
        return "${Environment.DIRECTORY_MOVIES}/YTDLnis"
    }

    private fun resolvePreferredDirectoryPath(meta: VaultItemMeta): String {
        val rawFile = meta.originalFilePath.trim()
        if (rawFile.isNotEmpty()) {
            val parent = File(rawFile).parent
            if (!parent.isNullOrBlank()) return parent
        }
        val rawDir = meta.originalDir.trim()
        if (rawDir.isNotEmpty() && !rawDir.startsWith("content://")) {
            return FileUtil.formatPath(rawDir).trimEnd('/')
        }
        if (rawDir.startsWith("content://")) {
            val formatted = FileUtil.formatPath(rawDir).trimEnd('/')
            if (formatted.startsWith("/storage/")) return formatted
        }
        return FileUtil.getDefaultVideoPath()
    }

    private fun resolveRestoreFileName(meta: VaultItemMeta): String {
        val raw = meta.originalFileName.trim().ifEmpty {
            meta.originalFilePath.trim().takeIf { it.isNotEmpty() }?.let { File(it).name } ?: ""
        }
        if (raw.isNotBlank()) return raw
        val safeBase = meta.title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "Private Video" }
        val ext = when (meta.mimeType.lowercase(Locale.ROOT)) {
            "video/webm" -> "webm"
            "video/x-matroska" -> "mkv"
            "video/quicktime" -> "mov"
            else -> "mp4"
        }
        return "$safeBase.$ext"
    }

    private fun resolveConcreteVideoMime(rawMime: String, fileName: String): String {
        val cleaned = rawMime.trim().lowercase(Locale.ROOT)
        if (cleaned.startsWith("video/") && cleaned != "video/*") {
            return cleaned
        }
        return when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            else -> "video/mp4"
        }
    }

    private fun pickUniqueFile(dir: File, preferredName: String): File {
        val candidate = File(dir, preferredName)
        if (!candidate.exists()) return candidate
        val dot = preferredName.lastIndexOf('.')
        val base = if (dot > 0) preferredName.substring(0, dot) else preferredName
        val ext = if (dot > 0) preferredName.substring(dot) else ""
        var counter = 1
        while (counter < 10_000) {
            val next = File(dir, "$base ($counter)$ext")
            if (!next.exists()) return next
            counter++
        }
        return File(dir, "${base}_${System.currentTimeMillis()}$ext")
    }

    private fun uniqueDocumentName(dir: DocumentFile, preferredName: String): String {
        if (dir.findFile(preferredName) == null) return preferredName
        val dot = preferredName.lastIndexOf('.')
        val base = if (dot > 0) preferredName.substring(0, dot) else preferredName
        val ext = if (dot > 0) preferredName.substring(dot) else ""
        var counter = 1
        while (counter < 1000) {
            val next = "$base ($counter)$ext"
            if (dir.findFile(next) == null) return next
            counter++
        }
        return "${base}_${System.currentTimeMillis()}$ext"
    }

    private fun shouldRestoreToDownloadHistory(meta: VaultItemMeta, restoredPath: String): Boolean {
        if (meta.fromDownloadHistory || meta.sourceUrl.isNotBlank()) return true
        // Legacy vault items imported before provenance existed or files inside YTDLnis's Download tree
        val defaultAppRoot = FileUtil.getDefaultApplicationPath().trimEnd('/')
        if (!restoredPath.startsWith("content://") && restoredPath.startsWith(defaultAppRoot, ignoreCase = true)) {
            return true
        }
        return meta.originalFilePath.isBlank() && meta.originalDir.isBlank()
    }

    private suspend fun reinsertDownloadHistory(
        context: Context,
        meta: VaultItemMeta,
        restoredPath: String
    ): Boolean = runCatching {
        val db = DBManager.getInstance(context)
        val restoredFile = if (!restoredPath.startsWith("content://")) File(restoredPath) else null
        val actualSize = restoredFile?.length()?.takeIf { it > 0L } ?: meta.filesizeBytes
        val ext = restoredFile?.extension?.takeIf { it.isNotBlank() } ?: "mp4"
        val parsedFormat = meta.formatJson.takeIf { it.isNotBlank() }?.let { json ->
            runCatching { Gson().fromJson(json, Format::class.java) }.getOrNull()
        } ?: Format(container = ext, filesize = actualSize)

        val itemUrl = meta.sourceUrl.ifBlank { "vault-restored://$restoredPath" }
        val existingAll = db.historyDao.getAllHistoryList()
        val alreadyForExactPath = existingAll.any { item ->
            item.downloadPath.any { it.equals(restoredPath, ignoreCase = true) }
        }
        if (alreadyForExactPath) return@runCatching true

        // If Decrypt a Copy restored a second file for an already-present URL, keep a distinct
        // URL key so PlayFragment's `distinctBy { it.url }` displays both without hiding one.
        val finalUrl = if (existingAll.any { it.url == itemUrl && it.type == DownloadType.video }) {
            "$itemUrl#copy=${System.currentTimeMillis()}"
        } else {
            itemUrl
        }

        val historyItem = HistoryItem(
            id = 0,
            url = finalUrl,
            title = meta.title,
            author = meta.author,
            duration = meta.durationText.ifBlank { formatHistoryDuration(meta.durationMs) },
            thumb = meta.thumbUrl,
            type = DownloadType.video,
            time = System.currentTimeMillis() / 1000L,
            downloadPath = listOf(restoredPath),
            website = meta.website,
            format = parsedFormat,
            filesize = actualSize,
            downloadId = meta.downloadId,
            command = ""
        )
        db.historyDao.insert(historyItem)
        true
    }.getOrDefault(false)

    private fun formatHistoryDuration(durationMs: Long): String {
        if (durationMs <= 0L) return ""
        val totalSeconds = durationMs / 1000L
        val h = totalSeconds / 3600L
        val m = (totalSeconds % 3600L) / 60L
        val s = totalSeconds % 60L
        return if (h > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    /** Encodes a bitmap as WebP (80% quality, max 512x512) completely in memory. */
    fun encodeThumbnailWebp(source: Bitmap): ByteArray {
        val maxDim = 512
        val w = source.width
        val h = source.height
        val scaled = if (w > maxDim || h > maxDim) {
            val scale = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
            val nw = (w * scale).toInt().coerceAtLeast(1)
            val nh = (h * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, nw, nh, true)
        } else {
            source
        }
        val baos = ByteArrayOutputStream(48 * 1024)
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        scaled.compress(format, 80, baos)
        if (scaled !== source) scaled.recycle()
        return baos.toByteArray()
    }
}
