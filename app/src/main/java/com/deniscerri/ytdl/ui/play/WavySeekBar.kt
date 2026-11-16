package com.deniscerri.ytdl.ui.play

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Material 3 Expressive squiggly/wavy seekbar:
 * - Animated sinusoidal wave along the active track while playing.
 * - Flat translucent track for unplayed duration.
 * - Circular thumb dot that expands with springy physics on drag.
 * - Dynamic spring scale on wave amplitude during touch.
 * - Supports both full player and mini player modes.
 */
class WavySeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnSeekBarChangeListener {
        fun onProgressChanged(seekBar: WavySeekBar, progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch(seekBar: WavySeekBar)
        fun onStopTrackingTouch(seekBar: WavySeekBar)
    }

    var onSeekBarChangeListener: OnSeekBarChangeListener? = null

    var max: Int = 1000
        set(value) {
            field = value.coerceAtLeast(1)
            invalidate()
        }

    var progress: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, max)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    fun setProgress(value: Int, fromUser: Boolean) {
        val clamped = value.coerceIn(0, max)
        if (progress != clamped) {
            progress = clamped
            onSeekBarChangeListener?.onProgressChanged(this, progress, fromUser)
        }
    }

    private val density = resources.displayMetrics.density

    var isMini: Boolean = false
        set(value) {
            field = value
            applyDimensions()
            invalidate()
        }

    private var baseWaveAmplitude = 3.5f * density
    private var wavePeriod = 22f * density
    private var trackStrokeWidth = 3.5f * density
    private var normalThumbRadius = 7f * density
    private var draggingThumbRadius = 9.5f * density
    private var currentThumbRadius = normalThumbRadius

    // Spring physics factors
    private var springAmplitudeMultiplier = 1.0f
    private var thumbSpringAnimator: ValueAnimator? = null
    private var amplitudeSpringAnimator: ValueAnimator? = null

    private var wavePhase = 0f
    private var isPlaying = false
    private var isDragging = false

    private val wavePath = Path()

    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = trackStrokeWidth
        color = Color.WHITE
    }

    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = trackStrokeWidth
        color = Color.argb(80, 255, 255, 255)
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private var waveAnimator: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        applyDimensions()
    }

    private fun applyDimensions() {
        if (isMini) {
            baseWaveAmplitude = 1.8f * density
            wavePeriod = 14f * density
            trackStrokeWidth = 2.4f * density
            normalThumbRadius = 3.5f * density
            draggingThumbRadius = 5.5f * density
        } else {
            baseWaveAmplitude = 3.5f * density
            wavePeriod = 22f * density
            trackStrokeWidth = 3.5f * density
            normalThumbRadius = 7f * density
            draggingThumbRadius = 9.5f * density
        }
        currentThumbRadius = normalThumbRadius
        playedPaint.strokeWidth = trackStrokeWidth
        unplayedPaint.strokeWidth = trackStrokeWidth
    }

    fun setColors(playedColor: Int, unplayedColor: Int, thumbColor: Int = playedColor) {
        playedPaint.color = playedColor
        unplayedPaint.color = unplayedColor
        thumbPaint.color = thumbColor
        invalidate()
    }

    fun setPlaying(playing: Boolean) {
        if (isPlaying == playing) return
        isPlaying = playing
        if (playing) {
            startWaveAnimation()
        } else {
            stopWaveAnimation()
        }
    }

    private fun startWaveAnimation() {
        if (waveAnimator?.isRunning == true) return
        waveAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = if (isMini) 1800L else 1600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                wavePhase = -(anim.animatedValue as Float)
                invalidate()
            }
            start()
        }
    }

    private fun stopWaveAnimation() {
        waveAnimator?.cancel()
        waveAnimator = null
    }

    private fun animateSpring(expanding: Boolean) {
        thumbSpringAnimator?.cancel()
        amplitudeSpringAnimator?.cancel()

        val targetThumb = if (expanding) draggingThumbRadius else normalThumbRadius
        val targetAmp = if (expanding) 1.35f else 1.0f

        thumbSpringAnimator = ValueAnimator.ofFloat(currentThumbRadius, targetThumb).apply {
            duration = 240L
            interpolator = OvershootInterpolator(2.4f)
            addUpdateListener {
                currentThumbRadius = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        amplitudeSpringAnimator = ValueAnimator.ofFloat(springAmplitudeMultiplier, targetAmp).apply {
            duration = 240L
            interpolator = OvershootInterpolator(2.0f)
            addUpdateListener {
                springAmplitudeMultiplier = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying) startWaveAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopWaveAnimation()
        thumbSpringAnimator?.cancel()
        amplitudeSpringAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val startX = paddingStart.toFloat() + normalThumbRadius
        val endX = width.toFloat() - paddingEnd.toFloat() - normalThumbRadius
        val availableWidth = (endX - startX).coerceAtLeast(1f)
        val centerY = height / 2f

        val fraction = (progress.toFloat() / max).coerceIn(0f, 1f)
        val thumbX = startX + fraction * availableWidth

        val currentWaveAmp = baseWaveAmplitude * springAmplitudeMultiplier

        // 1. Draw active wavy path from startX to thumbX
        wavePath.reset()
        if (thumbX > startX) {
            wavePath.moveTo(startX, centerY)
            val step = if (isMini) 1.5f * density else 2f * density
            var x = startX
            val transitionZone = (if (isMini) 8f else 14f) * density

            while (x <= thumbX) {
                val distFromStart = (x - startX).coerceAtLeast(0f)
                val distFromEnd = (thumbX - x).coerceAtLeast(0f)
                val damp = (distFromStart / transitionZone).coerceIn(0f, 1f) *
                           (distFromEnd / transitionZone).coerceIn(0f, 1f)
                val y = centerY + sin(2 * PI * (x - startX) / wavePeriod + wavePhase).toFloat() * currentWaveAmp * damp
                wavePath.lineTo(x, y)
                x += step
            }
            wavePath.lineTo(thumbX, centerY)
            canvas.drawPath(wavePath, playedPaint)
        }

        // 2. Draw unplayed flat line from thumbX to endX
        if (thumbX < endX) {
            canvas.drawLine(thumbX, centerY, endX, centerY, unplayedPaint)
        }

        // 3. Draw thumb circle at (thumbX, centerY)
        canvas.drawCircle(thumbX, centerY, currentThumbRadius, thumbPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                animateSpring(expanding = true)
                updateFromTouch(event.x)
                onSeekBarChangeListener?.onStartTrackingTouch(this)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    updateFromTouch(event.x)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    animateSpring(expanding = false)
                    updateFromTouch(event.x)
                    onSeekBarChangeListener?.onStopTrackingTouch(this)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(touchX: Float) {
        val startX = paddingStart.toFloat() + normalThumbRadius
        val endX = width.toFloat() - paddingEnd.toFloat() - normalThumbRadius
        val availableWidth = (endX - startX).coerceAtLeast(1f)
        val fraction = ((touchX - startX) / availableWidth).coerceIn(0f, 1f)
        val newProgress = (fraction * max).roundToInt()
        setProgress(newProgress, fromUser = true)
    }
}
