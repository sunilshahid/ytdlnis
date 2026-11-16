package com.deniscerri.ytdl.ui.play

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import androidx.activity.OnBackPressedCallback
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.playback.model.PlaybackExtrasCodec
import com.deniscerri.ytdl.playback.model.PlaybackSubtitle
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.google.android.material.button.MaterialButton
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fullscreen video player with MX Player-style ergonomics:
 * - Top bar with Back button, Marquee Video Title, and Quick Speed pill
 * - Level indicator bar (Volume & Brightness) near the top right (never blocking center screen)
 * - Tap-and-hold 2x speed boost with horizontal sliding speed selector
 * - Pinch-to-zoom (Fit <-> Zoomed to fill <-> Stretch to fill)
 * - Bottom controls with elapsed time, seekbar, remaining time, Lock, Prev, Play/Pause, Next, Aspect ratio, and PiP
 */
@UnstableApi
class VideoPlayerActivity : BaseActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var poster: ImageView
    private lateinit var loadingOverlay: View
    private lateinit var errorOverlay: View
    private lateinit var errorMessage: TextView
    private lateinit var seekPreview: TextView

    // Level indicator views (Volume & Brightness bar near top right)
    private var levelIndicatorView: View? = null
    private var levelIcon: ImageView? = null
    private var levelProgress: ProgressBar? = null
    private var levelText: TextView? = null
    private var brightnessMeterDrawable: BrightnessMeterDrawable? = null

    // Speed boost views (Top center tap-and-hold speed bar)
    private var speedBoostContainer: View? = null
    private var speedBoostTicksRow: View? = null
    private var speedBoostLabel: TextView? = null
    private var lockedUnlockBtn: ImageButton? = null

    private var attachedPlayer: Player? = null
    private var locked = false
    private var backgroundPlayEnabled = false
    private var scaleAccumulator = 1.0f
    private var isScaling = false
    private var showRemainingTime = false

    private fun handleExit() {
        if (!backgroundPlayEnabled && !isInPictureInPictureMode) {
            PlaybackController.stop()
        }
        finish()
    }

    // Speed boost state
    private var isSpeedBoostActive = false
    private var speedBoostStartX = 0f
    private var speedBoostStartIndex = 4 // Index of 2.0x in boostSpeeds
    private var currentBoostIndex = 4
    private var originalPlaybackSpeed = 1.0f
    private val boostSpeeds = floatArrayOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
    private val boostSpeedTextViewIds = listOf(
        R.id.speed_opt_0_25,
        R.id.speed_opt_0_5,
        R.id.speed_opt_1_0,
        R.id.speed_opt_1_5,
        R.id.speed_opt_2_0,
        R.id.speed_opt_2_5,
        R.id.speed_opt_3_0,
        R.id.speed_opt_4_0
    )

    private val speedBoostRunnable = Runnable {
        startSpeedBoost()
    }

    private val collapseSpeedBarRunnable = Runnable {
        speedBoostTicksRow?.visibility = View.GONE
    }

    private val hideLevelIndicatorRunnable = Runnable {
        levelIndicatorView?.visibility = View.GONE
    }

    private val hideHints = Runnable {
        seekPreview.visibility = View.GONE
    }

    private val scaleGestureDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                scaleAccumulator = 1.0f
                gestureMode = GestureMode.NONE
                doubleTapPending = false
                lastTapUpTime = 0L
                uiHandler.removeCallbacks(speedBoostRunnable)
                uiHandler.removeCallbacks(hideHints)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                scaleAccumulator *= factor
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
                scheduleHideHints()
            }
        })
    }

    private var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var gestureMode = GestureMode.NONE
    private var renderedFirstFrame = false
    private var posterMediaId: String? = null

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

    private val uiHandler = Handler(Looper.getMainLooper())

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val touchSlop by lazy {
        ViewConfiguration.get(this).scaledTouchSlop
    }

    private var lastChapterMarkerSignature: LongArray? = null
    private var appliedSubPrefsForMediaId: String? = null

    private val importSubtitleLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handleImportSubtitle(uri)
        }
    }

    private fun handleImportSubtitle(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = DocumentFile.fromSingleUri(this@VideoPlayerActivity, uri)?.name
                    ?: uri.lastPathSegment
                    ?: "custom.srt"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VideoPlayerActivity, R.string.subtitles_import_failed, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Auto-detect charset
                val text = when {
                    bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> {
                        String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
                    }
                    bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> {
                        String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
                    }
                    bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> {
                        String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
                    }
                    else -> {
                        try {
                            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                        } catch (e: Exception) {
                            try {
                                String(bytes, Charset.forName("windows-1252"))
                            } catch (e2: Exception) {
                                String(bytes, Charsets.ISO_8859_1)
                            }
                        }
                    }
                }

                val lower = fileName.lowercase(Locale.ROOT)
                val normalizedText = if (lower.endsWith(".vtt") && !text.trimStart().startsWith("WEBVTT", ignoreCase = true)) {
                    "WEBVTT\n\n$text"
                } else {
                    text
                }

                val subDir = File(cacheDir, "subtitles").apply { mkdirs() }
                val currentMediaId = PlaybackController.state.value.currentItem?.mediaId ?: "media"
                val safeMediaId = currentMediaId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val cachedFile = File(subDir, "${safeMediaId}_${safeFileName}")
                cachedFile.writeText(normalizedText, Charsets.UTF_8)

                val mimeType = PlaybackExtrasCodec.inferSubtitleMimeType(fileName)
                val playbackSub = PlaybackSubtitle(
                    uri = Uri.fromFile(cachedFile).toString(),
                    mimeType = mimeType,
                    language = null,
                    label = fileName,
                    isAutoGenerated = false,
                    isLocal = true
                )

                withContext(Dispatchers.Main) {
                    PlaybackController.addCustomSubtitle(playbackSub, autoSelect = true)
                    updateSubtitleButtonTint()
                    Toast.makeText(
                        this@VideoPlayerActivity,
                        getString(R.string.subtitles_imported_success, fileName),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("VideoPlayerActivity", "Failed to import subtitle", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoPlayerActivity, R.string.subtitles_import_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun currentPlaybackPositionMs(): Long {
        return attachedPlayer?.currentPosition?.takeIf { it >= 0L }
            ?: PlaybackController.state.value.positionMs
    }

    private fun updateSubtitleButtonTint() {
        val btn = playerView.findViewById<ImageButton>(R.id.video_subtitles) ?: return
        val active = VideoPlayerOverlays.isSubtitleActive(attachedPlayer ?: PlaybackController.player())
        val available = VideoPlayerOverlays.hasTextTracks(attachedPlayer ?: PlaybackController.player())
        btn.setColorFilter(
            when {
                active -> Color.parseColor("#64B5F6")
                available -> Color.WHITE
                else -> Color.argb(150, 255, 255, 255)
            }
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            val currentId = attachedPlayer?.currentMediaItem?.mediaId
            if (currentId != null && currentId != appliedSubPrefsForMediaId) {
                appliedSubPrefsForMediaId = currentId
                VideoPlayerOverlays.applyPreferredSubtitleSelection(attachedPlayer, this@VideoPlayerActivity)
            } else if (attachedPlayer != null) {
                VideoPlayerOverlays.applyPreferredSubtitleSelection(attachedPlayer, this@VideoPlayerActivity)
            }
            updateSubtitleButtonTint()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playerView.keepScreenOn = isPlaying
            playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.setImageResource(
                if (isPlaying) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play
            )
            if (isPlaying) {
                renderedFirstFrame = true
                renderState(PlaybackController.state.value)
            }
            updatePipParams()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                renderedFirstFrame = true
                renderState(PlaybackController.state.value)
            }
            updatePipParams()
        }

        override fun onRenderedFirstFrame() {
            renderedFirstFrame = true
            renderState(PlaybackController.state.value)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                renderedFirstFrame = true
                renderState(PlaybackController.state.value)
                updatePipParams()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(R.layout.activity_video_player)
        PlaybackController.init(this)

        playerView = findViewById(R.id.video_player)
        seekPreview = findViewById(R.id.video_seek_preview)
        poster = findViewById(R.id.video_poster)
        loadingOverlay = findViewById(R.id.video_loading)
        errorOverlay = findViewById(R.id.video_error)
        errorMessage = findViewById(R.id.video_error_message)

        // Level indicator views (Volume/Brightness near top right)
        levelIndicatorView = findViewById(R.id.video_level_indicator)
        levelIcon = findViewById(R.id.video_level_icon)
        levelProgress = findViewById(R.id.video_level_progress)
        levelText = findViewById(R.id.video_level_text)

        // Speed boost views
        speedBoostContainer = findViewById(R.id.video_speed_boost_container)
        speedBoostTicksRow = findViewById(R.id.video_speed_boost_ticks_row)
        speedBoostLabel = findViewById(R.id.video_speed_boost_label)
        lockedUnlockBtn = findViewById(R.id.video_locked_unlock_btn)

        playerView.setShutterBackgroundColor(Color.TRANSPARENT)
        playerView.setControllerShowTimeoutMs(3500)

        // Bind custom control buttons in exo_player_control_view
        bindCustomControls()

        findViewById<MaterialButton>(R.id.video_retry).setOnClickListener {
            retryPlayback()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExit()
            }
        })

        // Synchronize top and bottom controls visibility together
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            val isVisible = visibility == View.VISIBLE
            playerView.findViewById<View>(R.id.video_top_bar)?.visibility = if (isVisible) View.VISIBLE else View.GONE
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)?.visibility = if (isVisible) View.VISIBLE else View.GONE
            if (isVisible) {
                updateDurationDisplay()
            }
        })

        playerView.setOnTouchListener { _, event -> handleTouch(event) }
        setupImmersiveMode()
        observePlayback()
    }

    private fun bindCustomControls() {
        // Back button: exits video and stops playback unless background play is enabled
        playerView.findViewById<View>(R.id.video_back)?.setOnClickListener {
            handleExit()
        }

        // Title marquee
        playerView.findViewById<TextView>(R.id.video_title)?.isSelected = true

        // Background Play / Audio Mode toggle (Headphones icon)
        val bgAudioBtn = playerView.findViewById<ImageButton>(R.id.video_bg_audio)
        bgAudioBtn?.imageTintList = ColorStateList.valueOf(
            if (backgroundPlayEnabled) Color.parseColor("#2196F3") else Color.parseColor("#80FFFFFF")
        )
        bgAudioBtn?.setOnClickListener {
            backgroundPlayEnabled = !backgroundPlayEnabled
            bgAudioBtn.imageTintList = ColorStateList.valueOf(
                if (backgroundPlayEnabled) Color.parseColor("#2196F3") else Color.parseColor("#80FFFFFF")
            )
            showUnifiedFeedback(
                iconRes = R.drawable.ic_headphones,
                text = if (backgroundPlayEnabled) getString(R.string.bg_play_enabled) else getString(R.string.bg_play_disabled)
            )
            runCatching {
                playerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        // Top quick speed button
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)
        var speedIndex = 0
        val topSpeedBtn = playerView.findViewById<TextView>(R.id.video_top_speed)
        topSpeedBtn?.setOnClickListener {
            speedIndex = (speedIndex + 1) % speeds.size
            val spd = speeds[speedIndex]
            attachedPlayer?.playbackParameters = PlaybackParameters(spd, 1.0f)
            val text = if (spd % 1f == 0f) "${spd.toInt()}X" else "${spd}X"
            topSpeedBtn.text = text
        }

        // Lock button
        playerView.findViewById<ImageButton>(R.id.video_lock)?.setOnClickListener {
            setLocked(!locked)
        }

        // Floating unlock button
        lockedUnlockBtn?.setOnClickListener {
            setLocked(false)
        }

        // Resize button
        playerView.findViewById<ImageButton>(R.id.video_resize)?.setOnClickListener {
            cycleResizeMode()
        }

        // PiP button
        playerView.findViewById<ImageButton>(R.id.video_pip)?.setOnClickListener {
            enterPip()
        }

        // Previous / Next track navigation
        VideoPlayerOverlays.applySubtitleStyle(playerView, this)
        playerView.findViewById<ImageButton>(R.id.video_subtitles)?.setOnClickListener {
            VideoPlayerOverlays.showSubtitlesSheet(
                context = this,
                player = attachedPlayer ?: PlaybackController.player(),
                playerView = playerView,
                onImportSubtitle = {
                    importSubtitleLauncher.launch(
                        arrayOf(
                            "text/*",
                            "application/x-subrip",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                }
            ) {
                updateSubtitleButtonTint()
            }
        }
        val openChapters = {
            VideoPlayerOverlays.showChaptersSheet(
                context = this,
                chapters = PlaybackController.state.value.chapters,
                currentPositionMs = currentPlaybackPositionMs()
            ) { chapter ->
                PlaybackController.seekTo(chapter.startMs)
            }
        }
        playerView.findViewById<ImageButton>(R.id.video_chapters)?.setOnClickListener { openChapters() }
        playerView.findViewById<View>(R.id.video_chapter_pill)?.setOnClickListener { openChapters() }
        playerView.findViewById<ImageButton>(R.id.video_chapter_prev)?.setOnClickListener {
            VideoPlayerOverlays.computeChapterJumpMs(
                PlaybackController.state.value.chapters,
                currentPlaybackPositionMs(),
                delta = -1
            )?.let { PlaybackController.seekTo(it) }
        }
        playerView.findViewById<ImageButton>(R.id.video_chapter_next)?.setOnClickListener {
            VideoPlayerOverlays.computeChapterJumpMs(
                PlaybackController.state.value.chapters,
                currentPlaybackPositionMs(),
                delta = 1
            )?.let { PlaybackController.seekTo(it) }
        }

        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_prev)?.setOnClickListener {
            PlaybackController.previous()
        }
        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_next)?.setOnClickListener {
            PlaybackController.next()
        }

        // Single Play/Pause Button
        val playPauseBtn = playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)
        playPauseBtn?.setOnClickListener {
            PlaybackController.togglePlayPause()
        }
        val isCurrentPlaying = attachedPlayer?.isPlaying == true
        playPauseBtn?.setImageResource(if (isCurrentPlaying) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play)

        // Duration view (right time): click to toggle showing remaining time
        val durationView = playerView.findViewById<TextView>(androidx.media3.ui.R.id.exo_duration)
        durationView?.isClickable = true
        durationView?.isFocusable = true
        durationView?.setOnClickListener {
            showRemainingTime = !showRemainingTime
            updateDurationDisplay()
        }

        val controllerView = playerView.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
        controllerView?.setProgressUpdateListener { position, _ ->
            if (showRemainingTime) {
                val duration = currentDuration()
                val remaining = (duration - position).coerceAtLeast(0L)
                durationView?.text = "-${remaining.toStringTimeStamp()}"
            }
        }
    }

    private fun updateDurationDisplay() {
        val durationView = playerView.findViewById<TextView>(androidx.media3.ui.R.id.exo_duration) ?: return
        val duration = currentDuration()
        if (showRemainingTime) {
            val position = currentPlaybackPositionMs()
            val remaining = (duration - position).coerceAtLeast(0L)
            durationView.text = "-${remaining.toStringTimeStamp()}"
        } else {
            durationView.text = duration.toStringTimeStamp()
        }
    }

    private fun observePlayback() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackController.state.collect { state ->
                    val controller = PlaybackController.player()
                    if (controller != null && controller !== attachedPlayer) {
                        attachPlayer(controller)
                    }
                    val item = state.currentItem
                    if (item != null && item.type != PlaybackType.VIDEO) {
                        // The session moved on to an audio item; this surface no longer applies.
                        finish()
                    }
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: PlaybackController.UiState) {
        VideoPlayerOverlays.updateChapterMarkersAndBar(
            playerView = playerView,
            chapters = state.chapters,
            positionMs = currentPlaybackPositionMs(),
            lastMarkerSignature = lastChapterMarkerSignature
        ) { lastChapterMarkerSignature = it }
        updateSubtitleButtonTint()
        updateDurationDisplay()
        val item = state.currentItem ?: return
        val player = attachedPlayer
        val isReadyOrPlaying = player != null && (
            player.playbackState == Player.STATE_READY ||
            player.isPlaying ||
            player.currentPosition > 0L ||
            (player.videoSize.width > 0 && player.videoSize.height > 0)
        )

        // Set video title
        playerView.findViewById<TextView>(R.id.video_title)?.text = item.title

        // Update single Play/Pause button icon
        val isPlayingNow = player != null && player.isPlaying
        playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.setImageResource(
            if (isPlayingNow) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play
        )

        if (item.mediaId != posterMediaId) {
            posterMediaId = item.mediaId
            renderedFirstFrame = isReadyOrPlaying
            if (item.artworkUri.isNullOrBlank()) {
                poster.setImageDrawable(null)
            } else {
                Picasso.get().load(item.artworkUri).into(poster)
            }
        } else if (isReadyOrPlaying) {
            renderedFirstFrame = true
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_shutter)?.visibility = View.GONE
        }

        val loading = state.error == null && !renderedFirstFrame
        loadingOverlay.isVisible = loading
        poster.isVisible = loading && !item.artworkUri.isNullOrBlank()
        errorOverlay.isVisible = state.error != null
        state.error?.let { errorMessage.text = it }
    }

    private fun retryPlayback() {
        renderedFirstFrame = false
        errorOverlay.isVisible = false
        loadingOverlay.isVisible = true
        poster.isVisible =
            !PlaybackController.state.value.currentItem?.artworkUri.isNullOrBlank()
        PlaybackController.retryCurrent()
    }

    private fun attachPlayer(player: Player) {
        attachedPlayer?.removeListener(playerListener)
        attachedPlayer = player
        player.addListener(playerListener)
        VideoPlayerOverlays.applySubtitleStyle(playerView, this)
        VideoPlayerOverlays.applyPreferredSubtitleSelection(player, this)
        updateSubtitleButtonTint()
        playerView.post {
            playerView.player = player
            playerView.setShutterBackgroundColor(Color.TRANSPARENT)
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_shutter)?.visibility = View.GONE
        }
        playerView.keepScreenOn = player.isPlaying
        renderedFirstFrame = player.playbackState == Player.STATE_READY ||
            player.playbackState == Player.STATE_BUFFERING ||
            player.isPlaying ||
            player.currentPosition > 0L ||
            (player.videoSize.width > 0 && player.videoSize.height > 0)
        updatePipParams()
        renderState(PlaybackController.state.value)
    }

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, playerView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isInPictureInPictureMode) setupImmersiveMode()
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
        showUnifiedFeedback(
            iconRes = R.drawable.ic_aspect_ratio,
            text = getString(label),
            progress = null,
            timeoutMs = 1500L
        )
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
        this.locked = locked
        playerView.useController = !locked
        lockedUnlockBtn?.isVisible = locked
        playerView.findViewById<ImageButton>(R.id.video_lock)?.setImageResource(
            if (locked) R.drawable.ic_lock else R.drawable.ic_unlock
        )
        if (locked) {
            playerView.hideController()
        } else {
            playerView.showController()
        }
        showUnifiedFeedback(
            iconRes = if (locked) R.drawable.ic_lock else R.drawable.ic_unlock,
            text = getString(if (locked) R.string.lock_controls else R.string.unlock_controls),
            progress = null,
            timeoutMs = 1500L
        )
        updatePipParams()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, R.string.picture_in_picture, Toast.LENGTH_SHORT).show()
            return
        }
        enterPipInternal()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPipInternal() {
        val params = buildPipParams() ?: return
        runCatching { enterPictureInPictureMode(params) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(): PictureInPictureParams? {
        if (isFinishing) return null
        val size = attachedPlayer?.videoSize
        val width = size?.width?.takeIf { it > 0 } ?: 16
        val height = size?.height?.takeIf { it > 0 } ?: 9
        var ratio = Rational(width, height)
        if (ratio.toFloat() > 2.39f) ratio = Rational(239, 100)
        if (ratio.toFloat() < 0.4184f) ratio = Rational(4184, 10000)

        val builder = PictureInPictureParams.Builder().setAspectRatio(ratio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(canEnterPip())
        }
        return builder.build()
    }

    private fun canEnterPip(): Boolean =
        attachedPlayer?.isPlaying == true && !locked && !isFinishing

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isFinishing) return
        runCatching { setPictureInPictureParams(buildPipParams() ?: return) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S &&
            canEnterPip()
        ) {
            enterPipInternal()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            levelIndicatorView?.visibility = View.GONE
            speedBoostContainer?.visibility = View.GONE
            seekPreview.visibility = View.GONE
            playerView.useController = false
        } else {
            playerView.useController = !locked
            setupImmersiveMode()
        }
    }

    // --- Tap-and-Hold 2X Speed Boost ---
    private fun startSpeedBoost() {
        val player = attachedPlayer ?: return
        isSpeedBoostActive = true
        levelIndicatorView?.visibility = View.GONE
        originalPlaybackSpeed = player.playbackParameters.speed.takeIf { it > 0f } ?: 1.0f

        currentBoostIndex = 4 // Default to 2.0x
        speedBoostStartIndex = 4
        val speed = boostSpeeds[currentBoostIndex]
        player.playbackParameters = PlaybackParameters(speed, 1.0f)

        speedBoostContainer?.visibility = View.VISIBLE
        speedBoostTicksRow?.visibility = View.VISIBLE
        updateSpeedBoostUI(speed)

        uiHandler.removeCallbacks(collapseSpeedBarRunnable)
        uiHandler.postDelayed(collapseSpeedBarRunnable, 1500) // Collapses in 1.5s per user request

        runCatching {
            playerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun updateSpeedBoostUI(speed: Float) {
        speedBoostLabel?.text = "${speed}x Speed Playing"

        boostSpeedTextViewIds.forEachIndexed { index, viewId ->
            val tv = findViewById<TextView>(viewId) ?: return@forEachIndexed
            val isSelected = index == currentBoostIndex
            tv.setTextColor(if (isSelected) Color.parseColor("#2196F3") else Color.parseColor("#B3FFFFFF"))
            tv.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    // --- Unified Top-Center Feedback Pill (Volume, Brightness, Seek, Double-tap, etc.) ---
    private fun showUnifiedFeedback(
        iconRes: Int?,
        text: String,
        progress: Int? = null,
        maxProgress: Int = 100,
        timeoutMs: Long = 1500L
    ) {
        if (isSpeedBoostActive) return
        iconRes?.let { levelIcon?.setImageResource(it) }
        levelText?.text = text
        if (progress != null) {
            levelProgress?.visibility = View.VISIBLE
            levelProgress?.max = maxProgress
            levelProgress?.progress = progress
        } else {
            levelProgress?.visibility = View.GONE
        }
        levelIndicatorView?.visibility = View.VISIBLE

        uiHandler.removeCallbacks(hideLevelIndicatorRunnable)
        uiHandler.postDelayed(hideLevelIndicatorRunnable, timeoutMs)
    }

    // --- Volume & Brightness Indicator Bar at top-center (MX Player / Material 3 style) ---
    private fun showLevelIndicator(isVolume: Boolean, level: Int, maxLevel: Int) {
        val percent = ((level.toFloat() / maxLevel.coerceAtLeast(1)) * 100).roundToInt().coerceIn(0, 100)
        if (isVolume) {
            val iconRes = when {
                percent == 0 -> R.drawable.ic_volume_mute
                percent <= 33 -> R.drawable.ic_volume_low
                percent <= 66 -> R.drawable.ic_volume_mid
                else -> R.drawable.ic_volume_high
            }
            showUnifiedFeedback(
                iconRes = iconRes,
                text = "$percent",
                progress = percent,
                maxProgress = 100,
                timeoutMs = 1500L
            )
        } else {
            showBrightnessFeedback(percent)
        }
    }

    /**
     * Shows the brightness icon with a radial fill that tracks the exact
     * percentage: empty at 0%, quarter at 25%, half at 50%, three quarters at
     * 75% and completely filled at 100%.
     */
    private fun showBrightnessFeedback(percent: Int) {
        if (isSpeedBoostActive) return
        val iconView = levelIcon ?: return
        val meter = brightnessMeterDrawable
            ?: BrightnessMeterDrawable(iconView.context).also { brightnessMeterDrawable = it }
        if (iconView.drawable !== meter) {
            iconView.setImageDrawable(meter)
        }
        meter.setPercent(percent, animate = true)
        showUnifiedFeedback(
            iconRes = null,
            text = "$percent%",
            progress = percent,
            maxProgress = 100,
            timeoutMs = 1500L
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(event: MotionEvent): Boolean {
        if (locked) return true

        // Scale gesture detector for pinch-to-zoom
        scaleGestureDetector.onTouchEvent(event)
        if (scaleGestureDetector.isInProgress || isScaling || event.pointerCount >= 2) {
            gestureMode = GestureMode.NONE
            doubleTapPending = false
            lastTapUpTime = 0L
            uiHandler.removeCallbacks(speedBoostRunnable)
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
                    uiHandler.removeCallbacks(speedBoostRunnable)
                    return true
                }
                gestureMode = GestureMode.NONE
                gestureStartPosition = attachedPlayer?.currentPosition ?: 0L
                val currentBrightness = window.attributes.screenBrightness
                    .takeIf { it >= 0f } ?: 0.5f
                gestureStartBrightness = currentBrightness
                gestureStartBrightnessPercent = currentBrightness * 100f

                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                gestureStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                gestureStartVolumePercent = (gestureStartVolume.toFloat() / maxVolume) * 100f

                // Schedule speed boost if tap and hold on the right side
                if (downX >= playerView.width * 0.40f) {
                    uiHandler.removeCallbacks(speedBoostRunnable)
                    uiHandler.postDelayed(speedBoostRunnable, 400)
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (doubleTapPending) return true

                val dx = event.x - downX
                val dy = event.y - downY

                // If speed boost active: slide horizontally to change speed
                if (isSpeedBoostActive) {
                    val deltaX = event.x - speedBoostStartX
                    val stepPx = 45f * resources.displayMetrics.density
                    val stepOffset = (deltaX / stepPx).toInt()
                    val targetIndex = (speedBoostStartIndex + stepOffset).coerceIn(0, boostSpeeds.lastIndex)
                    if (targetIndex != currentBoostIndex) {
                        currentBoostIndex = targetIndex
                        val newSpeed = boostSpeeds[currentBoostIndex]
                        attachedPlayer?.playbackParameters = PlaybackParameters(newSpeed, 1.0f)
                        updateSpeedBoostUI(newSpeed)

                        speedBoostTicksRow?.visibility = View.VISIBLE
                        uiHandler.removeCallbacks(collapseSpeedBarRunnable)
                        uiHandler.postDelayed(collapseSpeedBarRunnable, 1500)

                        runCatching {
                            playerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                    return true
                }

                // If moving beyond slop: cancel speed boost long-press timer
                if (max(abs(dx), abs(dy)) > touchSlop) {
                    uiHandler.removeCallbacks(speedBoostRunnable)
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
                        setScreenBrightness(targetPercent / 100f)
                        showLevelIndicator(
                            isVolume = false,
                            level = targetPercent,
                            maxLevel = 100
                        )
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
                        showLevelIndicator(
                            isVolume = true,
                            level = targetPercent,
                            maxLevel = 100
                        )
                        return true
                    }

                    GestureMode.NONE -> return false
                }
            }

            MotionEvent.ACTION_UP -> {
                uiHandler.removeCallbacks(speedBoostRunnable)

                // If releasing speed boost: restore speed and hide bar
                if (isSpeedBoostActive) {
                    isSpeedBoostActive = false
                    uiHandler.removeCallbacks(collapseSpeedBarRunnable)
                    attachedPlayer?.playbackParameters = PlaybackParameters(originalPlaybackSpeed, 1.0f)
                    speedBoostContainer?.visibility = View.GONE
                    runCatching {
                        playerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    return true
                }

                val handled = doubleTapPending || gestureMode != GestureMode.NONE
                if (doubleTapPending) {
                    handleDoubleTap(event.x)
                    doubleTapPending = false
                } else if (gestureMode == GestureMode.SEEK) {
                    attachedPlayer?.seekTo(pendingSeekMs)
                    updateDurationDisplay()
                    scheduleHideHints()
                } else if (gestureMode != GestureMode.NONE) {
                    scheduleHideHints()
                }
                gestureMode = GestureMode.NONE
                lastTapUpTime = SystemClock.uptimeMillis()
                lastTapX = event.x
                return handled
            }

            MotionEvent.ACTION_CANCEL -> {
                uiHandler.removeCallbacks(speedBoostRunnable)
                if (isSpeedBoostActive) {
                    isSpeedBoostActive = false
                    uiHandler.removeCallbacks(collapseSpeedBarRunnable)
                    attachedPlayer?.playbackParameters = PlaybackParameters(originalPlaybackSpeed, 1.0f)
                    speedBoostContainer?.visibility = View.GONE
                }
                doubleTapPending = false
                gestureMode = GestureMode.NONE
                scheduleHideHints()
                return false
            }
        }
        return false
    }

    private fun handleDoubleTap(x: Float) {
        val player = attachedPlayer ?: return
        val width = playerView.width.coerceAtLeast(1)
        val duration = currentDuration().coerceAtLeast(1L)
        when {
            x < width * 0.35f -> {
                val newPos = (player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L)
                player.seekTo(newPos)
                val progressPercent = ((newPos.toFloat() / duration) * 100).roundToInt().coerceIn(0, 100)
                showUnifiedFeedback(
                    iconRes = R.drawable.ic_lyrics_rewind,
                    text = "-10s",
                    progress = progressPercent,
                    timeoutMs = 1500L
                )
                runCatching {
                    playerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
            x > width * 0.65f -> {
                val newPos = (player.currentPosition + SEEK_STEP_MS).coerceAtMost(duration)
                player.seekTo(newPos)
                val progressPercent = ((newPos.toFloat() / duration) * 100).roundToInt().coerceIn(0, 100)
                showUnifiedFeedback(
                    iconRes = R.drawable.ic_lyrics_forward,
                    text = "+10s",
                    progress = progressPercent,
                    timeoutMs = 1500L
                )
                runCatching {
                    playerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
            else -> PlaybackController.togglePlayPause()
        }
    }

    private fun showSeekPreview() {
        val duration = currentDuration().coerceAtLeast(1L)
        val progressPercent = ((pendingSeekMs.toFloat() / duration) * 100).roundToInt().coerceIn(0, 100)
        val iconRes = if (pendingSeekMs >= gestureStartPosition) R.drawable.ic_lyrics_forward else R.drawable.ic_lyrics_rewind
        showUnifiedFeedback(
            iconRes = iconRes,
            text = "${pendingSeekMs.toStringTimeStamp()} / ${duration.toStringTimeStamp()}",
            progress = progressPercent,
            timeoutMs = 1500L
        )
    }

    private fun showPreviewText(text: CharSequence) {
        showUnifiedFeedback(
            iconRes = R.drawable.ic_aspect_ratio,
            text = text.toString(),
            progress = null,
            timeoutMs = 1500L
        )
    }

    private fun scheduleHideHints() {
        uiHandler.removeCallbacks(hideHints)
        uiHandler.postDelayed(hideHints, 1500)
    }

    private fun setScreenBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }

    private fun currentDuration(): Long {
        val player = attachedPlayer
        val duration = player?.duration ?: 0L
        if (duration > 0L) return duration
        return PlaybackController.state.value.currentItem?.durationMs ?: 0L
    }

    override fun onStart() {
        super.onStart()
        val controller = PlaybackController.player()
        if (controller != null) {
            attachPlayer(controller)
        }
    }

    override fun onStop() {
        super.onStop()
        attachedPlayer?.removeListener(playerListener)
        playerView.player = null
        attachedPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!backgroundPlayEnabled && !isInPictureInPictureMode) {
            PlaybackController.stop()
        }
        uiHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val SEEK_WINDOW_MS = 60_000L
        private const val SEEK_STEP_MS = 10_000L
        private const val DOUBLE_TAP_TIMEOUT_MS = 300L
    }

    private enum class GestureMode {
        NONE, SEEK, BRIGHTNESS, VOLUME
    }
}
