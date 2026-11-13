package com.deniscerri.ytdl.lyrics.providers

import android.content.Context
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.lyrics.LyricsFormat
import com.deniscerri.ytdl.lyrics.LyricsProvider
import com.deniscerri.ytdl.lyrics.LyricsSidecarReader
import com.deniscerri.ytdl.lyrics.model.LyricsCandidate
import com.deniscerri.ytdl.lyrics.model.LyricsQuery
import com.deniscerri.ytdl.lyrics.model.ProviderLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YouTubeSubtitleProvider : LyricsProvider {
    override val id = "youtube_subs"
    override val displayName = "YouTube Subtitles"

    override fun isEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("lyrics_provider_youtube_subs", true)
    }

    override suspend fun fetch(query: LyricsQuery): ProviderLyrics? = withContext(Dispatchers.IO) {
        val sidecar = LyricsSidecarReader.find(null, query.localUri, query.title)
            ?: LyricsSidecarReader.find(null, query.mediaId, query.title)
            ?: return@withContext null

        ProviderLyrics(
            raw = sidecar.raw,
            format = sidecar.format,
            providerId = id,
            providerName = displayName,
            matchedTitle = query.title,
            matchedArtist = query.artist,
            matchedDurationMs = query.durationMs
        )
    }

    override suspend fun search(query: LyricsQuery): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val sidecar = LyricsSidecarReader.find(null, query.localUri, query.title)
            ?: LyricsSidecarReader.find(null, query.mediaId, query.title)
            ?: return@withContext emptyList()

        val preview = sidecar.raw.lines()
            .filter { it.isNotBlank() && !it.startsWith("WEBVTT") && !it.contains("-->") }
            .take(2).joinToString("\n")

        listOf(
            LyricsCandidate(
                providerId = id,
                providerName = displayName,
                trackName = query.title,
                artistName = query.artist,
                albumName = query.album,
                durationSec = (query.durationMs / 1000L).toInt(),
                format = sidecar.format,
                isSynced = sidecar.format != LyricsFormat.PLAIN,
                hasWordTimings = false,
                preview = preview,
                raw = sidecar.raw
            )
        )
    }
}
