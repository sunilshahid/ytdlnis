package com.deniscerri.ytdl.ui.play.lyrics

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import com.deniscerri.ytdl.lyrics.LyricsDocument
import com.deniscerri.ytdl.lyrics.LyricsLine
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class LyricsDisplayMode {
    PLAIN,
    SYNCED,
    WORD_BY_WORD,
    KARAOKE,
    SIMPLE
}

/**
 * Lightweight lyrics renderer.
 *
 * It owns no player state: the activity feeds media position, and the view computes the active
 * line/word from the document. Supports plain text, line-synced scrolling and a karaoke wipe when
 * the source has per-word timings.
 *
 * Scrolling is fully driven here: drags are followed directly, releases fling with [OverScroller],
 * and one second after the user stops the view animates back to the line that is playing.
 */
class KaraokeLyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.scaledDensity
    private var baseColor = MaterialColors.getColor(
        this,
        com.google.android.material.R.attr.colorOnSurfaceVariant,
        Color.LTGRAY
    )
    private var accentColor = MaterialColors.getColor(
        this,
        com.google.android.material.R.attr.colorPrimary,
        Color.WHITE
    )
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 19f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        color = baseColor
    }
    private val activePaint = Paint(textPaint).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 26f * density
        color = accentColor
        setShadowLayer(11f * density, 0f, 0f, applyAlpha(accentColor, 0.52f))
    }
    private val inactivePaint = Paint(textPaint).apply {
        setShadowLayer(3.5f * density, 0f, 0f, applyAlpha(baseColor, 0.34f))
        maskFilter = BlurMaskFilter(0.9f * density, BlurMaskFilter.Blur.NORMAL)
    }
    private val translationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * density
        color = baseColor
    }

    private var document: LyricsDocument? = null
    private var positionMs = 0L
    private var mode = LyricsDisplayMode.SYNCED
    private var approximateWordTimings = false
    private var isPlaying = false
    private var playbackSpeed = 1f
    private var lastPositionElapsedMs = 0L

    fun setApproximateWordTimings(enabled: Boolean) {
        if (approximateWordTimings == enabled) return
        approximateWordTimings = enabled
        rebuildLayout()
        invalidate()
    }

    fun isApproximateWordTimings(): Boolean = approximateWordTimings

    fun setPlaybackState(playing: Boolean, speed: Float = 1f) {
        val speedChanged = playbackSpeed != speed
        val playingChanged = isPlaying != playing
        isPlaying = playing
        playbackSpeed = speed.coerceAtLeast(0.1f)
        if ((playing && (playingChanged || speedChanged)) &&
            (mode == LyricsDisplayMode.WORD_BY_WORD || mode == LyricsDisplayMode.KARAOKE) &&
            document?.isSynced == true
        ) {
            postInvalidateOnAnimation()
        }
    }

    private fun effectivePositionMs(): Long {
        if (!isPlaying ||
            (mode != LyricsDisplayMode.WORD_BY_WORD && mode != LyricsDisplayMode.KARAOKE) ||
            document?.isSynced != true ||
            lastPositionElapsedMs == 0L
        ) {
            return positionMs
        }
        val elapsedDelta = (android.os.SystemClock.elapsedRealtime() - lastPositionElapsedMs).coerceIn(0L, 450L)
        return positionMs + (elapsedDelta * playbackSpeed).toLong()
    }

    var onLineClickListener: ((LyricsLine) -> Unit)? = null
    var onLyricsClickListener: (() -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()

    private var lineLayouts: List<LineLayout> = emptyList()
    private var contentHeight = 0f
    private var scrollY = 0f
    private var maxScroll = 0f
    private var lastActiveLine = -1
    private var lastTouchY = 0f
    private var isDragging = false

    /** Where the active line should sit inside the view (0.5 = vertically centered). */
    private var focusFraction = 0.5f

    private var velocityTracker: VelocityTracker? = null
    private val flingScroller = OverScroller(context)
    private var flingRunnable: Runnable? = null
    private var isFlinging = false

    /** Wall clock until which playback must not move the list because the user is browsing. */
    private var userScrollUntil = 0L
    private var snapRunnable: Runnable? = null
    private var scrollAnimator: ValueAnimator? = null

    init {
        // BlurMaskFilter is ignored by hardware-accelerated canvases.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val horizontalPadding = 20f * density
    private val verticalPadding = 14f * density
    private val lineSpacing = 12f * density
    private val rowHeight = 26f * density
    private val wordSpace = activePaint.measureText(" ")

    fun setDocument(document: LyricsDocument?) {
        this.document = document
        lastActiveLine = -1
        cancelScrollAnimation()
        cancelFling()
        cancelSnap()
        userScrollUntil = 0L
        scrollY = 0f
        rebuildLayout()
        scrollY = targetScrollForActive() ?: 0f
        contentDescription = document?.activeLine(positionMs)?.text
        invalidate()
    }

    fun setMode(mode: LyricsDisplayMode) {
        if (this.mode == mode) return
        this.mode = mode
        cancelScrollAnimation()
        cancelFling()
        cancelSnap()
        userScrollUntil = 0L
        scrollY = 0f
        lastActiveLine = -1
        rebuildLayout()
        scrollY = targetScrollForActive() ?: 0f
        invalidate()
    }

    fun getMode(): LyricsDisplayMode = mode

    /**
     * Recolors the lyrics. `base` is inactive text, `active` the highlighted line. Passing null
     * keeps the current color (theme default on first call). Rebuilds layout because glyph
     * metrics don't change but the karaoke wipe uses these paints directly.
     */
    fun setColors(base: Int?, active: Int?) {
        baseColor = base ?: MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.LTGRAY
        )
        accentColor = active ?: MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimary,
            Color.WHITE
        )
        textPaint.color = baseColor
        activePaint.color = accentColor
        activePaint.setShadowLayer(11f * density, 0f, 0f, applyAlpha(accentColor, 0.52f))
        inactivePaint.setShadowLayer(3.5f * density, 0f, 0f, applyAlpha(baseColor, 0.34f))
        translationPaint.color = baseColor
        invalidate()
    }

    /**
     * Used by the sheet: in the half-expanded state the bottom part of the view is off-screen,
     * so the active line is placed in the middle of the visible area instead of the full height.
     */
    fun setFocusFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0.15f, 0.85f)
        if (abs(clamped - focusFraction) < 0.005f) return
        focusFraction = clamped
        if (document?.isSynced == true && !isUserScrolling()) {
            animateToActive(FOCUS_ANIMATION_MS)
        } else {
            invalidate()
        }
    }

    fun setPosition(ms: Long) {
        lastPositionElapsedMs = android.os.SystemClock.elapsedRealtime()
        positionMs = ms
        val active = document?.activeLineIndex(ms) ?: -1
        contentDescription = document?.lines?.getOrNull(active)?.text
        if (active != lastActiveLine) {
            lastActiveLine = active
            if (document?.isSynced == true && !isUserScrolling()) {
                animateToActive(FOLLOW_ANIMATION_MS)
            }
        }
        postInvalidateOnAnimation()
    }

    private fun isUserScrolling(): Boolean =
        isDragging || isFlinging || SystemClock.uptimeMillis() < userScrollUntil

    private fun animateToActive(durationMs: Long) {
        val target = targetScrollForActive() ?: return
        animateScrollTo(target, durationMs)
    }

    private fun animateScrollTo(target: Float, durationMs: Long) {
        val clamped = target.coerceIn(0f, maxScroll)
        if (abs(clamped - scrollY) < 0.5f) {
            scrollY = clamped
            invalidate()
            return
        }
        cancelScrollAnimation()
        scrollAnimator = ValueAnimator.ofFloat(scrollY, clamped).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                scrollY = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun cancelScrollAnimation() {
        scrollAnimator?.cancel()
        scrollAnimator = null
    }

    private fun targetScrollForActive(): Float? {
        if (mode == LyricsDisplayMode.SIMPLE) return null
        val doc = document ?: return null
        if (!doc.isSynced) return null
        val active = doc.activeLineIndex(positionMs)
        if (active < 0) return 0f
        val layout = lineLayouts.getOrNull(active) ?: return 0f
        return (layout.top + layout.height / 2f - height * focusFraction)
            .coerceIn(0f, maxScroll)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout()
        if (document?.isSynced == true && !isUserScrolling()) {
            scrollY = targetScrollForActive() ?: scrollY
        }
    }

    private fun rebuildLayout() {
        val doc = document ?: run {
            lineLayouts = emptyList()
            contentHeight = 0f
            maxScroll = 0f
            return
        }
        if (width == 0) return
        val availableWidth = max(1f, width - horizontalPadding * 2)
        val layouts = mutableListOf<LineLayout>()
        var y = verticalPadding

        doc.lines.forEachIndexed { index, line ->
            val rows = mutableListOf<Row>()
            var rowBoxes = mutableListOf<Box>()
            var x = 0f
            var wordIndex = 0
            val resolvedWords = when {
                line.hasWordTimings -> line.words
                approximateWordTimings -> doc.approximateWordsForLine(index)
                else -> emptyList()
            }
            val words = if (resolvedWords.isNotEmpty()) {
                resolvedWords.map { it.text }
            } else {
                line.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            }
            if (words.isEmpty()) return@forEachIndexed

            words.forEach { word ->
                // Lay out with the active (largest) typeface so a bold active line never clips.
                var measured = activePaint.measureText(word)
                if (measured > availableWidth) {
                    // Very long token: let it occupy its own row; Canvas will clip, not crash.
                    measured = availableWidth
                }
                if (x > 0f && x + measured > availableWidth) {
                    rows.add(Row(rowBoxes.toList(), rowBoxes.size))
                    rowBoxes = mutableListOf()
                    x = 0f
                }
                val timingWord = resolvedWords.getOrNull(wordIndex)
                rowBoxes.add(
                    Box(
                        text = word,
                        wordIndex = if (line.hasWordTimings) wordIndex else -1,
                        x = x,
                        width = measured,
                        startMs = timingWord?.startMs,
                        endMs = timingWord?.endMs
                    )
                )
                x += measured + wordSpace
                wordIndex++
            }
            if (rowBoxes.isNotEmpty()) rows.add(Row(rowBoxes.toList(), rowBoxes.size))

            val lineHeight = max(rowHeight, rows.size * rowHeight + lineSpacing)
            layouts.add(LineLayout(index, line, rows, y, lineHeight))
            y += lineHeight
        }

        lineLayouts = layouts
        contentHeight = y + verticalPadding
        maxScroll = max(0f, contentHeight - height)
        scrollY = scrollY.coerceIn(0f, maxScroll)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val doc = document ?: return
        if (lineLayouts.isEmpty()) return

        val active = if (mode == LyricsDisplayMode.PLAIN || !doc.isSynced) {
            -1
        } else {
            doc.activeLineIndex(effectivePositionMs())
        }
        canvas.save()
        if (mode == LyricsDisplayMode.SIMPLE) {
            val layout = lineLayouts.getOrNull(active) ?: lineLayouts.first()
            val top = height * focusFraction - layout.height / 2f
            drawLine(canvas, layout, true, doc, top)
        } else {
            canvas.translate(0f, -scrollY)
            val visibleTop = scrollY - rowHeight * 2
            val visibleBottom = scrollY + height + rowHeight * 2
            lineLayouts.forEach { layout ->
                if (layout.top + layout.height < visibleTop || layout.top > visibleBottom) {
                    return@forEach
                }
                val alpha = when {
                    layout.index == active -> 1f
                    !doc.isSynced -> 1f
                    abs(layout.index - active) <= 2 -> 0.57f
                    else -> 0.28f
                }
                drawLine(
                    canvas,
                    layout,
                    layout.index == active,
                    doc,
                    layout.top,
                    alpha,
                    blurred = doc.isSynced && layout.index != active
                )
            }
        }
        canvas.restore()

        val activeLine = doc.lines.getOrNull(active)
        val translation = activeLine?.translation ?: activeLine?.romanization
        if (!translation.isNullOrBlank()) {
            canvas.drawText(translation, horizontalPadding, height - verticalPadding, translationPaint)
        }
        if (isPlaying &&
            (mode == LyricsDisplayMode.WORD_BY_WORD || mode == LyricsDisplayMode.KARAOKE) &&
            doc.isSynced
        ) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawLine(
        canvas: Canvas,
        layout: LineLayout,
        isActive: Boolean,
        doc: LyricsDocument,
        top: Float,
        alpha: Float = 1f,
        blurred: Boolean = false
    ) {
        var y = top + rowHeight * 0.78f
        layout.rows.forEach { row ->
            row.boxes.forEach { box ->
                val paint = when {
                    isActive -> activePaint
                    blurred -> inactivePaint
                    else -> textPaint
                }
                val pos = effectivePositionMs()
                val wordStart = box.startMs
                val wordEnd = box.endMs
                val wordProgress = if (
                    isActive &&
                    (mode == LyricsDisplayMode.WORD_BY_WORD || mode == LyricsDisplayMode.KARAOKE) &&
                    box.wordIndex >= 0 &&
                    wordStart != null &&
                    wordEnd != null
                ) {
                    doc.effectiveWordProgress(wordStart, wordEnd, pos)
                } else {
                    null
                }

                when {
                    mode == LyricsDisplayMode.PLAIN -> {
                        paint.color = applyAlpha(baseColor, alpha)
                        canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                    }
                    isActive && mode == LyricsDisplayMode.KARAOKE && wordProgress != null -> {
                        val pulse = if (wordProgress > 0f && wordProgress < 1f) {
                            1f + 0.045f * kotlin.math.sin(wordProgress * Math.PI).toFloat()
                        } else {
                            1f
                        }
                        val cx = horizontalPadding + box.x + box.width / 2f
                        val cy = y - rowHeight * 0.28f
                        canvas.save()
                        if (pulse != 1f) {
                            canvas.scale(pulse, pulse, cx, cy)
                        }
                        paint.color = applyAlpha(baseColor, alpha * 0.45f)
                        canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                        if (wordProgress > 0f) {
                            val clipRight = horizontalPadding + box.x + box.width * wordProgress
                            canvas.save()
                            canvas.clipRect(
                                RectF(
                                    horizontalPadding + box.x,
                                    y - rowHeight,
                                    clipRight,
                                    y + rowHeight * 0.4f
                                )
                            )
                            paint.color = applyAlpha(accentColor, alpha)
                            canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                            canvas.restore()
                        }
                        canvas.restore()
                    }
                    isActive && mode == LyricsDisplayMode.KARAOKE -> {
                        // Line-synced karaoke fallback without faking discrete word timestamps:
                        // wipe smoothly across each box proportionally to its position within the line.
                        val lineProgress = doc.lineProgress(pos)
                        val rowTotalWidth = layout.rows.sumOf { r ->
                            r.boxes.sumOf { it.text.length }
                        }.coerceAtLeast(1)
                        val boxesBefore = layout.rows.takeWhile { it !== row }
                            .sumOf { r -> r.boxes.sumOf { it.text.length } } +
                            row.boxes.takeWhile { it !== box }.sumOf { it.text.length }
                        val boxStartFrac = boxesBefore.toFloat() / rowTotalWidth
                        val boxEndFrac = (boxesBefore + box.text.length).toFloat() / rowTotalWidth
                        val boxFill = ((lineProgress - boxStartFrac) /
                            (boxEndFrac - boxStartFrac).coerceAtLeast(0.001f)).coerceIn(0f, 1f)

                        paint.color = applyAlpha(baseColor, alpha * 0.45f)
                        canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                        if (boxFill > 0f) {
                            val clipRight = horizontalPadding + box.x + box.width * boxFill
                            canvas.save()
                            canvas.clipRect(
                                RectF(
                                    horizontalPadding + box.x,
                                    y - rowHeight,
                                    clipRight,
                                    y + rowHeight * 0.4f
                                )
                            )
                            paint.color = applyAlpha(accentColor, alpha)
                            canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                            canvas.restore()
                        }
                    }
                    isActive && mode == LyricsDisplayMode.WORD_BY_WORD && wordProgress != null -> {
                        val isCurrentWord = wordProgress > 0f && wordProgress < 1f
                        val isPastWord = wordProgress >= 1f
                        paint.color = when {
                            isCurrentWord || isPastWord -> applyAlpha(accentColor, alpha)
                            else -> applyAlpha(baseColor, alpha * 0.45f)
                        }
                        if (isCurrentWord) {
                            val cx = horizontalPadding + box.x + box.width / 2f
                            val cy = y - rowHeight * 0.28f
                            canvas.save()
                            canvas.scale(1.06f, 1.06f, cx, cy)
                            canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                            canvas.restore()
                        } else {
                            canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                        }
                    }
                    else -> {
                        val color = if (isActive) accentColor else baseColor
                        paint.color = applyAlpha(color, alpha)
                        canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                    }
                }
            }
            y += rowHeight
        }
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        return Color.argb(
            (Color.alpha(color) * alpha.coerceIn(0f, 1f)).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (document == null) return super.onTouchEvent(event)

        if (mode == LyricsDisplayMode.SIMPLE) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                onLyricsClickListener?.invoke()
            }
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelScrollAnimation()
                cancelSnap()
                stopFling()
                downX = event.x
                downY = event.y
                downTime = SystemClock.uptimeMillis()
                lastTouchY = event.y
                isDragging = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.y - lastTouchY
                val totalDy = abs(event.y - downY)
                val totalDx = abs(event.x - downX)
                if (!isDragging && (totalDy > touchSlop || totalDx > touchSlop)) {
                    isDragging = true
                }
                lastTouchY = event.y
                if (isDragging) {
                    scrollY = (scrollY - dy).coerceIn(0f, maxScroll)
                    markUserScrolling()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val velocityY = finishVelocityTracking(event)
                val elapsed = SystemClock.uptimeMillis() - downTime
                val totalDy = abs(event.y - downY)
                val totalDx = abs(event.x - downX)
                if (!isDragging && totalDy < touchSlop && totalDx < touchSlop && elapsed < 400L) {
                    val tapY = event.y + scrollY
                    val tappedLayout = lineLayouts.firstOrNull {
                        tapY >= it.top && tapY <= (it.top + it.height)
                    }
                    if (tappedLayout != null) {
                        onLineClickListener?.invoke(tappedLayout.line)
                    } else {
                        onLyricsClickListener?.invoke()
                    }
                    performClick()
                    scheduleSnap()
                    return true
                }
                isDragging = false
                if (abs(velocityY) > minFlingVelocity && maxScroll > 0f) {
                    // fling() expects the velocity of the scroll position, which is opposite to
                    // the finger direction.
                    startFling(-velocityY)
                } else {
                    markUserScrolling()
                    scheduleSnap()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                finishVelocityTracking(event)
                isDragging = false
                markUserScrolling()
                scheduleSnap()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun finishVelocityTracking(event: MotionEvent): Float {
        val tracker = velocityTracker ?: return 0f
        tracker.addMovement(event)
        tracker.computeCurrentVelocity(1000, maxFlingVelocity)
        val velocity = tracker.yVelocity
        tracker.recycle()
        velocityTracker = null
        return velocity
    }

    private fun markUserScrolling() {
        userScrollUntil = SystemClock.uptimeMillis() + SNAP_DELAY_MS
    }

    private fun startFling(velocityY: Float) {
        cancelScrollAnimation()
        cancelSnap()
        flingScroller.forceFinished(true)
        flingScroller.fling(
            0,
            scrollY.roundToInt(),
            0,
            velocityY.roundToInt(),
            0,
            0,
            0,
            max(0, maxScroll.roundToInt())
        )
        isFlinging = true
        markUserScrolling()
        val runnable = object : Runnable {
            override fun run() {
                if (!isFlinging) return
                if (flingScroller.computeScrollOffset()) {
                    scrollY = flingScroller.currY.toFloat().coerceIn(0f, maxScroll)
                    markUserScrolling()
                    invalidate()
                    postOnAnimation(this)
                } else {
                    isFlinging = false
                    flingRunnable = null
                    scheduleSnap()
                }
            }
        }
        flingRunnable = runnable
        postOnAnimation(runnable)
    }

    private fun stopFling() {
        isFlinging = false
        flingRunnable?.let(::removeCallbacks)
        flingRunnable = null
        flingScroller.forceFinished(true)
    }

    private fun cancelFling() {
        stopFling()
    }

    private fun scheduleSnap() {
        if (document?.isSynced != true) return
        cancelSnap()
        val delay = (userScrollUntil - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        val runnable = Runnable { snapIfIdle() }
        snapRunnable = runnable
        postDelayed(runnable, delay)
    }

    private fun cancelSnap() {
        snapRunnable?.let(::removeCallbacks)
        snapRunnable = null
    }

    private fun snapIfIdle() {
        snapRunnable = null
        if (isDragging || isFlinging) {
            scheduleSnap()
            return
        }
        val remaining = userScrollUntil - SystemClock.uptimeMillis()
        if (remaining > 0L) {
            val runnable = Runnable { snapIfIdle() }
            snapRunnable = runnable
            postDelayed(runnable, remaining)
            return
        }
        animateToActive(SNAP_ANIMATION_MS)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        cancelScrollAnimation()
        stopFling()
        cancelSnap()
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDetachedFromWindow()
    }

    private data class LineLayout(
        val index: Int,
        val line: LyricsLine,
        val rows: List<Row>,
        val top: Float,
        val height: Float
    )

    private data class Row(val boxes: List<Box>, val count: Int)

    private data class Box(
        val text: String,
        val wordIndex: Int,
        val x: Float,
        val width: Float,
        val startMs: Long?,
        val endMs: Long?
    )

    private companion object {
        /** Hold time after the last gesture frame before snapping back. */
        const val SNAP_DELAY_MS = 550L

        /** Snap-back completes inside one second after the user stops. */
        const val SNAP_ANIMATION_MS = 320L
        const val FOLLOW_ANIMATION_MS = 420L
        const val FOCUS_ANIMATION_MS = 280L
    }
}
