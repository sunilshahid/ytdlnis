package com.deniscerri.ytdl.ui.play

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Soft radial halo painted behind the artwork card. The color comes from the artwork itself
 * (see [com.deniscerri.ytdl.util.PlaybackColors]) — the same extraction the system applies to
 * colorize the media notification, boosted for visibility. A hardware layer keeps the repaint
 * cheap, and while audio is playing the halo breathes gently with the music.
 */
class GlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glowColor: Int = 0
    private var baseStrength: Float = 0f
    private var glowStrength: Float = 0f
    private var pulsing = false

    fun setColor(color: Int?) {
        glowColor = color ?: 0
        baseStrength = if (color == null) 0f else 0.82f
        glowStrength = baseStrength
        invalidate()
    }

    /**
     * Subtle breathing while audio plays: the halo slowly swells and shrinks (~3s cycle,
     * ±13%). Small enough to feel alive without distracting.
     */
    fun setPulsing(enabled: Boolean) {
        if (pulsing == enabled) return
        pulsing = enabled
        if (enabled) postInvalidateOnAnimation() else invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (glowColor == 0 || baseStrength <= 0f) return
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return
        if (pulsing) {
            val phase = (SystemClock.elapsedRealtime() % PULSE_PERIOD_MS) / PULSE_PERIOD_MS.toFloat()
            glowStrength = baseStrength * (1f - PULSE_AMPLITUDE * 0.5f * (1f - sin(2.0 * PI * phase).toFloat()))
            postInvalidateOnAnimation()
        }
        val cx = width / 2f
        val cy = height / 2f
        // Wide radius + late falloff = feathered edge, like the notification tint bleed.
        val radius = max(width, height) / 2f * 1.5f
        val center = applyAlpha(glowColor, glowStrength)
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(center, applyAlpha(glowColor, glowStrength * 0.6f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private fun applyAlpha(color: Int, alphaFraction: Float): Int {
        val alpha = (255 * alphaFraction.coerceIn(0f, 1f)).toInt()
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    companion object {
        private const val PULSE_PERIOD_MS = 3000f
        private const val PULSE_AMPLITUDE = 0.26f
    }
}
