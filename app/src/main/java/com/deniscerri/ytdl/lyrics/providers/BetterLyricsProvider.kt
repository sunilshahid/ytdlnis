package com.deniscerri.ytdl.lyrics.providers

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.lyrics.LyricsFormat
import com.deniscerri.ytdl.lyrics.LyricsProvider
import com.deniscerri.ytdl.lyrics.model.LyricsCandidate
import com.deniscerri.ytdl.lyrics.model.LyricsQuery
import com.deniscerri.ytdl.lyrics.model.ProviderLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object BetterLyricsProvider : LyricsProvider {
    override val id = "betterlyrics"
    override val displayName = "BetterLyrics"

    private const val API_URL = "https://lyrics-api.boidu.dev/getLyrics"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val junkBrackets = Regex(
        """\s*[(\[][^)\]]*(?:official|video|audio|lyric|visuali[sz]er|\bhd\b|\b4k\b|\bmv\b|remaster)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE
    )

    override fun isEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("lyrics_provider_betterlyrics", true)
    }

    private fun cleanTitle(title: String, artist: String?): String {
        var clean = junkBrackets.replace(title, "").trim()
        if (!artist.isNullOrBlank() && clean.startsWith("$artist - ", ignoreCase = true)) {
            clean = clean.substring(artist.length + 3).trim()
        }
        return clean.ifBlank { title }
    }

    private fun requestTtml(title: String, artist: String?, album: String?, durationSec: Int): String? {
        val params = mutableMapOf<String, String>()
        params["s"] = title
        if (!artist.isNullOrBlank()) params["a"] = artist
        if (durationSec > 0) params["d"] = durationSec.toString()
        if (!album.isNullOrBlank()) params["al"] = album

        val query = params.map { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")
        val fullUrl = "$API_URL?$query"

        val conn = URL(fullUrl).openConnection() as HttpURLConnection
        return try {
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            json.optString("ttml").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.d(TAG, "BetterLyrics request failed: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun fetch(query: LyricsQuery): ProviderLyrics? = withContext(Dispatchers.IO) {
        val clean = cleanTitle(query.title, query.artist)
        val durationSec = (query.durationMs / 1000L).toInt()

        // 1. Try with cleaned title and artist
        var ttml = requestTtml(clean, query.artist, query.album, durationSec)

        // 2. If null and title was modified, try original title
        if (ttml == null && clean != query.title) {
            ttml = requestTtml(query.title, query.artist, query.album, durationSec)
        }

        // 3. If still null and artist has feat/ft, try stripping feat from artist
        if (ttml == null && !query.artist.isNullOrBlank() && query.artist.contains(Regex("""\b(feat|ft)\b""", RegexOption.IGNORE_CASE))) {
            val primaryArtist = query.artist.split(Regex("""\b(feat|ft)\b""", RegexOption.IGNORE_CASE))[0].trim()
            if (primaryArtist.isNotBlank()) {
                ttml = requestTtml(clean, primaryArtist, query.album, durationSec)
            }
        }

        if (ttml == null) return@withContext null

        ProviderLyrics(
            raw = ttml,
            format = LyricsFormat.TTML,
            providerId = id,
            providerName = displayName,
            matchedTitle = query.title,
            matchedArtist = query.artist,
            matchedDurationMs = query.durationMs
        )
    }

    override suspend fun search(query: LyricsQuery): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val clean = cleanTitle(query.title, query.artist)
        val durationSec = (query.durationMs / 1000L).toInt()

        val candidates = mutableListOf<LyricsCandidate>()
        val tried = mutableSetOf<String>()

        fun tryCandidate(t: String, a: String?) {
            val key = "$t|$a"
            if (!tried.add(key)) return
            val ttml = requestTtml(t, a, query.album, durationSec) ?: return
            val preview = extractPreview(ttml)
            val hasWords = ttml.contains("<span") && ttml.contains("begin=")
            candidates.add(
                LyricsCandidate(
                    providerId = id,
                    providerName = displayName,
                    trackName = t,
                    artistName = a,
                    albumName = query.album,
                    durationSec = durationSec,
                    format = LyricsFormat.TTML,
                    isSynced = true,
                    hasWordTimings = hasWords,
                    preview = preview,
                    raw = ttml
                )
            )
        }

        tryCandidate(clean, query.artist)
        if (candidates.isEmpty() && clean != query.title) {
            tryCandidate(query.title, query.artist)
        }
        if (candidates.isEmpty() && !query.artist.isNullOrBlank() && query.artist.contains(Regex("""\b(feat|ft)\b""", RegexOption.IGNORE_CASE))) {
            val primaryArtist = query.artist.split(Regex("""\b(feat|ft)\b""", RegexOption.IGNORE_CASE))[0].trim()
            tryCandidate(clean, primaryArtist)
        }

        candidates
    }

    private fun extractPreview(ttml: String): String {
        val pRegex = Regex("""<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val tagRegex = Regex("""<[^>]+>""")
        val matches = pRegex.findAll(ttml).take(2).toList()
        return matches.map { match ->
            tagRegex.replace(match.groupValues[1], "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim()
        }.filter { it.isNotBlank() }.joinToString("\n")
    }

    private const val TAG = "BetterLyricsProvider"
}
