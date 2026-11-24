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
import android.widget.TextView
import android.widget.Toast
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import androidx.media3.ui.TimeBar
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.playback.chapters.ChapterRepository
import com.deniscerri.ytdl.playback.chapters.SponsorSegment
import com.deniscerri.ytdl.playback.model.PlaybackExtrasCodec
import com.deniscerri.ytdl.playback.model.PlaybackSubtitle
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.compose.play.video.VideoPlayerScreen
import com.deniscerri.ytdl.ui.compose.play.video.VideoPlayerUiState
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
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
    private var uiState by mutableStateOf(VideoPlayerUiState())
    private var isPlayerViewInitialized = false

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

    private val speedBoostRunnable = Runnable {
        startSpeedBoost()
    }

    private val collapseSpeedBarRunnable = Runnable {
        uiState = uiState.copy(showBoostTicks = false)
    }

    private val hideLevelIndicatorRunnable = Runnable {
        uiState = uiState.copy(levelVisible = false)
    }

    private val hideHints = Runnable {
        uiState = uiState.copy(seekPreviewText = null)
    }

    private val hideScrubPreviewRunnable = Runnable {
        if (!isScrubbingSeekBar) hideScrubPreview()
    }

    private val liveSeekRunnable = Runnable {
        if (isScrubbingSeekBar && !miniScrubPreview) {
            lastLiveSeekElapsedMs = SystemClock.elapsedRealtime()
            attachedPlayer?.seekTo(pendingLiveSeekPosMs.coerceAtLeast(0L))
        }
    }

    private val sponsorSkipRunnable = object : Runnable {
        override fun run() {
            maybeSkipSponsorSegment()
            uiHandler.postDelayed(this, SPONSOR_CHECK_INTERVAL_MS)
        }
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

    private var appliedSubPrefsForMediaId: String? = null

    // Seek-preview stack (frame card + timestamp + chapter) and chapter segmentation
    private lateinit var scrubPreview: View
    private lateinit var scrubPreviewThumb: ImageView
    private lateinit var scrubPreviewThumbCard: View
    private lateinit var scrubPreviewTime: TextView
    private lateinit var scrubPreviewChapter: TextView
    private lateinit var scrubPreviewFull: ImageView
    private lateinit var chapterTimeBar: ChapterTimeBar
    private lateinit var scrubFrameLoader: ScrubFrameLoader
    private var isScrubbingSeekBar = false
    private var controllerVisible = false
    private var pendingPreviewFraction = 0.5f
    private var lastLiveSeekElapsedMs = 0L
    private var pendingLiveSeekPosMs = 0L
    private var lastControllerPingElapsedMs = 0L
    private var scrubMediaId: String? = null
    // Chapter the seekbar drag currently sits in, so crossing a boundary ticks only once.
    private var scrubbedChapterIndex = -1
    // False (default): MX Player-style live video scrubbing on main surface; True: Mini Preview Card.
    private var miniScrubPreview = false
    private var resumeAfterScrub = false

    // SponsorBlock-style segments detected from embedded chapters
    private var sponsorSegments: List<SponsorSegment> = emptyList()
    private var sponsorSegmentsMediaId: String? = null
    private var lastSponsorSkipAt = 0L

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
        if (!::playerView.isInitialized) return
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
            // The preview pauses playback on purpose; do not flip the button or drop the
            // screen-on flag while the user is still dragging.
            val heldForPreview = isScrubbingSeekBar && resumeAfterScrub
            if (::playerView.isInitialized) {
                playerView.keepScreenOn = isPlaying || heldForPreview
                if (!heldForPreview) {
                    playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.setImageResource(
                        if (isPlaying) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play
                    )
                }
            }
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
                if (prefs().getBoolean(PREF_SCRUB_PREVIEW_MINI, false)) {
                    val uri = PlaybackController.state.value.currentItem?.localUri
                    val durationMs = currentDuration()
                    if (!uri.isNullOrBlank() && durationMs > 0L) {
                        scrubFrameLoader.prewarm(uri, durationMs)
                    }
                }
            } else if (playbackState == Player.STATE_BUFFERING) {
                renderState(PlaybackController.state.value)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    VideoPlayerScreen(
                        state = uiState,
                        player = attachedPlayer ?: PlaybackController.player(),
                        onPlayerReady = { pv ->
                            if (!isPlayerViewInitialized) {
                                isPlayerViewInitialized = true
                                initPlayerView(pv)
                            }
                        },
                        onRetryClick = { retryPlayback() },
                        onUnlockClick = { setLocked(false) },
                    )
                }
            }
        }
        setContentView(composeView)
        PlaybackController.init(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExit()
            }
        })
    }

    private fun initPlayerView(pv: PlayerView) {
        playerView = pv

        // Seek-preview stack lives inside the player's control overlay so it fades with the HUD
        scrubPreview = playerView.findViewById(R.id.video_scrub_preview)
        scrubPreviewThumb = playerView.findViewById(R.id.video_scrub_thumb)
        scrubPreviewThumbCard = playerView.findViewById(R.id.video_scrub_thumb_card)
        scrubPreviewTime = playerView.findViewById(R.id.video_scrub_time)
        scrubPreviewChapter = playerView.findViewById(R.id.video_scrub_chapter)
        scrubPreviewFull = playerView.findViewById(R.id.video_scrub_full)
        chapterTimeBar = playerView.findViewById(R.id.exo_progress)
        scrubFrameLoader = ScrubFrameLoader(this)
        // The stack changes height between mini card and pill-only modes; re-place it once the
        // new size is known so the pills stay glued to the seekbar.
        scrubPreview.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (isScrubbingSeekBar) positionScrubPreview(pendingPreviewFraction)
        }
        bindSeekPreview()

        playerView.setShutterBackgroundColor(Color.TRANSPARENT)
        playerView.setControllerShowTimeoutMs(3500)

        // Bind custom control buttons in exo_player_control_view
        bindCustomControls()

        // Synchronize top and bottom controls visibility together
        controllerVisible = playerView.isControllerFullyVisible
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            val isVisible = visibility == View.VISIBLE
            controllerVisible = isVisible
            playerView.findViewById<View>(R.id.video_top_bar)?.visibility = if (isVisible) View.VISIBLE else View.GONE
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)?.visibility = if (isVisible) View.VISIBLE else View.GONE
            if (isVisible) {
                updateDurationDisplay()
            }
            refreshChapterBarVisibility()
        })

        playerView.setOnTouchListener { _, event -> handleTouch(event) }
        setupImmersiveMode()
        observePlayback()
        attachedPlayer?.let { attachPlayer(it) }
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
        playerView.findViewById<View>(R.id.video_chapter_bar)?.setOnClickListener { openChapters() }

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
        if (!::playerView.isInitialized) return
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

    // --- Seekbar scrubbing preview -----------------------------------------------------------

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(this)

    private fun bindSeekPreview() {
        chapterTimeBar.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                isScrubbingSeekBar = true
                uiHandler.removeCallbacks(hideScrubPreviewRunnable)
                uiHandler.removeCallbacks(liveSeekRunnable)
                // Seed the chapter we start in so the first tick only fires on a real crossing.
                scrubbedChapterIndex = PlaybackExtrasCodec.findActiveChapterIndex(
                    PlaybackController.state.value.chapters,
                    position
                )
                miniScrubPreview = prefs().getBoolean(PREF_SCRUB_PREVIEW_MINI, false)
                scrubFrameLoader.setUserScrubbing(true)

                val state = PlaybackController.state.value
                val item = state.currentItem
                if (item?.mediaId != scrubMediaId) {
                    scrubMediaId = item?.mediaId
                    scrubPreviewThumb.setImageDrawable(null)
                }

                if (miniScrubPreview) {
                    val durationMs = currentDuration()
                    if (!item?.localUri.isNullOrBlank() && durationMs > 0L) {
                        scrubFrameLoader.prewarm(item.localUri, durationMs)
                    }
                } else {
                    // Full-screen live video scrubbing: hide bitmap overlay & seek ExoPlayer keyframes live.
                    scrubPreviewFull.visibility = View.GONE
                    holdPlaybackForPreview()
                    PlaybackController.setScrubSeekMode(closestSync = true)
                    lastLiveSeekElapsedMs = SystemClock.elapsedRealtime()
                    pendingLiveSeekPosMs = position
                    attachedPlayer?.seekTo(position.coerceAtLeast(0L))
                }
                showScrubPreview(position)
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                if (!isScrubbingSeekBar) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastControllerPingElapsedMs >= 300L) {
                    lastControllerPingElapsedMs = now
                    playerView.showController()
                }
                showScrubPreview(position)
                if (!miniScrubPreview) {
                    scheduleLiveSeek(position, now)
                }
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                isScrubbingSeekBar = false
                scrubbedChapterIndex = -1
                scrubFrameLoader.setUserScrubbing(false)
                uiHandler.removeCallbacks(liveSeekRunnable)
                if (!miniScrubPreview) {
                    PlaybackController.setScrubSeekMode(closestSync = false)
                    if (!canceled) {
                        attachedPlayer?.seekTo(position.coerceAtLeast(0L))
                    }
                }
                releasePlaybackAfterPreview()
                uiHandler.removeCallbacks(hideScrubPreviewRunnable)
                uiHandler.postDelayed(hideScrubPreviewRunnable, 140L)
            }
        })
    }

    private fun scheduleLiveSeek(positionMs: Long, nowMs: Long) {
        pendingLiveSeekPosMs = positionMs
        val elapsed = nowMs - lastLiveSeekElapsedMs
        if (elapsed >= LIVE_SEEK_THROTTLE_MS) {
            uiHandler.removeCallbacks(liveSeekRunnable)
            lastLiveSeekElapsedMs = nowMs
            attachedPlayer?.seekTo(positionMs.coerceAtLeast(0L))
        } else {
            uiHandler.removeCallbacks(liveSeekRunnable)
            uiHandler.postDelayed(liveSeekRunnable, LIVE_SEEK_THROTTLE_MS - elapsed)
        }
    }

    /**
     * Full-player preview: pause so the frame under the overlay is the seeked still, and so the
     * read-ahead does not compete with dragging. Playback resumes on release.
     */
    private fun holdPlaybackForPreview() {
        val player = attachedPlayer ?: return
        resumeAfterScrub = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        if (resumeAfterScrub) player.pause()
    }

    private fun releasePlaybackAfterPreview() {
        if (!resumeAfterScrub) return
        resumeAfterScrub = false
        attachedPlayer?.play()
    }

    /**
     * One short tick each time the drag enters a different chapter, the way YouTube signals a
     * chapter snap. Fires from the same place that updates the preview's chapter label, so the
     * tick always lines up with the title changing.
     */
    private fun tickChapterBoundary() {
        chapterTimeBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun showScrubPreview(positionMs: Long) {
        val duration = currentDuration()
        if (duration <= 0L) return
        val fraction = (positionMs.toFloat() / duration).coerceIn(0f, 1f)

        scrubPreview.visibility = View.VISIBLE
        // Mini mode floats the thumbnail card above the thumb; OFF hides the card and shows live video.
        scrubPreviewThumbCard.isVisible = miniScrubPreview
        scrubPreviewFull.visibility = View.GONE
        scrubPreviewTime.text = PlaybackExtrasCodec.formatTimestampMs(positionMs)

        val state = PlaybackController.state.value
        val chapters = state.chapters
        val activeIndex = PlaybackExtrasCodec.findActiveChapterIndex(chapters, positionMs)
        if (activeIndex != scrubbedChapterIndex) {
            scrubbedChapterIndex = activeIndex
            if (activeIndex >= 0) tickChapterBoundary()
        }
        if (activeIndex in chapters.indices) {
            scrubPreviewChapter.text = chapters[activeIndex].title
            scrubPreviewChapter.visibility = View.VISIBLE
        } else {
            scrubPreviewChapter.visibility = View.GONE
        }

        // Synchronous X/Y tracking on every touch/drag event (zero async delay).
        pendingPreviewFraction = fraction
        positionScrubPreview(fraction)

        if (miniScrubPreview) {
            scrubFrameLoader.request(
                localUri = state.currentItem?.localUri,
                positionMs = positionMs,
                durationMs = duration
            ) { bitmap ->
                // Retain the last decoded Bitmap until a newer frame arrives (never flash empty/black).
                if (isScrubbingSeekBar && miniScrubPreview && bitmap != null && !bitmap.isRecycled) {
                    scrubPreviewThumb.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun positionScrubPreview(fraction: Float) {
        val parent = scrubPreview.parent as? View ?: return
        if (parent.width <= 0 || scrubPreview.width <= 0) return

        val bar = chapterTimeBar
        val usableWidth = (bar.width - bar.paddingLeft - bar.paddingRight).coerceAtLeast(1)
        val thumbX = bar.paddingLeft + fraction.coerceIn(0f, 1f) * usableWidth

        var x = thumbX
        var y = 0f
        var view: View? = bar
        while (view != null && view !== parent) {
            x += view.left + view.translationX
            y += view.top + view.translationY
            view = view.parent as? View
        }

        val margin = 8f * resources.displayMetrics.density
        val maxX = (parent.width - scrubPreview.width - margin).coerceAtLeast(margin)
        scrubPreview.x = (x - scrubPreview.width / 2f).coerceIn(margin, maxX)

        val gap = 12f * resources.displayMetrics.density
        scrubPreview.y = (y - gap - scrubPreview.height).coerceAtLeast(margin)
    }

    private fun hideScrubPreview() {
        val wasScrubbing = isScrubbingSeekBar
        isScrubbingSeekBar = false
        scrubFrameLoader.setUserScrubbing(false)
        uiHandler.removeCallbacks(hideScrubPreviewRunnable)
        uiHandler.removeCallbacks(liveSeekRunnable)
        if (wasScrubbing && !miniScrubPreview) {
            PlaybackController.setScrubSeekMode(closestSync = false)
        }
        if (::scrubPreview.isInitialized) scrubPreview.visibility = View.INVISIBLE
        if (::scrubPreviewFull.isInitialized) {
            scrubPreviewFull.visibility = View.GONE
            scrubPreviewFull.setImageDrawable(null)
        }
    }

    /** Keeps the chapter pill in lock-step with the rest of the HUD and the lock state. */
    private fun refreshChapterBarVisibility() {
        if (!::playerView.isInitialized) return
        val bar = playerView.findViewById<View>(R.id.video_chapter_bar) ?: return
        val hasChapters = PlaybackController.state.value.chapters.isNotEmpty()
        bar.visibility = if (hasChapters && controllerVisible && !locked) View.VISIBLE else View.GONE
    }

    // --- SponsorBlock auto-skip --------------------------------------------------------------

    private fun trackSponsorSegments(state: PlaybackController.UiState) {
        val item = state.currentItem
        if (item == null) {
            sponsorSegments = emptyList()
            sponsorSegmentsMediaId = null
            return
        }
        if (item.mediaId == sponsorSegmentsMediaId) return
        sponsorSegmentsMediaId = item.mediaId
        sponsorSegments = ChapterRepository.cachedSegments(item.localUri)
        scrubPreviewThumb.setImageDrawable(null)
        val duration = currentDuration()
        scrubFrameLoader.prewarm(item.localUri, duration)

        val localUri = item.localUri
        if (localUri.isNullOrBlank()) return
        lifecycleScope.launch {
            val loaded = ChapterRepository.load(this@VideoPlayerActivity, localUri).sponsorSegments
            if (PlaybackController.state.value.currentItem?.mediaId == item.mediaId) {
                sponsorSegments = loaded
            }
        }
    }

    private fun maybeSkipSponsorSegment() {
        if (sponsorSegments.isEmpty()) return
        val player = attachedPlayer ?: return
        if (!player.isPlaying) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(PREF_USE_SPONSORBLOCK, true)) return

        val position = player.currentPosition
        val segment = sponsorSegments.firstOrNull { pos ->
            position >= pos.startTimeMs && position < pos.endTimeMs - SPONSOR_SKIP_TAIL_MS
        } ?: return

        val now = SystemClock.elapsedRealtime()
        if (now - lastSponsorSkipAt < SPONSOR_SKIP_GUARD_MS) return
        lastSponsorSkipAt = now
        player.seekTo(segment.endTimeMs)
        showUnifiedFeedback(
            iconRes = R.drawable.ic_chapter_list,
            text = getString(R.string.sponsorblock),
            progress = null,
            timeoutMs = 1400L
        )
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
        val item = state.currentItem
        val player = attachedPlayer
        val isReadyOrPlaying = player != null && (
            player.playbackState == Player.STATE_READY ||
            player.isPlaying ||
            player.currentPosition > 0L ||
            (player.videoSize.width > 0 && player.videoSize.height > 0)
        )

        if (item != null && item.mediaId != posterMediaId) {
            posterMediaId = item.mediaId
            renderedFirstFrame = isReadyOrPlaying
        } else if (isReadyOrPlaying) {
            renderedFirstFrame = true
        }

        val isBuffering = player != null && player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
        val loading = state.error == null && (!renderedFirstFrame || isBuffering)
        uiState = uiState.copy(
            artworkUri = item?.artworkUri,
            showPoster = !renderedFirstFrame && !item?.artworkUri.isNullOrBlank(),
            isLoading = loading,
            showLoadingText = !renderedFirstFrame,
            errorMessage = state.error
        )

        if (!::playerView.isInitialized) return

        val durationMs = state.durationMs.takeIf { it > 0L } ?: currentDuration()
        VideoPlayerOverlays.updateChapterUi(
            playerView = playerView,
            chapters = state.chapters,
            positionMs = currentPlaybackPositionMs(),
            durationMs = durationMs,
            controlsVisible = controllerVisible && !locked
        )
        trackSponsorSegments(state)
        updateSubtitleButtonTint()
        updateDurationDisplay()
        if (item == null) return

        // Set video title
        playerView.findViewById<TextView>(R.id.video_title)?.text = item.title

        // Update single Play/Pause button icon
        val isPlayingNow = player != null && player.isPlaying
        playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.setImageResource(
            if (isPlayingNow) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play
        )

        if (renderedFirstFrame) {
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_shutter)?.visibility = View.GONE
        }
    }

    private fun retryPlayback() {
        renderedFirstFrame = false
        uiState = uiState.copy(
            errorMessage = null,
            isLoading = true,
            showPoster = !PlaybackController.state.value.currentItem?.artworkUri.isNullOrBlank()
        )
        PlaybackController.retryCurrent()
    }

    private fun attachPlayer(player: Player) {
        attachedPlayer?.removeListener(playerListener)
        attachedPlayer = player
        player.addListener(playerListener)
        if (::playerView.isInitialized) {
            VideoPlayerOverlays.applySubtitleStyle(playerView, this)
            VideoPlayerOverlays.applyPreferredSubtitleSelection(player, this)
            updateSubtitleButtonTint()
            playerView.post {
                playerView.player = player
                playerView.setShutterBackgroundColor(Color.TRANSPARENT)
                playerView.findViewById<View>(androidx.media3.ui.R.id.exo_shutter)?.visibility = View.GONE
            }
            playerView.keepScreenOn = player.isPlaying
        }
        renderedFirstFrame = player.playbackState == Player.STATE_READY ||
            player.isPlaying ||
            player.currentPosition > 0L ||
            (player.videoSize.width > 0 && player.videoSize.height > 0)
        updatePipParams()
        renderState(PlaybackController.state.value)
    }

    private fun setupImmersiveMode() {
        val targetView = if (::playerView.isInitialized) playerView else window.decorView
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, targetView).apply {
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
        uiState = uiState.copy(isLocked = locked)
        playerView.findViewById<ImageButton>(R.id.video_lock)?.setImageResource(
            if (locked) R.drawable.ic_lock else R.drawable.ic_unlock
        )
        if (locked) {
            playerView.hideController()
            hideScrubPreview()
        } else {
            playerView.showController()
        }
        refreshChapterBarVisibility()
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
            uiState = uiState.copy(
                levelVisible = false,
                speedBoostVisible = false,
                seekPreviewText = null
            )
            hideScrubPreview()
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
        uiState = uiState.copy(levelVisible = false)
        originalPlaybackSpeed = player.playbackParameters.speed.takeIf { it > 0f } ?: 1.0f

        currentBoostIndex = 4 // Default to 2.0x
        speedBoostStartIndex = 4
        val speed = boostSpeeds[currentBoostIndex]
        player.playbackParameters = PlaybackParameters(speed, 1.0f)

        uiState = uiState.copy(
            speedBoostVisible = true,
            showBoostTicks = true,
            currentBoostIndex = currentBoostIndex,
            speedBoostLabel = "${speed}x Speed Playing"
        )

        uiHandler.removeCallbacks(collapseSpeedBarRunnable)
        uiHandler.postDelayed(collapseSpeedBarRunnable, 1500) // Collapses in 1.5s per user request

        runCatching {
            playerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun updateSpeedBoostUI(speed: Float) {
        uiState = uiState.copy(
            speedBoostVisible = true,
            currentBoostIndex = currentBoostIndex,
            speedBoostLabel = "${speed}x Speed Playing"
        )
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
        uiState = uiState.copy(
            levelVisible = true,
            levelIconRes = iconRes,
            levelText = text,
            levelProgress = progress,
            levelMaxProgress = maxProgress
        )

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

    private fun showBrightnessFeedback(percent: Int) {
        if (isSpeedBoostActive) return
        val iconRes = when {
            percent <= 25 -> R.drawable.ic_brightness_low
            percent <= 50 -> R.drawable.ic_brightness_half
            percent <= 75 -> R.drawable.ic_brightness_high
            else -> R.drawable.ic_brightness_full
        }
        showUnifiedFeedback(
            iconRes = iconRes,
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

                        uiState = uiState.copy(showBoostTicks = true)
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
                    uiState = uiState.copy(speedBoostVisible = false)
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
                    uiState = uiState.copy(speedBoostVisible = false)
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
        uiHandler.removeCallbacks(sponsorSkipRunnable)
        uiHandler.postDelayed(sponsorSkipRunnable, SPONSOR_CHECK_INTERVAL_MS)
    }

    override fun onStop() {
        super.onStop()
        uiHandler.removeCallbacks(sponsorSkipRunnable)
        releasePlaybackAfterPreview()
        hideScrubPreview()
        attachedPlayer?.removeListener(playerListener)
        if (::playerView.isInitialized) {
            playerView.player = null
        }
        attachedPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacks(sponsorSkipRunnable)
        scrubFrameLoader.release()
        if (!backgroundPlayEnabled && !isInPictureInPictureMode) {
            PlaybackController.stop()
        }
        uiHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val SEEK_WINDOW_MS = 60_000L
        private const val SEEK_STEP_MS = 10_000L
        private const val DOUBLE_TAP_TIMEOUT_MS = 300L
        /** Existing SponsorBlock toggle; embedded segments are only skipped while it is on. */
        private const val PREF_USE_SPONSORBLOCK = "use_sponsorblock"
        /** Off = MX Player-style live video scrubbing, on = floating mini preview card. */
        private const val PREF_SCRUB_PREVIEW_MINI = "scrub_preview_mini"
        /** Throttle interval for live ExoPlayer.seekTo() during full-screen scrubbing. */
        private const val LIVE_SEEK_THROTTLE_MS = 45L
        /** Leave a tail so the segment's last frame is not swallowed by the seek. */
        private const val SPONSOR_SKIP_TAIL_MS = 350L
        /** Debounce so a slow seek cannot retrigger inside the same segment. */
        private const val SPONSOR_SKIP_GUARD_MS = 1_200L
        private const val SPONSOR_CHECK_INTERVAL_MS = 750L
    }

    private enum class GestureMode {
        NONE, SEEK, BRIGHTNESS, VOLUME
    }
}
