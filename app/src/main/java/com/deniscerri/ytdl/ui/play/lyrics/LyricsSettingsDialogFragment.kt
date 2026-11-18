package com.deniscerri.ytdl.ui.play.lyrics

import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.lyrics.LyricsDocument
import com.deniscerri.ytdl.lyrics.LyricsProvider
import com.deniscerri.ytdl.lyrics.LyricsProviderRegistry
import com.deniscerri.ytdl.ui.compose.host.ComposeDialogFragment
import com.deniscerri.ytdl.ui.compose.play.LyricsSettingsScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.abs

/**
 * Full-screen lyrics settings dialog in Jetpack Compose (Phase 6c).
 *
 * Replaces `fragment_lyrics_settings.xml`.
 */
class LyricsSettingsDialogFragment : ComposeDialogFragment() {

    private lateinit var lyricsView: KaraokeLyricsView
    private var renderedDocument: LyricsDocument? = null

    private var currentAnchor by mutableFloatStateOf(0.35f)
    private var currentScale by mutableFloatStateOf(1.0f)
    private var providers by mutableStateOf<List<LyricsProvider>>(emptyList())
    private val providerEnabledMap = mutableStateMapOf<String, Boolean>()

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

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        currentAnchor = prefs.getFloat("lyrics_scroll_anchor", 0.35f)
        currentScale = prefs.getFloat("lyrics_font_scale", 1.0f)

        val ordered = LyricsProviderRegistry.ordered(requireContext())
        providers = ordered
        ordered.forEach { provider ->
            providerEnabledMap[provider.id] = provider.isEnabled(requireContext())
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setWindowAnimations(R.style.LyricsFullScreenAnimation)
        }
    }

    @Composable
    override fun Content() {
        val anchorOptions = listOf(
            0.35f to getString(R.string.lyrics_scroll_anchor_center),
            0.20f to getString(R.string.lyrics_scroll_anchor_top),
            0.50f to getString(R.string.lyrics_scroll_anchor_middle),
        )

        val sizeOptions = listOf(
            1.0f to getString(R.string.lyrics_text_size_normal),
            1.15f to getString(R.string.lyrics_text_size_large),
            1.30f to getString(R.string.lyrics_text_size_xlarge),
        )

        val anchorLabel = anchorOptions.minByOrNull { abs(it.first - currentAnchor) }?.second ?: anchorOptions[0].second
        val sizeLabel = sizeOptions.minByOrNull { abs(it.first - currentScale) }?.second ?: sizeOptions[0].second

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        LyricsSettingsScreen(
            anchorLabel = anchorLabel,
            fontSizeLabel = sizeLabel,
            providers = providers,
            providerEnabledMap = providerEnabledMap,
            onAnchorClick = {
                val checkedIndex = anchorOptions.indexOfFirst { abs(it.first - currentAnchor) < 0.05f }.coerceAtLeast(0)
                val labels = anchorOptions.map { it.second }.toTypedArray()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.lyrics_scroll_anchor)
                    .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                        val chosen = anchorOptions[which].first
                        currentAnchor = chosen
                        prefs.edit().putFloat("lyrics_scroll_anchor", chosen).apply()
                        if (::lyricsView.isInitialized) {
                            lyricsView.setFocusFraction(chosen)
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            },
            onFontSizeClick = {
                val checkedIndex = sizeOptions.indexOfFirst { abs(it.first - currentScale) < 0.05f }.coerceAtLeast(0)
                val labels = sizeOptions.map { it.second }.toTypedArray()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.lyrics_text_size)
                    .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                        val chosen = sizeOptions[which].first
                        currentScale = chosen
                        prefs.edit().putFloat("lyrics_font_scale", chosen).apply()
                        if (::lyricsView.isInitialized) {
                            lyricsView.setTextScale(chosen)
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            },
            onMoveProviderUp = { index ->
                if (index > 0) {
                    val list = providers.toMutableList()
                    val item = list.removeAt(index)
                    list.add(index - 1, item)
                    providers = list
                    prefs.edit().putString(
                        "lyrics_provider_order",
                        LyricsProviderRegistry.serializeOrder(list.map { it.id }),
                    ).apply()
                }
            },
            onMoveProviderDown = { index ->
                if (index < providers.size - 1) {
                    val list = providers.toMutableList()
                    val item = list.removeAt(index)
                    list.add(index + 1, item)
                    providers = list
                    prefs.edit().putString(
                        "lyrics_provider_order",
                        LyricsProviderRegistry.serializeOrder(list.map { it.id }),
                    ).apply()
                }
            },
            onToggleProvider = { providerId, enabled ->
                providerEnabledMap[providerId] = enabled
                prefs.edit().putBoolean("lyrics_provider_$providerId", enabled).apply()
            },
            onDismiss = { dismiss() },
        )
    }
}
