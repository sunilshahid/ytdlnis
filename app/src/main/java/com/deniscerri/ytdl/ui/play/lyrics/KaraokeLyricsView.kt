package com.deniscerri.ytdl.ui.play.lyrics

import android.animation.ValueAnimator
import android.content.Context
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
import com.deniscerri.ytdl.lyrics.LyricsWord
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class LyricsDisplayMode {
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
        // Soft halo only: BlurMaskFilter would force LAYER_TYPE_SOFTWARE (full-view software
        // rendering at 60fps = jank/battery). Shadow layers are hardware-accelerated for text.
        setShadowLayer(3.5f * density, 0f, 0f, applyAlpha(baseColor, 0.34f))
    }
    private val translationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * density
        color = baseColor
    }

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrimHeight = 36f * density
    private var topScrimShader: android.graphics.Shader? = null
    private var bottomScrimShader: android.graphics.Shader? = null

    private var document: LyricsDocument? = null
    private var positionMs = 0L
    private var mode = LyricsDisplayMode.SYNCED
    private var approximateWordTimings = false
    private var showRomanization = false
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

    fun setShowRomanization(show: Boolean) {
        if (showRomanization == show) return
        showRomanization = show
        rebuildLayout()
        invalidate()
    }

    fun isShowRomanization(): Boolean = showRomanization

    fun setTextScale(scale: Float) {
        val clamped = scale.coerceIn(0.8f, 1.4f)
        textScale = clamped
        textPaint.textSize = 19f * density * clamped
        activePaint.textSize = 26f * density * clamped
        activePaint.setShadowLayer(11f * density * clamped, 0f, 0f, applyAlpha(accentColor, 0.52f))
        inactivePaint.textSize = 19f * density * clamped
        inactivePaint.setShadowLayer(3.5f * density * clamped, 0f, 0f, applyAlpha(baseColor, 0.34f))
        translationPaint.textSize = 13f * density * clamped
        rebuildLayout()
        invalidate()
    }

    var onUserInteractionListener: (() -> Unit)? = null
    var onScrollDirectionListener: ((isScrollingUpToHeader: Boolean) -> Unit)? = null

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
        // Hardware rendering throughout: no BlurMaskFilter is used anymore, so the per-frame
        // karaoke redraws run on the GPU pipeline instead of a full-view software bitmap.
    }

    private var textScale = 1.0f
    private val horizontalPadding = 20f * density
    private val verticalPadding = 14f * density
    private val lineSpacing: Float get() = 18f * density * textScale
    private val rowHeight: Float get() = 34f * density * textScale
    private val wordSpace: Float get() = activePaint.measureText(" ")
    private val inactiveWordSpace: Float get() = textPaint.measureText(" ")
    private val romHeight: Float get() = 22f * density * textScale

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

    /**
     * Swaps in a document whose text is identical but whose offset changed. Skips the layout
     * rebuild (glyph layout, wrap and romanization are unchanged) and keeps the current scroll
     * position, so tapping the offset buttons doesn't reset the view.
     */
    fun updateOffset(document: LyricsDocument?) {
        val next = document ?: return
        if (lineLayouts.isEmpty()) {
            setDocument(next)
            return
        }
        this.document = next
        contentDescription = next.activeLine(positionMs)?.text
        val active = next.activeLineIndex(positionMs)
        if (active != lastActiveLine) {
            lastActiveLine = active
            if (next.isSynced && !isUserScrolling()) {
                animateToActive(FOLLOW_ANIMATION_MS)
            }
        }
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
        activePaint.setShadowLayer(11f * density * textScale, 0f, 0f, applyAlpha(accentColor, 0.52f))
        inactivePaint.setShadowLayer(3.5f * density * textScale, 0f, 0f, applyAlpha(baseColor, 0.34f))
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

    /**
     * Drops the browse hold and animates straight back to the line that is playing now.
     * Tapping the lyrics is an explicit request to stop browsing, so it must not wait out
     * [SNAP_DELAY_MS] the way a scroll release does.
     */
    fun returnToActiveLine() {
        if (document?.isSynced != true || mode == LyricsDisplayMode.SIMPLE) {
            invalidate()
            return
        }
        userScrollUntil = 0L
        isDragging = false
        cancelSnap()
        cancelFling()
        animateToActive(SNAP_ANIMATION_MS)
    }

    /** Puts [layout] at the focus position right away (used when a tap seeks to that line). */
    private fun anchorTo(layout: LineLayout) {
        if (document?.isSynced != true || mode == LyricsDisplayMode.SIMPLE) return
        userScrollUntil = 0L
        isDragging = false
        cancelSnap()
        cancelFling()
        animateScrollTo(
            layout.top + layout.height / 2f - height * focusFraction,
            SNAP_ANIMATION_MS
        )
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
        updateScrims(w, h)
        if (oldh == 0 && document?.isSynced == true) {
            scrollY = targetScrollForActive() ?: 0f
        } else {
            scrollY = scrollY.coerceIn(0f, maxScroll)
        }
    }

    private fun updateScrims(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val surface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            Color.BLACK
        )
        topScrimShader = android.graphics.LinearGradient(
            0f, 0f, 0f, scrimHeight,
            surface, Color.TRANSPARENT,
            android.graphics.Shader.TileMode.CLAMP
        )
        bottomScrimShader = android.graphics.LinearGradient(
            0f, h - scrimHeight, 0f, h.toFloat(),
            Color.TRANSPARENT, surface,
            android.graphics.Shader.TileMode.CLAMP
        )
    }

    private fun countGraphemes(text: String): Int {
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(text)
        var count = 0
        while (it.next() != java.text.BreakIterator.DONE) {
            count++
        }
        return count.coerceAtLeast(1)
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
            val resolvedWords = when {
                line.hasWordTimings -> line.words
                approximateWordTimings -> doc.approximateWordsForLine(index)
                else -> emptyList()
            }
            val timed = resolvedWords.isNotEmpty()
            val words = if (timed) {
                resolvedWords.map { it.text }
            } else {
                line.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            }
            if (words.isEmpty()) return@forEachIndexed

            val isRtl = isRtlText(line.text)

            // Two wrap passes: the active line is laid out with the large bold metrics so it
            // never clips, while inactive lines wrap with their own (smaller) metrics so they
            // fill the row completely before moving to the next one.
            val activeRows = buildRows(words, resolvedWords, activePaint, wordSpace, availableWidth, isRtl)
            val inactiveRows = buildRows(words, emptyList(), textPaint, inactiveWordSpace, availableWidth, isRtl)
            val rowCount = max(activeRows.size, inactiveRows.size)

            val showRom = showRomanization && !line.romanization.isNullOrBlank()
            val wrappedRomLines = if (showRom) {
                wrapText(line.romanization!!, translationPaint, availableWidth)
            } else {
                emptyList()
            }
            val romLineHeight = translationPaint.textSize * 1.35f
            val romGap = 4f * density * textScale
            val romExtra = if (wrappedRomLines.isNotEmpty()) {
                romGap + wrappedRomLines.size * romLineHeight
            } else {
                0f
            }
            // The rom block is positioned from romTop inside drawLine; the height budget here
            // must cover it fully or it bleeds into the next line (text overlap bug).
            val lineHeight = rowCount * rowHeight + lineSpacing + romExtra
            val romTop = rowCount * rowHeight + romGap
            layouts.add(
                LineLayout(
                    index, line, activeRows, inactiveRows, y, lineHeight,
                    if (showRom) line.romanization else null,
                    wrappedRomLines,
                    romTop,
                    activeRows.sumOf { it.graphemes },
                    isRtl
                )
            )
            y += lineHeight
        }

        lineLayouts = layouts
        contentHeight = y + verticalPadding
        maxScroll = max(0f, contentHeight - height)
        scrollY = scrollY.coerceIn(0f, maxScroll)
    }

    private fun isRtlText(text: String): Boolean {
        for (i in 0 until text.length) {
            val c = text[i]
            val dir = Character.getDirectionality(c)
            if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC ||
                c in '\u0600'..'\u08FF' || c in '\uFB50'..'\uFDFF' || c in '\uFE70'..'\uFEFF'
            ) {
                return true
            }
            if (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                return false
            }
        }
        return false
    }

    /** Greedy word-wrap of [words] into rows that fit [availableWidth] using [paint]'s metrics. */
    private fun buildRows(
        words: List<String>,
        timings: List<LyricsWord>,
        paint: Paint,
        spaceWidth: Float,
        availableWidth: Float,
        isRtl: Boolean = false
    ): List<Row> {
        val rows = mutableListOf<Row>()
        var rowBoxes = mutableListOf<Box>()
        var currentLineWidth = 0f

        fun flushRow() {
            if (rowBoxes.isEmpty()) return
            val positionedBoxes = if (isRtl) {
                // Right-aligned and right-to-left:
                // Word 0 is at the far right (right edge at availableWidth).
                // Subsequent words are placed to its left.
                var rightX = availableWidth
                rowBoxes.map { b ->
                    val boxX = rightX - b.width
                    rightX = boxX - spaceWidth
                    b.copy(x = boxX)
                }
            } else {
                rowBoxes.toList()
            }
            rows.add(Row(positionedBoxes, positionedBoxes.sumOf { it.graphemes }))
            rowBoxes = mutableListOf()
            currentLineWidth = 0f
        }

        words.forEachIndexed { wordIndex, word ->
            var measured = paint.measureText(word)
            if (measured > availableWidth) {
                // Very long token: let it occupy its own row; Canvas will clip, not crash.
                measured = availableWidth
            }
            val neededWidth = if (rowBoxes.isEmpty()) measured else currentLineWidth + spaceWidth + measured
            if (rowBoxes.isNotEmpty() && neededWidth > availableWidth) {
                flushRow()
            }
            val timingWord = timings.getOrNull(wordIndex)
            val boxX = if (isRtl) 0f else (if (rowBoxes.isEmpty()) 0f else currentLineWidth + spaceWidth)
            rowBoxes.add(
                Box(
                    text = word,
                    wordIndex = if (timings.isNotEmpty()) wordIndex else -1,
                    x = boxX,
                    width = measured,
                    startMs = timingWord?.startMs,
                    endMs = timingWord?.endMs,
                    graphemes = countGraphemes(word)
                )
            )
            currentLineWidth = if (rowBoxes.size == 1) measured else currentLineWidth + spaceWidth + measured
        }
        flushRow()
        return rows
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val doc = document ?: return
        if (lineLayouts.isEmpty()) return

        val active = if (!doc.isSynced) {
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
                    alpha
                )
            }
        }
        canvas.restore()

        val activeLine = doc.lines.getOrNull(active)
        val translation = activeLine?.translation
        if (!showRomanization && !translation.isNullOrBlank()) {
            canvas.drawText(translation, horizontalPadding, height - verticalPadding, translationPaint)
        }

        // Draw top & bottom edge dissolve scrims
        if (width > 0 && height > 0) {
            topScrimShader?.let { shader ->
                scrimPaint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), scrimHeight, scrimPaint)
            }
            bottomScrimShader?.let { shader ->
                scrimPaint.shader = shader
                canvas.drawRect(0f, height.toFloat() - scrimHeight, width.toFloat(), height.toFloat(), scrimPaint)
            }
            scrimPaint.shader = null
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
        alpha: Float = 1f
    ) {
        var y = top + rowHeight * 0.78f
        if (isActive) {
            layout.rows.forEach { row ->
                row.boxes.forEach { box ->
                    val paint = activePaint
                    val pos = effectivePositionMs()
                    val wordStart = box.startMs
                    val wordEnd = box.endMs
                    val wordProgress = if (
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
                        mode == LyricsDisplayMode.KARAOKE && wordProgress != null -> {
                            val isSinging = wordProgress > 0f && wordProgress < 1f
                            val isFullySung = wordProgress >= 1f
                            val pulse = if (isSinging) {
                                1f + 0.042f * kotlin.math.sin(wordProgress * Math.PI).toFloat()
                            } else {
                                1f
                            }
                            val rise = if (isSinging) {
                                -2.5f * density * kotlin.math.sin(wordProgress * Math.PI).toFloat()
                            } else {
                                0f
                            }
                            val cx = horizontalPadding + box.x + box.width / 2f
                            val cy = y - rowHeight * 0.28f

                            canvas.save()
                            if (rise != 0f) {
                                canvas.translate(0f, rise)
                            }
                            if (pulse != 1f) {
                                canvas.scale(pulse, pulse, cx, cy)
                            }

                            // Base inactive text
                            paint.color = applyAlpha(baseColor, alpha * 0.45f)
                            canvas.drawText(box.text, horizontalPadding + box.x, y, paint)

                            if (isFullySung) {
                                paint.color = applyAlpha(accentColor, alpha)
                                canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                            } else if (isSinging) {
                                // Apple Music soft feathered gradient wipe across singing syllable
                                val boxLeft = horizontalPadding + box.x
                                val feather = (14f * density).coerceAtMost(box.width * 0.35f).coerceAtLeast(3f)
                                val activeAlphaColor = applyAlpha(accentColor, alpha)

                                val wipeShader = if (layout.isRtl) {
                                    val wipeX = boxLeft + box.width * (1f - wordProgress)
                                    val gradStart = (wipeX - feather).coerceAtLeast(boxLeft - 1f)
                                    val gradEnd = (wipeX + feather).coerceAtMost(boxLeft + box.width + 1f)
                                    android.graphics.LinearGradient(
                                        gradStart, 0f, gradEnd, 0f,
                                        Color.TRANSPARENT, activeAlphaColor,
                                        android.graphics.Shader.TileMode.CLAMP
                                    )
                                } else {
                                    val wipeX = boxLeft + box.width * wordProgress
                                    val gradStart = (wipeX - feather).coerceAtLeast(boxLeft - 1f)
                                    val gradEnd = (wipeX + feather).coerceAtMost(boxLeft + box.width + 1f)
                                    android.graphics.LinearGradient(
                                        gradStart, 0f, gradEnd, 0f,
                                        activeAlphaColor, Color.TRANSPARENT,
                                        android.graphics.Shader.TileMode.CLAMP
                                    )
                                }
                                paint.shader = wipeShader
                                canvas.drawText(box.text, boxLeft, y, paint)
                                paint.shader = null
                            }
                            canvas.restore()
                        }
                        mode == LyricsDisplayMode.KARAOKE -> {
                            // Line-synced karaoke fallback; grapheme counts are precomputed
                            // per row at layout time (no per-frame BreakIterator allocations).
                            val lineProgress = doc.lineProgress(pos)
                            val rowTotalGraphemes = layout.totalGraphemes.coerceAtLeast(1)
                            val boxesBefore = layout.rows.takeWhile { it !== row }
                                .sumOf { it.graphemes } +
                                row.boxes.takeWhile { it !== box }.sumOf { it.graphemes }
                            val boxGraphemes = box.graphemes
                            val boxStartFrac = boxesBefore.toFloat() / rowTotalGraphemes
                            val boxEndFrac = (boxesBefore + boxGraphemes).toFloat() / rowTotalGraphemes
                            val boxFill = ((lineProgress - boxStartFrac) /
                                (boxEndFrac - boxStartFrac).coerceAtLeast(0.001f)).coerceIn(0f, 1f)

                            paint.color = applyAlpha(baseColor, alpha * 0.45f)
                            canvas.drawText(box.text, horizontalPadding + box.x, y, paint)

                            if (boxFill >= 1f) {
                                paint.color = applyAlpha(accentColor, alpha)
                                canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                            } else if (boxFill > 0f) {
                                val boxLeft = horizontalPadding + box.x
                                val feather = (14f * density).coerceAtMost(box.width * 0.35f).coerceAtLeast(3f)
                                val activeAlphaColor = applyAlpha(accentColor, alpha)

                                val wipeShader = if (layout.isRtl) {
                                    val wipeX = boxLeft + box.width * (1f - boxFill)
                                    val gradStart = (wipeX - feather).coerceAtLeast(boxLeft - 1f)
                                    val gradEnd = (wipeX + feather).coerceAtMost(boxLeft + box.width + 1f)
                                    android.graphics.LinearGradient(
                                        gradStart, 0f, gradEnd, 0f,
                                        Color.TRANSPARENT, activeAlphaColor,
                                        android.graphics.Shader.TileMode.CLAMP
                                    )
                                } else {
                                    val wipeX = boxLeft + box.width * boxFill
                                    val gradStart = (wipeX - feather).coerceAtLeast(boxLeft - 1f)
                                    val gradEnd = (wipeX + feather).coerceAtMost(boxLeft + box.width + 1f)
                                    android.graphics.LinearGradient(
                                        gradStart, 0f, gradEnd, 0f,
                                        activeAlphaColor, Color.TRANSPARENT,
                                        android.graphics.Shader.TileMode.CLAMP
                                    )
                                }
                                paint.shader = wipeShader
                                canvas.drawText(box.text, boxLeft, y, paint)
                                paint.shader = null
                            }
                        }
                        mode == LyricsDisplayMode.WORD_BY_WORD && wordProgress != null -> {
                            val isCurrentWord = wordProgress > 0f && wordProgress < 1f
                            val isPastWord = wordProgress >= 1f
                            paint.color = when {
                                isCurrentWord || isPastWord -> applyAlpha(accentColor, alpha)
                                else -> applyAlpha(baseColor, alpha * 0.45f)
                            }
                            if (isCurrentWord) {
                                val cx = horizontalPadding + box.x + box.width / 2f
                                val cy = y - rowHeight * 0.28f
                                val rise = -2f * density * kotlin.math.sin(wordProgress * Math.PI).toFloat()
                                canvas.save()
                                canvas.translate(0f, rise)
                                canvas.scale(1.06f, 1.06f, cx, cy)
                                canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                                canvas.restore()
                            } else {
                                canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                            }
                        }
                        else -> {
                            val color = accentColor
                            paint.color = applyAlpha(color, alpha)
                            canvas.drawText(box.text, horizontalPadding + box.x, y, paint)
                        }
                    }
                }
                y += rowHeight
            }
        } else {
            // Inactive line: draw its own (smaller-metric) wrap so each row is filled
            // completely before the text continues on the next row.
            layout.inactiveRows.forEach { row ->
                inactivePaint.color = applyAlpha(baseColor, alpha)
                row.boxes.forEach { box ->
                    canvas.drawText(box.text, horizontalPadding + box.x, y, inactivePaint)
                }
                y += rowHeight
            }
        }

        if (layout.romLines.isNotEmpty()) {
            translationPaint.color = applyAlpha(if (isActive) accentColor else baseColor, if (isActive) alpha * 0.85f else alpha * 0.52f)
            val romLineHeight = translationPaint.textSize * 1.35f
            // Anchored to the space reserved for it in rebuildLayout (romTop) so the
            // romanized rows can never overlap the next lyrics line.
            var romY = top + layout.romTop + translationPaint.textSize
            // Mirror the primary line's alignment: RTL lines are right-aligned, so their
            // romanization must share the same right edge instead of hanging on the left.
            val romRight = width - horizontalPadding
            layout.romLines.forEach { romLine ->
                val romX = if (layout.isRtl) {
                    romRight - translationPaint.measureText(romLine)
                } else {
                    horizontalPadding
                }
                canvas.drawText(romLine, romX, romY, translationPaint)
                romY += romLineHeight
            }
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

    /** Word-wrap [text] into lines that fit within [maxWidth] using [paint]'s metrics. */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val spaceW = paint.measureText(" ")
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var currentWidth = 0f
        words.forEach { word ->
            val wordW = paint.measureText(word)
            if (current.isEmpty()) {
                current.append(word)
                currentWidth = wordW
            } else if (currentWidth + spaceW + wordW <= maxWidth) {
                current.append(' ').append(word)
                currentWidth += spaceW + wordW
            } else {
                lines.add(current.toString())
                current.clear().append(word)
                currentWidth = wordW
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
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
                // A touch-hold without movement must still stop playback-driven scrolling
                // under the finger until the hold ends.
                markUserScrolling()
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
                    val cumulativeDownDrag = event.y - downY
                    if (scrollY <= 0f && cumulativeDownDrag > touchSlop * 2.5f && dy > 0) {
                        // Only user intentionally pulling down from the very top reveals header
                        onScrollDirectionListener?.invoke(true)
                    } else if (dy < -touchSlop * 0.5f) {
                        // Scrolling down into lyrics hides the header
                        onScrollDirectionListener?.invoke(false)
                    }
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
                        // Tapping a line seeks to it and immediately re-anchors the list, so
                        // the tapped line lands where the playing line normally sits.
                        onLineClickListener?.invoke(tappedLayout.line)
                        anchorTo(tappedLayout)
                    } else {
                        onLyricsClickListener?.invoke()
                    }
                    performClick()
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
        val inactiveRows: List<Row>,
        val top: Float,
        val height: Float,
        val romanization: String? = null,
        val romLines: List<String> = emptyList(),
        /** Distance from [top] to where the romanization block starts. */
        val romTop: Float = 0f,
        /** Total grapheme count of [rows], used by the line-synced karaoke fallback. */
        val totalGraphemes: Int = 0,
        val isRtl: Boolean = false
    )

    private data class Row(val boxes: List<Box>, val graphemes: Int)

    private data class Box(
        val text: String,
        val wordIndex: Int,
        val x: Float,
        val width: Float,
        val startMs: Long?,
        val endMs: Long?,
        val graphemes: Int
    )

    private companion object {
        /** Hold time after the last gesture frame before snapping back. */
        const val SNAP_DELAY_MS = 4500L

        /** Snap-back completes inside one second after the user stops. */
        const val SNAP_ANIMATION_MS = 320L
        const val FOLLOW_ANIMATION_MS = 420L
        const val FOCUS_ANIMATION_MS = 280L
    }
}
