package com.deniscerri.ytdl.vault.ingest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.vault.VaultRepository
import java.io.File

/**
 * Extracts video duration, dimensions, MIME type, and an encrypted-ready WebP thumbnail strictly
 * inside volatile RAM / `vault_tmp/{uuid}/` without touching `cacheDir/video_frames` or `MediaStore`.
 */
object VaultMediaExtractor {

    private const val THUMB_MAX_DIM = 512

    data class ExtractedMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val mimeType: String?,
        val embeddedTitle: String?,
        val thumbnailWebp: ByteArray?
    )

    fun extractFromFile(context: Context, file: File, jobTempDir: File): ExtractedMetadata {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 0
        var height = 0
        var mimeType: String? = null
        var embeddedTitle: String? = null
        var bitmap: Bitmap? = null

        try {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            embeddedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            bitmap = extractScaledFrame(retriever)
        } catch (_: Throwable) {
            // Fall through to ffmpeg fallback when platform retriever fails (e.g. AV1 WebM).
        } finally {
            runCatching { retriever.release() }
        }

        if (bitmap == null) {
            bitmap = extractFrameViaFfmpeg(file, jobTempDir)
        }

        val webp = bitmap?.let { bmp ->
            try {
                VaultRepository.encodeThumbnailWebp(bmp)
            } finally {
                bmp.recycle()
            }
        }

        return ExtractedMetadata(
            durationMs = durationMs,
            width = width,
            height = height,
            mimeType = mimeType,
            embeddedTitle = embeddedTitle?.takeIf { it.isNotBlank() },
            thumbnailWebp = webp
        )
    }

    fun extractFromUri(context: Context, uri: Uri): ExtractedMetadata {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 0
        var height = 0
        var mimeType: String? = null
        var embeddedTitle: String? = null
        var bitmap: Bitmap? = null

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                embeddedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                bitmap = extractScaledFrame(retriever)
            }
        } catch (_: Throwable) {
            // Ignore; callers treat nullable fields gracefully.
        } finally {
            runCatching { retriever.release() }
        }

        val webp = bitmap?.let { bmp ->
            try {
                VaultRepository.encodeThumbnailWebp(bmp)
            } finally {
                bmp.recycle()
            }
        }

        return ExtractedMetadata(
            durationMs = durationMs,
            width = width,
            height = height,
            mimeType = mimeType,
            embeddedTitle = embeddedTitle?.takeIf { it.isNotBlank() },
            thumbnailWebp = webp
        )
    }

    private fun extractScaledFrame(retriever: MediaMetadataRetriever): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                THUMB_MAX_DIM,
                THUMB_MAX_DIM
            ) ?: retriever.getScaledFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                THUMB_MAX_DIM,
                THUMB_MAX_DIM
            )
        } else {
            @Suppress("DEPRECATION")
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }

    private fun extractFrameViaFfmpeg(sourceFile: File, jobTempDir: File): Bitmap? {
        val ffmpeg = runCatching { RuntimeManager.getInstance().ffmpegLocation }.getOrNull()
        if (ffmpeg == null || !ffmpeg.isAvailable) return null
        val tempThumb = File(jobTempDir, "vault_thumb_tmp.jpg")
        tempThumb.delete()
        return try {
            val primaryCmd = listOf(
                ffmpeg.executable.absolutePath,
                "-y", "-nostdin", "-ss", "1", "-i", sourceFile.absolutePath,
                "-frames:v", "1", "-an",
                "-vf", "scale=$THUMB_MAX_DIM:$THUMB_MAX_DIM:force_original_aspect_ratio=decrease",
                tempThumb.absolutePath
            )
            val ran = runCatching { RuntimeManager.getInstance().executeImpl(primaryCmd) }.isSuccess
            if (!ran || !tempThumb.exists() || tempThumb.length() <= 0L) {
                tempThumb.delete()
                val fallbackCmd = listOf(
                    ffmpeg.executable.absolutePath,
                    "-y", "-nostdin", "-i", sourceFile.absolutePath,
                    "-frames:v", "1", "-an",
                    "-vf", "scale=$THUMB_MAX_DIM:$THUMB_MAX_DIM:force_original_aspect_ratio=decrease",
                    tempThumb.absolutePath
                )
                runCatching { RuntimeManager.getInstance().executeImpl(fallbackCmd) }
            }
            if (tempThumb.exists() && tempThumb.length() > 0L) {
                BitmapFactory.decodeFile(tempThumb.absolutePath)
            } else {
                null
            }
        } finally {
            tempThumb.delete()
        }
    }
}
