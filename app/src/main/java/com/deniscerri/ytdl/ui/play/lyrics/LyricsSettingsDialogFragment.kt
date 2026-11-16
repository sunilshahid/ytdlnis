package com.deniscerri.ytdl.ui.play.lyrics

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.lyrics.LyricsDocument
import com.deniscerri.ytdl.lyrics.LyricsProviderRegistry
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch

/**
 * Full-screen lyrics settings dialog.
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
        val density = resources.displayMetrics.density

        val scrollView = view.findViewById<ScrollView>(R.id.lyrics_settings_scroll)
        val rootLayout = view.findViewById<LinearLayout>(R.id.lyrics_settings_root)
        rootLayout.removeAllViews()

        val surfaceContainer = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.DKGRAY)
        val outlineColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, Color.TRANSPARENT)
        val primaryColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.WHITE)
        val onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val onSurfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY)

        fun createCardSection(titleRes: Int, iconRes: Int, buildContent: (LinearLayout) -> Unit): View {
            val card = com.google.android.material.card.MaterialCardView(context).apply {
                radius = 16 * density
                strokeWidth = (1 * density).toInt()
                strokeColor = outlineColor
                setCardBackgroundColor(surfaceContainer)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (16 * density).toInt()
                }
            }
            val cardLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val p = (16 * density).toInt()
                setPadding(p, p, p, p)
            }
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (12 * density).toInt()
                }
            }
            val iconView = ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(primaryColor)
                layoutParams = LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt()).apply {
                    marginEnd = (10 * density).toInt()
                }
            }
            val titleView = TextView(context).apply {
                setText(titleRes)
                setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                setTextColor(onSurface)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            headerRow.addView(iconView)
            headerRow.addView(titleView)
            cardLayout.addView(headerRow)

            buildContent(cardLayout)
            card.addView(cardLayout)
            return card
        }

        // Section 1: Scroll Anchor Card
        val currentAnchor = prefs.getFloat("lyrics_scroll_anchor", 0.35f)
        val anchorCard = createCardSection(R.string.lyrics_scroll_anchor, R.drawable.ic_speed) { contentLayout ->
            val anchorGroup = RadioGroup(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val anchorOptions = listOf(
                Triple(0.35f, R.string.lyrics_scroll_anchor_center, 1001),
                Triple(0.20f, R.string.lyrics_scroll_anchor_top, 1002),
                Triple(0.50f, R.string.lyrics_scroll_anchor_middle, 1003)
            )
            anchorOptions.forEach { (fraction, labelRes, id) ->
                val rb = MaterialRadioButton(context).apply {
                    this.id = id
                    setText(labelRes)
                    setTextColor(onSurface)
                    isChecked = kotlin.math.abs(currentAnchor - fraction) < 0.05f
                    setPadding((8 * density).toInt(), (6 * density).toInt(), 0, (6 * density).toInt())
                }
                anchorGroup.addView(rb)
            }
            anchorGroup.setOnCheckedChangeListener { _, checkedId ->
                val chosen = anchorOptions.firstOrNull { it.third == checkedId }?.first ?: 0.35f
                prefs.edit().putFloat("lyrics_scroll_anchor", chosen).apply()
                lyricsView.setFocusFraction(chosen)
            }
            contentLayout.addView(anchorGroup)
        }
        rootLayout.addView(anchorCard)

        // Section 2: Text Size Card
        val currentScale = prefs.getFloat("lyrics_font_scale", 1.0f)
        val sizeCard = createCardSection(R.string.lyrics_text_size, R.drawable.ic_textformat) { contentLayout ->
            val sizeGroup = RadioGroup(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val sizeOptions = listOf(
                Triple(1.0f, R.string.lyrics_text_size_normal, 2001),
                Triple(1.15f, R.string.lyrics_text_size_large, 2002),
                Triple(1.30f, R.string.lyrics_text_size_xlarge, 2003)
            )
            sizeOptions.forEach { (scale, labelRes, id) ->
                val rb = MaterialRadioButton(context).apply {
                    this.id = id
                    setText(labelRes)
                    setTextColor(onSurface)
                    isChecked = kotlin.math.abs(currentScale - scale) < 0.05f
                    setPadding((8 * density).toInt(), (6 * density).toInt(), 0, (6 * density).toInt())
                }
                sizeGroup.addView(rb)
            }
            sizeGroup.setOnCheckedChangeListener { _, checkedId ->
                val chosen = sizeOptions.firstOrNull { it.third == checkedId }?.first ?: 1.0f
                prefs.edit().putFloat("lyrics_font_scale", chosen).apply()
                lyricsView.setTextScale(chosen)
            }
            contentLayout.addView(sizeGroup)
        }
        rootLayout.addView(sizeCard)

        // Section 3: Lyrics Providers Card
        val currentProviders = LyricsProviderRegistry.ordered(context).toMutableList()
        val providersCard = createCardSection(R.string.lyrics_providers, R.drawable.ic_subtitles) { contentLayout ->
            val providerContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            contentLayout.addView(providerContainer)

            fun refreshProviders() {
                providerContainer.removeAllViews()
                currentProviders.forEachIndexed { index, provider ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                    }

                    val toggle = MaterialSwitch(context).apply {
                        isChecked = provider.isEnabled(context)
                        setOnCheckedChangeListener { _, isChecked ->
                            prefs.edit().putBoolean("lyrics_provider_${provider.id}", isChecked).apply()
                        }
                    }

                    val info = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            marginStart = (12 * density).toInt()
                            marginEnd = (8 * density).toInt()
                        }
                    }

                    val nameView = TextView(context).apply {
                        text = provider.displayName
                        setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                        setTextColor(onSurface)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    }
                    val summaryText = when (provider.id) {
                        "betterlyrics" -> getString(R.string.lyrics_provider_betterlyrics_summary)
                        "lyricsplus" -> getString(R.string.lyrics_provider_lyricsplus_summary)
                        "lrclib" -> getString(R.string.lyrics_provider_lrclib_summary)
                        "youtube_subs" -> getString(R.string.lyrics_provider_youtube_subs_summary)
                        else -> ""
                    }
                    val summaryView = TextView(context).apply {
                        text = summaryText
                        setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                        setTextColor(onSurfaceVariant)
                    }
                    info.addView(nameView)
                    if (summaryText.isNotEmpty()) info.addView(summaryView)

                    val upBtn = ImageButton(context).apply {
                        setImageResource(R.drawable.ic_up)
                        setBackgroundResource(android.R.color.transparent)
                        setColorFilter(if (index > 0) onSurface else onSurfaceVariant)
                        contentDescription = getString(R.string.lyrics_move_up)
                        isEnabled = index > 0
                        alpha = if (index > 0) 1f else 0.25f
                        val pad = (8 * density).toInt()
                        setPadding(pad, pad, pad, pad)
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
                        setImageResource(R.drawable.ic_down)
                        setBackgroundResource(android.R.color.transparent)
                        setColorFilter(if (index < currentProviders.size - 1) onSurface else onSurfaceVariant)
                        contentDescription = getString(R.string.lyrics_move_down)
                        isEnabled = index < currentProviders.size - 1
                        alpha = if (index < currentProviders.size - 1) 1f else 0.25f
                        val pad = (8 * density).toInt()
                        setPadding(pad, pad, pad, pad)
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

                    row.addView(toggle)
                    row.addView(info)
                    row.addView(upBtn)
                    row.addView(downBtn)
                    providerContainer.addView(row)

                    if (index < currentProviders.size - 1) {
                        val divider = com.google.android.material.divider.MaterialDivider(context).apply {
                            dividerColor = outlineColor
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                marginStart = (48 * density).toInt()
                            }
                        }
                        providerContainer.addView(divider)
                    }
                }
            }
            refreshProviders()
        }
        rootLayout.addView(providersCard)

        // Back and Close buttons
        view.findViewById<View>(R.id.lyrics_settings_back)?.setOnClickListener { dismiss() }
        val closeBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.lyrics_settings_close)
        closeBtn.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setWindowAnimations(R.style.LyricsFullScreenAnimation)
        }
    }
}