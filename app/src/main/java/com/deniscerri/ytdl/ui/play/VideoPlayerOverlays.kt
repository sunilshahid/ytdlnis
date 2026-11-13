package com.deniscerri.ytdl.ui.play

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.model.PlaybackChapter
import com.deniscerri.ytdl.playback.model.PlaybackExtrasCodec
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import java.util.Locale

object VideoPlayerOverlays {
    const val PREF_SUB_ENABLED = "video_subtitles_enabled"
    const val PREF_SUB_LANG = "video_subtitles_preferred_lang"
    const val PREF_SUB_SIZE = "video_subtitle_text_size"
    const val PREF_SUB_BG = "video_subtitle_bg_mode"

    fun applySubtitleStyle(playerView: PlayerView, context: Context) {
        val subtitleView = playerView.subtitleView ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val sizePreset = prefs.getInt(PREF_SUB_SIZE, 1).coerceIn(0, 2)
        val bgPreset = prefs.getInt(PREF_SUB_BG, 1).coerceIn(0, 2)

        val fraction = when (sizePreset) {
            0 -> 0.042f
            2 -> 0.068f
            else -> 0.0533f
        }
        val (bgColor, edgeType, edgeColor) = when (bgPreset) {
            0 -> Triple(Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK)
            2 -> Triple(Color.argb(230, 0, 0, 0), CaptionStyleCompat.EDGE_TYPE_NONE, Color.TRANSPARENT)
            else -> Triple(Color.argb(140, 0, 0, 0), CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK)
        }

        subtitleView.setFractionalTextSize(fraction)
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setApplyEmbeddedFontSizes(false)
        subtitleView.setStyle(
            CaptionStyleCompat(
                Color.WHITE,
                bgColor,
                Color.TRANSPARENT,
                edgeType,
                edgeColor,
                Typeface.SANS_SERIF
            )
        )
    }

    fun applyPreferredSubtitleSelection(player: Player?, context: Context) {
        val controller = player ?: return
        if (!controller.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val hasEnabledPref = prefs.contains(PREF_SUB_ENABLED)
        val enabled = prefs.getBoolean(PREF_SUB_ENABLED, false)
        val lang = prefs.getString(PREF_SUB_LANG, null)?.takeIf { it.isNotBlank() }

        val builder = controller.trackSelectionParameters.buildUpon()
        if (hasEnabledPref && !enabled) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            controller.trackSelectionParameters = builder.build()
        } else if (enabled) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            if (lang != null) builder.setPreferredTextLanguage(lang)
            controller.trackSelectionParameters = builder.build()
        }
    }

    fun hasTextTracks(player: Player?): Boolean {
        val tracks = player?.currentTracks ?: return false
        return tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
    }

    fun isSubtitleActive(player: Player?): Boolean {
        val tracks = player?.currentTracks ?: return false
        if (player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) return false
        return tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_TEXT && (0 until group.length).any { group.isTrackSelected(it) }
        }
    }

    fun updateChapterMarkersAndBar(
        playerView: PlayerView,
        chapters: List<PlaybackChapter>,
        positionMs: Long,
        lastMarkerSignature: LongArray?,
        onSignatureUpdated: (LongArray?) -> Unit
    ) {
        val chaptersBtn = playerView.findViewById<ImageButton>(R.id.video_chapters)
        val chapterBar = playerView.findViewById<View>(R.id.video_chapter_bar)
        val chapterPill = playerView.findViewById<TextView>(R.id.video_chapter_pill)
        val prevBtn = playerView.findViewById<ImageButton>(R.id.video_chapter_prev)
        val nextBtn = playerView.findViewById<ImageButton>(R.id.video_chapter_next)
        val timeBar = playerView.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)

        if (chapters.isEmpty()) {
            chaptersBtn?.visibility = View.GONE
            chapterBar?.visibility = View.GONE
            if (lastMarkerSignature != null && lastMarkerSignature.isNotEmpty()) {
                timeBar?.setAdGroupTimesMs(null, null, 0)
                onSignatureUpdated(null)
            }
            return
        }

        chaptersBtn?.visibility = View.VISIBLE
        chapterBar?.visibility = View.VISIBLE

        val markers = chapters.map { it.startMs }.filter { it > 0L }.toLongArray()
        if (lastMarkerSignature == null || !lastMarkerSignature.contentEquals(markers)) {
            timeBar?.setAdGroupTimesMs(markers, BooleanArray(markers.size) { false }, markers.size)
            onSignatureUpdated(markers)
        }

        val activeIdx = PlaybackExtrasCodec.findActiveChapterIndex(chapters, positionMs).coerceIn(0, chapters.lastIndex)
        val activeChapter = chapters[activeIdx]
        chapterPill?.text = "${activeIdx + 1}/${chapters.size} · ${activeChapter.title}"

        val canPrev = activeIdx > 0 || positionMs - activeChapter.startMs > 3_000L
        val canNext = activeIdx < chapters.lastIndex
        prevBtn?.isEnabled = canPrev
        prevBtn?.alpha = if (canPrev) 1f else 0.35f
        nextBtn?.isEnabled = canNext
        nextBtn?.alpha = if (canNext) 1f else 0.35f
    }

    fun computeChapterJumpMs(chapters: List<PlaybackChapter>, positionMs: Long, delta: Int): Long? {
        if (chapters.isEmpty()) return null
        val activeIdx = PlaybackExtrasCodec.findActiveChapterIndex(chapters, positionMs).coerceIn(0, chapters.lastIndex)
        val targetIdx = if (delta < 0) {
            if (positionMs - chapters[activeIdx].startMs > 3_000L || activeIdx == 0) {
                activeIdx
            } else {
                activeIdx - 1
            }
        } else {
            (activeIdx + delta).coerceIn(0, chapters.lastIndex)
        }
        return chapters[targetIdx].startMs
    }

    fun showSubtitlesSheet(
        context: Context,
        player: Player?,
        playerView: PlayerView,
        onSelectionChanged: () -> Unit
    ) {
        val controller = player ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val dialog = BottomSheetDialog(context)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 24))
        }

        val header = TextView(context).apply {
            text = context.getString(R.string.subtitles)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(context, 12))
        }
        container.addView(header)

        val tracksScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val tracksList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        tracksScroll.addView(tracksList)

        val accentColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.WHITE)
        val textColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val mutedColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)

        val textGroups = controller.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
        val textDisabled = controller.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val anySelected = !textDisabled && textGroups.any { group ->
            (0 until group.length).any { group.isTrackSelected(it) }
        }

        fun addTrackRow(title: String, selected: Boolean, onClick: () -> Unit) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
                setOnClickListener {
                    onClick()
                    dialog.dismiss()
                }
            }
            val label = TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(if (selected) accentColor else textColor)
                if (selected) setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val check = TextView(context).apply {
                text = if (selected) "✓" else ""
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(accentColor)
            }
            row.addView(label)
            row.addView(check)
            tracksList.addView(row)
        }

        addTrackRow(context.getString(R.string.subtitles_off), !anySelected) {
            prefs.edit().putBoolean(PREF_SUB_ENABLED, false).apply()
            if (controller.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) {
                controller.trackSelectionParameters = controller.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .build()
            }
            onSelectionChanged()
        }

        if (textGroups.isEmpty()) {
            tracksList.addView(
                TextView(context).apply {
                    text = context.getString(R.string.subtitles_none_available)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(mutedColor)
                    setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
                }
            )
        } else {
            textGroups.forEach { group ->
                for (trackIdx in 0 until group.length) {
                    val format = group.getTrackFormat(trackIdx)
                    val lang = format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
                    val langDisplay = lang?.let {
                        runCatching { Locale.forLanguageTag(it.replace('_', '-')).displayName }.getOrNull()
                    }?.takeIf { it.isNotBlank() }
                    val label = format.label?.takeIf { it.isNotBlank() }
                        ?: langDisplay
                        ?: lang?.uppercase(Locale.ROOT)
                        ?: "Track ${trackIdx + 1}"
                    val selected = !textDisabled && group.isTrackSelected(trackIdx)
                    addTrackRow(label, selected) {
                        prefs.edit()
                            .putBoolean(PREF_SUB_ENABLED, true)
                            .apply { if (lang != null) putString(PREF_SUB_LANG, lang) else remove(PREF_SUB_LANG) }
                            .apply()
                        if (controller.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) {
                            controller.trackSelectionParameters = controller.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIdx))
                                .build()
                        }
                        applySubtitleStyle(playerView, context)
                        onSelectionChanged()
                    }
                }
            }
        }
        container.addView(tracksScroll)

        // Subtitle Style section
        val styleTitle = TextView(context).apply {
            text = context.getString(R.string.subtitles_style_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(context, 14), 0, dp(context, 6))
        }
        container.addView(styleTitle)

        fun buildOptionRow(labelRes: Int, options: List<Pair<Int, Int>>, prefKey: String, defaultVal: Int) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(context, 4), 0, dp(context, 4))
            }
            val labelView = TextView(context).apply {
                text = context.getString(labelRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(mutedColor)
                layoutParams = LinearLayout.LayoutParams(dp(context, 92), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            row.addView(labelView)
            val buttons = mutableListOf<Pair<Int, MaterialButton>>()
            fun refreshButtons(selected: Int) {
                buttons.forEach { (value, btn) ->
                    btn.setTextColor(if (value == selected) accentColor else mutedColor)
                    btn.alpha = if (value == selected) 1f else 0.7f
                }
            }
            options.forEach { (value, textRes) ->
                val btn = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = context.getString(textRes)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(dp(context, 8), dp(context, 2), dp(context, 8), dp(context, 2))
                    minHeight = 0
                    minWidth = 0
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(context, 6) }
                    setOnClickListener {
                        prefs.edit().putInt(prefKey, value).apply()
                        refreshButtons(value)
                        applySubtitleStyle(playerView, context)
                    }
                }
                buttons.add(value to btn)
                row.addView(btn)
            }
            refreshButtons(prefs.getInt(prefKey, defaultVal))
            container.addView(row)
        }

        buildOptionRow(
            R.string.subtitles_size_label,
            listOf(0 to R.string.subtitles_size_small, 1 to R.string.subtitles_size_medium, 2 to R.string.subtitles_size_large),
            PREF_SUB_SIZE,
            1
        )
        buildOptionRow(
            R.string.subtitles_bg_label,
            listOf(0 to R.string.subtitles_bg_none, 1 to R.string.subtitles_bg_dim, 2 to R.string.subtitles_bg_solid),
            PREF_SUB_BG,
            1
        )

        dialog.setContentView(container)
        dialog.show()
    }

    fun showChaptersSheet(
        context: Context,
        chapters: List<PlaybackChapter>,
        currentPositionMs: Long,
        onChapterSelected: (PlaybackChapter) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 24))
        }

        val header = TextView(context).apply {
            text = context.getString(R.string.chapters_sheet_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(context, 12))
        }
        container.addView(header)

        if (chapters.isEmpty()) {
            container.addView(
                TextView(context).apply {
                    text = context.getString(R.string.chapters_none_available)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(0, dp(context, 12), 0, dp(context, 12))
                }
            )
            dialog.setContentView(container)
            dialog.show()
            return
        }

        val scroll = ScrollView(context)
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        container.addView(scroll)

        val accentColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.WHITE)
        val textColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val mutedColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
        val activeIdx = PlaybackExtrasCodec.findActiveChapterIndex(chapters, currentPositionMs)
        var activeRowView: View? = null

        chapters.forEachIndexed { index, chapter ->
            val isActive = index == activeIdx
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
                setOnClickListener {
                    onChapterSelected(chapter)
                    dialog.dismiss()
                }
            }
            if (isActive) activeRowView = row

            val timeView = TextView(context).apply {
                text = PlaybackExtrasCodec.formatTimestampMs(chapter.startMs)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (isActive) accentColor else mutedColor)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(dp(context, 64), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val titleView = TextView(context).apply {
                text = chapter.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(if (isActive) accentColor else textColor)
                if (isActive) setTypeface(typeface, Typeface.BOLD)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(timeView)
            row.addView(titleView)
            list.addView(row)
        }

        dialog.setContentView(container)
        dialog.show()
        activeRowView?.let { target ->
            scroll.post {
                scroll.smoothScrollTo(0, (target.top - dp(context, 48)).coerceAtLeast(0))
            }
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
