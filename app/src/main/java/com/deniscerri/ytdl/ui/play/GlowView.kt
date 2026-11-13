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
import kotlin.math.min
import kotlin.math.sin

/**
 * Lush, circular radial halo painted behind the artwork card.
 *
 * Uses a cached multi-stop RadialGradient to produce an organic, 360-degree round bloom that
 * naturally feathers into the ambient background without any flat or square edges.
 * Breathes gently with playback and pulses dynamically with the beat.
 */
class GlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }
    private var glowColor: Int = 0
    private var baseStrength: Float = 0f
    private var glowStrength: Float = 0f
    private var pulsing = false

    private var targetEnergy: Float = 0f
    private var smoothedEnergy: Float = 0f
    private var lastFrameTimeMs: Long = 0L

    /** Beat rate in Hz fed by the player (0 = no beat). The pulse is timed here, inside the
     *  animation loop, so it never misses a beat because the state tick is coarser than the
     *  beat window. */
    private var beatRateHz: Float = 0f
    private var lastKickMs: Long = 0L

    // Cached gradient: rebuilt only when the size or color changes. Frame-to-frame intensity
    // changes are applied via paint.alpha and breath via canvas scale — no per-frame allocation.
    private var cachedShader: Shader? = null
    private var cachedShaderColor: Int = 0
    private var cachedShaderRadius: Float = 0f

    fun setColor(color: Int?) {
        glowColor = color ?: 0
        baseStrength = if (color == null) 0f else 0.88f
        glowStrength = baseStrength
        targetEnergy = 0f
        smoothedEnergy = 0f
        cachedShader = null
        invalidate()
    }

    /**
     * Sets the synthetic beat rate in Hz while playing (0 disables the beat kick). The energy
     * value of each kick is [BEAT_ENERGY]; GlowView re-triggers it on time every cycle.
     */
    fun setBeatRate(rateHz: Float) {
        val clamped = rateHz.coerceIn(0f, 4f)
        if (clamped == beatRateHz) return
        beatRateHz = clamped
        lastKickMs = 0L
    }

    /**
     * Organic breathing while audio plays with dynamic harmonic envelope and beat response.
     */
    fun setPulsing(enabled: Boolean) {
        if (pulsing == enabled) return
        pulsing = enabled
        lastFrameTimeMs = 0L
        if (enabled) postInvalidateOnAnimation() else invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (glowColor == 0 || baseStrength <= 0f) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        var breathFactor = 1.0f
        if (pulsing) {
            val now = SystemClock.elapsedRealtime()
            val dt = if (lastFrameTimeMs == 0L) 16L else (now - lastFrameTimeMs).coerceIn(1L, 64L)
            lastFrameTimeMs = now

            // Beat kicks are generated on the animation clock so a ~100ms kick window is hit
            // every cycle even though the player state only ticks every 500ms.
            if (beatRateHz > 0f) {
                val periodMs = 1000f / beatRateHz
                val phase = (now % periodMs.toLong()) / periodMs
                if (phase < BEAT_KICK_WIDTH &&
                    (lastKickMs == 0L || now - lastKickMs >= periodMs.toLong() / 2)
                ) {
                    targetEnergy = BEAT_ENERGY
                    lastKickMs = now
                }
            }

            // Attack ~80ms, Release ~350ms
            if (targetEnergy > smoothedEnergy) {
                smoothedEnergy += (targetEnergy - smoothedEnergy) * (dt / 80f).coerceAtMost(1f)
            } else {
                smoothedEnergy += (targetEnergy - smoothedEnergy) * (dt / 350f).coerceAtMost(1f)
            }
            targetEnergy = (targetEnergy - (dt / 250f)).coerceAtLeast(0f)

            val t = (now % PULSE_PERIOD_MS.toLong()) / PULSE_PERIOD_MS
            // Organic dual-frequency harmonic modulation (primary kick + syncopation)
            val harmonic = (0.65f * sin(2.0 * PI * t) + 0.35f * sin(4.0 * PI * t + 0.4)).toFloat()

            val energyBoost = smoothedEnergy * 0.35f
            glowStrength = (baseStrength * (0.82f + 0.18f * harmonic + energyBoost)).coerceIn(0f, 1.4f)
            breathFactor = (1f + PULSE_SCALE_AMPLITUDE * harmonic + smoothedEnergy * 0.05f).coerceIn(0.95f, 1.14f)
            postInvalidateOnAnimation()
        }

        val cx = w / 2f
        val cy = h / 2f
        val radius = (min(w, h) / 2f) * 0.95f

        val shader = cachedShader
        if (shader == null || cachedShaderColor != glowColor || cachedShaderRadius != radius) {
            // Multi-stop radial gradient with pure circular roundness. Stop alphas are baked in
            // relative to a full-strength core; overall intensity is modulated with paint.alpha.
            cachedShader = RadialGradient(
                cx, cy, radius,
                intArrayOf(
                    applyAlpha(glowColor, 0.72f),
                    applyAlpha(glowColor, 0.40f),
                    applyAlpha(glowColor, 0.14f),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.44f, 0.76f, 1.0f),
                Shader.TileMode.CLAMP
            )
            cachedShaderColor = glowColor
            cachedShaderRadius = radius
            paint.shader = cachedShader
        } else {
            paint.shader = shader
        }

        // Breathing scales the cached gradient instead of rebuilding it.
        canvas.save()
        canvas.scale(breathFactor, breathFactor, cx, cy)
        paint.alpha = (glowStrength * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.restore()
    }

    private fun applyAlpha(color: Int, alphaFraction: Float): Int {
        val alpha = (255 * alphaFraction.coerceIn(0f, 1f)).toInt()
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    private companion object {
        private const val PULSE_PERIOD_MS = 3200f
        private const val PULSE_SCALE_AMPLITUDE = 0.08f

        /** Synthetic beat kick energy and its width as a fraction of one beat cycle. */
        private const val BEAT_ENERGY = 0.70f
        private const val BEAT_KICK_WIDTH = 0.18f
    }
}
