package com.deniscerri.ytdl.ui.play.lyrics

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.lyrics.LyricsDocument
import com.deniscerri.ytdl.lyrics.LyricsProviderRegistry
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.abs

/**
 * Full-screen lyrics settings dialog styled with the same Material 3 card-list UI/UX as app settings.
 */
class LyricsSettingsDialogFragment : DialogFragment() {

    private lateinit var lyricsView: KaraokeLyricsView
    private var renderedDocument: LyricsDocument? = null

    companion object {
        fun newInstance(lyricsView: KaraokeLyricsView, renderedDocument: LyricsDocument?): LyricsSettingsDialogFragment {
            val fragment = LyricsSettingsDialogFragment()
            fragment.lyricsView = lyricsView
            fragment.renderedDocument = renderedDocument
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenLyricsTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_lyrics_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val inflater = LayoutInflater.from(context)
        val density = resources.displayMetrics.density

        val rootLayout = view.findViewById<LinearLayout>(R.id.lyrics_settings_root)
        rootLayout.removeAllViews()

        view.findViewById<MaterialToolbar>(R.id.lyrics_settings_top_bar)?.setNavigationOnClickListener {
            dismiss()
        }

        val onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0)
        val onSurfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)

        fun addCategoryHeader(titleRes: Int) {
            val header = inflater.inflate(R.layout.preference_category, rootLayout, false) as TextView
            header.setText(titleRes)
            rootLayout.addView(header)
        }

        fun createPreferenceRow(
            parent: ViewGroup,
            iconRes: Int,
            title: CharSequence,
            summary: CharSequence?,
            buildTrailing: (LinearLayout) -> Unit = {}
        ): Triple<View, TextView, LinearLayout> {
            val row = inflater.inflate(R.layout.preference, parent, false)
            row.findViewById<ImageView>(android.R.id.icon).setImageResource(iconRes)
            row.findViewById<TextView>(android.R.id.title).text = title
            val summaryView = row.findViewById<TextView>(android.R.id.summary)
            summaryView.text = summary
            summaryView.isVisible = !summary.isNullOrBlank()
            val widgetFrame = row.findViewById<LinearLayout>(android.R.id.widget_frame)
            buildTrailing(widgetFrame)
            parent.addView(row)
            return Triple(row, summaryView, widgetFrame)
        }

        // Category: Lyrics appearance / behavior
        addCategoryHeader(R.string.lyrics)

        // Scroll Anchor selector card
        val anchorOptions = listOf(
            0.35f to getString(R.string.lyrics_scroll_anchor_center),
            0.20f to getString(R.string.lyrics_scroll_anchor_top),
            0.50f to getString(R.string.lyrics_scroll_anchor_middle)
        )
        var currentAnchor = prefs.getFloat("lyrics_scroll_anchor", 0.35f)
        fun anchorLabel(value: Float): String =
            anchorOptions.minByOrNull { abs(it.first - value) }?.second ?: anchorOptions[0].second

        val (anchorRow, anchorSummary, _) = createPreferenceRow(
            parent = rootLayout,
            iconRes = R.drawable.ic_speed,
            title = getString(R.string.lyrics_scroll_anchor),
            summary = anchorLabel(currentAnchor)
        )
        anchorRow.setOnClickListener {
            val checkedIndex = anchorOptions.indexOfFirst { abs(it.first - currentAnchor) < 0.05f }.coerceAtLeast(0)
            val labels = anchorOptions.map { it.second }.toTypedArray()
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.lyrics_scroll_anchor)
                .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                    val chosen = anchorOptions[which].first
                    currentAnchor = chosen
                    prefs.edit().putFloat("lyrics_scroll_anchor", chosen).apply()
                    if (::lyricsView.isInitialized) {
                        lyricsView.setFocusFraction(chosen)
                    }
                    anchorSummary.text = anchorOptions[which].second
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // Text Size selector card
        val sizeOptions = listOf(
            1.0f to getString(R.string.lyrics_text_size_normal),
            1.15f to getString(R.string.lyrics_text_size_large),
            1.30f to getString(R.string.lyrics_text_size_xlarge)
        )
        var currentScale = prefs.getFloat("lyrics_font_scale", 1.0f)
        fun sizeLabel(value: Float): String =
            sizeOptions.minByOrNull { abs(it.first - value) }?.second ?: sizeOptions[0].second

        val (sizeRow, sizeSummary, _) = createPreferenceRow(
            parent = rootLayout,
            iconRes = R.drawable.ic_textformat,
            title = getString(R.string.lyrics_text_size),
            summary = sizeLabel(currentScale)
        )
        sizeRow.setOnClickListener {
            val checkedIndex = sizeOptions.indexOfFirst { abs(it.first - currentScale) < 0.05f }.coerceAtLeast(0)
            val labels = sizeOptions.map { it.second }.toTypedArray()
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.lyrics_text_size)
                .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                    val chosen = sizeOptions[which].first
                    currentScale = chosen
                    prefs.edit().putFloat("lyrics_font_scale", chosen).apply()
                    if (::lyricsView.isInitialized) {
                        lyricsView.setTextScale(chosen)
                    }
                    sizeSummary.text = sizeOptions[which].second
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // Category: Lyrics Providers
        addCategoryHeader(R.string.lyrics_providers)

        val currentProviders = LyricsProviderRegistry.ordered(context).toMutableList()
        val providerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootLayout.addView(providerContainer)

        fun refreshProviders() {
            providerContainer.removeAllViews()
            currentProviders.forEachIndexed { index, provider ->
                val iconRes = when (provider.id) {
                    "lyricsplus" -> R.drawable.ic_subtitles
                    "youtube_subs" -> R.drawable.ic_subtitles_alternative
                    else -> R.drawable.ic_lyrics
                }
                val summaryText = when (provider.id) {
                    "betterlyrics" -> getString(R.string.lyrics_provider_betterlyrics_summary)
                    "lyricsplus" -> getString(R.string.lyrics_provider_lyricsplus_summary)
                    "lrclib" -> getString(R.string.lyrics_provider_lrclib_summary)
                    "youtube_subs" -> getString(R.string.lyrics_provider_youtube_subs_summary)
                    else -> ""
                }

                var toggleRef: MaterialSwitch? = null
                val (row, _, _) = createPreferenceRow(
                    parent = providerContainer,
                    iconRes = iconRes,
                    title = provider.displayName,
                    summary = summaryText
                ) { widgetFrame ->
                    widgetFrame.orientation = LinearLayout.HORIZONTAL
                    widgetFrame.gravity = Gravity.CENTER_VERTICAL

                    val btnSize = (36 * density).toInt()
                    val btnPad = (6 * density).toInt()

                    val upBtn = ImageButton(context).apply {
                        layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
                        setImageResource(R.drawable.ic_up)
                        setBackgroundResource(android.R.color.transparent)
                        setColorFilter(if (index > 0) onSurface else onSurfaceVariant)
                        contentDescription = getString(R.string.lyrics_move_up)
                        isEnabled = index > 0
                        alpha = if (index > 0) 0.85f else 0.25f
                        setPadding(btnPad, btnPad, btnPad, btnPad)
                        setOnClickListener {
                            if (index > 0) {
                                val item = currentProviders.removeAt(index)
                                currentProviders.add(index - 1, item)
                                prefs.edit().putString(
                                    "lyrics_provider_order",
                                    LyricsProviderRegistry.serializeOrder(currentProviders.map { it.id })
                                ).apply()
                                refreshProviders()
                            }
                        }
                    }

                    val downBtn = ImageButton(context).apply {
                        layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                            marginEnd = (4 * density).toInt()
                        }
                        setImageResource(R.drawable.ic_down)
                        setBackgroundResource(android.R.color.transparent)
                        setColorFilter(if (index < currentProviders.size - 1) onSurface else onSurfaceVariant)
                        contentDescription = getString(R.string.lyrics_move_down)
                        isEnabled = index < currentProviders.size - 1
                        alpha = if (index < currentProviders.size - 1) 0.85f else 0.25f
                        setPadding(btnPad, btnPad, btnPad, btnPad)
                        setOnClickListener {
                            if (index < currentProviders.size - 1) {
                                val item = currentProviders.removeAt(index)
                                currentProviders.add(index + 1, item)
                                prefs.edit().putString(
                                    "lyrics_provider_order",
                                    LyricsProviderRegistry.serializeOrder(currentProviders.map { it.id })
                                ).apply()
                                refreshProviders()
                            }
                        }
                    }

                    val toggle = (inflater.inflate(R.layout.preferece_material_switch, widgetFrame, false) as MaterialSwitch).apply {
                        isChecked = provider.isEnabled(context)
                        setOnCheckedChangeListener { _, isChecked ->
                            prefs.edit().putBoolean("lyrics_provider_${provider.id}", isChecked).apply()
                        }
                    }
                    toggleRef = toggle

                    widgetFrame.addView(upBtn)
                    widgetFrame.addView(downBtn)
                    widgetFrame.addView(toggle)
                }

                row.setOnClickListener {
                    toggleRef?.toggle()
                }
            }
        }
        refreshProviders()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setWindowAnimations(R.style.LyricsFullScreenAnimation)
        }
    }
}
