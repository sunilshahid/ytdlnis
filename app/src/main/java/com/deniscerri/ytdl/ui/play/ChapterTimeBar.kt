package com.deniscerri.ytdl.ui.play

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import kotlin.math.roundToInt

/**
 * [DefaultTimeBar] that cuts the seekbar into chapter segments.
 *
 * Every chapter start is rendered as a crisp 2.5dp vertical gap, painted in the overlay's
 * background colour so the played/unplayed bar looks physically split at each boundary.
 */
@OptIn(UnstableApi::class)
class ChapterTimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DefaultTimeBar(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val notchWidthPx = 2.5f * density
    private val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // Matches the dark bottom overlay so each notch reads as a physical cut.
        color = Color.argb(235, 0, 0, 0)
    }

    private val barHeightPx: Float
    private val barGravity: Int

    private var chapterFractions = FloatArray(0)

    init {
        val styled = context.obtainStyledAttributes(
            attrs,
            androidx.media3.ui.R.styleable.DefaultTimeBar,
            defStyleAttr,
            0
        )
        barHeightPx = styled.getDimension(
            androidx.media3.ui.R.styleable.DefaultTimeBar_bar_height,
            3.5f * density
        )
        barGravity = styled.getInt(
            androidx.media3.ui.R.styleable.DefaultTimeBar_bar_gravity,
            DefaultTimeBar.BAR_GRAVITY_CENTER
        )
        styled.recycle()
    }

    /** Updates the segment boundaries. Cheap no-op when nothing changed. */
    fun setChapterStarts(startsMs: List<Long>, durationMs: Long) {
        val fractions = if (durationMs > 0L) {
            startsMs.asSequence()
                .filter { it > 0L && it < durationMs }
                .map { it.toFloat() / durationMs.toFloat() }
                .distinct()
                .toList()
        } else {
            emptyList()
        }
        val next = fractions.toFloatArray()
        if (next.contentEquals(chapterFractions)) return
        chapterFractions = next
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (chapterFractions.isEmpty()) return

        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val usableWidth = (right - left).coerceAtLeast(1f)
        val centerY = if (barGravity == DefaultTimeBar.BAR_GRAVITY_BOTTOM) {
            height - paddingBottom - barHeightPx / 2f
        } else {
            height / 2f
        }
        val halfHeight = (barHeightPx + 2f * density) / 2f
        val halfWidth = notchWidthPx / 2f

        chapterFractions.forEach { fraction ->
            val centerX = left + usableWidth * fraction
            canvas.drawRect(
                (centerX - halfWidth).roundToInt().toFloat(),
                (centerY - halfHeight).roundToInt().toFloat(),
                (centerX + halfWidth).roundToInt().toFloat(),
                (centerY + halfHeight).roundToInt().toFloat(),
                notchPaint
            )
        }
    }
}
