package com.deniscerri.ytdl.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.PlaybackQueueState
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackSource
import com.deniscerri.ytdl.ui.play.PlaybackArtworkLoader
import com.deniscerri.ytdl.playback.resolve.PlaybackStreamResolver
import com.deniscerri.ytdl.playback.resolve.ResolvedStream
import com.deniscerri.ytdl.playback.resolve.ResolveOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * App-wide playback façade over a [MediaController].
 *
 * The app queue is the source of truth. Only resolved items are ever handed to the player, so a
 * YouTube item is never placed in the playlist as a web page URL. The next item is resolved
 * proactively and appended, which keeps the notification/Bluetooth "next" action working without
 * pre-resolving an entire queue.
 */
object PlaybackController {
    private const val TAG = "PlaybackController"
    private const val TICK_MS = 500L
    private const val PERSIST_INTERVAL_MS = 5_000L

    data class UiState(
        val queue: List<PlaybackItem> = emptyList(),
        val currentIndex: Int = -1,
        val connected: Boolean = false,
        val playing: Boolean = false,
        val buffering: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val bufferedMs: Long = 0L,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val shuffle: Boolean = false,
        val chapters: List<com.deniscerri.ytdl.playback.model.PlaybackChapter> = emptyList(),
        val subtitles: List<com.deniscerri.ytdl.playback.model.PlaybackSubtitle> = emptyList(),
        val playbackSpeed: Float = 1f,
        val error: String? = null
    ) {
        val currentItem: PlaybackItem? get() = queue.getOrNull(currentIndex)
        val hasNext: Boolean get() = queue.isNotEmpty() && (shuffle || currentIndex < queue.lastIndex)
        val hasPrevious: Boolean get() = queue.isNotEmpty() && (shuffle || currentIndex > 0)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var resolver: PlaybackStreamResolver? = null
    private var initialized = false
    private var restoring = false

    private var restoreJob: Job? = null
    private var nextResolveJob: Job? = null
    private var currentResolveJob: Job? = null
    private var lastPersistAt = 0L
    private var pendingStart: PendingStart? = null
    private var retriedMediaId: String? = null
    private var lastResolveError: String? = null

    private data class PendingStart(
        val index: Int,
        val autoPlay: Boolean,
        val positionMs: Long
    )

    private val resolvedItems = mutableMapOf<String, CachedMediaItem>()
    private val playerMediaIds = mutableListOf<String>()

    private data class CachedMediaItem(val item: MediaItem, val expiresAtMs: Long?)

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val player = controller ?: return
            if (mediaItem == null) return
            val index = _state.value.queue.indexOfFirst { it.mediaId == mediaItem.mediaId }
            if (index >= 0 && index != _state.value.currentIndex) {
                retriedMediaId = null
                _state.update { it.copy(currentIndex = index, positionMs = 0L, error = null) }
                onTrackChanged(index, player.currentPosition)
            }
            preResolveNext()
            publishPlayerState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishPlayerState()
            if (!isPlaying) persistProgress()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishPlayerState()
            if (playbackState == Player.STATE_ENDED) {
                val state = _state.value
                val atEnd = state.currentIndex >= state.queue.lastIndex
                if (state.repeatMode == Player.REPEAT_MODE_ALL || !atEnd) {
                    nextOrWrap()
                } else {
                    persistProgress()
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.w(TAG, "Playback error for ${_state.value.currentItem?.mediaId}", error)
            // A failed dav1d renderer is recovered inside PlaybackService (player is rebuilt
            // without extensions and playback resumes). Don't surface it or retry from here —
            // that would race the service's rebuild.
            val isDav1dRendererError = (
                    error is androidx.media3.exoplayer.ExoPlaybackException &&
                            error.type == androidx.media3.exoplayer.ExoPlaybackException.TYPE_RENDERER &&
                            error.rendererName?.contains("dav1d", ignoreCase = true) == true
                    ) || error.message?.contains("dav1d", ignoreCase = true) == true
            if (isDav1dRendererError) return
            _state.update {
                it.copy(
                    error = error.message ?: "Playback error",
                    buffering = false
                )
            }
            retryCurrentOnce()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update { it.copy(repeatMode = repeatMode) }
            persistStateSoon()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.update { it.copy(shuffle = shuffleModeEnabled) }
            persistStateSoon()
        }
    }

    /**
     * Note: this media3 version cannot register a [MediaController.Listener] through
     * [MediaController.addListener] (it only accepts [Player.Listener]). Disconnect is instead
     * detected by the staleness check in [ensureConnected] and the ticker's liveness probe,
     * both of which consult [MediaController.isConnected].
     */

    private val ticker = object : Runnable {
        override fun run() {
            // Liveness check: if the service died (notification swipe-away), drop `connected`
            // so the UI and ensureConnected() know the stale controller is unusable.
            val current = controller
            if (_state.value.connected && current != null && !current.isConnected) {
                Log.w(TAG, "MediaController no longer connected to PlaybackService")
                handler.removeCallbacks(this)
                _state.update {
                    it.copy(connected = false, playing = false, buffering = false)
                }
                return
            }
            publishPlayerState()
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        resolver = PlaybackStreamResolver(context.applicationContext)

        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val mediaController = future.get()
                controller = mediaController
                mediaController.addListener(playerListener)
                _state.update {
                    it.copy(
                        connected = true,
                        repeatMode = mediaController.repeatMode,
                        shuffle = mediaController.shuffleModeEnabled
                    )
                }
                publishPlayerState()
                handler.post(ticker)
                tryRestoreQueue()
                val pending = pendingStart
                if (pending != null) {
                    pendingStart = null
                    startAt(pending.index, pending.autoPlay, pending.positionMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not connect to PlaybackService", e)
                _state.update { it.copy(connected = false, error = e.message) }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        handler.removeCallbacks(ticker)
        restoreJob?.cancel()
        nextResolveJob?.cancel()
        currentResolveJob?.cancel()
        pendingStart = null
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        initialized = false
        _state.value = UiState()
    }

    /** Exposes the player to a PlayerView. Only call on the main thread. */
    fun player(): Player? = controller

    /**
     * Makes sure a live MediaController exists before any playback command. If the session
     * died (notification swipe-away, background kill) the stale controller silently swallows
     * every command — detect that and rebuild the connection. The persisted queue is restored
     * by [tryRestoreQueue] when the fresh connection completes.
     */
    private fun ensureConnected() {
        val current = controller
        if (initialized && (current == null || !current.isConnected)) {
            Log.w(TAG, "Dead MediaController detected — reconnecting")
            runCatching { current?.removeListener(playerListener) }
            controllerFuture?.let { future -> runCatching { MediaController.releaseFuture(future) } }
            controllerFuture = null
            controller = null
            initialized = false
            _state.update { it.copy(connected = false) }
        }
        if (!initialized && appContext != null) {
            init(appContext!!)
        }
    }

    /** Replaces the queue and starts at [startIndex]. Network items resolve asynchronously. */
    fun playNow(items: List<PlaybackItem>, startIndex: Int = 0, autoPlay: Boolean = true) {
        val safeItems = items.filterNot { it.isVault || it.localUri?.startsWith("vault://", ignoreCase = true) == true }
        if (safeItems.isEmpty()) return
        ensureConnected()
        nextResolveJob?.cancel()
        retriedMediaId = null
        lastResolveError = null
        val index = startIndex.coerceIn(safeItems.indices)
        _state.update {
            it.copy(
                queue = safeItems,
                currentIndex = index,
                positionMs = 0L,
                durationMs = safeItems[index].durationMs ?: 0L,
                error = null
            )
        }
        resolvedItems.clear()
        persistQueueItems()
        startAt(index, autoPlay, resumePositionMs = 0L)
    }

    /** Starts a specific index of the current queue. */
    fun playAt(index: Int, autoPlay: Boolean = true) {
        ensureConnected()
        if (index !in _state.value.queue.indices) return
        retriedMediaId = null
        lastResolveError = null
        startAt(index, autoPlay, resumePositionMs = 0L)
    }

    /** Continues the restored/current queue, or starts from the beginning. */
    fun resume(autoPlay: Boolean = true) {
        ensureConnected()
        val state = _state.value
        if (state.queue.isEmpty()) {
            // Nothing in memory (e.g. playback was stopped earlier): replay the persisted queue
            // and actually start it — the previous behavior only repopulated the state and left
            // the play tap a silent no-op.
            tryRestoreQueue(autoPlay = autoPlay)
            return
        }
        retriedMediaId = null
        val index = state.currentIndex.takeIf { it in state.queue.indices } ?: 0
        startAt(index, autoPlay, resumePositionMs = state.positionMs)
    }

    fun playNext(item: PlaybackItem) {
        if (item.isVault || item.localUri?.startsWith("vault://", ignoreCase = true) == true) return
        val state = _state.value
        val mutable = state.queue.toMutableList()
        val insertAt = if (state.currentIndex < 0) mutable.size
        else (state.currentIndex + 1).coerceAtMost(mutable.size)
        mutable.add(insertAt, item)
        _state.update { it.copy(queue = mutable) }
        persistQueueItems()
        if (insertAt == state.currentIndex + 1) {
            trimLookahead()
            preResolveNext()
        }
    }

    fun addToQueue(item: PlaybackItem) {
        if (item.isVault || item.localUri?.startsWith("vault://", ignoreCase = true) == true) return
        val mutable = _state.value.queue.toMutableList()
        if (mutable.isEmpty()) {
            playNow(listOf(item))
        } else {
            mutable.add(item)
            _state.update { it.copy(queue = mutable) }
            persistQueueItems()
        }
    }

    fun removeFromQueue(index: Int) {
        val state = _state.value
        if (index !in state.queue.indices) return
        if (index == state.currentIndex) {
            // Removing the playing item: move to the following item, or stop when none remain.
            val mutable = state.queue.toMutableList()
            mutable.removeAt(index)
            if (mutable.isEmpty()) {
                clearQueue()
                return
            }
            _state.update { it.copy(currentIndex = -1) }
            playNow(mutable, index.coerceAtMost(mutable.lastIndex))
            return
        }
        val mutable = state.queue.toMutableList()
        mutable.removeAt(index)
        val newCurrent = if (index < state.currentIndex) state.currentIndex - 1 else state.currentIndex
        _state.update { it.copy(queue = mutable, currentIndex = newCurrent) }
        persistQueueItems()
        trimLookahead()
        preResolveNext()
    }

    fun removeByMediaIdsOrPaths(mediaIds: Set<String>, pathsOrUris: Set<String>) {
        if (mediaIds.isEmpty() && pathsOrUris.isEmpty()) return
        val currentQueue = _state.value.queue
        if (currentQueue.isNotEmpty()) {
            for (i in currentQueue.indices.reversed()) {
                val q = currentQueue[i]
                if (q.mediaId in mediaIds || (!q.localUri.isNullOrBlank() && q.localUri in pathsOrUris)) {
                    removeFromQueue(i)
                }
            }
            return
        }
        val ctx = appContext ?: return
        scope.launch(Dispatchers.IO) {
            val db = DBManager.getInstance(ctx)
            val savedItems = db.playbackQueueDao.getItems()
            val remaining = savedItems.filterNot { item ->
                item.mediaId in mediaIds || (!item.localUri.isNullOrBlank() && item.localUri in pathsOrUris)
            }
            if (remaining.size != savedItems.size) {
                if (remaining.isEmpty()) {
                    db.playbackQueueDao.clear()
                } else {
                    val oldState = db.playbackQueueDao.getState()
                    val reindexed = remaining.mapIndexed { idx, item -> item.copy(position = idx) }
                    val safeIndex = (oldState?.currentIndex ?: 0).coerceIn(reindexed.indices)
                    db.playbackQueueDao.replaceQueue(
                        com.deniscerri.ytdl.database.models.PlaybackQueueState(
                            currentIndex = safeIndex,
                            currentPositionMs = oldState?.currentPositionMs ?: 0L,
                            repeatMode = oldState?.repeatMode ?: Player.REPEAT_MODE_OFF,
                            shuffleMode = oldState?.shuffleMode ?: 0
                        ),
                        reindexed
                    )
                }
            }
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        val state = _state.value
        if (from !in state.queue.indices || to !in state.queue.indices || from == to) return
        val mutable = state.queue.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        var current = state.currentIndex
        current = when {
            current == from -> to
            from < current && to >= current -> current - 1
            from > current && to <= current -> current + 1
            else -> current
        }
        _state.update { it.copy(queue = mutable, currentIndex = current) }
        persistQueueItems()
        trimLookahead()
        preResolveNext()
    }

    fun togglePlayPause() {
        ensureConnected()
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            resume()
            return
        }
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        _state.update { it.copy(positionMs = positionMs) }
        persistProgress()
    }

    fun skipBy(deltaMs: Long) {
        val player = controller ?: return
        seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    fun next() {
        ensureConnected()
        val state = _state.value
        if (state.queue.isEmpty()) return
        val nextIndex = nextIndexFor(state)
        if (nextIndex == null) {
            persistProgress()
            return
        }
        if (state.shuffle) {
            startAt(nextIndex, autoPlay = true)
        } else if (controller?.hasNextMediaItem() == true) {
            controller?.seekToNextMediaItem()
        } else {
            startAt(nextIndex, autoPlay = true, resumePositionMs = 0L)
        }
    }

    fun previous() {
        ensureConnected()
        val player = controller ?: return
        val state = _state.value
        if (state.queue.isEmpty()) return
        if (player.currentPosition > 3_000L) {
            seekTo(0L)
            return
        }
        val previous = when {
            state.shuffle -> randomIndex(state)
            state.currentIndex > 0 -> state.currentIndex - 1
            state.repeatMode == Player.REPEAT_MODE_ALL -> state.queue.lastIndex
            else -> null
        } ?: return
        startAt(previous, autoPlay = true, resumePositionMs = 0L)
    }

    /**
     * Artwork-swipe navigation: audio only. Skips video entries in the queue, wraps around, and
     * restarts from the beginning when the queue holds a single audio item.
     */
    fun skipToNextAudio() {
        val target = stepAudio(1) ?: return
        // target == currentIndex happens when the queue holds a single audio item: restart it.
        startAt(target, autoPlay = true, resumePositionMs = 0L)
    }

    fun skipToPreviousAudio() {
        val target = stepAudio(-1) ?: return
        startAt(target, autoPlay = true, resumePositionMs = 0L)
    }

    private fun stepAudio(direction: Int): Int? {
        val state = _state.value
        val audioIndices = state.queue
            .withIndex()
            .filter { it.value.type == com.deniscerri.ytdl.playback.model.PlaybackType.AUDIO }
            .map { it.index }
        if (audioIndices.isEmpty()) return null
        if (audioIndices.size == 1) return audioIndices[0] // single audio item: restart
        val currentPos = audioIndices.indexOf(state.currentIndex)
        return if (currentPos >= 0) {
            val nextPos = (currentPos + direction).mod(audioIndices.size)
            audioIndices[nextPos]
        } else {
            // The current item is a video (or unset): walk to the nearest audio in [direction].
            when (direction) {
                1 -> audioIndices.firstOrNull { it > state.currentIndex }
                    ?: audioIndices.first()
                else -> audioIndices.lastOrNull { it < state.currentIndex }
                    ?: audioIndices.last()
            }
        }
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = false // shuffling is app-owned; keep player order stable
        _state.update { it.copy(shuffle = enabled) }
        persistStateSoon()
        preResolveNext()
    }

    fun toggleShuffle() = setShuffle(!_state.value.shuffle)

    fun setRepeatMode(mode: Int) {
        val normalized = mode.coerceIn(
            Player.REPEAT_MODE_OFF,
            Player.REPEAT_MODE_ALL
        )
        controller?.repeatMode = normalized
        _state.update { it.copy(repeatMode = normalized) }
        persistStateSoon()
        preResolveNext()
    }

    fun cycleRepeatMode() {
        setRepeatMode(
            when (_state.value.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        )
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed.coerceIn(0.25f, 4f))
    }

    /**
     * User-initiated retry from an error surface. Drops the cached stream URL so an expired
     * YouTube signature is resolved again, then resumes at the last known position.
     */
    fun retryCurrent() {
        val state = _state.value
        val item = state.currentItem ?: return
        resolvedItems.remove(item.mediaId)
        retriedMediaId = null
        lastResolveError = null
        startAt(state.currentIndex, autoPlay = true, resumePositionMs = state.positionMs)
    }

    /**
     * Stops playback. [dismissed] must only be true when the user closed playback from the
     * notification's dismiss action — every other stop (miniplayer close, video player swap)
     * must keep the persisted queue eligible for auto-restore on the next app open.
     */
    fun stop(dismissed: Boolean = false) {
        currentResolveJob?.cancel()
        nextResolveJob?.cancel()
        pendingStart = null
        retriedMediaId = null
        controller?.stop()
        controller?.clearMediaItems()
        playerMediaIds.clear()
        resolvedItems.clear()
        // Remember WHERE we stopped before wiping the in-memory state: the wipe resets
        // currentIndex to 0, and persisting after it made resume() replay the first song
        // instead of the one the user was listening to.
        persistStateSnapshot(positionMs = 0L)
        appContext?.let {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(it)
                .edit().putBoolean("playback_dismissed", dismissed).apply()
        }
        // Keep the persisted queue intact: the next play/resume tap replays it through
        // tryRestoreQueue(). Wiping it here made the play button dead after the notification
        // close button (PlaybackService.onCustomCommand -> stop()).
        _state.update {
            UiState(
                connected = controller != null,
                repeatMode = it.repeatMode,
                shuffle = it.shuffle
            )
        }
    }

    /** Stops playback and also forgets the persisted queue so resume() can't replay it. */
    fun clearQueue() {
        stop()
        persistQueueItems()
    }

    private fun startAt(index: Int, autoPlay: Boolean, resumePositionMs: Long = 0L) {
        appContext?.let {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(it)
                .edit().putBoolean("playback_dismissed", false).apply()
        }
        val state = _state.value
        val item = state.queue.getOrNull(index) ?: return
        // The one-retry guard only applies to the item that failed; a new target starts fresh.
        if (retriedMediaId != null && retriedMediaId != item.mediaId) {
            retriedMediaId = null
        }
        currentResolveJob?.cancel()
        currentResolveJob = scope.launch {
            _state.update {
                it.copy(
                    currentIndex = index,
                    buffering = true,
                    error = null,
                    positionMs = resumePositionMs,
                    durationMs = item.durationMs ?: 0L
                )
            }
            val mediaItem = resolveItem(item)
            if (mediaItem == null) {
                _state.update {
                    it.copy(
                        buffering = false,
                        error = lastResolveError ?: "Could not resolve this item"
                    )
                }
                return@launch
            }
            val player = controller
            if (player == null) {
                // The MediaController connection is still in flight. Replay this request when
                // PlaybackController.init() finishes connecting.
                pendingStart = PendingStart(index, autoPlay, resumePositionMs)
                _state.update { it.copy(buffering = false) }
                return@launch
            }
            resolvedItems[item.mediaId] = mediaItem
            player.setMediaItem(mediaItem.item)
            playerMediaIds.clear()
            playerMediaIds.add(item.mediaId)
            player.prepare()
            if (resumePositionMs > 0L) player.seekTo(resumePositionMs)
            player.playWhenReady = autoPlay
            _state.update { it.copy(buffering = false) }
            onTrackChanged(index, resumePositionMs)
            publishPlayerState()
            preResolveNext()
        }
    }

    /**
     * Local items frequently have no usable `artworkUri` (AV1 webm videos, opus files without
     * MediaStore album art). Swap in the ffmpeg-cached frame when one exists so the media
     * notification and lock screen show real artwork; when the cache is cold, request a
     * background extraction so the next attempt is instant.
     */
    private suspend fun withLocalArtwork(item: PlaybackItem): PlaybackItem {
        val context = appContext ?: return item
        if (item.source != PlaybackSource.LOCAL) return item
        return withContext(Dispatchers.IO) {
            val boosted = PlaybackArtworkLoader.cachedArtworkUri(context, item)
            if (boosted != null) {
                item.copy(artworkUri = boosted)
            } else {
                PlaybackArtworkLoader.warmup(context, item)
                item
            }
        }
    }

    private suspend fun resolveItem(item: PlaybackItem): CachedMediaItem? {        resolvedItems[item.mediaId]?.let { cached ->
            if (cached.expiresAtMs == null || cached.expiresAtMs > System.currentTimeMillis()) {
                return cached
            }
        }
        val appResolver = resolver ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val resolved: ResolvedStream = appResolver.resolve(
                    item,
                    ResolveOptions(
                        audioOnly = item.type == com.deniscerri.ytdl.playback.model.PlaybackType.AUDIO
                    )
                )
                if (resolved.isEmpty) {
                    lastResolveError = "No playable stream found"
                    return@withContext null
                }
                val single = resolved.singleUri
                val audio = resolved.audioUri ?: single
                val video = resolved.videoUri
                // Local files often have no usable MediaMetadata.artworkUri (AV1 webm, opus).
                // Seed it from the ffmpeg artwork cache so the notification/lock screen get
                // real art, and kick off extraction in the background when not cached yet.
                val forMetadata = withLocalArtwork(item)
                val chapters = resolved.playbackChapters.ifEmpty {
                    com.deniscerri.ytdl.playback.model.PlaybackExtrasCodec.chaptersFromChapterItems(resolved.chapters)
                }
                val mediaItem = forMetadata.toMediaItem(
                    audioUri = audio,
                    videoUri = video,
                    mimeType = if (video.isNullOrBlank()) resolved.mimeType else null,
                    subtitles = resolved.subtitles,
                    chapters = chapters
                )
                lastResolveError = null
                CachedMediaItem(mediaItem, resolved.expiresAtMs)
            } catch (e: Exception) {
                Log.e(TAG, "Resolve failed for ${item.mediaId}", e)
                lastResolveError = e.message?.takeIf { it.isNotBlank() }
                    ?: "Could not resolve this item"
                null
            }
        }
    }

    /**
     * Drops the pre-resolved item(s) after the current one. Called when the app queue changes so
     * the player cannot auto-advance into an item that is no longer next.
     */
    private fun trimLookahead() {
        nextResolveJob?.cancel()
        val player = controller ?: return
        val current = player.currentMediaItem?.mediaId ?: return
        val currentIndex = player.currentMediaItemIndex
        while (player.mediaItemCount > currentIndex + 1) {
            player.removeMediaItem(player.mediaItemCount - 1)
        }
        playerMediaIds.clear()
        playerMediaIds.add(current)
    }

    private fun preResolveNext() {
        val state = _state.value
        if (state.queue.isEmpty()) return
        if (state.repeatMode == Player.REPEAT_MODE_ONE) return
        val nextIndex = nextIndexFor(state) ?: return
        val nextItem = state.queue.getOrNull(nextIndex) ?: return
        if (resolvedItems.containsKey(nextItem.mediaId)) {
            val player = controller ?: return
            if (!playerHasMediaId(nextItem.mediaId)) {
                player.addMediaItem(resolvedItems.getValue(nextItem.mediaId).item)
                playerMediaIds.add(nextItem.mediaId)
            }
            return
        }
        nextResolveJob?.cancel()
        nextResolveJob = scope.launch {
            val mediaItem = resolveItem(nextItem) ?: return@launch
            resolvedItems[nextItem.mediaId] = mediaItem
            val player = controller ?: return@launch
            if (!playerHasMediaId(nextItem.mediaId)) {
                player.addMediaItem(mediaItem.item)
                playerMediaIds.add(nextItem.mediaId)
            }
        }
    }

    private fun playerHasMediaId(mediaId: String): Boolean {
        val player = controller ?: return false
        for (i in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(i).mediaId == mediaId) return true
        }
        return false
    }

    private fun retryCurrentOnce() {
        val state = _state.value
        val item = state.currentItem ?: return
        if (retriedMediaId == item.mediaId) {
            // Second failure for the same item: surface the error instead of looping.
            return
        }
        retriedMediaId = item.mediaId
        // Drop the cached URL; a 403 is almost always an expired stream URL.
        resolvedItems.remove(item.mediaId)
        scope.launch {
            delay(500L)
            val position = state.positionMs
            startAt(state.currentIndex, autoPlay = true, resumePositionMs = position)
        }
    }

    private fun nextIndexFor(state: UiState): Int? {
        if (state.queue.isEmpty()) return null
        if (state.shuffle) return randomIndex(state)
        val next = state.currentIndex + 1
        if (next <= state.queue.lastIndex) return next
        return if (state.repeatMode == Player.REPEAT_MODE_ALL) 0 else null
    }

    private fun randomIndex(state: UiState): Int? {
        if (state.queue.size <= 1) return null
        var index = state.currentIndex
        while (index == state.currentIndex) {
            index = Random.nextInt(state.queue.size)
        }
        return index
    }

    private fun nextOrWrap() {
        val state = _state.value
        val next = nextIndexFor(state) ?: return
        startAt(next, autoPlay = state.playing || state.repeatMode != Player.REPEAT_MODE_OFF)
    }

    private fun onTrackChanged(index: Int, startPositionMs: Long) {
        val item = _state.value.queue.getOrNull(index) ?: return
        if (item.isVault || item.localUri?.startsWith("vault://", ignoreCase = true) == true) return
        persistStateSnapshot(startPositionMs)
        val appContext = appContext ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val dao = DBManager.getInstance(appContext).playbackHistoryDao
                dao.record(item.toHistory())
                dao.updateProgress(
                    item.mediaId,
                    startPositionMs,
                    _state.value.durationMs,
                    System.currentTimeMillis()
                )
            }
        }
    }

    private fun publishPlayerState() {
        val player = controller ?: return
        val queue = _state.value.queue
        val mediaId = player.currentMediaItem?.mediaId
        val index = queue.indexOfFirst { it.mediaId == mediaId }
        _state.update {
            it.copy(
                connected = true,
                currentIndex = if (index >= 0) index else it.currentIndex,
                playing = player.isPlaying,
                buffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { d -> d > 0 && d != androidx.media3.common.C.TIME_UNSET }
                    ?: it.durationMs,
                bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                repeatMode = player.repeatMode,
                chapters = com.deniscerri.ytdl.playback.model.PlaybackExtrasCodec.chaptersFromJson(
                    player.currentMediaItem?.mediaMetadata?.extras?.getString(PlaybackItem.KEY_CHAPTERS_JSON)
                ),
                subtitles = com.deniscerri.ytdl.playback.model.PlaybackExtrasCodec.subtitlesFromJson(
                    player.currentMediaItem?.mediaMetadata?.extras?.getString(PlaybackItem.KEY_SUBTITLES_JSON)
                ),
                playbackSpeed = player.playbackParameters.speed,
                shuffle = it.shuffle
            )
        }
        persistProgressThrottled()
    }

    private fun persistProgressThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastPersistAt < PERSIST_INTERVAL_MS) return
        lastPersistAt = now
        persistProgress()
    }

    private fun persistProgress() {
        val state = _state.value
        val item = state.currentItem ?: return
        if (item.isVault || item.localUri?.startsWith("vault://", ignoreCase = true) == true) return
        val appContext = appContext ?: return
        persistStateSnapshot(state.positionMs)
        scope.launch(Dispatchers.IO) {
            runCatching {
                DBManager.getInstance(appContext).playbackHistoryDao.updateProgress(
                    item.mediaId,
                    state.positionMs,
                    state.durationMs,
                    System.currentTimeMillis()
                )
            }
        }
    }

    private fun persistQueueItems() {
        val state = _state.value
        val appContext = appContext ?: return
        val safeQueue = state.queue.filterNot { it.isVault || it.localUri?.startsWith("vault://", ignoreCase = true) == true }
        val items = safeQueue.mapIndexed { index, item -> item.toQueueItem(index) }
        scope.launch(Dispatchers.IO) {
            runCatching {
                DBManager.getInstance(appContext).playbackQueueDao.replaceQueue(
                    PlaybackQueueState(
                        currentIndex = if (safeQueue.isEmpty()) -1 else state.currentIndex.coerceIn(safeQueue.indices),
                        currentPositionMs = state.positionMs,
                        shuffleMode = if (state.shuffle) 1 else 0,
                        repeatMode = state.repeatMode
                    ),
                    items
                )
            }
        }
    }

    private fun persistStateSoon() {
        persistStateSnapshot(_state.value.positionMs)
    }

    private fun persistStateSnapshot(positionMs: Long) {
        val state = _state.value
        if (state.currentItem?.isVault == true) return
        val appContext = appContext ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                DBManager.getInstance(appContext).playbackQueueDao.saveState(
                    PlaybackQueueState(
                        currentIndex = state.currentIndex,
                        currentPositionMs = positionMs,
                        shuffleMode = if (state.shuffle) 1 else 0,
                        repeatMode = state.repeatMode
                    )
                )
            }
        }
    }

    private fun tryRestoreQueue(autoPlay: Boolean = false) {
        if (restoring || _state.value.queue.isNotEmpty()) return
        val appContext = appContext ?: return

        val isDismissed = androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
            .getBoolean("playback_dismissed", false)
        val player = controller
        val isCurrentlyPlaying = player?.isPlaying == true || (player?.mediaItemCount ?: 0) > 0
        if (!autoPlay && isDismissed && !isCurrentlyPlaying) {
            return
        }

        restoring = true
        restoreJob?.cancel()
        restoreJob = scope.launch {
            val restored = withContext(Dispatchers.IO) {
                val db = DBManager.getInstance(appContext)
                val queueState = db.playbackQueueDao.getState()
                val items = db.playbackQueueDao.getItems()
                if (items.isEmpty()) null else queueState to items
            }
            restoring = false
            if (restored == null) return@launch
            val (queueState, items) = restored
            val queue = items.map { PlaybackItem.fromQueueItem(it) }
            val index = (queueState?.currentIndex ?: 0).coerceIn(queue.indices)
            val savedPosition = queueState?.currentPositionMs ?: 0L
            _state.update {
                it.copy(
                    queue = queue,
                    currentIndex = index,
                    positionMs = savedPosition,
                    durationMs = queue.getOrNull(index)?.durationMs ?: 0L,
                    shuffle = (queueState?.shuffleMode ?: 0) == 1,
                    repeatMode = queueState?.repeatMode ?: Player.REPEAT_MODE_OFF
                )
            }
            // If the service is already playing (activity recreation), reconcile with it.
            val player = controller
            if (player != null && player.mediaItemCount > 0) {
                val playingId = player.currentMediaItem?.mediaId
                val playingIndex = queue.indexOfFirst { it.mediaId == playingId }
                if (playingIndex >= 0) {
                    _state.update { it.copy(currentIndex = playingIndex) }
                    publishPlayerState()
                    return@launch
                }
            } else if (autoPlay) {
                // Explicit resume tap with an empty player (e.g. after a notification dismiss):
                // replay the restored queue from its saved position instead of staying silent.
                startAt(index, autoPlay = true, resumePositionMs = savedPosition)
            }
        }
    }
}
