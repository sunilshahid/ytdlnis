package com.deniscerri.ytdl.lyrics

import android.content.Context
import android.util.Log
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.LyricsCacheItem
import com.deniscerri.ytdl.lyrics.model.LyricsCandidate
import com.deniscerri.ytdl.lyrics.model.LyricsQuery
import com.deniscerri.ytdl.lyrics.parse.LyricsParserRegistry
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.util.extractors.LrcLibUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Provider chain for lyrics: embedded tags -> sidecar -> cache -> providers.
 *
 * Synced sources win over plain text: an unsynchronized embedded tag is only used when no
 * timed lyrics are available. Network access is explicit and can be disabled, so plain local
 * playback never blocks on it.
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
        // An unsynchronized tag is a last resort, not a replacement for timed lyrics: keep it
        // aside so it cannot hide a synced sidecar/cache/provider result, and fall back to it
        // only when nothing timed exists (e.g. offline playback).
        var unsyncedEmbedded: LyricsDocument? = null

        if (!forceRefresh) {
            // Stored online searches and user imports take priority over embedded tags
            dao.getByMediaId(item.mediaId)?.let { cached ->
                val cachedSource = runCatching { LyricsSource.valueOf(cached.source) }.getOrNull()
                if (cachedSource != null && cachedSource != LyricsSource.EMBEDDED) {
                    val document = LyricsParserRegistry.parse(
                        raw = cached.rawLyrics,
                        formatHint = runCatching { LyricsFormat.valueOf(cached.format) }.getOrNull(),
                        mediaId = item.mediaId,
                        title = title,
                        artist = artist,
                        album = album,
                        source = cachedSource,
                        providerId = cached.providerId,
                        providerName = LyricsProviderRegistry.getProvider(cached.providerId)?.displayName,
                        offsetMs = cached.offsetMs
                    )
                    if (document.lines.isNotEmpty()) return@withContext document
                }
            }

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
                    if (document.isSynced) {
                        cache(item, document, durationMs)
                        return@withContext document
                    }
                    // Not cached yet: caching here would make the cache tier below return the
                    // plain text before a provider ever gets a chance to supply timed lyrics.
                    unsyncedEmbedded = document
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
                    providerName = LyricsProviderRegistry.getProvider(cached.providerId)?.displayName,
                    offsetMs = cached.offsetMs
                )
                if (document.lines.isNotEmpty()) return@withContext document
            }
        }

        if (!allowNetwork || (title.isBlank() && artist.isNullOrBlank())) {
            return@withContext unsyncedEmbedded
                ?: loadCacheIgnoreOffset(item, title, artist, album)
        }

        val enabled = LyricsProviderRegistry.ordered(context).filter { it.isEnabled(context) }
        if (enabled.isNotEmpty()) {
            val query = LyricsQuery(
                mediaId = item.mediaId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                localUri = item.localUri
            )

            val remoteLyrics = coroutineScope {
                val jobs = enabled.associateWith { provider ->
                    async {
                        withTimeoutOrNull(8_000L) {
                            runCatching { provider.fetch(query) }.getOrNull()
                        }
                    }
                }

                withTimeoutOrNull(25_000L) {
                    for (provider in enabled) {
                        val result = jobs[provider]?.await()
                        if (result != null && result.raw.isNotBlank()) {
                            return@withTimeoutOrNull result
                        }
                    }
                    null
                }
            }

            if (remoteLyrics != null) {
                val document = LyricsParserRegistry.parse(
                    raw = remoteLyrics.raw,
                    formatHint = remoteLyrics.format,
                    mediaId = item.mediaId,
                    title = title,
                    artist = artist,
                    album = album,
                    source = LyricsSource.ONLINE,
                    providerId = remoteLyrics.providerId,
                    providerName = remoteLyrics.providerName
                )
                if (document.lines.isNotEmpty()) {
                    cache(item, document, durationMs)
                    return@withContext document
                }
            }
        }

        val fallback = unsyncedEmbedded ?: loadCacheIgnoreOffset(item, title, artist, album)
        if (fallback != null && fallback === unsyncedEmbedded) {
            cache(item, fallback, durationMs)
        }
        return@withContext fallback
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
            providerName = LyricsProviderRegistry.getProvider(cached.providerId)?.displayName,
            offsetMs = cached.offsetMs
        )
        return document.takeIf { it.lines.isNotEmpty() }
    }

    suspend fun searchCandidates(query: LyricsQuery): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val enabled = LyricsProviderRegistry.ordered(context).filter { it.isEnabled(context) }
        if (enabled.isEmpty()) return@withContext emptyList()

        val preferWordSync = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("lyrics_prefer_word_sync", true)

        val allCandidates = coroutineScope {
            val deferred = enabled.map { provider ->
                async {
                    withTimeoutOrNull(10_000L) {
                        runCatching { provider.search(query) }.getOrNull()
                    } ?: emptyList()
                }
            }
            deferred.awaitAll().flatten()
        }

        val targetDurationSec = (query.durationMs / 1000L).toInt()
        val providerRank = enabled.mapIndexed { idx, p -> p.id to idx }.toMap()

        // Dedupe identical raw lyrics across providers too, so the search dialog doesn't show
        // the same LRCLIB/LyricsPlus result twice.
        allCandidates.distinctBy { it.raw }
            .sortedWith(
                compareBy<LyricsCandidate> {
                    if (preferWordSync) (if (it.hasWordTimings) 0 else if (it.isSynced) 1 else 2)
                    else (if (it.isSynced) 0 else 1)
                }.thenBy {
                    if (targetDurationSec > 0 && it.durationSec > 0) {
                        kotlin.math.abs(it.durationSec - targetDurationSec)
                    } else {
                        1000
                    }
                }.thenBy {
                    providerRank[it.providerId] ?: 99
                }
            )
    }

    suspend fun applyCandidate(
        item: PlaybackItem,
        candidate: LyricsCandidate,
        durationMs: Long
    ): LyricsDocument? = withContext(Dispatchers.IO) {
        val document = LyricsParserRegistry.parse(
            raw = candidate.raw,
            formatHint = candidate.format,
            mediaId = item.mediaId,
            title = candidate.trackName.ifBlank { item.title },
            artist = candidate.artistName ?: item.artist,
            album = candidate.albumName ?: item.album,
            source = LyricsSource.ONLINE,
            providerId = candidate.providerId,
            providerName = candidate.providerName
        )
        if (document.lines.isNotEmpty()) {
            cache(item, document, durationMs)
            return@withContext document
        }
        null
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
            val start = line.startMs ?: 0L
            val stamp = formatLrcStamp(start)
            val body = buildString {
                if (hasWords && line.words.isNotEmpty()) {
                    line.words.forEach { word ->
                        append(formatLrcWordStamp(word.startMs ?: start))
                        append(word.text)
                        // Word stamps only delimit tokens when the tokens are separated.
                        // Without this space the round-trip glues the line into one word.
                        append(' ')
                    }
                } else {
                    append(line.text)
                }
            }.trimEnd()
            // A translation round-trips as a second line sharing the timestamp; parseLrc
            // recognises the duplicated stamp and pairs it back into LyricsLine.translation.
            if (line.translation.isNullOrBlank()) {
                "$stamp$body"
            } else {
                "$stamp$body\n$stamp${line.translation}"
            }
        }
    }

    private fun formatLrcStamp(ms: Long): String = String.format(
        java.util.Locale.US, "[%02d:%02d.%02d]", ms / 60_000, (ms % 60_000) / 1000, (ms % 1000) / 10
    )

    private fun formatLrcWordStamp(ms: Long): String = String.format(
        java.util.Locale.US, "<%02d:%02d.%02d>", ms / 60_000, (ms % 60_000) / 1000, (ms % 1000) / 10
    )

    companion object {
        private const val TAG = "LyricsRepository"
    }
}
