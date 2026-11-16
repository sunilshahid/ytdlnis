package com.deniscerri.ytdl.ui.play

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackSource
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.util.FrameExtractor
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Loads playback artwork for Play tab rows and the audio player.
 *
 * Remote items go through Picasso as before. Local items are decoded from the media file itself
 * (system thumbnail, embedded picture or a video frame) so history and queue entries get real
 * artwork even when `artworkUri` is missing or points to an album-art URI the device cannot serve.
 */
object PlaybackArtworkLoader {

    private var appContextRef: Context? = null
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private val loading = mutableSetOf<String>()

    private val cache by lazy {
        object : LruCache<String, Bitmap>(maxCacheSize) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    private val maxCacheSize: Int
        get() = (Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt()

    /**
     * Fire-and-forget generation of the ffmpeg artwork for a local item. Used to warm the disk
     * cache ahead of the media notification (which can only read cached files synchronously).
     */
    fun warmup(context: Context, item: PlaybackItem) {
        appContextRef = context.applicationContext
        scope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { loadLocalBitmap(context.applicationContext, item) }
            }
        }
    }

    fun load(target: ImageView, item: PlaybackItem, crossfade: Boolean = false) {
        val context = target.context.applicationContext
        appContextRef = context

        val key = "${item.mediaId}|${item.artworkUri.orEmpty()}|${item.localUri.orEmpty()}"
        if (target.tag == key) return
        val prevDrawable = if (crossfade) target.drawable else null
        target.tag = key

        val fallback = when (item.type) {
            PlaybackType.VIDEO -> R.drawable.ic_video
            PlaybackType.AUDIO -> R.drawable.ic_music
        }

        fun applyBitmap(bm: Bitmap) {
            if (prevDrawable != null) {
                val nextDrawable = android.graphics.drawable.BitmapDrawable(target.resources, bm)
                val transition = android.graphics.drawable.TransitionDrawable(arrayOf(prevDrawable, nextDrawable)).apply {
                    isCrossFadeEnabled = true
                }
                target.setImageDrawable(transition)
                transition.startTransition(260)
            } else {
                target.setImageBitmap(bm)
            }
        }

        cache.get(key)?.let {
            applyBitmap(it)
            return
        }
        if (prevDrawable == null) {
            target.setImageResource(fallback)
        }

        // Anything backed by a local file goes through the local pipeline (system thumbnail,
        // embedded picture, ffmpeg frame). This includes recent/queue rows whose persisted
        // source is not LOCAL — their artworkUri may be blank (e.g. AV1 webm downloads).
        val preferLocal = item.source == PlaybackSource.LOCAL || item.isLocal
        if (!preferLocal) {
            val uri = item.artworkUri?.takeIf { it.isNotBlank() }
            if (uri == null) return
            val request = Picasso.get().load(uri).fit().centerCrop()
            if (prevDrawable != null) {
                request.placeholder(prevDrawable)
            } else {
                request.placeholder(fallback)
            }
            request.error(fallback).into(target)
            return
        }

        synchronized(loading) {
            if (!loading.add(key)) return
        }
        scope.launch {
            val bitmap = kotlinx.coroutines.withContext(Dispatchers.IO) {
                loadLocalBitmap(context, item)
            }
            synchronized(loading) { loading.remove(key) }
            if (bitmap != null) cache.put(key, bitmap)
            if (target.tag == key) {
                if (bitmap != null) {
                    applyBitmap(bitmap)
                } else {
                    // Local extraction produced nothing — fall back to remote art when present.
                    val remote = item.artworkUri?.takeIf { it.isNotBlank() }
                    if (remote != null && !remote.startsWith("file:")) {
                        val request = Picasso.get().load(remote).fit().centerCrop()
                        if (prevDrawable != null) {
                            request.placeholder(prevDrawable)
                        } else {
                            request.placeholder(fallback)
                        }
                        request.error(fallback).into(target)
                    } else {
                        target.setImageResource(fallback)
                    }
                }
            }
        }
    }

    /**
     * Best-effort artwork bitmap for color extraction (lyrics tint, glow). Serves the LRU cache
     * first, then decodes the local pipeline, then fetches remote art. Never spawns ffmpeg
     * extraction twice for the same item — the disk cache handles dedup.
     */
    suspend fun bitmapFor(context: Context, item: PlaybackItem): Bitmap? {
        val key = "${item.mediaId}|${item.artworkUri.orEmpty()}|${item.localUri.orEmpty()}"
        cache.get(key)?.let { return it }
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val preferLocal = item.source == PlaybackSource.LOCAL || item.isLocal
            if (preferLocal) {
                loadLocalBitmap(context.applicationContext, item)
            } else {
                runCatching {
                    Picasso.get().load(item.artworkUri).get()
                }.getOrNull()
            }
        }
    }

    private fun loadLocalBitmap(context: Context, item: PlaybackItem): Bitmap? {
        val local = item.localUri?.takeIf { it.isNotBlank() } ?: return null
        val uri = runCatching { Uri.parse(local) }.getOrNull() ?: return null

        // Check persistent disk thumbnail cache first
        val safeKey = item.mediaId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val diskThumb = java.io.File(java.io.File(context.cacheDir, "art_thumbs"), "$safeKey.webp")
        if (diskThumb.exists() && diskThumb.length() > 0L) {
            runCatching { BitmapFactory.decodeFile(diskThumb.absolutePath) }.getOrNull()?.let { return it }
        }

        // Fast path: ffmpeg disk cache already holds artwork for this file (one stat call).
        val cachedPath = localPathOf(context, uri)
        if (cachedPath.isNotBlank()) {
            val cached = when (item.type) {
                PlaybackType.VIDEO -> FrameExtractor.cachedFor(
                    context, FrameExtractor.VIDEO_VARIANT, cachedPath, THUMBNAIL_SIZE
                )
                else -> FrameExtractor.cachedFor(
                    context, FrameExtractor.AUDIO_VARIANT, cachedPath, THUMBNAIL_SIZE
                )
            }
            if (cached != null) {
                Log.d(TAG, "artwork cached: ${item.title} -> $cachedPath")
                return cached
            }
        } else {
            Log.d(TAG, "no local path for ${item.title} (uri=$local)")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isFileScheme(uri)) {
            runCatching {
                context.contentResolver.loadThumbnail(
                    uri,
                    Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE),
                    null
                )
            }.getOrNull()?.let { return it }
        }

        val retriever = MediaMetadataRetriever()
        val bitmap: Bitmap? = try {
            when {
                isFileScheme(uri) -> retriever.setDataSource(uri.path ?: return null)
                uri.scheme == null && uri.path?.startsWith("/") == true ->
                    retriever.setDataSource(uri.path)
                else -> retriever.setDataSource(context, uri)
            }
            if (item.type == PlaybackType.AUDIO) {
                runCatching { retriever.embeddedPicture }.getOrNull()
                    ?.let { bytes -> decodeSampled(bytes) }
            } else {
                videoFrame(retriever)
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
        val resultBitmap = bitmap ?: run {
            val path = cachedPath.ifBlank { localPathOf(context, uri) }
            if (path.isBlank()) null
            else when (item.type) {
                PlaybackType.VIDEO -> FrameExtractor.frameFor(context, path, THUMBNAIL_SIZE)
                PlaybackType.AUDIO -> FrameExtractor.coverFor(context, path, THUMBNAIL_SIZE)
            }
        }
        if (resultBitmap != null) {
            runCatching {
                diskThumb.parentFile?.mkdirs()
                val tmp = java.io.File(diskThumb.parentFile, "${diskThumb.name}.tmp")
                tmp.outputStream().use { out ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        resultBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                    } else {
                        @Suppress("DEPRECATION")
                        resultBitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
                    }
                }
                tmp.renameTo(diskThumb)
            }
            Log.d(TAG, "artwork ready: ${item.title}")
        }
        return resultBitmap
    }

    /**
     * Blocking lookup of the ffmpeg-cached artwork file for a local item. Stat-only: never
     * spawns ffmpeg. Used to seed the media notification before playback starts; returns a
     * `file://` URI string or null.
     */
    fun cachedArtworkUri(context: Context, item: PlaybackItem): String? {
        if (!item.isLocal && item.source != PlaybackSource.LOCAL) return null
        val local = item.localUri?.takeIf { it.isNotBlank() } ?: return null
        val uri = runCatching { Uri.parse(local) }.getOrNull() ?: return null
        val path = localPathOf(context, uri)
        if (path.isBlank()) return null
        val variant = if (item.type == PlaybackType.VIDEO) {
            FrameExtractor.VIDEO_VARIANT
        } else {
            FrameExtractor.AUDIO_VARIANT
        }
        val file = FrameExtractor.cachedFileFor(context, variant, path) ?: return null
        return Uri.fromFile(file).toString()
    }

    /** Absolute file path for a media URI, either directly or through the MediaStore DATA column. */
    private fun localPathOf(context: Context, uri: Uri): String {
        if (isFileScheme(uri)) return uri.path ?: ""
        if (uri.scheme == null && uri.path?.startsWith("/") == true) return uri.path ?: ""
        return runCatching {
            context.contentResolver
                .query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: ""
    }

    private fun videoFrame(retriever: MediaMetadataRetriever): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching {
                retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMBNAIL_SIZE,
                    THUMBNAIL_SIZE
                )
            }.getOrNull()?.let { return it }
        }
        return runCatching { retriever.getFrameAtTime(0L) }.getOrNull()?.let(::scaleDown)
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        val largest = maxOf(source.width, source.height).coerceAtLeast(1)
        if (largest <= THUMBNAIL_SIZE) return source
        val ratio = THUMBNAIL_SIZE.toFloat() / largest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun decodeSampled(bytes: ByteArray, targetSize: Int = THUMBNAIL_SIZE): Bitmap? = runCatching {
        if (bytes.isEmpty()) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        if (bounds.outHeight > targetSize || bounds.outWidth > targetSize) {
            val halfHeight = bounds.outHeight / 2
            val halfWidth = bounds.outWidth / 2
            while ((halfHeight / sample) >= targetSize && (halfWidth / sample) >= targetSize) {
                sample *= 2
            }
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@runCatching null
        if (decoded.width > targetSize * 1.5f || decoded.height > targetSize * 1.5f) {
            val maxDim = maxOf(decoded.width, decoded.height)
            val scale = targetSize.toFloat() / maxDim
            val scaled = Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (scaled != decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
    }.getOrNull()

    private fun isFileScheme(uri: Uri): Boolean =
        "file".equals(uri.scheme, ignoreCase = true)

    private const val TAG = "PlaybackArt"
    private const val THUMBNAIL_SIZE = 256
}
