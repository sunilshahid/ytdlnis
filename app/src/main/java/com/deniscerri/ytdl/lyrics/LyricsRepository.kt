package com.deniscerri.ytdl.lyrics

import android.content.Context
import android.util.Log
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.LyricsCacheItem
import com.deniscerri.ytdl.lyrics.parse.LyricsParserRegistry
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.util.extractors.LrcLibUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provider chain for lyrics: embedded tags -> sidecar -> cache -> LRCLIB.
 *
 * Network access is explicit and can be disabled, so plain local playback never blocks on it.
 */
class LyricsRepository(private val context: Context) {

    private val dao = DBManager.getInstance(context.applicationContext).lyricsCacheDao

    suspend fun load(
        item: PlaybackItem,
        durationMs: Long = item.durationMs ?: 0L,
        forceRefresh: Boolean = false,
        allowNetwork: Boolean = true
    ): LyricsDocument? = withContext(Dispatchers.IO) {
        val title = item.title.ifBlank { return@withContext null }
        val artist = item.artist
        val album = item.album

        if (!forceRefresh) {
            LyricsTagReader.read(context, item.localUri)?.let { embedded ->
                val document = LyricsParserRegistry.parse(
                    raw = embedded.raw,
                    formatHint = embedded.format,
                    mediaId = item.mediaId,
                    title = title,
                    artist = artist,
                    album = album,
                    source = LyricsSource.EMBEDDED
                )
                if (document.lines.isNotEmpty()) {
                    cache(item, document, durationMs)
                    return@withContext document
                }
            }

            LyricsSidecarReader.find(context, item.localUri, title)?.let { sidecar ->
                val document = LyricsParserRegistry.parse(
                    raw = sidecar.raw,
                    formatHint = sidecar.format,
                    mediaId = item.mediaId,
                    title = title,
                    artist = artist,
                    album = album,
                    source = LyricsSource.SIDECAR
                )
                if (document.lines.isNotEmpty()) {
                    cache(item, document, durationMs)
                    return@withContext document
                }
            }

            dao.getByMediaId(item.mediaId)?.let { cached ->
                val document = LyricsParserRegistry.parse(
                    raw = cached.rawLyrics,
                    formatHint = runCatching { LyricsFormat.valueOf(cached.format) }.getOrNull(),
                    mediaId = item.mediaId,
                    title = title,
                    artist = artist,
                    album = album,
                    source = LyricsSource.CACHE,
                    providerId = cached.providerId,
                    offsetMs = cached.offsetMs
                )
                if (document.lines.isNotEmpty()) return@withContext document
            }
        }

        if (!allowNetwork || artist.isNullOrBlank()) {
            return@withContext loadCacheIgnoreOffset(item, title, artist, album)
        }

        val remote = runCatching {
            LrcLibUtil.find(
                track = title,
                artist = artist,
                album = album.orEmpty(),
                durationSec = (durationMs / 1000L).toInt()
            )
        }.onFailure {
            Log.w(TAG, "LRCLIB lookup failed for $title", it)
        }.getOrNull()

        if (remote != null) {
            val raw = if (remote.synced.isNotEmpty()) {
                remote.synced.joinToString("\n") { line ->
                    val minutes = line.timeMs / 60_000
                    val seconds = (line.timeMs % 60_000) / 1000
                    val centis = (line.timeMs % 1000) / 10
                    String.format(java.util.Locale.US, "[%02d:%02d.%02d]%s", minutes, seconds, centis, line.text)
                }
            } else {
                remote.plain
            }
            val format = if (remote.synced.isNotEmpty()) LyricsFormat.LRC else LyricsFormat.PLAIN
            val document = LyricsParserRegistry.parse(
                raw = raw,
                formatHint = format,
                mediaId = item.mediaId,
                title = title,
                artist = artist,
                album = album,
                source = LyricsSource.LRCLIB,
                providerId = remote.id.toString()
            )
            if (document.lines.isNotEmpty()) {
                cache(item, document, durationMs)
                return@withContext document
            }
        }

        loadCacheIgnoreOffset(item, title, artist, album)
    }

    private suspend fun loadCacheIgnoreOffset(
        item: PlaybackItem,
        title: String,
        artist: String?,
        album: String?
    ): LyricsDocument? {
        val cached = dao.getByMediaId(item.mediaId) ?: return null
        val document = LyricsParserRegistry.parse(
            raw = cached.rawLyrics,
            formatHint = runCatching { LyricsFormat.valueOf(cached.format) }.getOrNull(),
            mediaId = item.mediaId,
            title = title,
            artist = artist,
            album = album,
            source = LyricsSource.CACHE,
            providerId = cached.providerId,
            offsetMs = cached.offsetMs
        )
        return document.takeIf { it.lines.isNotEmpty() }
    }

    suspend fun saveUserLyrics(item: PlaybackItem, raw: String, durationMs: Long = 0L): LyricsDocument? {
        val document = LyricsParserRegistry.parse(
            raw = raw,
            mediaId = item.mediaId,
            title = item.title,
            artist = item.artist,
            album = item.album,
            source = LyricsSource.USER
        )
        if (document.lines.isEmpty()) return null
        cache(item, document, durationMs)
        return document
    }

    suspend fun updateOffset(mediaId: String, offsetMs: Long) {
        dao.updateOffset(mediaId, offsetMs)
    }

    suspend fun clear(mediaId: String) {
        dao.delete(mediaId)
    }

    private suspend fun cache(item: PlaybackItem, document: LyricsDocument, durationMs: Long) {
        runCatching {
            val raw = if (document.raw.isNotBlank()) document.raw else serialize(document)
            dao.upsert(
                LyricsCacheItem(
                    mediaId = item.mediaId,
                    videoId = item.videoId,
                    title = item.title,
                    artist = item.artist,
                    album = item.album,
                    durationMs = durationMs.takeIf { it > 0 } ?: 0L,
                    rawLyrics = raw,
                    format = document.format.name,
                    source = document.source.name,
                    providerId = document.providerId,
                    offsetMs = document.offsetMs,
                    isSynced = document.isSynced
                )
            )
        }.onFailure { Log.w(TAG, "Could not cache lyrics for ${item.mediaId}", it) }
    }

    private fun serialize(document: LyricsDocument): String {
        if (!document.isSynced) return document.lines.joinToString("\n") { it.text }
        val hasWords = document.hasWordTimings
        return document.lines.joinToString("\n") { line ->
            buildString {
                val start = line.startMs ?: 0L
                val minutes = start / 60_000
                val seconds = (start % 60_000) / 1000
                val centis = (start % 1000) / 10
                append(String.format(java.util.Locale.US, "[%02d:%02d.%02d]", minutes, seconds, centis))
                if (hasWords && line.words.isNotEmpty()) {
                    line.words.forEach { word ->
                        val wordStart = word.startMs ?: start
                        append(
                            String.format(
                                java.util.Locale.US,
                                "<%02d:%02d.%02d>",
                                wordStart / 60_000,
                                (wordStart % 60_000) / 1000,
                                (wordStart % 1000) / 10
                            )
                        )
                        append(word.text)
                    }
                } else {
                    append(line.text)
                }
            }
        }
    }

    companion object {
        private const val TAG = "LyricsRepository"
    }
}
