package com.deniscerri.ytdl.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.deniscerri.ytdl.core.RuntimeManager
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Extracts stills with the bundled ffmpeg runtime. Used as a thumbnail fallback for media the
 * platform cannot decode: AV1 webm videos on devices without a hardware decoder, and audio
 * files (ogg/opus) whose embedded artwork MediaMetadataRetriever cannot read.
 *
 * Every result is cached as a jpg in the app cache dir, keyed by a hash of the source path
 * (and a format variant). Later lookups are a single stat call, so the cache directory acts
 * as the persisted thumbnail store.
 */
object FrameExtractor {

    private val extractionLock = Any()

    /** Source paths whose extraction has already failed in this process lifetime. */
    private val knownMissing = ConcurrentHashMap.newKeySet<String>()

    /** Video frame near the start of the file. */
    fun frameFor(context: Context, sourcePath: String, targetSize: Int = 512): Bitmap? =
        artworkFor(context, VIDEO_VARIANT, sourcePath, targetSize) { output ->
            mutableListOf(
                "-y", "-nostdin", "-ss", "1", "-i", sourcePath,
                "-frames:v", "1", "-an",
                "-vf", "scale=$targetSize:$targetSize:force_original_aspect_ratio=decrease",
                output
            ) to mutableListOf(
                "-y", "-nostdin", "-i", sourcePath, "-frames:v", "1", "-an", output
            )
        }

    /**
     * Embedded cover art of an audio file. ffmpeg exposes attached pictures as a video
     * stream, which the platform retriever often fails to surface for ogg/opus.
     */
    fun coverFor(context: Context, sourcePath: String, targetSize: Int = 512): Bitmap? =
        artworkFor(context, AUDIO_VARIANT, sourcePath, targetSize) { output ->
            mutableListOf(
                "-y", "-nostdin", "-i", sourcePath, "-map", "0:v", "-frames:v", "1", "-an",
                "-vf", "scale=$targetSize:$targetSize:force_original_aspect_ratio=decrease",
                output
            ) to null
        }

    /**
     * Fast check for an already-extracted artwork file: one stat call, no process spawning.
     * Returns the decoded bitmap when the cache already holds the image for this source.
     */
    fun cachedFor(
        context: Context,
        variant: String,
        sourcePath: String,
        targetSize: Int = 512
    ): Bitmap? {
        val output = cachedFileFor(context, variant, sourcePath) ?: return null
        return decodeSampled(output, targetSize)
    }

    /**
     * Stat-only lookup of the cached artwork [File] for a source path, or null when the cache
     * does not hold an image for it yet. Callers that only need the file path (e.g. to point a
     * notification at it) avoid decoding the bitmap entirely.
     */
    fun cachedFileFor(context: Context, variant: String, sourcePath: String): File? {
        if (sourcePath in knownMissing) return null
        val output = outputFile(context, "$variant\u0000$sourcePath") ?: return null
        if (!output.exists() || output.length() <= 0L) return null
        return output
    }

    const val VIDEO_VARIANT = "video"
    const val AUDIO_VARIANT = "cover"

    /** Deletes any cached video/cover thumbnails associated with [sourcePath]. */
    fun deleteCachedFor(context: Context, sourcePath: String) {
        knownMissing.remove(sourcePath)
        runCatching { outputFile(context, "$VIDEO_VARIANT\u0000$sourcePath")?.delete() }
        runCatching { outputFile(context, "$AUDIO_VARIANT\u0000$sourcePath")?.delete() }
    }

    private fun artworkFor(
        context: Context,
        variant: String,
        sourcePath: String,
        targetSize: Int,
        buildCommands: (outputPath: String) -> Pair<MutableList<String>, MutableList<String>?>
    ): Bitmap? {
        val output = outputFile(context, "$variant\u0000$sourcePath") ?: return null
        if (output.exists() && output.length() > 0L) return decodeSampled(output, targetSize)
        if (sourcePath in knownMissing) return null
        if (!extract(context, sourcePath, output, targetSize, buildCommands)) return null
        return decodeSampled(output, targetSize)
    }

    private fun extract(
        context: Context,
        sourcePath: String,
        output: File,
        targetSize: Int,
        buildCommands: (outputPath: String) -> Pair<MutableList<String>, MutableList<String>?>
    ): Boolean {
        val ffmpeg = runCatching { RuntimeManager.getInstance().ffmpegLocation }.getOrNull()
        if (ffmpeg == null || !ffmpeg.isAvailable) {
            Log.d(TAG, "ffmpeg unavailable")
            return false
        }
        val binary = ffmpeg.executable.absolutePath
        synchronized(extractionLock) {
            if (output.exists() && output.length() > 0L) return true
            val (primary, fallback) = buildCommands(output.absolutePath)

            runCatching { RuntimeManager.getInstance().executeImpl(listOf(binary) + primary) }
                .onFailure { failure ->
                    Log.d(TAG, "ffmpeg failed for ${output.name}: $failure")
                    output.delete()
                    fallback?.let {
                        runCatching {
                            RuntimeManager.getInstance().executeImpl(listOf(binary) + it)
                        }
                    }
                }
            val success = output.exists() && output.length() > 0L
            if (success) {
                knownMissing.remove(sourcePath)
                trimCache(context)
                Log.d(TAG, "ffmpeg extracted ${output.name}")
            } else {
                knownMissing.add(sourcePath)
                output.delete()
                Log.d(TAG, "ffmpeg could not produce ${output.name}")
            }
            return success
        }
    }

    private fun outputFile(context: Context, cacheKey: String): File? =
        runCatching {
            val digest = MessageDigest.getInstance("MD5").digest(cacheKey.toByteArray())
            val hex = digest.joinToString("") { "%02x".format(it) }
            val directory = File(context.cacheDir, "video_frames").apply { mkdirs() }
            File(directory, "$hex.jpg")
        }.getOrNull()

    private fun decodeSampled(file: File, targetSize: Int): Bitmap? = runCatching {
        if (!file.exists() || file.length() <= 0L) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > targetSize * 2 ||
            bounds.outHeight / sample > targetSize * 2
        ) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }.getOrNull()

    private fun trimCache(context: Context) {
        runCatching {
            val directory = File(context.cacheDir, "video_frames")
            val files = directory.listFiles()?.sortedBy { it.lastModified() } ?: return
            if (files.size > MAX_FRAME_CACHE_FILES) {
                files.take(files.size - MAX_FRAME_CACHE_FILES).forEach { it.delete() }
            }
        }
    }

    private const val MAX_FRAME_CACHE_FILES = 200
    private const val TAG = "FrameExtractor"
}
