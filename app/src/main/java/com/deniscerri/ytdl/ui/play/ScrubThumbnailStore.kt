package com.deniscerri.ytdl.ui.play

import android.content.Context
import android.util.Log
import com.deniscerri.ytdl.playback.chapters.BundledMediaTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Builds and serves a keyframe thumbnail strip for the seek preview.
 *
 * A naive "decode every keyframe" pass is far too slow: `-skip_frame nokey` is only honoured by
 * some decoders (H.264/H.265 do, VP9/AV1 do not), so on a VP9 download it decodes the entire
 * movie — minutes of CPU for a 30-minute file.
 *
 * Instead the strip is built the way a demuxer would:
 *  1. list keyframe packet timestamps with ffprobe (demux only, ~1 s for a 30-minute file);
 *  2. extract each of those exact positions with `-ss` before `-i`, batching many seeked inputs
 *     into a single ffmpeg process (~40 ms per frame instead of a process per frame).
 *
 * Batches are published as they finish, so the first drag is instant after a couple of seconds
 * even while the rest of the strip is still being written.
 */
class ScrubThumbnailStore(context: Context) {

    data class Thumbnail(val timestampMs: Long, val file: File)

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val byUri = ConcurrentHashMap<String, Source>()
    private val lock = Any()

    private class Source(val path: String, val directory: File) {
        @Volatile var frames: Array<Frame>? = null
        @Volatile var failed = false
        @Volatile var job: Job? = null
        @Volatile var process: Process? = null
    }

    private data class Frame(val timestampMs: Long, val fileName: String)

    /** Starts strip generation for [localUri] if it is not cached yet. Safe to call repeatedly. */
    fun prepare(localUri: String?) {
        if (localUri.isNullOrBlank()) return
        // Fast path: this is called from touch-move on the UI thread, so avoid launching a
        // coroutine (or touching the filesystem) when the source is already settled.
        byUri[localUri]?.let { source ->
            if (source.frames != null || source.failed || source.job?.isActive == true) return
        }

        scope.launch {
            // Resolving the source and reading the index are disk I/O; keep them off the caller.
            val source = sourceFor(localUri) ?: return@launch
            if (source.frames != null || source.failed || source.job?.isActive == true) return@launch

            val cached = readIndex(source.directory)
            if (cached != null) {
                source.frames = cached
                return@launch
            }

            synchronized(lock) {
                if (source.frames != null || source.failed || source.job?.isActive == true) return@launch
                source.job = scope.launch { generate(source) }
            }
        }
    }

    /** Nearest cached keyframe at [positionMs], or null while that part of the strip is missing. */
    fun thumbnailFor(localUri: String?, positionMs: Long): Thumbnail? {
        if (localUri.isNullOrBlank()) return null
        val source = byUri[localUri] ?: return null
        val frames = source.frames ?: return null
        if (frames.isEmpty()) return null
        // While batches are still being published the strip only covers the start of the file;
        // refuse positions it cannot represent instead of showing a stale frame.
        if (positionMs < frames[0].timestampMs - EDGE_TOLERANCE_MS) return null
        if (positionMs > frames[frames.size - 1].timestampMs + EDGE_TOLERANCE_MS) return null
        val entry = frames[nearestIndex(frames, positionMs)]
        return Thumbnail(entry.timestampMs, File(source.directory, entry.fileName))
    }

    fun release() {
        byUri.values.forEach { source ->
            runCatching { source.process?.destroyForcibly() }
            source.job?.cancel()
        }
        scope.cancel()
        byUri.clear()
    }

    // --- generation ------------------------------------------------------------------------

    private suspend fun generate(source: Source) {
        val ffprobe = BundledMediaTools.ffprobe(appContext)
        val ffmpeg = BundledMediaTools.ffmpeg(appContext)
        if (ffprobe == null || ffmpeg == null) {
            Log.w(TAG, "ffmpeg/ffprobe missing; scrub previews stay on the retriever path")
            source.failed = true
            return
        }

        try {
            val keyframes = probeKeyframes(ffprobe, source.path)
            if (keyframes.isEmpty()) {
                Log.w(TAG, "no keyframes reported for ${source.path}")
                source.failed = true
                return
            }

            // Long files are sampled instead of storing every keyframe.
            val step = ((keyframes.size + MAX_FRAMES - 1) / MAX_FRAMES).coerceAtLeast(1)
            val wanted = ArrayList<Long>(minOf(keyframes.size, MAX_FRAMES))
            var index = 0
            while (index < keyframes.size) {
                wanted.add(keyframes[index])
                index += step
            }

            // A strip left behind by an interrupted run is incomplete; start clean.
            source.directory.deleteRecursively()
            if (!source.directory.mkdirs() && !source.directory.isDirectory) {
                source.failed = true
                return
            }

            val published = ArrayList<Frame>(wanted.size)
            var offset = 0
            var emptyBatches = 0
            for (batch in wanted.chunked(BATCH_FRAMES)) {
                currentCoroutineContext().ensureActive()
                extractBatch(ffmpeg, source.path, batch, offset, source.directory, source)
                var added = 0
                for (i in batch.indices) {
                    val name = fileNameFor(offset + i)
                    if (File(source.directory, name).isFile) {
                        published.add(Frame(batch[i], name))
                        added++
                    }
                }
                offset += batch.size
                // Nothing at all from three batches in a row means the file cannot be decoded
                // with this build; stop instead of spinning through the whole movie.
                emptyBatches = if (added == 0) emptyBatches + 1 else 0
                if (published.isEmpty() && emptyBatches >= 3) {
                    Log.w(TAG, "ffmpeg cannot extract thumbnails for ${source.path}")
                    source.failed = true
                    source.directory.deleteRecursively()
                    return
                }
                if (published.isEmpty()) continue

                // Publish what exists so far so dragging is already instant for this range.
                val snapshot = published.toTypedArray()
                writeIndex(source.directory, snapshot, complete = false)
                source.frames = snapshot
                Log.d(TAG, "published ${snapshot.size}/${wanted.size} thumbnails for ${source.path}")
            }

            if (published.isEmpty()) {
                Log.w(TAG, "ffmpeg produced no thumbnails for ${source.path}")
                source.failed = true
                source.directory.deleteRecursively()
                return
            }
            writeIndex(source.directory, published.toTypedArray(), complete = true)
            source.frames = published.toTypedArray()
            Log.d(TAG, "prepared ${published.size} scrub thumbnails for ${source.path}")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w(TAG, "thumbnail generation failed: ${error.message}")
            source.failed = true
        }
    }

    /**
     * Extracts [times] (milliseconds) into `directory`, naming each image after its sequence
     * index. All positions are passed to one ffmpeg process as separate seeked inputs, which
     * avoids paying the process start-up cost per frame.
     */
    private fun extractBatch(
        ffmpeg: File,
        path: String,
        times: List<Long>,
        offset: Int,
        directory: File,
        source: Source
    ) {
        val arguments = ArrayList<String>(times.size * 10 + 4)
        arguments.add("-nostdin")
        arguments.add("-v")
        arguments.add("error")
        times.forEachIndexed { index, timeMs ->
            arguments.add("-ss")
            arguments.add(String.format(Locale.ROOT, "%.3f", timeMs / 1000.0))
            arguments.add("-i")
            arguments.add(path)
            arguments.add("-frames:v")
            arguments.add("1")
            arguments.add("-vsync")
            arguments.add("0")
            arguments.add("-vf")
            arguments.add("scale=$THUMB_WIDTH:-2")
            arguments.add("-q:v")
            arguments.add("5")
            arguments.add(File(directory, fileNameFor(offset + index)).absolutePath)
        }

        val result = BundledMediaTools.run(
            context = appContext,
            binary = ffmpeg,
            arguments = arguments,
            timeoutMs = BATCH_TIMEOUT_MS
        ) { process -> source.process = process }
        source.process = null

        if (result == null || !result.isSuccess) {
            Log.w(
                TAG,
                "thumbnail batch failed (${result?.exitCode}): " +
                    result?.stderr?.take(300)?.replace('\n', ' ')
            )
        }
    }

    /**
     * Keyframe timestamps from packet flags. This only demuxes, so it costs a second even on a
     * full-length movie, unlike decoding.
     */
    private fun probeKeyframes(binary: File, path: String): LongArray {
        val result = BundledMediaTools.run(
            context = appContext,
            binary = binary,
            arguments = listOf(
                // ffprobe has no -nostdin; passing it makes the process exit before printing.
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "packet=pts_time,flags",
                "-of", "csv=p=0",
                path
            ),
            timeoutMs = PROBE_TIMEOUT_MS
        )
        if (result == null || !result.isSuccess) {
            Log.w(
                TAG,
                "keyframe probe failed (${result?.exitCode}): " +
                    result?.stderr?.take(300)?.replace('\n', ' ')
            )
            return LongArray(0)
        }

        val timestamps = ArrayList<Long>(1024)
        result.stdout.lineSequence().forEach { line ->
            // Format: "12.345000,K__"
            val comma = line.indexOf(',')
            if (comma <= 0) return@forEach
            if (!line.substring(comma + 1).contains('K')) return@forEach
            val seconds = line.substring(0, comma).trim().toDoubleOrNull() ?: return@forEach
            if (seconds >= 0.0) timestamps.add((seconds * 1000.0).roundToLong())
        }
        if (timestamps.isEmpty()) return LongArray(0)
        return timestamps.distinct().sorted().toLongArray()
    }

    private fun nearestIndex(frames: Array<Frame>, positionMs: Long): Int {
        if (frames.size == 1) return 0
        var low = 0
        var high = frames.size - 1
        while (low < high) {
            val mid = (low + high) / 2
            if (frames[mid].timestampMs < positionMs) low = mid + 1 else high = mid
        }
        val next = low
        val previous = (low - 1).coerceAtLeast(0)
        return if (abs(frames[next].timestampMs - positionMs) <
            abs(frames[previous].timestampMs - positionMs)
        ) next else previous
    }

    // --- cache files -----------------------------------------------------------------------

    private fun sourceFor(localUri: String): Source? {
        byUri[localUri]?.let { return it }
        val file = BundledMediaTools.localFile(appContext, localUri) ?: return null
        val directory = File(File(appContext.cacheDir, CACHE_DIR), cacheKey(file))
        val source = Source(file.absolutePath, directory)
        return byUri.putIfAbsent(localUri, source) ?: source
    }

    private fun cacheKey(file: File): String {
        // The version segment invalidates strips built by older settings (smaller tiles).
        val raw = "${file.absolutePath}|${file.length()}|${file.lastModified()}|$CACHE_VERSION"
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
        return digest.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }

    private fun readIndex(directory: File): Array<Frame>? {
        val index = File(directory, INDEX_FILE)
        if (!index.isFile) return null
        return runCatching {
            val root = JSONObject(index.readText())
            if (!root.optBoolean("complete", false)) return null
            val array = root.optJSONArray("frames") ?: return null
            ArrayList<Frame>(array.length()).apply {
                for (i in 0 until array.length()) {
                    val entry = array.optJSONObject(i) ?: continue
                    val name = entry.optString("file")
                    if (name.isBlank()) continue
                    add(Frame(entry.optLong("time", 0L), name))
                }
            }.toTypedArray()
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun writeIndex(directory: File, frames: Array<Frame>, complete: Boolean) {
        val array = org.json.JSONArray()
        frames.forEach { frame ->
            array.put(
                JSONObject()
                    .put("time", frame.timestampMs)
                    .put("file", frame.fileName)
            )
        }
        runCatching {
            File(directory, INDEX_FILE).writeText(
                JSONObject()
                    .put("complete", complete)
                    .put("frames", array)
                    .toString()
            )
        }
    }

    private fun fileNameFor(index: Int): String = String.format(Locale.ROOT, "%05d.jpg", index)

    private companion object {
        const val TAG = "ScrubThumbnailStore"
        const val CACHE_DIR = "scrub_thumbs"
        const val INDEX_FILE = "index.json"

        /** Strips built with other tile sizes must not be reused. */
        const val CACHE_VERSION = "480-kf"

        /** Wide enough for a full-screen preview without looking mushy on a 1080p panel. */
        const val THUMB_WIDTH = 480

        /** Upper bound on stored frames; longer files keep every n-th keyframe instead. */
        const val MAX_FRAMES = 900

        /** Seeked inputs per ffmpeg run: enough to amortise start-up, few enough for file limits. */
        const val BATCH_FRAMES = 48

        /** How far past the published strip a lookup may go while batches are still landing. */
        const val EDGE_TOLERANCE_MS = 4_000L

        const val PROBE_TIMEOUT_MS = 120_000L
        const val BATCH_TIMEOUT_MS = 120_000L
    }
}
