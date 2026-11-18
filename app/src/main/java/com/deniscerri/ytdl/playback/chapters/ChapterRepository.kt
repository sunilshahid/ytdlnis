package com.deniscerri.ytdl.playback.chapters

import android.content.Context
import android.util.Log
import com.deniscerri.ytdl.playback.model.PlaybackChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

/** One embedded chapter track entry, as reported by ffprobe. */
data class VideoChapter(
    val index: Int,
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

/** A chapter that belongs to a SponsorBlock-style category and is skipped instead of listed. */
data class SponsorSegment(
    val category: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

data class VideoChapterData(
    val chapters: List<VideoChapter>,
    val sponsorSegments: List<SponsorSegment>
) {
    val isEmpty: Boolean get() = chapters.isEmpty() && sponsorSegments.isEmpty()

    companion object {
        val EMPTY = VideoChapterData(emptyList(), emptyList())
    }
}

/**
 * Reads embedded chapters out of local media files with the bundled `ffprobe` binary.
 *
 * ffprobe understands container-level chapter tracks in MP4/M4A, Matroska (MKV/WebM) and most
 * other containers, which the platform MediaExtractor does not expose. Results are cached in
 * memory and on disk (keyed by path + size + mtime) so replaying a file costs a single stat.
 *
 * Chapters whose titles match a SponsorBlock category are split into [SponsorSegment]s: the
 * player auto-skips those, and they never show up in the chapter pill / seekbar notches.
 */
object ChapterRepository {

    private const val TAG = "ChapterRepository"
    private const val PROBE_TIMEOUT_MS = 4_000L
    private const val CACHE_DIR = "chapter_cache"

    private val memory = ConcurrentHashMap<String, VideoChapterData>()
    private val uriMemory = ConcurrentHashMap<String, VideoChapterData>()

    /** Loads and caches chapter data for a local `file://`, plain path or MediaStore URI. */
    suspend fun load(context: Context, localUri: String): VideoChapterData {
        if (localUri.isBlank()) return VideoChapterData.EMPTY
        uriMemory[localUri]?.let { return it }
        return withContext(Dispatchers.IO) {
            uriMemory[localUri]?.let { return@withContext it }
            val appContext = context.applicationContext
            val source = resolveSource(appContext, localUri)
            if (source == null) {
                uriMemory[localUri] = VideoChapterData.EMPTY
                return@withContext VideoChapterData.EMPTY
            }
            memory[source.cacheKey]?.let {
                uriMemory[localUri] = it
                return@withContext it
            }
            val cacheFile = cacheFile(appContext, source.cacheKey)
            readCache(cacheFile)?.let {
                memory[source.cacheKey] = it
                uriMemory[localUri] = it
                return@withContext it
            }
            val probed = probe(appContext, source)
            // A successful probe with no chapters is worth caching too: it stops every replay
            // from spawning ffprobe again. A failed probe is not written, so a transient
            // failure can recover after a restart.
            if (probed != null) writeCache(cacheFile, probed)
            val resolved = probed ?: VideoChapterData.EMPTY
            memory[source.cacheKey] = resolved
            uriMemory[localUri] = resolved
            resolved
        }
    }

    /** Sponsor segments for an already-resolved local item. Never probes when cold. */
    fun cachedSegments(localUri: String?): List<SponsorSegment> {
        if (localUri.isNullOrBlank()) return emptyList()
        return uriMemory[localUri]?.sponsorSegments ?: emptyList()
    }

    fun toPlaybackChapters(data: VideoChapterData): List<PlaybackChapter> =
        data.chapters.map {
            PlaybackChapter(
                startMs = it.startTimeMs,
                endMs = it.endTimeMs.coerceAtLeast(it.startTimeMs),
                title = it.title
            )
        }

    // --- internals -------------------------------------------------------------------------

    private data class MediaSource(val path: String, val cacheKey: String)

    private fun resolveSource(context: Context, localUri: String): MediaSource? {
        val file = BundledMediaTools.localFile(context, localUri) ?: return null
        val key = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        return MediaSource(file.absolutePath, key)
    }

    /** Returns null when ffprobe could not run, so the caller can decide not to cache. */
    private fun probe(context: Context, source: MediaSource): VideoChapterData? {
        val binary = BundledMediaTools.ffprobe(context)
        if (binary == null) {
            Log.w(TAG, "ffprobe binary missing; no chapters for ${source.path}")
            return null
        }
        val result = BundledMediaTools.run(
            context = context,
            binary = binary,
            arguments = listOf(
                "-v", "error",
                "-print_format", "json",
                "-show_chapters",
                source.path
            ),
            timeoutMs = PROBE_TIMEOUT_MS
        )
        if (result == null) {
            Log.w(TAG, "ffprobe could not start for ${source.path}")
            return null
        }
        if (!result.isSuccess) {
            Log.w(
                TAG,
                "ffprobe exit ${result.exitCode} for ${source.path}: " +
                    result.stderr.take(400).replace('\n', ' ')
            )
            return null
        }
        if (result.stdout.isBlank()) {
            Log.w(TAG, "ffprobe returned no output for ${source.path}")
            return null
        }
        return parseProbeJson(result.stdout)
    }

    private fun parseProbeJson(raw: String): VideoChapterData? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val array = root.optJSONArray("chapters") ?: return VideoChapterData.EMPTY

        val parsed = ArrayList<VideoChapter>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val start = parseTimeMs(obj, "start_time", "start")
            if (start < 0L) continue
            val end = parseTimeMs(obj, "end_time", "end").coerceAtLeast(start)
            val title = chapterTitle(obj).ifBlank { "Chapter ${i + 1}" }
            parsed.add(VideoChapter(index = i, title = title, startTimeMs = start, endTimeMs = end))
        }
        if (parsed.isEmpty()) return VideoChapterData.EMPTY
        parsed.sortBy { it.startTimeMs }

        val normalized = parsed.mapIndexed { index, chapter ->
            val nextStart = parsed.getOrNull(index + 1)?.startTimeMs
            val end = when {
                chapter.endTimeMs > chapter.startTimeMs -> chapter.endTimeMs
                nextStart != null && nextStart > chapter.startTimeMs -> nextStart
                else -> chapter.startTimeMs
            }
            chapter.copy(endTimeMs = end)
        }

        val chapters = ArrayList<VideoChapter>(normalized.size)
        val sponsors = ArrayList<SponsorSegment>()
        normalized.forEach { chapter ->
            val category = sponsorCategory(chapter.title)
            if (category != null) {
                sponsors.add(
                    SponsorSegment(
                        category = category,
                        startTimeMs = chapter.startTimeMs,
                        endTimeMs = chapter.endTimeMs
                    )
                )
            } else {
                chapters.add(chapter)
            }
        }
        return VideoChapterData(chapters = chapters, sponsorSegments = sponsors)
    }

    private fun parseTimeMs(obj: JSONObject, secondsKey: String, rawKey: String): Long {
        obj.optString(secondsKey).toDoubleOrNull()?.let { seconds ->
            if (seconds >= 0.0 && !seconds.isNaN()) {
                return (seconds * 1000.0).roundToLong()
            }
        }
        val raw = obj.optDouble(rawKey, Double.NaN)
        if (raw.isNaN()) return -1L
        val timeBase = obj.optString("time_base", "1/1000")
        val seconds = raw * timeBaseSeconds(timeBase)
        if (seconds < 0.0) return -1L
        return (seconds * 1000.0).roundToLong()
    }

    private fun timeBaseSeconds(timeBase: String): Double = runCatching {
        val parts = timeBase.split('/')
        if (parts.size == 2) {
            val numerator = parts[0].toDouble()
            val denominator = parts[1].toDouble()
            if (denominator != 0.0) numerator / denominator else 1.0 / 1000.0
        } else {
            1.0 / 1000.0
        }
    }.getOrDefault(1.0 / 1000.0)

    private fun chapterTitle(obj: JSONObject): String {
        val tags = obj.optJSONObject("tags") ?: return ""
        val keys = tags.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.equals("title", ignoreCase = true)) {
                val value = tags.optString(key)
                if (value.isNotBlank()) return value.trim()
            }
        }
        return ""
    }

    /**
     * Maps a chapter title to a SponsorBlock category. Deliberately narrow: ordinary story
     * chapters called "Intro"/"Outro" stay visible chapters (the mockup shows "Intro" in the
     * pill), only explicit sponsor/self-promo/reminder/ad titles are treated as skippable.
     */
    private fun sponsorCategory(title: String): String? {
        val normalized = title.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (normalized.isEmpty()) return null
        return when {
            normalized.contains("interaction reminder") -> "Interaction Reminder"
            normalized.contains("self promotion") || normalized.contains("selfpromotion") ->
                "Self-Promotion"
            normalized.contains("sponsor") || normalized.contains("paid promotion") -> "Sponsor"
            normalized.contains("advertisement") ||
                normalized == "ad" ||
                normalized.startsWith("ad ") -> "Advertisement"
            else -> null
        }
    }

    private fun cacheFile(context: Context, cacheKey: String): File {
        val digest = MessageDigest.getInstance("MD5").digest(cacheKey.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        val directory = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        return File(directory, "$hex.json")
    }

    private fun readCache(file: File): VideoChapterData? = runCatching {
        if (!file.exists() || file.length() <= 0L) return null
        fromJson(file.readText())
    }.getOrNull()

    private fun writeCache(file: File, data: VideoChapterData) {
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(toJson(data))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private fun toJson(data: VideoChapterData): String {
        val chapters = JSONArray()
        data.chapters.forEach { chapter ->
            chapters.put(
                JSONObject().apply {
                    put("index", chapter.index)
                    put("title", chapter.title)
                    put("startTimeMs", chapter.startTimeMs)
                    put("endTimeMs", chapter.endTimeMs)
                }
            )
        }
        val sponsors = JSONArray()
        data.sponsorSegments.forEach { segment ->
            sponsors.put(
                JSONObject().apply {
                    put("category", segment.category)
                    put("startTimeMs", segment.startTimeMs)
                    put("endTimeMs", segment.endTimeMs)
                }
            )
        }
        return JSONObject().apply {
            put("chapters", chapters)
            put("sponsorSegments", sponsors)
        }.toString()
    }

    private fun fromJson(raw: String): VideoChapterData {
        val root = JSONObject(raw)
        val chapters = ArrayList<VideoChapter>()
        root.optJSONArray("chapters")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                chapters.add(
                    VideoChapter(
                        index = obj.optInt("index", i),
                        title = obj.optString("title").ifBlank { "Chapter ${i + 1}" },
                        startTimeMs = obj.optLong("startTimeMs", 0L).coerceAtLeast(0L),
                        endTimeMs = obj.optLong("endTimeMs", 0L).coerceAtLeast(0L)
                    )
                )
            }
        }
        val sponsors = ArrayList<SponsorSegment>()
        root.optJSONArray("sponsorSegments")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                sponsors.add(
                    SponsorSegment(
                        category = obj.optString("category").ifBlank { "Sponsor" },
                        startTimeMs = obj.optLong("startTimeMs", 0L).coerceAtLeast(0L),
                        endTimeMs = obj.optLong("endTimeMs", 0L).coerceAtLeast(0L)
                    )
                )
            }
        }
        return VideoChapterData(chapters = chapters, sponsorSegments = sponsors)
    }
}
