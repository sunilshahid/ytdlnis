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
import java.util.Locale

object LyricsPlusProvider : LyricsProvider {
    override val id = "lyricsplus"
    override val displayName = "LyricsPlus"

    private const val PREF_LAST_WORKING_MIRROR = "lyricsplus_last_mirror"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** Captured in [isEnabled]; the provider interface only passes Context there. */
    @Volatile
    private var appContext: Context? = null

    private val MIRRORS = listOf(
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus-seven.vercel.app"
    )

    private val junkBrackets = Regex(
        """\s*[(\[][^)\]]*(?:official|video|audio|lyric|visuali[sz]er|\bhd\b|\b4k\b|\bmv\b|remaster)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE
    )

    override fun isEnabled(context: Context): Boolean {
        appContext = context.applicationContext
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("lyrics_provider_lyricsplus", true)
    }

    private fun cleanTitle(title: String, artist: String?): String {
        var clean = junkBrackets.replace(title, "").trim()
        if (!artist.isNullOrBlank() && clean.startsWith("$artist - ", ignoreCase = true)) {
            clean = clean.substring(artist.length + 3).trim()
        }
        return clean.ifBlank { title }
    }

    private fun getOrderedMirrors(): List<String> {
        val prefs = appContext?.let {
            PreferenceManager.getDefaultSharedPreferences(it)
        }
        val last = prefs?.getString(PREF_LAST_WORKING_MIRROR, null)
        if (last != null && last in MIRRORS) {
            return listOf(last) + MIRRORS.filter { it != last }
        }
        return MIRRORS
    }

    private fun rememberWorkingMirror(mirror: String) {
        appContext?.let {
            PreferenceManager.getDefaultSharedPreferences(it)
                .edit().putString(PREF_LAST_WORKING_MIRROR, mirror).apply()
        }
    }

    private fun fetchJson(mirror: String, title: String, artist: String?, album: String?, durationSec: Int): JSONObject? {
        val params = mutableMapOf<String, String>()
        params["title"] = title
        if (!artist.isNullOrBlank()) params["artist"] = artist
        if (durationSec > 0) params["duration"] = durationSec.toString()
        if (!album.isNullOrBlank()) params["album"] = album

        val query = params.map { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")
        val fullUrl = "$mirror/v2/lyrics/get?$query"

        val conn = URL(fullUrl).openConnection() as HttpURLConnection
        return try {
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            rememberWorkingMirror(mirror)
            JSONObject(body)
        } catch (e: Exception) {
            Log.d(TAG, "LyricsPlus mirror $mirror failed: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun formatLrc(json: JSONObject): Pair<String, Boolean>? {
        val lyricsArray = json.optJSONArray("lyrics") ?: return null
        if (lyricsArray.length() == 0) return null

        val isWordLevel = json.optString("type").equals("Word", ignoreCase = true)
        val sb = StringBuilder()

        for (i in 0 until lyricsArray.length()) {
            val line = lyricsArray.optJSONObject(i) ?: continue
            val timeMs = line.optLong("time")
            val min = timeMs / 60_000
            val sec = (timeMs % 60_000) / 1000
            val cen = (timeMs % 1000) / 10
            val lineStamp = String.format(Locale.US, "[%02d:%02d.%02d]", min, sec, cen)

            val syllabus = line.optJSONArray("syllabus")
            if (isWordLevel && syllabus != null && syllabus.length() > 0) {
                sb.append(lineStamp)
                for (w in 0 until syllabus.length()) {
                    val word = syllabus.optJSONObject(w) ?: continue
                    val wTime = word.optLong("time")
                    val wText = word.optString("text")
                    val wMin = wTime / 60_000
                    val wSec = (wTime % 60_000) / 1000
                    val wCen = (wTime % 1000) / 10
                    val wordStamp = String.format(Locale.US, "<%02d:%02d.%02d>", wMin, wSec, wCen)
                    sb.append(wordStamp).append(wText)
                }
                sb.append("\n")
            } else {
                val text = line.optString("text")
                sb.append(lineStamp).append(text).append("\n")
            }
        }

        val raw = sb.toString().trim()
        if (raw.isBlank()) return null
        return raw to isWordLevel
    }

    override suspend fun fetch(query: LyricsQuery): ProviderLyrics? = withContext(Dispatchers.IO) {
        val clean = cleanTitle(query.title, query.artist)
        val durationSec = (query.durationMs / 1000L).toInt()

        for (mirror in getOrderedMirrors()) {
            val json = fetchJson(mirror, clean, query.artist, query.album, durationSec)
                ?: (if (clean != query.title) fetchJson(mirror, query.title, query.artist, query.album, durationSec) else null)
            if (json != null) {
                val parsed = formatLrc(json)
                if (parsed != null) {
                    val (raw, isWord) = parsed
                    return@withContext ProviderLyrics(
                        raw = raw,
                        format = if (isWord) LyricsFormat.LRC_A2 else LyricsFormat.LRC,
                        providerId = id,
                        providerName = displayName,
                        matchedTitle = query.title,
                        matchedArtist = query.artist,
                        matchedDurationMs = query.durationMs
                    )
                }
            }
        }
        null
    }

    override suspend fun search(query: LyricsQuery): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val clean = cleanTitle(query.title, query.artist)
        val durationSec = (query.durationMs / 1000L).toInt()

        for (mirror in getOrderedMirrors()) {
            val json = fetchJson(mirror, clean, query.artist, query.album, durationSec)
                ?: (if (clean != query.title) fetchJson(mirror, query.title, query.artist, query.album, durationSec) else null)
            if (json != null) {
                val parsed = formatLrc(json)
                if (parsed != null) {
                    val (raw, isWord) = parsed
                    val preview = raw.lines().take(2).joinToString("\n") { line ->
                        line.replace(Regex("""\[\d+:\d+\.\d+]"""), "")
                            .replace(Regex("""<\d+:\d+\.\d+>"""), "")
                            .trim()
                    }
                    return@withContext listOf(
                        LyricsCandidate(
                            providerId = id,
                            providerName = displayName,
                            trackName = query.title,
                            artistName = query.artist,
                            albumName = query.album,
                            durationSec = durationSec,
                            format = if (isWord) LyricsFormat.LRC_A2 else LyricsFormat.LRC,
                            isSynced = true,
                            hasWordTimings = isWord,
                            preview = preview,
                            raw = raw
                        )
                    )
                }
            }
        }
        emptyList()
    }

    private const val TAG = "LyricsPlusProvider"
}
