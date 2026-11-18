package com.deniscerri.ytdl.ui.play

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Instant frame provider for Mini Scrubber mode.
 *
 * - Hardware-scaled decoding at 256x144 in RGB_565 (no full-res bitmap allocations).
 * - Single [Channel.CONFLATED] worker on [Dispatchers.IO] so fast dragging skips stale timestamps.
 * - Background pre-warming across [PREWARM_SLOTS] (~80) intervals on `Player.STATE_READY` so drags
 *   hit memory directly (~5 MB RAM total).
 */
class ScrubFrameLoader(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val retrieverLock = Mutex()
    private val requestChannel = Channel<PendingRequest>(Channel.CONFLATED)

    private var retriever: MediaMetadataRetriever? = null
    private var retrieverSource: String? = null
    private var released = false

    private var prewarmJob: Job? = null
    private var prewarmedKey: String? = null

    @Volatile
    private var activeUri: String? = null

    @Volatile
    private var prewarmIntervalMs: Long = 0L

    @Volatile
    private var isUserScrubbing: Boolean = false

    private val cache = object : LruCache<String, Bitmap>(CACHE_SIZE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private data class PendingRequest(
        val localUri: String,
        val positionMs: Long,
        val onFrame: (Bitmap?) -> Unit
    )

    init {
        scope.launch(Dispatchers.IO) {
            for (request in requestChannel) {
                if (released) break
                val bitmap = retrieverLock.withLock {
                    extract(request.localUri, request.positionMs)
                }
                if (bitmap != null) {
                    val bucket = request.positionMs.coerceAtLeast(0L) / BUCKET_MS
                    cache.put(bucketKey(request.localUri, bucket), bitmap)
                    withContext(Dispatchers.Main.immediate) {
                        if (!released && request.localUri == activeUri) {
                            request.onFrame(bitmap)
                        }
                    }
                }
            }
        }
    }

    fun setUserScrubbing(scrubbing: Boolean) {
        isUserScrubbing = scrubbing
    }

    /**
     * Pre-warms ~80 evenly spaced RGB_565 thumbnails (256x144) in the background when playback is ready
     * and Mini Scrubber is active. Uses a separate retriever and yields while the user is dragging
     * so live scrub frames are never blocked.
     */
    fun prewarm(localUri: String?, durationMs: Long = 0L) {
        if (released || localUri.isNullOrBlank() || durationMs <= 0L) return
        activeUri = localUri
        val key = "$localUri|$durationMs"
        if (prewarmedKey == key) return
        prewarmedKey = key

        val intervalMs = (durationMs / PREWARM_SLOTS).coerceAtLeast(BUCKET_MS)
        prewarmIntervalMs = intervalMs

        prewarmJob?.cancel()
        prewarmJob = scope.launch(Dispatchers.IO) {
            val prewarmRetriever = createRetriever(localUri) ?: return@launch
            try {
                for (slot in 0 until PREWARM_SLOTS) {
                    if (released || activeUri != localUri) break
                    while (isUserScrubbing && !released) {
                        delay(80L)
                    }
                    val posMs = (slot * intervalMs).coerceAtMost(durationMs)
                    val sKey = slotKey(localUri, slot)
                    if (cache.get(sKey) != null) continue
                    val bitmap = extractWithRetriever(prewarmRetriever, posMs) ?: continue
                    cache.put(sKey, bitmap)
                    // Also seed the 1-second bucket so exact hits on this interval are instant.
                    cache.put(bucketKey(localUri, posMs / BUCKET_MS), bitmap)
                }
            } finally {
                runCatching { prewarmRetriever.release() }
            }
        }
    }

    /**
     * Requests the frame at [positionMs]. Checks the 1-second LruCache and nearest pre-warmed slot
     * synchronously for instant hits, and queues exact extraction via [Channel.CONFLATED].
     */
    fun request(
        localUri: String?,
        positionMs: Long,
        durationMs: Long,
        onFrame: (Bitmap?) -> Unit
    ) {
        if (released || localUri.isNullOrBlank()) return
        activeUri = localUri

        val safePos = positionMs.coerceAtLeast(0L)
        val bucket = safePos / BUCKET_MS
        cache.get(bucketKey(localUri, bucket))?.let { hit ->
            onFrame(hit)
            return
        }

        // Instant hit from the nearest pre-warmed interval while exact extraction runs.
        val interval = prewarmIntervalMs.takeIf { it > 0L }
            ?: if (durationMs > 0L) (durationMs / PREWARM_SLOTS).coerceAtLeast(BUCKET_MS) else 0L
        if (interval > 0L) {
            val slot = (safePos / interval).toInt().coerceIn(0, PREWARM_SLOTS - 1)
            val prewarmed = cache.get(slotKey(localUri, slot))
                ?: cache.get(slotKey(localUri, (slot + 1).coerceAtMost(PREWARM_SLOTS - 1)))
            if (prewarmed != null) {
                onFrame(prewarmed)
            }
        }

        requestChannel.trySend(PendingRequest(localUri, safePos, onFrame))
    }

    fun release() {
        if (released) return
        released = true
        prewarmJob?.cancel()
        requestChannel.close()
        scope.cancel()
        releaseRetriever()
        cache.evictAll()
    }

    private fun extract(localUri: String, positionMs: Long): Bitmap? {
        val source = retrieverFor(localUri) ?: return null
        return extractWithRetriever(source, positionMs)
    }

    private fun extractWithRetriever(source: MediaMetadataRetriever, positionMs: Long): Bitmap? {
        val timeUs = positionMs.coerceAtLeast(0L) * 1000L
        val raw = runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val params = MediaMetadataRetriever.BitmapParams().apply {
                        preferredConfig = Bitmap.Config.RGB_565
                    }
                    source.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        FRAME_WIDTH,
                        FRAME_HEIGHT,
                        params
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    source.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        FRAME_WIDTH,
                        FRAME_HEIGHT
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> {
                    source.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        FRAME_WIDTH,
                        FRAME_HEIGHT
                    )
                }
                else -> {
                    source.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                        val scaled = Bitmap.createScaledBitmap(full, FRAME_WIDTH, FRAME_HEIGHT, true)
                        if (scaled !== full) full.recycle()
                        scaled
                    }
                }
            }
        }.getOrNull() ?: return null

        if (raw.config == Bitmap.Config.RGB_565) return raw
        val rgb565 = runCatching { raw.copy(Bitmap.Config.RGB_565, false) }.getOrNull()
        if (rgb565 != null) {
            raw.recycle()
            return rgb565
        }
        return raw
    }

    private fun retrieverFor(localUri: String): MediaMetadataRetriever? {
        retriever?.let { existing ->
            if (retrieverSource == localUri) return existing
            releaseRetriever()
        }
        val created = createRetriever(localUri) ?: return null
        retriever = created
        retrieverSource = localUri
        return created
    }

    private fun createRetriever(localUri: String): MediaMetadataRetriever? {
        val created = MediaMetadataRetriever()
        val configured = runCatching {
            val uri = Uri.parse(localUri)
            when {
                uri.scheme.isNullOrBlank() && uri.path?.startsWith("/") == true ->
                    created.setDataSource(uri.path)
                uri.scheme.equals("file", ignoreCase = true) -> created.setDataSource(uri.path)
                else -> created.setDataSource(appContext, uri)
            }
        }.isSuccess
        if (!configured) {
            Log.w(TAG, "MediaMetadataRetriever cannot open $localUri")
            runCatching { created.release() }
            return null
        }
        return created
    }

    private fun releaseRetriever() {
        runCatching { retriever?.release() }
        retriever = null
        retrieverSource = null
    }

    private fun bucketKey(localUri: String, bucket: Long): String = "$localUri|b|$bucket"
    private fun slotKey(localUri: String, slot: Int): String = "$localUri|s|$slot"

    private companion object {
        const val TAG = "ScrubFrameLoader"
        const val BUCKET_MS = 1_000L
        const val PREWARM_SLOTS = 80
        const val FRAME_WIDTH = 256
        const val FRAME_HEIGHT = 144
        // 80 RGB_565 256x144 frames use ~5.8 MB; 8 MB holds the pre-warmed grid + active drag frames.
        const val CACHE_SIZE_KB = 8 * 1024
    }
}
