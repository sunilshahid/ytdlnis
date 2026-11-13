package com.deniscerri.ytdl.vault

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
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

    // Serializes registration + removal so the startup sweep can never delete a staging dir
    // between a worker registering its UUID and creating the directory (and vice versa).
    private val tempDirLock = Any()

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

    /** True when [uuid] is currently registered as an in-process vault job staging dir. */
    fun isJobActive(uuid: String): Boolean = activeJobTempUuids.contains(uuid)

    fun tempDirForJob(context: Context, jobUuid: String): File {
        // Register the UUID before the directory exists so a concurrent startup sweep can never
        // race the creation of the staging dir (wipeOrphanedTempDirs consults this set).
        synchronized(tempDirLock) {
            activeJobTempUuids.add(jobUuid)
            return File(tempRootDir(context), jobUuid).apply {
                if (!exists()) mkdirs()
                ensureNoMedia(this)
            }
        }
    }

    fun releaseTempDirForJob(context: Context, jobUuid: String) {
        synchronized(tempDirLock) {
            activeJobTempUuids.remove(jobUuid)
            runCatching { secureDeleteRecursively(File(tempRootDir(context), jobUuid)) }
        }
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
            val children = root.listFiles() ?: return
            children.forEach { child ->
                if (child.name == ".nomedia") return@forEach
                // Re-check activeness while holding the lock so a worker registering its UUID
                // concurrently (same-process job startup racing this startup sweep) is skipped.
                synchronized(tempDirLock) {
                    if (child.isDirectory && isJobActive(child.name)) return@forEach
                    secureDeleteRecursively(child)
                }
            }
        }
    }

    /** Completely destroys every vault file, decoy file, staging temp dir, and keystore handle. */
    fun wipeEntireVault(context: Context) {
        VaultSessionManager.lockAndZeroize()
        runCatching { VaultBiometrics.deleteKey(context) }
        // vault_tmp holds plaintext staging material; scrub it before removal. Everything under
        // vault/ is already encrypted (.enc sidecars/containers), so a plain delete is fine there.
        runCatching { File(context.noBackupFilesDir, VAULT_DIR_NAME).deleteRecursively() }
        synchronized(tempDirLock) {
            runCatching { secureDeleteRecursively(File(context.noBackupFilesDir, TMP_DIR_NAME)) }
            activeJobTempUuids.clear()
        }
    }

    fun ensureNoMedia(dir: File) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            val nomedia = File(dir, ".nomedia")
            if (!nomedia.exists()) nomedia.createNewFile()
        }
    }

    /**
     * Best-effort secure removal of a plaintext temp file: overwrites its contents with zeros in
     * 1 MiB chunks (flushed to storage) before deleting.
     *
     * NOTE: this is best-effort only — flash wear leveling and controller-side garbage collection
     * may retain the old data regardless, and any copies already on flash cannot be reached. It
     * merely closes the cheap window on the file's live blocks. Overwrite failures never block
     * deletion: the file is removed anyway.
     */
    fun overwriteAndDelete(file: File) {
        runCatching {
            if (!file.isFile) return
            val size = file.length()
            if (size > 0L) {
                RandomAccessFile(file, "rw").use { raf ->
                    val zeros = ByteArray(1 shl 20) // 1 MiB
                    var written = 0L
                    while (written < size) {
                        val chunk = minOf(zeros.size.toLong(), size - written).toInt()
                        raf.write(zeros, 0, chunk)
                        written += chunk
                    }
                    raf.fd.sync()
                }
            }
        }
        runCatching {
            if (!file.delete() && file.exists()) file.deleteOnExit()
        }
    }

    /** Depth-first removal where every regular file is overwritten before deletion. */
    private fun secureDeleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { secureDeleteRecursively(it) }
        }
        if (file.isFile) {
            overwriteAndDelete(file)
        } else {
            runCatching { file.delete() }
        }
    }
}
