package com.deniscerri.ytdl.ui.play

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.LikedStore
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackSource
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.play.lyrics.LyricsSheetFragment
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.deniscerri.ytdl.util.PlaybackColors
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.platform.MaterialArcMotion
import com.google.android.material.transition.platform.MaterialContainerTransform
import kotlinx.coroutines.launch
import kotlin.math.abs

class AudioPlayerActivity : BaseActivity() {

    private lateinit var seekBar: WavySeekBar
    private lateinit var likeButton: MaterialButton

    private var currentItem: PlaybackItem? = null
    private var isUserSeeking = false
    private var speedIndex = 0
    private var contentBottomPadding = 0
    private var coloredForMediaId: String? = null
    private var showRemainingTime = false
    private var currentDominantColor: Int? = null
    private var lastPlayingState: Boolean? = null
    private var lastMediaId: String? = null

    private val speeds = listOf(1.0f, 1.25f, 1.5f, 0.75f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val enterShared = MaterialContainerTransform().apply {
            addTarget(R.id.audio_art)
            duration = 340L
            scrimColor = Color.TRANSPARENT
            setPathMotion(MaterialArcMotion())
            interpolator = DecelerateInterpolator(1.8f)
        }
        val returnShared = MaterialContainerTransform().apply {
            addTarget(R.id.audio_art)
            duration = 320L
            scrimColor = Color.TRANSPARENT
            setPathMotion(MaterialArcMotion())
            interpolator = DecelerateInterpolator(1.8f)
        }
        window.sharedElementEnterTransition = enterShared
        window.sharedElementReturnTransition = returnShared

        val fadeTransition = android.transition.Fade().apply {
            duration = 240L
            excludeTarget(R.id.audio_art, true)
            excludeTarget(android.R.id.statusBarBackground, true)
            excludeTarget(android.R.id.navigationBarBackground, true)
        }
        window.enterTransition = fadeTransition
        window.returnTransition = fadeTransition

        setContentView(R.layout.activity_audio_player)
        PlaybackController.init(this)
        LikedStore.init(this)

        postponeEnterTransition()
        val artView = findViewById<ImageView>(R.id.audio_art)
        val initialItem = PlaybackController.state.value.currentItem
        if (initialItem != null) {
            PlaybackArtworkLoader.load(artView, initialItem)
        }
        artView.post {
            startPostponedEnterTransition()
        }

        findViewById<View>(R.id.audio_back).setOnClickListener {
            supportFinishAfterTransition()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val sheet = supportFragmentManager.findFragmentByTag(LYRICS_SHEET_TAG) as? LyricsSheetFragment
                if (sheet != null && sheet.isVisible) {
                    sheet.dismiss()
                } else {
                    supportFinishAfterTransition()
                }
            }
        })

        seekBar = findViewById(R.id.audio_seek)
        likeButton = findViewById(R.id.audio_like)

        val durationView = findViewById<TextView>(R.id.audio_duration)
        durationView.setOnClickListener {
            showRemainingTime = !showRemainingTime
            updateDurationDisplay()
        }

        val content = findViewById<View>(R.id.audio_content)
        val header = findViewById<View>(R.id.audio_header)
        contentBottomPadding = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.audio_root)) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val density = resources.displayMetrics.density
            header.updatePadding(top = statusBars + (6 * density).toInt())
            content.updatePadding(
                top = statusBars + (60 * density).toInt(),
                bottom = contentBottomPadding + navigationBars
            )
            insets
        }

        bindControls()
        bindArtworkGestures()
        observePlayback()
    }

    /** Fluid horizontal swipes on the artwork skip to previous/next track with physics and animation. */
    private fun bindArtworkGestures() {
        val artworkCard = findViewById<View>(R.id.audio_artwork_card)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var startX = 0f
        var startY = 0f
        var isDragging = false
        var velocityTracker: VelocityTracker? = null

        artworkCard.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isDragging = false
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY

                    if (!isDragging && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.15f) {
                        isDragging = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    if (isDragging) {
                        val cardWidth = view.width.toFloat().coerceAtLeast(100f)
                        view.translationX = dx * 0.8f
                        view.rotation = (dx / cardWidth) * 7.5f
                        view.alpha = (1f - (abs(dx) / (cardWidth * 2.2f))).coerceIn(0.55f, 1f)
                        val scale = (1f - (abs(dx) / (cardWidth * 7f))).coerceIn(0.93f, 1f)
                        view.scaleX = scale
                        view.scaleY = scale
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val dx = event.rawX - startX
                    val cardWidth = view.width.toFloat().coerceAtLeast(100f)
                    val flingThreshold = 450f * resources.displayMetrics.density
                    val distanceThreshold = cardWidth * 0.28f

                    val isSwipe = isDragging && (abs(vx) > flingThreshold || abs(dx) > distanceThreshold)

                    if (isSwipe) {
                        val isNext = if (abs(vx) > flingThreshold) vx < 0 else dx < 0
                        val exitX = if (isNext) -cardWidth * 1.35f else cardWidth * 1.35f
                        val exitRot = if (isNext) -12f else 12f

                        view.animate()
                            .translationX(exitX)
                            .rotation(exitRot)
                            .scaleX(0.92f)
                            .scaleY(0.92f)
                            .alpha(0f)
                            .setDuration(160)
                            .setInterpolator(AccelerateInterpolator())
                            .withEndAction {
                                if (isNext) {
                                    PlaybackController.skipToNextAudio()
                                } else {
                                    PlaybackController.skipToPreviousAudio()
                                }
                                view.translationX = if (isNext) cardWidth * 1.15f else -cardWidth * 1.15f
                                view.rotation = if (isNext) 10f else -10f
                                view.scaleX = 0.92f
                                view.scaleY = 0.92f
                                view.alpha = 0f
                                view.animate()
                                    .translationX(0f)
                                    .rotation(0f)
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .alpha(1f)
                                    .setDuration(320)
                                    .setInterpolator(OvershootInterpolator(1.25f))
                                    .start()
                            }
                            .start()
                    } else if (isDragging) {
                        view.animate()
                            .translationX(0f)
                            .rotation(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(280)
                            .setInterpolator(OvershootInterpolator(1.35f))
                            .start()
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        view.performClick()
                    }

                    velocityTracker?.recycle()
                    velocityTracker = null
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun bindControls() {
        findViewById<ImageButton>(R.id.audio_play_pause).setOnClickListener {
            val player = PlaybackController.player()
            if (player == null || player.mediaItemCount == 0) {
                PlaybackController.resume()
            } else {
                PlaybackController.togglePlayPause()
            }
        }
        findViewById<ImageButton>(R.id.audio_previous).setOnClickListener {
            PlaybackController.previous()
        }
        findViewById<ImageButton>(R.id.audio_next).setOnClickListener {
            PlaybackController.next()
        }
        findViewById<ImageButton>(R.id.audio_shuffle).setOnClickListener {
            PlaybackController.toggleShuffle()
        }
        findViewById<ImageButton>(R.id.audio_repeat).setOnClickListener {
            PlaybackController.cycleRepeatMode()
        }

        findViewById<MaterialButton>(R.id.audio_speed).setOnClickListener { button ->
            speedIndex = (speedIndex + 1) % speeds.size
            val speed = speeds[speedIndex]
            PlaybackController.setPlaybackSpeed(speed)
            (button as MaterialButton).text = getString(R.string.speed_value, speed)
        }

        likeButton.setOnClickListener {
            val item = currentItem ?: return@setOnClickListener
            LikedStore.toggle(item)
            updateLikeState(item)
        }

        findViewById<MaterialButton>(R.id.audio_lyrics_button).setOnClickListener {
            openLyricsSheet()
        }

        seekBar.onSeekBarChangeListener = object : WavySeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: WavySeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = currentDuration()
                    val pos = ((progress / 1000f) * duration).toLong()
                    findViewById<TextView>(R.id.audio_position).text = pos.toStringTimeStamp()
                    updateDurationDisplay(duration, pos)
                }
            }

            override fun onStartTrackingTouch(seekBar: WavySeekBar) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: WavySeekBar) {
                isUserSeeking = false
                val duration = currentDuration()
                if (duration > 0) {
                    PlaybackController.seekTo(((seekBar.progress / 1000f) * duration).toLong())
                }
            }
        }
    }

    private fun observePlayback() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackController.state.collect { state ->
                    currentItem = state.currentItem
                    bindState(state)
                }
            }
        }
    }

    private fun updateDurationDisplay(
        duration: Long = currentDuration(),
        position: Long = PlaybackController.state.value.positionMs.coerceAtLeast(0L)
    ) {
        val durationView = findViewById<TextView>(R.id.audio_duration) ?: return
        if (showRemainingTime) {
            val remaining = (duration - position).coerceAtLeast(0L)
            durationView.text = "-${remaining.toStringTimeStamp()}"
        } else {
            durationView.text = duration.toStringTimeStamp()
        }
    }

    private fun bindState(state: PlaybackController.UiState) {
        val item = state.currentItem ?: run {
            findViewById<GlowView>(R.id.audio_art_glow).apply {
                setPulsing(false)
                setColor(null)
            }
            applyAmbientBackground(null)
            return
        }
        val isTrackChanged = lastMediaId != null && lastMediaId != item.mediaId
        lastMediaId = item.mediaId

        val artistStr = item.artist?.takeIf { it.isNotBlank() } ?: getString(R.string.unknown_artist)
        if (isTrackChanged) {
            updateMetadataWithSlide(item.title, artistStr)
        } else {
            findViewById<TextView>(R.id.audio_title).text = item.title
            findViewById<TextView>(R.id.audio_artist).text = artistStr
        }
        findViewById<TextView>(R.id.audio_source).text = buildString {
            append(
                when (item.source) {
                    PlaybackSource.YOUTUBE -> getString(R.string.youtube)
                    PlaybackSource.LOCAL -> getString(R.string.audio)
                    PlaybackSource.DOWNLOAD -> getString(R.string.downloads)
                }
            )
            if (item.type == PlaybackType.VIDEO) {
                append(" · ")
                append(getString(R.string.video))
            }
        }

        val duration = currentDuration(state)
        val position = state.positionMs.coerceAtLeast(0L)
        if (!isUserSeeking && duration > 0) {
            seekBar.progress = ((position.toFloat() / duration) * 1000f).toInt().coerceIn(0, 1000)
        }
        seekBar.setPlaying(state.playing)
        findViewById<TextView>(R.id.audio_position).text = position.toStringTimeStamp()
        updateDurationDisplay(duration, position)

        findViewById<ImageButton>(R.id.audio_play_pause).setImageResource(
            if (state.playing) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play
        )
        findViewById<ImageButton>(R.id.audio_play_pause).contentDescription = getString(
            if (state.playing) R.string.pause else R.string.play
        )

        val glow = findViewById<GlowView>(R.id.audio_art_glow)
        glow.setPulsing(state.playing)
        glow.setBeatRate(if (state.playing) 1.8f else 0f)

        applyDynamicControlsTint(currentDominantColor)

        val art = findViewById<ImageView>(R.id.audio_art)
        PlaybackArtworkLoader.load(art, item, crossfade = isTrackChanged)
        updateArtworkPlayState(state.playing, animate = (lastPlayingState != null))

        if (coloredForMediaId != item.mediaId) {
            coloredForMediaId = item.mediaId
            applyGlow(item)
        }

        updateLikeState(item)
    }

    private fun updateArtworkPlayState(isPlaying: Boolean, animate: Boolean = true) {
        if (lastPlayingState == isPlaying) return
        lastPlayingState = isPlaying
        val art = findViewById<ImageView>(R.id.audio_art) ?: return
        val targetScale = if (isPlaying) 1.0f else 0.92f
        val targetElevation = (if (isPlaying) 16f else 4f) * resources.displayMetrics.density

        if (!animate) {
            art.scaleX = targetScale
            art.scaleY = targetScale
            art.translationZ = targetElevation
            return
        }

        val interpolator = if (isPlaying) OvershootInterpolator(1.25f) else DecelerateInterpolator(1.6f)
        val duration = if (isPlaying) 400L else 320L
        art.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .translationZ(targetElevation)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .start()
    }

    private fun updateMetadataWithSlide(title: String, artist: String) {
        val titleView = findViewById<TextView>(R.id.audio_title) ?: return
        val artistView = findViewById<TextView>(R.id.audio_artist) ?: return

        val slideDistance = 32f * resources.displayMetrics.density
        titleView.animate()
            .translationX(-slideDistance)
            .alpha(0f)
            .setDuration(140)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                titleView.text = title
                titleView.translationX = slideDistance
                titleView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()

        artistView.animate()
            .translationX(-slideDistance)
            .alpha(0f)
            .setDuration(140)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                artistView.text = artist
                artistView.translationX = slideDistance
                artistView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun applyDynamicControlsTint(dominantColor: Int?) {
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.BLACK)
        val state = PlaybackController.state.value

        if (dominantColor == null) {
            val unplayedColor = Color.argb(80, Color.red(onSurface), Color.green(onSurface), Color.blue(onSurface))
            seekBar.setColors(playedColor = onSurface, unplayedColor = unplayedColor, thumbColor = onSurface)
            val defaultPillBg = androidx.core.content.ContextCompat.getColor(this, R.color.capsule_time_bg)
            val defaultPillText = androidx.core.content.ContextCompat.getColor(this, R.color.capsule_time_text)
            findViewById<TextView>(R.id.audio_position).apply {
                backgroundTintList = ColorStateList.valueOf(defaultPillBg)
                setTextColor(defaultPillText)
            }
            findViewById<TextView>(R.id.audio_duration).apply {
                backgroundTintList = ColorStateList.valueOf(defaultPillBg)
                setTextColor(defaultPillText)
            }
            return
        }

        // Luminous tint for active seekbar wave and thumb
        val seekbarColor = androidx.core.graphics.ColorUtils.blendARGB(dominantColor, Color.WHITE, 0.35f)
        val unplayedColor = Color.argb(55, Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor))
        seekBar.setColors(
            playedColor = seekbarColor,
            unplayedColor = unplayedColor,
            thumbColor = seekbarColor
        )

        // Glowing timing capsules
        val timeBgColor = androidx.core.graphics.ColorUtils.blendARGB(dominantColor, surfaceColor, 0.76f)
        val timeTextColor = androidx.core.graphics.ColorUtils.blendARGB(dominantColor, Color.WHITE, 0.20f)
        findViewById<TextView>(R.id.audio_position).apply {
            backgroundTintList = ColorStateList.valueOf(timeBgColor)
            setTextColor(timeTextColor)
        }
        findViewById<TextView>(R.id.audio_duration).apply {
            backgroundTintList = ColorStateList.valueOf(timeBgColor)
            setTextColor(timeTextColor)
        }

        // Vibrant pastel container for center play/pause button
        val playBtn = findViewById<ImageButton>(R.id.audio_play_pause)
        val playBgColor = androidx.core.graphics.ColorUtils.blendARGB(dominantColor, Color.WHITE, 0.40f)
        playBtn.backgroundTintList = ColorStateList.valueOf(playBgColor)
        val onPlayColor = if (androidx.core.graphics.ColorUtils.calculateLuminance(playBgColor) > 0.45) {
            androidx.core.graphics.ColorUtils.blendARGB(dominantColor, Color.BLACK, 0.85f)
        } else {
            Color.WHITE
        }
        playBtn.imageTintList = ColorStateList.valueOf(onPlayColor)

        // Subtle ambient tint for the 4 satellite squircle buttons
        val buttonBgColor = androidx.core.graphics.ColorUtils.blendARGB(dominantColor, surfaceColor, 0.80f)
        val iconColor = androidx.core.graphics.ColorUtils.blendARGB(dominantColor, Color.WHITE, 0.70f)

        val shuffleBtn = findViewById<ImageButton>(R.id.audio_shuffle)
        shuffleBtn.backgroundTintList = ColorStateList.valueOf(buttonBgColor)
        shuffleBtn.imageTintList = ColorStateList.valueOf(if (state.shuffle) seekbarColor else iconColor)

        val prevBtn = findViewById<ImageButton>(R.id.audio_previous)
        prevBtn.backgroundTintList = ColorStateList.valueOf(buttonBgColor)
        prevBtn.imageTintList = ColorStateList.valueOf(iconColor)

        val nextBtn = findViewById<ImageButton>(R.id.audio_next)
        nextBtn.backgroundTintList = ColorStateList.valueOf(buttonBgColor)
        nextBtn.imageTintList = ColorStateList.valueOf(iconColor)

        val repeatBtn = findViewById<ImageButton>(R.id.audio_repeat)
        repeatBtn.backgroundTintList = ColorStateList.valueOf(buttonBgColor)
        repeatBtn.setImageResource(
            when (state.repeatMode) {
                androidx.media3.common.Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                androidx.media3.common.Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
                else -> R.drawable.ic_repeat
            }
        )
        repeatBtn.imageTintList = ColorStateList.valueOf(
            if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) iconColor else seekbarColor
        )

        // Action pills: Like, Audio, Lyrics
        findViewById<View>(R.id.audio_like).backgroundTintList = ColorStateList.valueOf(buttonBgColor)
        findViewById<View>(R.id.audio_source).backgroundTintList = ColorStateList.valueOf(buttonBgColor)
        findViewById<View>(R.id.audio_lyrics_button).backgroundTintList = ColorStateList.valueOf(buttonBgColor)
    }

    /** Colors the radial glow behind the artwork card from the artwork's dominant color. */
    private fun applyGlow(item: PlaybackItem) {
        val glow = findViewById<GlowView>(R.id.audio_art_glow)
        glow.setColor(null)
        lifecycleScope.launch {
            val color = PlaybackColors.artworkGlowColor(this@AudioPlayerActivity, item)
            if (currentItem?.mediaId == item.mediaId) {
                currentDominantColor = color
                glow.setColor(color)
                applyAmbientBackground(color)
                applyDynamicControlsTint(color)
            }
        }
    }

    private var currentBgDrawable: android.graphics.drawable.Drawable? = null

    private fun applyAmbientBackground(dominantColor: Int?) {
        val root = findViewById<View>(R.id.audio_root) ?: return
        val surfaceColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            Color.BLACK
        )
        if (dominantColor == null) {
            root.setBackgroundColor(surfaceColor)
            currentBgDrawable = null
            return
        }

        val topColor = androidx.core.graphics.ColorUtils.blendARGB(dominantColor, surfaceColor, 0.72f)
        val midColor = androidx.core.graphics.ColorUtils.blendARGB(dominantColor, surfaceColor, 0.88f)
        val newBg = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, midColor, surfaceColor)
        )

        val previous = currentBgDrawable ?: android.graphics.drawable.ColorDrawable(surfaceColor)
        val transition = android.graphics.drawable.TransitionDrawable(arrayOf(previous, newBg))
        transition.isCrossFadeEnabled = true
        root.background = transition
        transition.startTransition(450)
        currentBgDrawable = newBg
    }

    private fun updateLikeState(item: PlaybackItem) {
        val liked = LikedStore.isLiked(item.mediaId)
        likeButton.setIconResource(
            if (liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        likeButton.text = getString(if (liked) R.string.liked else R.string.like)
        likeButton.iconTint = ColorStateList.valueOf(
            if (liked) LIKED_RED else MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                Color.LTGRAY
            )
        )
        likeButton.contentDescription =
            getString(if (liked) R.string.unlike else R.string.like)
    }

    private fun currentDuration(state: PlaybackController.UiState = PlaybackController.state.value): Long {
        return state.durationMs.takeIf { it > 0 }
            ?: currentItem?.durationMs?.takeIf { it > 0 }
            ?: 0L
    }

    private fun openLyricsSheet() {
        if (currentItem == null) return
        if (supportFragmentManager.findFragmentByTag(LYRICS_SHEET_TAG) != null) return
        LyricsSheetFragment().show(supportFragmentManager, LYRICS_SHEET_TAG)
    }

    companion object {
        private val LIKED_RED = Color.parseColor("#E53935")
        private const val LYRICS_SHEET_TAG = "lyrics_sheet"
    }
}
