package com.deniscerri.ytdl.lyrics.providers

import android.content.Context
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.lyrics.LyricsFormat
import com.deniscerri.ytdl.lyrics.LyricsProvider
import com.deniscerri.ytdl.lyrics.model.LyricsCandidate
import com.deniscerri.ytdl.lyrics.model.LyricsQuery
import com.deniscerri.ytdl.lyrics.model.ProviderLyrics
import com.deniscerri.ytdl.util.extractors.LrcLibUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object LrcLibProvider : LyricsProvider {
    override val id = "lrclib"
    override val displayName = "LRCLIB"

    override fun isEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("lyrics_provider_lrclib", true)
    }

    override suspend fun fetch(query: LyricsQuery): ProviderLyrics? = withContext(Dispatchers.IO) {
        val remote = runCatching {
            LrcLibUtil.find(
                track = query.title,
                artist = query.artist.orEmpty(),
                album = query.album.orEmpty(),
                durationSec = (query.durationMs / 1000L).toInt()
            )
        }.getOrNull() ?: return@withContext null

        val raw = if (remote.synced.isNotEmpty()) {
            remote.synced.joinToString("\n") { line ->
                val minutes = line.timeMs / 60_000
                val seconds = (line.timeMs % 60_000) / 1000
                val centis = (line.timeMs % 1000) / 10
                String.format(Locale.US, "[%02d:%02d.%02d]%s", minutes, seconds, centis, line.text)
            }
        } else {
            remote.plain
        }
        val format = if (remote.synced.isNotEmpty()) LyricsFormat.LRC else LyricsFormat.PLAIN

        ProviderLyrics(
            raw = raw,
            format = format,
            providerId = id,
            providerName = displayName,
            matchedTitle = remote.trackName,
            matchedArtist = remote.artistName,
            matchedDurationMs = remote.durationSec * 1000L
        )
    }

    override suspend fun search(query: LyricsQuery): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val q = listOfNotNull(query.artist?.takeIf { it.isNotBlank() }, query.title)
            .joinToString(" ")
            .trim()
        if (q.isBlank()) return@withContext emptyList()

        val results = runCatching { LrcLibUtil.search(q) }.getOrNull() ?: emptyList()
        results.map { r ->
            val raw = if (r.synced.isNotEmpty()) {
                r.synced.joinToString("\n") { line ->
                    val minutes = line.timeMs / 60_000
                    val seconds = (line.timeMs % 60_000) / 1000
                    val centis = (line.timeMs % 1000) / 10
                    String.format(Locale.US, "[%02d:%02d.%02d]%s", minutes, seconds, centis, line.text)
                }
            } else {
                r.plain
            }
            val preview = (if (r.isSynced) r.synced.map { it.text } else r.plain.lines())
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("\n")

            LyricsCandidate(
                providerId = id,
                providerName = displayName,
                trackName = r.trackName,
                artistName = r.artistName,
                albumName = r.albumName,
                durationSec = r.durationSec,
                format = if (r.isSynced) LyricsFormat.LRC else LyricsFormat.PLAIN,
                isSynced = r.isSynced,
                hasWordTimings = false,
                preview = preview,
                raw = raw
            )
        }
    }
}
