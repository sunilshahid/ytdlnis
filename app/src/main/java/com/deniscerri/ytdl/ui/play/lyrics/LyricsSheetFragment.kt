package com.deniscerri.ytdl.ui.play.lyrics

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.lyrics.LyricsDocument
import com.deniscerri.ytdl.lyrics.model.LyricsCandidate
import com.deniscerri.ytdl.lyrics.romanize.Romanizer
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.ui.compose.play.LyricsCandidatesDialog
import com.deniscerri.ytdl.ui.compose.play.LyricsSearchDialog
import com.deniscerri.ytdl.ui.compose.play.LyricsSheetContent
import com.deniscerri.ytdl.ui.compose.theme.YtdlTheme
import com.deniscerri.ytdl.util.PlaybackColors
import kotlinx.coroutines.launch

/**
 * Full-screen, smoothly animated lyrics experience with Compose chrome and embedded [KaraokeLyricsView].
 */
class LyricsSheetFragment : DialogFragment() {

    private lateinit var viewModel: LyricsViewModel
    private var lyricsViewRef: KaraokeLyricsView? = null
    private var renderedDocument: LyricsDocument? = null
    private var coloredForMediaId: String? = null

    private var approximateWordTimingsEnabled by mutableStateOf(false)
    private var romanizeEnabled by mutableStateOf(false)
    private var isPlayingState by mutableStateOf(false)
    private var baseColorState by mutableStateOf<Int?>(null)
    private var accentColorState by mutableStateOf<Int?>(null)

    // Dialog states
    private var showSearchDialog by mutableStateOf(false)
    private var searchCandidates by mutableStateOf<List<LyricsCandidate>?>(null)

    private val importLyricsLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) viewModel.importLyrics(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenLyricsTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        PlaybackController.init(requireContext())
        viewModel = ViewModelProvider(requireActivity())[LyricsViewModel::class.java]

        val lyricsPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        approximateWordTimingsEnabled = lyricsPrefs.getBoolean(PREF_LYRICS_APPROX_WORDS, false)
        romanizeEnabled = lyricsPrefs.getBoolean(PREF_LYRICS_ROMANIZE, false)
        val scrollAnchor = lyricsPrefs.getFloat(PREF_LYRICS_SCROLL_ANCHOR, 0.35f)
        val fontScale = lyricsPrefs.getFloat(PREF_LYRICS_FONT_SCALE, 1.0f)

        val playback = PlaybackController.state.value
        viewModel.onItemChanged(playback.currentItem, playback.durationMs)
        observe()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlTheme {
                    val uiState by viewModel.state.collectAsState()
                    val playbackState by PlaybackController.state.collectAsState()

                    LyricsSheetContent(
                        uiState = uiState,
                        isPlaying = playbackState.playing,
                        playbackPositionMs = playbackState.positionMs,
                        playbackSpeed = playbackState.playbackSpeed,
                        durationMs = playbackState.durationMs,
                        baseColor = baseColorState,
                        accentColor = accentColorState,
                        approximateWordTimingsEnabled = approximateWordTimingsEnabled,
                        romanizeEnabled = romanizeEnabled,
                        scrollAnchor = scrollAnchor,
                        fontScale = fontScale,
                        onBackClick = { dismiss() },
                        onLineClick = { line ->
                            val start = line.startMs
                            if (start != null) {
                                val offset = renderedDocument?.offsetMs ?: 0L
                                PlaybackController.seekTo((start + offset).coerceAtLeast(0L))
                            }
                        },
                        onSelectMode = { mode -> selectMode(mode) },
                        onToggleRomanization = { toggleRomanization() },
                        onToggleApproximateTimings = { toggleApproximateTimings() },
                        onChangeOffset = { delta -> viewModel.changeOffset(delta) },
                        onResetOffset = { viewModel.resetOffset() },
                        onSearchClick = { showSearchDialog = true },
                        onImportClick = { importLyricsLauncher.launch(arrayOf("*/*")) },
                        onSettingsClick = {
                            lyricsViewRef?.let {
                                LyricsSettingsDialogFragment.newInstance(it, renderedDocument)
                                    .show(parentFragmentManager, "lyricsSettings")
                            }
                        },
                        onLyricsViewCreated = { view ->
                            lyricsViewRef = view
                            applyLyricsColors(uiState.item)
                            val doc = uiState.document
                            if (doc != null) {
                                updateRenderedDocument(doc)
                            }
                        }
                    )

                    if (showSearchDialog) {
                        LyricsSearchDialog(
                            initialTitle = uiState.item?.title.orEmpty(),
                            initialArtist = uiState.item?.artist.orEmpty(),
                            onDismiss = { showSearchDialog = false },
                            onSearch = { title, artist ->
                                showSearchDialog = false
                                performSearch(title, artist)
                            }
                        )
                    }

                    val candidates = searchCandidates
                    if (!candidates.isNullOrEmpty()) {
                        val currentDurationSec = (renderedDocument?.lines?.lastOrNull()?.endMs?.let { it / 1000L }
                            ?: (playbackState.durationMs / 1000L)).toInt()

                        LyricsCandidatesDialog(
                            candidates = candidates,
                            currentDurationSec = currentDurationSec,
                            onDismiss = { searchCandidates = null },
                            onSelect = { candidate ->
                                searchCandidates = null
                                viewModel.applyCandidate(candidate)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setWindowAnimations(R.style.LyricsFullScreenAnimation)
            if (isPlayingState) {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun onStop() {
        dialog?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    private fun updateKeepScreenOn(playing: Boolean) {
        if (playing) {
            dialog?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            dialog?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        applyLyricsColors(state.item)
                        val doc = state.document
                        if (doc !== renderedDocument) {
                            updateRenderedDocument(doc)
                        }
                    }
                }
                launch {
                    viewModel.messages.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    PlaybackController.state.collect { playback ->
                        playback.currentItem?.let {
                            viewModel.onItemChanged(it, playback.durationMs)
                        }
                        isPlayingState = playback.playing
                        updateKeepScreenOn(isPlayingState)
                    }
                }
            }
        }
    }

    private fun updateRenderedDocument(document: LyricsDocument?) {
        val view = lyricsViewRef ?: return
        if (document == null) {
            renderedDocument = null
            view.visibility = View.INVISIBLE
            view.setDocument(null)
            return
        }

        if (isSameLyricsContent(document, renderedDocument)) {
            val prepared = renderedDocument!!.withOffset(document.offsetMs)
            renderedDocument = prepared
            view.updateOffset(prepared)
        } else {
            view.visibility = View.VISIBLE
            val savedModeName = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(PREF_LYRICS_MODE, null)
            val savedMode = savedModeName?.let { name ->
                runCatching { LyricsDisplayMode.valueOf(name) }.getOrNull()
            }
            val initialMode = when {
                !document.isSynced -> LyricsDisplayMode.SYNCED
                savedMode != null -> savedMode
                document.hasWordTimings -> LyricsDisplayMode.KARAOKE
                else -> LyricsDisplayMode.SYNCED
            }
            view.setMode(initialMode)
            applyRomanization(document, romanizeEnabled)
        }
    }

    private fun isSameLyricsContent(a: LyricsDocument?, b: LyricsDocument?): Boolean {
        if (a == null || b == null) return false
        if (a === b) return true
        if (a.mediaId != b.mediaId || a.lines.size != b.lines.size) return false
        return a.lines.zip(b.lines).all { (x, y) -> x.text == y.text }
    }

    private fun applyRomanization(doc: LyricsDocument, enabled: Boolean) {
        val view = lyricsViewRef ?: return
        view.setShowRomanization(enabled)
        val preparedDoc = if (enabled && Romanizer.canRomanizeDocument(doc)) {
            Romanizer.romanizeDocument(doc)
        } else {
            doc
        }
        renderedDocument = preparedDoc
        view.setDocument(preparedDoc)
    }

    private fun selectMode(mode: LyricsDisplayMode) {
        val document = renderedDocument ?: return
        if (!document.isSynced) return
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit()
            .putString(PREF_LYRICS_MODE, mode.name)
            .apply()
        lyricsViewRef?.setMode(mode)
    }

    private fun toggleRomanization() {
        val doc = renderedDocument ?: return
        romanizeEnabled = !romanizeEnabled
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit()
            .putBoolean(PREF_LYRICS_ROMANIZE, romanizeEnabled)
            .apply()
        applyRomanization(doc, romanizeEnabled)
    }

    private fun toggleApproximateTimings() {
        val doc = renderedDocument ?: return
        if (!doc.isSynced || doc.hasWordTimings) return
        approximateWordTimingsEnabled = !approximateWordTimingsEnabled
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit()
            .putBoolean(PREF_LYRICS_APPROX_WORDS, approximateWordTimingsEnabled)
            .apply()
        lyricsViewRef?.setApproximateWordTimings(approximateWordTimingsEnabled)
    }

    private fun applyLyricsColors(item: com.deniscerri.ytdl.playback.model.PlaybackItem?) {
        if (item == null || coloredForMediaId == item.mediaId) return
        coloredForMediaId = item.mediaId
        lifecycleScope.launch {
            val palette = PlaybackColors.lyricsPalette(requireContext(), item)
            baseColorState = palette.base
            accentColorState = palette.active
            lyricsViewRef?.setColors(palette.base, palette.active)
        }
    }

    private fun performSearch(title: String, artist: String?) {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), R.string.lyrics_searching_providers, Toast.LENGTH_SHORT).show()
                val candidates = viewModel.findCandidates(title, artist)
                if (!isAdded) return@launch
                if (candidates.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.lyrics_not_found, Toast.LENGTH_SHORT).show()
                } else {
                    searchCandidates = candidates
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(requireContext(), R.string.lyrics_not_found, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        lyricsViewRef = null
        renderedDocument = null
        coloredForMediaId = null
        super.onDestroyView()
    }

    companion object {
        private const val PREF_LYRICS_MODE = "lyrics_display_mode"
        private const val PREF_LYRICS_APPROX_WORDS = "lyrics_approx_word_timings"
        private const val PREF_LYRICS_ROMANIZE = "lyrics_romanization_enabled"
        private const val PREF_LYRICS_SCROLL_ANCHOR = "lyrics_scroll_anchor"
        private const val PREF_LYRICS_FONT_SCALE = "lyrics_font_scale"
    }
}
