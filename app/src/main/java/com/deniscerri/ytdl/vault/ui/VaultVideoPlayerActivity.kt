package com.deniscerri.ytdl.vault.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.ui.compose.vault.VaultPlayerScreen
import com.deniscerri.ytdl.ui.compose.vault.VaultPlayerUiState
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.deniscerri.ytdl.util.VideoPlayerUtil
import com.deniscerri.ytdl.vault.VaultRepository
import com.deniscerri.ytdl.vault.VaultSessionManager
import com.deniscerri.ytdl.vault.VaultStorage
import com.deniscerri.ytdl.vault.playback.VaultDataSourceFactory
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Strictly foreground-only ExoPlayer for Private Vault items matching the main Play player UX:
 * - Same `PlayerView` controller (`exo_vault_player_control_view`), `DefaultTimeBar`, gestures
 *   (pinch-to-zoom, swipe brightness/volume/seek, double-tap, hold-to-2x `0.25x..4.0x`).
 * - Zero `PlaybackService`, zero `MediaSession`, zero notifications, zero background play, zero PiP.
 * - `FLAG_SECURE` + `setRecentsScreenshotEnabled(false)` + blackout veil on unexpected pause/Home/Power.
 * - Smooth animated close when pressing Back; instant 0ms lock + blackout on Home/Recents/Power.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VaultVideoPlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var uiState by mutableStateOf(VaultPlayerUiState())


    private var screenControlsLocked = false
    private var isMuted = false
    private var speedIndex = 1 // 1.0x in speeds
    private var originalPlaybackSpeed = 1.0f
    private var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var renderedFirstFrame = false

    private var keepOpenOnBack = false
    private var panicLocked = false
    private var exitingToGallery = false
    private var smoothExitInFlight = false

    // Gesture & speed boost state
    private var gestureMode = GestureMode.NONE
    private var downX = 0f
    private var downY = 0f
    private var gestureStartPosition = 0L
    private var gestureStartBrightness = 0.5f
    private var gestureStartBrightnessPercent = 50f
    private var gestureStartVolume = 0
    private var gestureStartVolumePercent = 50f
    private var pendingSeekMs = 0L
    private var lastTapUpTime = 0L
    private var lastTapX = 0f
    private var doubleTapPending = false
    private var scaleAccumulator = 1.0f
    private var isScaling = false

    private var isSpeedBoostActive = false
    private var speedBoostStartX = 0f
    private var currentBoostIndex = 4 // 2.0x in boostSpeeds

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
    private val boostSpeeds = floatArrayOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)

    private val speedBoostRunnable = Runnable { startSpeedBoost() }
    private val collapseSpeedBarRunnable = Runnable {
        uiState = uiState.copy(showBoostTicks = false)
    }
    private val hideLevelIndicatorRunnable = Runnable {
        uiState = uiState.copy(levelVisible = false)
    }

    private val scaleGestureDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                scaleAccumulator = 1.0f
                gestureMode = GestureMode.NONE
                doubleTapPending = false
                lastTapUpTime = 0L
                handler.removeCallbacks(speedBoostRunnable)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleAccumulator *= detector.scaleFactor
                if (scaleAccumulator > 1.12f) {
                    when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> setVideoResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> setVideoResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL)
                    }
                    scaleAccumulator = 1.0f
                } else if (scaleAccumulator < 0.88f) {
                    when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> setVideoResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> setVideoResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                    }
                    scaleAccumulator = 1.0f
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                scaleAccumulator = 1.0f
            }
        })
    }

    private val securityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> triggerPanicLockAndExit()
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    player?.pause()
                    isMuted = true
                    player?.volume = 0f
                    updateMuteIcon()
                    updatePlayPauseIcon()
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        super.onCreate(savedInstanceState)

        val uuid = intent.getStringExtra(EXTRA_VAULT_UUID)?.takeIf { it.isNotBlank() }
        val title = intent.getStringExtra(EXTRA_VAULT_TITLE).orEmpty().ifBlank { "Private Video" }

        if (uuid == null || !VaultSessionManager.isUnlocked.value) {
            triggerPanicLockAndExit()
            return
        }

        // Pause normal app audio silently without registering auto-resume on exit.
        PlaybackController.pause()

        keepOpenOnBack = runCatching {
            VaultRepository.loadSettings(this).keepOpenOnPlayerBack
        }.getOrDefault(false)

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    VaultPlayerScreen(
                        state = uiState,
                        player = player,
                        onPlayerReady = { pv ->
                            initPlayerView(pv, title)
                        },
                        onUnlockClick = {
                            setLocked(false)
                        }
                    )
                }
            }
        }
        setContentView(composeView)
        setupImmersiveMode()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackAction()
                }
            }
        )

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        registerReceiver(securityReceiver, filter)

        initPlayer(uuid)
    }

    private fun initPlayerView(pv: PlayerView, title: String) {
        playerView = pv
        if (playerView.findViewById<View>(R.id.vault_player_back) == null) {
            val vaultControls = LayoutInflater.from(this).inflate(R.layout.exo_vault_player_control_view, playerView, false)
            playerView.addView(vaultControls)
        }
        playerView.setShutterBackgroundColor(Color.TRANSPARENT)
        playerView.setControllerShowTimeoutMs(3500)
        player?.let { p ->
            playerView.player = p
            playerView.resizeMode = resizeMode
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_shutter)?.visibility = View.GONE
        }

        bindCustomControls(title)

        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            val visible = visibility == View.VISIBLE
            playerView.findViewById<View>(R.id.video_top_bar)?.visibility =
                if (visible) View.VISIBLE else View.GONE
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)?.visibility =
                if (visible) View.VISIBLE else View.GONE
        })

        playerView.setOnTouchListener { _, event -> handleTouch(event) }
    }

    private fun bindCustomControls(title: String) {
        playerView.findViewById<View>(R.id.vault_player_back)?.setOnClickListener {
            handleBackAction()
        }

        playerView.findViewById<TextView>(R.id.vault_player_title)?.apply {
            text = title
            isSelected = true
        }

        val speedBtn = playerView.findViewById<TextView>(R.id.vault_player_speed)
        speedBtn?.setOnClickListener {
            speedIndex = (speedIndex + 1) % speeds.size
            val spd = speeds[speedIndex]
            originalPlaybackSpeed = spd
            player?.playbackParameters = PlaybackParameters(spd, 1.0f)
            val label = formatPillSpeed(spd)
            speedBtn.text = label
            showUnifiedFeedback(R.drawable.ic_speed_play, "${formatSpeedLabel(spd)} ${getString(R.string.speed)}")
        }

        playerView.findViewById<ImageButton>(R.id.vault_player_screen_lock)?.setOnClickListener {
            setLocked(!screenControlsLocked)
        }

        playerView.findViewById<ImageButton>(R.id.vault_player_rewind)?.setOnClickListener {
            seekRelative(-SEEK_STEP_MS)
            val duration = currentDuration().coerceAtLeast(1L)
            val pos = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            val pct = ((pos.toFloat() / duration) * 100f).roundToInt().coerceIn(0, 100)
            showUnifiedFeedback(R.drawable.ic_lyrics_rewind, "-10s", progress = pct)
        }

        playerView.findViewById<ImageButton>(R.id.vault_player_forward)?.setOnClickListener {
            seekRelative(SEEK_STEP_MS)
            val duration = currentDuration().coerceAtLeast(1L)
            val pos = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            val pct = ((pos.toFloat() / duration) * 100f).roundToInt().coerceIn(0, 100)
            showUnifiedFeedback(R.drawable.ic_lyrics_forward, "+10s", progress = pct)
        }

        playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isPlaying) p.pause() else p.play()
            updatePlayPauseIcon()
        }

        playerView.findViewById<ImageButton>(R.id.vault_player_mute)?.setOnClickListener {
            isMuted = !isMuted
            player?.volume = if (isMuted) 0f else 1f
            updateMuteIcon()
            showUnifiedFeedback(
                if (isMuted) R.drawable.ic_volume_mute else R.drawable.ic_volume_up,
                getString(if (isMuted) R.string.vault_player_muted else R.string.volume)
            )
        }

        playerView.findViewById<ImageButton>(R.id.vault_player_aspect)?.setOnClickListener {
            cycleResizeMode()
        }

        playerView.subtitleView?.visibility = View.GONE
        updateMuteIcon()
        updatePlayPauseIcon()
    }

    private fun initPlayer(uuid: String) {
        val itemsDir = VaultStorage.itemsDir(this, VaultSessionManager.isDecoySession)
        val dataSourceFactory = VaultDataSourceFactory(
            itemsDir = itemsDir,
            vaultKeyProvider = { VaultSessionManager.getKeyCopyOrNull() }
        )
        val built = VideoPlayerUtil.buildPlayer(
            context = this,
            mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory),
            extensionsEnabled = true
        )
        built.trackSelectionParameters = built.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        player = built
        VaultSessionManager.registerPlayer(built)

        if (::playerView.isInitialized) {
            playerView.player = built
            playerView.resizeMode = resizeMode
            playerView.setShutterBackgroundColor(Color.TRANSPARENT)
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_shutter)?.visibility = View.GONE
        }

        built.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (::playerView.isInitialized) {
                    playerView.keepScreenOn = isPlaying
                    updatePlayPauseIcon()
                }
                if (isPlaying) {
                    renderedFirstFrame = true
                    uiState = uiState.copy(isLoading = false)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    renderedFirstFrame = true
                    uiState = uiState.copy(isLoading = false)
                }
            }

            override fun onRenderedFirstFrame() {
                renderedFirstFrame = true
                uiState = uiState.copy(isLoading = false)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    renderedFirstFrame = true
                    uiState = uiState.copy(isLoading = false)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@VaultVideoPlayerActivity,
                    R.string.vault_player_error,
                    Toast.LENGTH_SHORT
                ).show()
                handleBackAction()
            }
        })

        // Zero MediaMetadata (title, artwork, album, artist) attached to ExoPlayer.
        val mediaItem = MediaItem.Builder()
            .setMediaId("vault:$uuid")
            .setUri("vault://$uuid")
            .build()

        built.setMediaItem(mediaItem)
        built.prepare()
        built.playWhenReady = true
    }

    private fun setVideoResizeMode(mode: Int) {
        if (resizeMode == mode) return
        resizeMode = mode
        playerView.resizeMode = resizeMode
        val label = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> R.string.resize_zoom
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> R.string.resize_fill
            else -> R.string.resize_fit
        }
        showUnifiedFeedback(R.drawable.ic_aspect_ratio, getString(label))
        runCatching {
            playerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun cycleResizeMode() {
        val next = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        setVideoResizeMode(next)
    }

    private fun setLocked(locked: Boolean) {
        screenControlsLocked = locked
        if (::playerView.isInitialized) {
            playerView.useController = !locked
            playerView.findViewById<ImageButton>(R.id.vault_player_screen_lock)?.setImageResource(
                if (locked) R.drawable.ic_lock else R.drawable.ic_unlock
            )
            if (locked) {
                playerView.hideController()
            } else {
                playerView.showController()
            }
        }
        uiState = uiState.copy(isLocked = locked)
        showUnifiedFeedback(
            iconRes = if (locked) R.drawable.ic_lock else R.drawable.ic_unlock,
            text = getString(if (locked) R.string.lock_controls else R.string.unlock_controls)
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(event: MotionEvent): Boolean {
        if (screenControlsLocked) return true

        scaleGestureDetector.onTouchEvent(event)
        if (scaleGestureDetector.isInProgress || isScaling || event.pointerCount >= 2) {
            gestureMode = GestureMode.NONE
            doubleTapPending = false
            lastTapUpTime = 0L
            handler.removeCallbacks(speedBoostRunnable)
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL ||
                event.actionMasked == MotionEvent.ACTION_POINTER_UP
            ) {
                if (event.pointerCount <= 1) {
                    isScaling = false
                }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                speedBoostStartX = event.x
                val now = SystemClock.uptimeMillis()
                doubleTapPending = now - lastTapUpTime <= DOUBLE_TAP_TIMEOUT_MS &&
                    abs(event.x - lastTapX) <= touchSlop * 6f
                if (doubleTapPending) {
                    lastTapUpTime = 0L
                    handler.removeCallbacks(speedBoostRunnable)
                    return true
                }
                gestureMode = GestureMode.NONE
                gestureStartPosition = player?.currentPosition ?: 0L
                val currentBrightness = window.attributes.screenBrightness
                    .takeIf { it >= 0f } ?: 0.5f
                gestureStartBrightness = currentBrightness
                gestureStartBrightnessPercent = currentBrightness * 100f

                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                gestureStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                gestureStartVolumePercent = (gestureStartVolume.toFloat() / maxVolume) * 100f

                handler.removeCallbacks(speedBoostRunnable)
                handler.postDelayed(speedBoostRunnable, 400L)
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (doubleTapPending) return true

                val dx = event.x - downX
                val dy = event.y - downY

                if (isSpeedBoostActive) {
                    val deltaX = event.x - speedBoostStartX
                    val stepPx = 45f * resources.displayMetrics.density
                    val stepOffset = (deltaX / stepPx).toInt()
                    val targetIndex = (4 + stepOffset).coerceIn(0, boostSpeeds.lastIndex)
                    if (targetIndex != currentBoostIndex) {
                        currentBoostIndex = targetIndex
                        val newSpeed = boostSpeeds[currentBoostIndex]
                        player?.playbackParameters = PlaybackParameters(newSpeed, 1.0f)
                        updateSpeedBoostUi(newSpeed)
                        uiState = uiState.copy(showBoostTicks = true)
                        handler.removeCallbacks(collapseSpeedBarRunnable)
                        handler.postDelayed(collapseSpeedBarRunnable, 1500L)
                        runCatching {
                            playerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                    return true
                }

                if (max(abs(dx), abs(dy)) > touchSlop) {
                    handler.removeCallbacks(speedBoostRunnable)
                }

                if (gestureMode == GestureMode.NONE && max(abs(dx), abs(dy)) > touchSlop) {
                    gestureMode = when {
                        abs(dx) > abs(dy) -> GestureMode.SEEK
                        downX < playerView.width / 2f -> GestureMode.BRIGHTNESS
                        else -> GestureMode.VOLUME
                    }
                }

                when (gestureMode) {
                    GestureMode.SEEK -> {
                        val duration = currentDuration()
                        if (duration <= 0L) return true
                        val delta = (dx / playerView.width.coerceAtLeast(1)) * SEEK_WINDOW_MS
                        pendingSeekMs = (gestureStartPosition + delta.toLong()).coerceIn(0L, duration)
                        showSeekPreview()
                        return true
                    }

                    GestureMode.BRIGHTNESS -> {
                        val deltaPercent = (-dy / playerView.height.coerceAtLeast(1)) * 100f
                        val targetPercent = (gestureStartBrightnessPercent + deltaPercent).roundToInt().coerceIn(1, 100)
                        window.attributes = window.attributes.apply { screenBrightness = targetPercent / 100f }
                        showBrightnessFeedback(targetPercent)
                        return true
                    }

                    GestureMode.VOLUME -> {
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val deltaPercent = (-dy / playerView.height.coerceAtLeast(1)) * 100f
                        val targetPercent = (gestureStartVolumePercent + deltaPercent).roundToInt().coerceIn(0, 100)
                        val streamVal = ((targetPercent / 100f) * maxVolume).roundToInt().coerceIn(0, maxVolume)
                        if (streamVal != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, streamVal, 0)
                        }
                        if (isMuted && streamVal > 0) {
                            isMuted = false
                            player?.volume = 1f
                            updateMuteIcon()
                        }
                        showVolumeFeedback(targetPercent)
                        return true
                    }

                    GestureMode.NONE -> return false
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(speedBoostRunnable)
                if (isSpeedBoostActive) {
                    endSpeedBoost()
                    return true
                }

                val handled = doubleTapPending || gestureMode != GestureMode.NONE
                if (doubleTapPending) {
                    handleDoubleTap(event.x)
                    doubleTapPending = false
                } else if (gestureMode == GestureMode.SEEK) {
                    player?.seekTo(pendingSeekMs)
                }
                gestureMode = GestureMode.NONE
                lastTapUpTime = SystemClock.uptimeMillis()
                lastTapX = event.x
                return handled
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(speedBoostRunnable)
                if (isSpeedBoostActive) {
                    endSpeedBoost()
                }
                doubleTapPending = false
                gestureMode = GestureMode.NONE
                return false
            }
        }
        return false
    }

    private fun handleDoubleTap(x: Float) {
        val p = player ?: return
        val width = playerView.width.coerceAtLeast(1)
        val duration = currentDuration().coerceAtLeast(1L)
        when {
            x < width * 0.35f -> {
                val newPos = (p.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L)
                p.seekTo(newPos)
                val pct = ((newPos.toFloat() / duration) * 100f).roundToInt().coerceIn(0, 100)
                showUnifiedFeedback(R.drawable.ic_lyrics_rewind, "-10s", progress = pct)
            }
            x > width * 0.65f -> {
                val newPos = (p.currentPosition + SEEK_STEP_MS).coerceAtMost(duration)
                p.seekTo(newPos)
                val pct = ((newPos.toFloat() / duration) * 100f).roundToInt().coerceIn(0, 100)
                showUnifiedFeedback(R.drawable.ic_lyrics_forward, "+10s", progress = pct)
            }
            else -> {
                if (p.isPlaying) p.pause() else p.play()
                updatePlayPauseIcon()
                showUnifiedFeedback(
                    if (p.isPlaying) R.drawable.ic_lyrics_play else R.drawable.ic_lyrics_pause,
                    getString(if (p.isPlaying) R.string.play else R.string.pause)
                )
            }
        }
        runCatching {
            playerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun seekRelative(deltaMs: Long) {
        val p = player ?: return
        val dur = currentDuration().coerceAtLeast(0L)
        val target = (p.currentPosition + deltaMs).coerceIn(0L, if (dur > 0L) dur else Long.MAX_VALUE)
        p.seekTo(target)
    }

    private fun showSeekPreview() {
        val duration = currentDuration().coerceAtLeast(1L)
        val pct = ((pendingSeekMs.toFloat() / duration) * 100f).roundToInt().coerceIn(0, 100)
        val iconRes = if (pendingSeekMs >= gestureStartPosition) {
            R.drawable.ic_lyrics_forward
        } else {
            R.drawable.ic_lyrics_rewind
        }
        showUnifiedFeedback(
            iconRes = iconRes,
            text = "${pendingSeekMs.toStringTimeStamp()} / ${duration.toStringTimeStamp()}",
            progress = pct
        )
    }

    private fun showVolumeFeedback(percent: Int) {
        val iconRes = when {
            percent == 0 -> R.drawable.ic_volume_mute
            percent <= 33 -> R.drawable.ic_volume_low
            percent <= 66 -> R.drawable.ic_volume_mid
            else -> R.drawable.ic_volume_high
        }
        showUnifiedFeedback(iconRes = iconRes, text = "$percent", progress = percent)
    }

    private fun showBrightnessFeedback(percent: Int) {
        if (isSpeedBoostActive || panicLocked) return
        val iconRes = when {
            percent <= 25 -> R.drawable.ic_brightness_low
            percent <= 50 -> R.drawable.ic_brightness_medium
            percent <= 75 -> R.drawable.ic_brightness_high
            else -> R.drawable.ic_brightness_full
        }
        showUnifiedFeedback(iconRes = iconRes, text = "$percent%", progress = percent)
    }

    private fun showUnifiedFeedback(
        iconRes: Int?,
        text: String,
        progress: Int? = null,
        timeoutMs: Long = 1500L
    ) {
        if (isSpeedBoostActive || panicLocked) return
        uiState = uiState.copy(
            levelVisible = true,
            levelIconRes = iconRes ?: R.drawable.ic_volume_up,
            levelText = text,
            levelProgress = progress
        )
        handler.removeCallbacks(hideLevelIndicatorRunnable)
        handler.postDelayed(hideLevelIndicatorRunnable, timeoutMs)
    }

    private fun startSpeedBoost() {
        val p = player ?: return
        if (screenControlsLocked || panicLocked) return
        isSpeedBoostActive = true
        originalPlaybackSpeed = p.playbackParameters.speed.takeIf { it > 0f } ?: speeds[speedIndex]
        currentBoostIndex = 4 // Default 2.0x
        val speed = boostSpeeds[currentBoostIndex]
        p.playbackParameters = PlaybackParameters(speed, 1.0f)

        uiState = uiState.copy(
            speedBoostVisible = true,
            showBoostTicks = true,
            currentBoostIndex = currentBoostIndex,
            speedBoostLabel = "${formatSpeedLabel(speed)} Speed Playing",
            levelVisible = false
        )

        handler.removeCallbacks(collapseSpeedBarRunnable)
        handler.postDelayed(collapseSpeedBarRunnable, 1500L)

        runCatching {
            playerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun endSpeedBoost() {
        if (!isSpeedBoostActive) return
        isSpeedBoostActive = false
        handler.removeCallbacks(collapseSpeedBarRunnable)
        player?.playbackParameters = PlaybackParameters(originalPlaybackSpeed, 1.0f)
        playerView.findViewById<TextView>(R.id.vault_player_speed)?.text =
            formatPillSpeed(originalPlaybackSpeed)
        uiState = uiState.copy(speedBoostVisible = false)
        runCatching {
            playerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun updateSpeedBoostUi(speed: Float) {
        uiState = uiState.copy(
            currentBoostIndex = currentBoostIndex,
            speedBoostLabel = "${formatSpeedLabel(speed)} Speed Playing"
        )
    }

    private fun formatSpeedLabel(speed: Float): String {
        val rounded = kotlin.math.round(speed * 100f) / 100f
        return when {
            rounded % 1f == 0f -> "${rounded.toInt()}.0x"
            (rounded * 10f) % 1f == 0f -> String.format(Locale.US, "%.1fx", rounded)
            else -> String.format(Locale.US, "%.2fx", rounded)
        }
    }

    private fun formatPillSpeed(speed: Float): String {
        val rounded = kotlin.math.round(speed * 100f) / 100f
        return if (rounded % 1f == 0f) "${rounded.toInt()}X" else "${rounded}X"
    }

    private fun updatePlayPauseIcon() {
        val playing = player?.isPlaying == true
        playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.setImageResource(
            if (playing) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play
        )
    }

    private fun updateMuteIcon() {
        playerView.findViewById<ImageButton>(R.id.vault_player_mute)?.setImageResource(
            if (isMuted) R.drawable.ic_volume_mute else R.drawable.ic_volume_up
        )
    }

    private fun currentDuration(): Long = (player?.duration ?: 0L).coerceAtLeast(0L)

    private fun applyCloseTransition(enterAnim: Int, exitAnim: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, enterAnim, exitAnim)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(enterAnim, exitAnim)
        }
    }

    private fun handleBackAction() {
        if (smoothExitInFlight || panicLocked) return
        smoothExitInFlight = true
        if (keepOpenOnBack && VaultSessionManager.isUnlocked.value) {
            exitingToGallery = true
            releasePlayerOnly()
            finish()
            applyCloseTransition(0, 0)
        } else {
            panicLocked = true
            cancelAnimationsImmediate()
            handler.removeCallbacksAndMessages(null)
            releasePlayerOnly()
            VaultSessionManager.lockAndZeroize()
            finishAndRemoveTask()
            applyCloseTransition(0, 0)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!exitingToGallery && !smoothExitInFlight) {
            triggerPanicLockAndExit()
        }
    }

    private fun cancelAnimationsImmediate() {
    }

    override fun onPause() {
        cancelAnimationsImmediate()
        if (!smoothExitInFlight && !exitingToGallery) {
            uiState = uiState.copy(isBlackout = true)
        }
        if (::playerView.isInitialized && !smoothExitInFlight) {
            playerView.player = null
        }
        handler.removeCallbacksAndMessages(null)
        super.onPause()
        if (!exitingToGallery && !smoothExitInFlight && !panicLocked) {
            triggerPanicLockAndExit()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(securityReceiver) }
        releasePlayerOnly()
        super.onDestroy()
    }

    private fun releasePlayerOnly() {
        if (::playerView.isInitialized) {
            playerView.player = null
        }
        player?.let { p ->
            runCatching {
                p.playWhenReady = false
                p.volume = 0f
                p.stop()
                p.clearMediaItems()
                p.release()
            }
        }
        player = null
        VaultSessionManager.registerPlayer(null)
    }

    private fun triggerPanicLockAndExit() {
        if (panicLocked) return
        panicLocked = true
        cancelAnimationsImmediate()
        uiState = uiState.copy(isBlackout = true)
        handler.removeCallbacksAndMessages(null)
        releasePlayerOnly()
        VaultSessionManager.lockAndZeroize()
        finishAndRemoveTask()
        applyCloseTransition(0, 0)
    }

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersiveMode()
    }

    companion object {
        private const val EXTRA_VAULT_UUID = "vault_uuid"
        private const val EXTRA_VAULT_TITLE = "vault_title"
        private const val SEEK_WINDOW_MS = 60_000L
        private const val SEEK_STEP_MS = 10_000L
        private const val DOUBLE_TAP_TIMEOUT_MS = 300L

        fun createIntent(context: Context, uuid: String, title: String): Intent =
            Intent(context, VaultVideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VAULT_UUID, uuid)
                putExtra(EXTRA_VAULT_TITLE, title)
            }
    }

    private enum class GestureMode {
        NONE, SEEK, BRIGHTNESS, VOLUME
    }
}
