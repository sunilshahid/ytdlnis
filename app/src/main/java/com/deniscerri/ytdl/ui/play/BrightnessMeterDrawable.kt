package com.deniscerri.ytdl.ui.play

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.deniscerri.ytdl.R
import android.view.animation.DecelerateInterpolator

/**
 * Brightness icon whose inner sun circle fills clockwise according to the exact
 * brightness percentage (like a pie/radial meter clipped inside the icon).
 *
 * - 0%   -> only the badge/ring outline is visible
 * - 25%  -> quarter filled (matches ic_brightness_low)
 * - 50%  -> half filled (matches ic_brightness_half)
 * - 75%  -> three quarters filled (matches ic_brightness_high)
 * - 100% -> fully filled (matches ic_brightness_full)
 */
class BrightnessMeterDrawable(context: Context) : Drawable() {

    // ic_brightness_medium paints its inner disc solid white, which would hide any
    // fill drawn on top of it. Use the badge + ring only, with a hollow centre.
    private val base: Drawable = DrawableCompat.wrap(
        ContextCompat.getDrawable(context.applicationContext, R.drawable.ic_brightness_base)!!
            .mutate()
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val pieBounds = RectF()
    private var animator: ValueAnimator? = null
    private var percent: Float = 0f

    init {
        DrawableCompat.setTint(base, Color.WHITE)
    }

    /** Sets the brightness percentage (0..100). Animates the fill when requested. */
    fun setPercent(value: Int, animate: Boolean) {
        val target = value.coerceIn(0, 100).toFloat()
        animator?.cancel()
        if (!animate) {
            percent = target
            invalidateSelf()
            return
        }
        animator = ValueAnimator.ofFloat(percent, target).apply {
            duration = 120L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                percent = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        base.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        base.draw(canvas)
        val b = bounds
        if (b.isEmpty || percent <= 0f) return

        // The vector's sun centre is at (12,12) with the inner disc radius 4
        // in a 24x24 viewport.
        val radius = minOf(b.width(), b.height()) * (4f / 24f)
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        pieBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(pieBounds, -90f, 360f * (percent / 100f), true, fillPaint)
    }

    override fun setAlpha(alpha: Int) {
        base.alpha = alpha
        fillPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        base.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
