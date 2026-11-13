package com.deniscerri.ytdl.vault.ingest

import com.deniscerri.ytdl.database.models.DownloadItem
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds volatile in-RAM metadata (real URL, title, author, source thumbnail, playlist details, extra
 * commands) for Flow A ("Save Directly to Private Vault") downloads so NO URL or title ever touches
 * Room/SQLite (`downloads`, `history`, `logs`), `SharedPreferences`, or `info.json`.
 *
 * The Room `downloads` row contains only a redacted placeholder (`vault://pending/{jobUuid}`) while
 * the worker runs inside the same app process, and is deleted cleanly in `finally` (or on startup
 * if the process was killed before completion).
 */
object VaultPendingDownloadStore {

    data class PendingVaultPayload(
        val jobUuid: String,
        val realUrl: String,
        val playlistUrl: String?,
        val playlistTitle: String,
        val title: String,
        val author: String,
        val duration: String,
        val website: String,
        val thumb: String,
        val extraCommands: String,
        val originalDownloadPath: String = ""
    )

    private val payloads = ConcurrentHashMap<String, PendingVaultPayload>()

    fun registerAndRedact(original: DownloadItem): Pair<DownloadItem, String> {
        val jobUuid = original.vaultJobUuid.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        payloads[jobUuid] = PendingVaultPayload(
            jobUuid = jobUuid,
            realUrl = original.url,
            playlistUrl = original.playlistURL,
            playlistTitle = original.playlistTitle,
            title = original.title,
            author = original.author,
            duration = original.duration,
            website = original.website,
            thumb = original.thumb,
            extraCommands = original.extraCommands,
            originalDownloadPath = original.downloadPath
        )
        val redacted = original.copy(
            url = "vault://pending/$jobUuid",
            title = "",
            author = "",
            thumb = "",
            playlistTitle = "",
            playlistURL = null,
            website = "",
            downloadPath = "",
            customFileNameTemplate = "",
            extraCommands = "",
            SaveThumb = false,
            incognito = true,
            downloadStartTime = 0L,
            vaultMode = true,
            vaultJobUuid = jobUuid
        )
        return redacted to jobUuid
    }

    fun peek(jobUuid: String): PendingVaultPayload? = payloads[jobUuid]

    fun hydrateForWorker(dbItem: DownloadItem, jobTempDir: File): DownloadItem? {
        if (!dbItem.vaultMode || dbItem.vaultJobUuid.isBlank()) return null
        val payload = payloads[dbItem.vaultJobUuid] ?: return null
        return dbItem.copy(
            url = payload.realUrl,
            playlistURL = payload.playlistUrl,
            playlistTitle = payload.playlistTitle,
            title = payload.title,
            author = payload.author,
            duration = payload.duration,
            website = payload.website,
            thumb = payload.thumb,
            extraCommands = payload.extraCommands,
            downloadPath = jobTempDir.absolutePath,
            customFileNameTemplate = dbItem.vaultJobUuid,
            SaveThumb = false,
            incognito = true,
            vaultMode = true,
            vaultJobUuid = dbItem.vaultJobUuid
        )
    }

    fun consume(jobUuid: String): PendingVaultPayload? {
        if (jobUuid.isBlank()) return null
        return payloads.remove(jobUuid)
    }

    fun clearAll() {
        payloads.clear()
    }

    fun hasAny(): Boolean = payloads.isNotEmpty()
}
