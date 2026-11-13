package com.deniscerri.ytdl.vault

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the app-private, backup-excluded directory layout for the Private Vault:
 * - `no_backup/vault/.nomedia`
 * - `no_backup/vault/vault_envelope.json`
 * - `no_backup/vault/vault_settings.enc`
 * - `no_backup/vault/items/{uuid}.(video|meta|thumb|itemkey|wrap).enc`
 * - `no_backup/vault/decoy_items/...` (duress decoy vault)
 * - `no_backup/vault_tmp/{uuid}/` (isolated ephemeral Flow A yt-dlp/ffmpeg working dirs)
 */
object VaultStorage {

    private const val VAULT_DIR_NAME = "vault"
    private const val ITEMS_DIR_NAME = "items"
    private const val DECOY_ITEMS_DIR_NAME = "decoy_items"
    private const val TMP_DIR_NAME = "vault_tmp"
    private const val ENVELOPE_FILE_NAME = "vault_envelope.json"
    private const val SETTINGS_FILE_NAME = "vault_settings.enc"

    private val activeJobTempUuids = ConcurrentHashMap.newKeySet<String>()

    fun vaultDir(context: Context): File =
        File(context.noBackupFilesDir, VAULT_DIR_NAME).apply {
            if (!exists()) mkdirs()
            ensureNoMedia(this)
        }

    fun itemsDir(context: Context, decoy: Boolean = false): File =
        File(vaultDir(context), if (decoy) DECOY_ITEMS_DIR_NAME else ITEMS_DIR_NAME).apply {
            if (!exists()) mkdirs()
            ensureNoMedia(this)
        }

    fun tempRootDir(context: Context): File =
        File(context.noBackupFilesDir, TMP_DIR_NAME).apply {
            if (!exists()) mkdirs()
            ensureNoMedia(this)
        }

    fun tempDirForJob(context: Context, jobUuid: String): File {
        activeJobTempUuids.add(jobUuid)
        return File(tempRootDir(context), jobUuid).apply {
            if (!exists()) mkdirs()
            ensureNoMedia(this)
        }
    }

    fun releaseTempDirForJob(context: Context, jobUuid: String) {
        activeJobTempUuids.remove(jobUuid)
        runCatching { File(tempRootDir(context), jobUuid).deleteRecursively() }
    }

    fun envelopeFile(context: Context): File =
        File(vaultDir(context), ENVELOPE_FILE_NAME)

    fun settingsFile(context: Context): File =
        File(vaultDir(context), SETTINGS_FILE_NAME)

    fun videoFile(itemsDir: File, uuid: String): File = File(itemsDir, "$uuid.video.enc")
    fun metaFile(itemsDir: File, uuid: String): File = File(itemsDir, "$uuid.meta.enc")
    fun thumbFile(itemsDir: File, uuid: String): File = File(itemsDir, "$uuid.thumb.enc")
    fun itemKeyFile(itemsDir: File, uuid: String): File = File(itemsDir, "$uuid.itemkey.enc")
    fun wrappedKeyFile(itemsDir: File, uuid: String): File = File(itemsDir, "$uuid.wrap.enc")

    /**
     * Sweeps any orphaned `noBackupFilesDir/vault_tmp/<uuid>` staging folders left behind by a kill
     * or crash while skipping any job currently running in-process.
     */
    fun wipeOrphanedTempDirs(context: Context) {
        runCatching {
            val root = File(context.noBackupFilesDir, TMP_DIR_NAME)
            if (!root.exists()) return
            root.listFiles()?.forEach { child ->
                if (child.name == ".nomedia") return@forEach
                if (child.isDirectory && activeJobTempUuids.contains(child.name)) return@forEach
                child.deleteRecursively()
            }
        }
    }

    /** Completely destroys every vault file, decoy file, staging temp dir, and keystore handle. */
    fun wipeEntireVault(context: Context) {
        VaultSessionManager.lockAndZeroize()
        runCatching { VaultBiometrics.deleteKey(context) }
        runCatching { File(context.noBackupFilesDir, VAULT_DIR_NAME).deleteRecursively() }
        runCatching { File(context.noBackupFilesDir, TMP_DIR_NAME).deleteRecursively() }
        activeJobTempUuids.clear()
    }

    fun ensureNoMedia(dir: File) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            val nomedia = File(dir, ".nomedia")
            if (!nomedia.exists()) nomedia.createNewFile()
        }
    }
}
