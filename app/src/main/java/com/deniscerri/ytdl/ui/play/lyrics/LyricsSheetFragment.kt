package com.deniscerri.ytdl.ui.play.lyrics

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.lyrics.LyricsDocument
import com.deniscerri.ytdl.lyrics.LyricsSource
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Half-height, draggable lyrics sheet.
 *
 * Embedded/sidecar/cached lyrics are shown immediately. When there are none, the sheet offers an
 * explicit online search or a local file import instead of silently downloading lyrics.
 */
class LyricsSheetFragment : BottomSheetDialogFragment() {

    private lateinit var viewModel: LyricsViewModel

    private lateinit var root: View
    private lateinit var lyricsView: KaraokeLyricsView
    private lateinit var titleView: TextView
    private lateinit var artistView: TextView
    private lateinit var sourceView: TextView
    private lateinit var expandButton: ImageButton
    private lateinit var promptContainer: View
    private lateinit var promptProgress: CircularProgressIndicator
    private lateinit var promptIcon: ImageView
    private lateinit var promptMessage: TextView
    private lateinit var promptHint: TextView
    private lateinit var promptActions: View
    private lateinit var searchButton: MaterialButton
    private lateinit var importButton: MaterialButton
    private lateinit var searchInlineButton: MaterialButton
    private lateinit var focusModeButton: MaterialButton
    private lateinit var syncedModeButton: MaterialButton
    private lateinit var wordModeButton: MaterialButton
    private lateinit var karaokeModeButton: MaterialButton
    private lateinit var plainModeButton: MaterialButton
    private var timingBadgeButton: MaterialButton? = null
    private var approximateWordTimingsEnabled = false
    private lateinit var positionView: TextView
    private lateinit var durationView: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var offsetView: TextView
    private lateinit var offsetResetButton: MaterialButton
    private lateinit var earlierButton: MaterialButton
    private lateinit var laterButton: MaterialButton

    private var renderedDocument: LyricsDocument? = null
    private var coloredForMediaId: String? = null
    private var topInset = 0
    private var sheetConfigured = false

    private val importLyricsLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) viewModel.importLyrics(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_lyrics_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        PlaybackController.init(requireContext())
        viewModel = ViewModelProvider(requireActivity())[LyricsViewModel::class.java]

        root = view.findViewById(R.id.lyrics_sheet_root)
        lyricsView = view.findViewById(R.id.lyrics_sheet_view)
        titleView = view.findViewById(R.id.lyrics_sheet_title)
        artistView = view.findViewById(R.id.lyrics_sheet_artist)
        sourceView = view.findViewById(R.id.lyrics_sheet_source)
        expandButton = view.findViewById(R.id.lyrics_sheet_expand)
        promptContainer = view.findViewById(R.id.lyrics_sheet_prompt)
        promptProgress = view.findViewById(R.id.lyrics_sheet_progress)
        promptIcon = view.findViewById(R.id.lyrics_sheet_prompt_icon)
        promptMessage = view.findViewById(R.id.lyrics_sheet_message)
        promptHint = view.findViewById(R.id.lyrics_sheet_hint)
        promptActions = view.findViewById(R.id.lyrics_sheet_actions)
        searchButton = view.findViewById(R.id.lyrics_sheet_search)
        importButton = view.findViewById(R.id.lyrics_sheet_import)
        searchInlineButton = view.findViewById(R.id.lyrics_sheet_search_inline)
        focusModeButton = view.findViewById(R.id.lyrics_sheet_mode_focus)
        syncedModeButton = view.findViewById(R.id.lyrics_sheet_mode_synced)
        wordModeButton = view.findViewById(R.id.lyrics_sheet_mode_word)
        karaokeModeButton = view.findViewById(R.id.lyrics_sheet_mode_karaoke)
        plainModeButton = view.findViewById(R.id.lyrics_sheet_mode_plain)
        timingBadgeButton = view.findViewById(R.id.lyrics_sheet_timing_badge)
        positionView = view.findViewById(R.id.lyrics_sheet_position)
        durationView = view.findViewById(R.id.lyrics_sheet_duration)
        playPauseButton = view.findViewById(R.id.lyrics_sheet_play_pause)
        offsetView = view.findViewById(R.id.lyrics_sheet_offset)
        offsetResetButton = view.findViewById(R.id.lyrics_sheet_offset_reset)
        earlierButton = view.findViewById(R.id.lyrics_sheet_earlier)
        laterButton = view.findViewById(R.id.lyrics_sheet_later)

        view.findViewById<View>(R.id.lyrics_sheet_close).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.lyrics_sheet_handle_area).setOnClickListener { toggleSheet() }
        expandButton.setOnClickListener { toggleSheet() }

        searchButton.setOnClickListener { viewModel.searchOnline() }
        importButton.setOnClickListener { importLyricsLauncher.launch(arrayOf("*/*")) }
        searchInlineButton.setOnClickListener { viewModel.searchOnline() }
        focusModeButton.setOnClickListener { selectMode(LyricsDisplayMode.SIMPLE) }
        syncedModeButton.setOnClickListener { selectMode(LyricsDisplayMode.SYNCED) }
        wordModeButton.setOnClickListener { selectMode(LyricsDisplayMode.WORD_BY_WORD) }
        karaokeModeButton.setOnClickListener { selectMode(LyricsDisplayMode.KARAOKE) }
        plainModeButton.setOnClickListener { selectMode(LyricsDisplayMode.PLAIN) }

        val lyricsPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        approximateWordTimingsEnabled = lyricsPrefs.getBoolean(PREF_LYRICS_APPROX_WORDS, false)
        lyricsView.setApproximateWordTimings(approximateWordTimingsEnabled)
        timingBadgeButton?.setOnClickListener {
            val doc = renderedDocument ?: return@setOnClickListener
            val currentMode = lyricsView.getMode()
            if (doc.isSynced && !doc.hasWordTimings &&
                (currentMode == LyricsDisplayMode.WORD_BY_WORD || currentMode == LyricsDisplayMode.KARAOKE)
            ) {
                approximateWordTimingsEnabled = !approximateWordTimingsEnabled
                lyricsPrefs.edit().putBoolean(PREF_LYRICS_APPROX_WORDS, approximateWordTimingsEnabled).apply()
                lyricsView.setApproximateWordTimings(approximateWordTimingsEnabled)
                updateModeUi()
            }
        }

        view.findViewById<View>(R.id.lyrics_sheet_previous).setOnClickListener {
            PlaybackController.previous()
        }
        view.findViewById<View>(R.id.lyrics_sheet_next).setOnClickListener {
            PlaybackController.next()
        }
        playPauseButton.setOnClickListener {
            val player = PlaybackController.player()
            if (player == null || player.mediaItemCount == 0) {
                PlaybackController.resume()
            } else {
                PlaybackController.togglePlayPause()
            }
        }

        earlierButton.setOnClickListener { viewModel.changeOffset(-500L) }
        laterButton.setOnClickListener { viewModel.changeOffset(500L) }
        offsetResetButton.setOnClickListener { viewModel.resetOffset() }

        lyricsView.onLineClickListener = { line ->
            val start = line.startMs
            if (start != null) {
                val offset = renderedDocument?.offsetMs ?: 0L
                PlaybackController.seekTo((start + offset).coerceAtLeast(0L))
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            val state = (dialog as? BottomSheetDialog)?.behavior?.state
                ?: BottomSheetBehavior.STATE_HALF_EXPANDED
            updateSheetStateUi(state)
            insets
        }

        val playback = PlaybackController.state.value
        viewModel.onItemChanged(playback.currentItem, playback.durationMs)
        observe()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { renderLyrics(it) }
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
                        bindPlayback(playback)
                    }
                }
            }
        }
    }

    private fun renderLyrics(state: LyricsUiState) {
        val item = state.item
        titleView.text = item?.title?.takeIf { it.isNotBlank() } ?: getString(R.string.lyrics)
        artistView.text = item?.artist?.takeIf { it.isNotBlank() }.orEmpty()

        val document = state.document
        if (document !== renderedDocument) {
            renderedDocument = document
            if (document != null) {
                lyricsView.visibility = View.VISIBLE
                val savedModeName = androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(requireContext())
                    .getString(PREF_LYRICS_MODE, null)
                val savedMode = savedModeName?.let { name ->
                    runCatching { LyricsDisplayMode.valueOf(name) }.getOrNull()
                }
                val initialMode = when {
                    !document.isSynced -> LyricsDisplayMode.PLAIN
                    savedMode != null -> savedMode
                    document.hasWordTimings -> LyricsDisplayMode.KARAOKE
                    else -> LyricsDisplayMode.SYNCED
                }
                lyricsView.setMode(initialMode)
                lyricsView.setDocument(document)
            } else {
                lyricsView.visibility = View.INVISIBLE
                lyricsView.setDocument(null)
            }
            updateModeUi()
            updateOffsetUi()
        }

        applyLyricsColors(item)

        val showPrompt = when (state.status) {
            LyricsLoadStatus.IDLE -> true
            LyricsLoadStatus.LOADING_LOCAL -> document == null
            LyricsLoadStatus.LOADING_NETWORK -> document == null
            LyricsLoadStatus.NOT_FOUND -> document == null
            LyricsLoadStatus.READY -> false
        }
        promptContainer.visibility = if (showPrompt) View.VISIBLE else View.GONE
        if (!showPrompt) return

        val loading = state.status == LyricsLoadStatus.LOADING_LOCAL ||
            state.status == LyricsLoadStatus.LOADING_NETWORK
        val notFound = state.status == LyricsLoadStatus.NOT_FOUND || state.status == LyricsLoadStatus.IDLE

        promptProgress.visibility = if (loading) View.VISIBLE else View.GONE
        promptIcon.visibility = if (notFound) View.VISIBLE else View.GONE
        promptMessage.text = when (state.status) {
            LyricsLoadStatus.LOADING_LOCAL -> getString(R.string.lyrics_loading)
            LyricsLoadStatus.LOADING_NETWORK -> getString(R.string.lyrics_searching_online)
            LyricsLoadStatus.IDLE -> getString(R.string.lyrics_sheet_nothing_playing)
            else -> getString(R.string.lyrics_no_lyrics_title)
        }
        promptHint.visibility = if (notFound) View.VISIBLE else View.GONE
        promptHint.setText(R.string.lyrics_no_lyrics_hint)
        promptActions.visibility = if (notFound) View.VISIBLE else View.GONE
        searchButton.isEnabled = notFound
        importButton.isEnabled = notFound || state.status == LyricsLoadStatus.NOT_FOUND
    }

    private fun updateModeUi() {
        val document = renderedDocument
        val synced = document?.isSynced == true
        view?.findViewById<View>(R.id.lyrics_sheet_mode_row)?.visibility =
            if (document != null) View.VISIBLE else View.GONE

        // Disabled when there is nothing to synchronize.
        focusModeButton.isEnabled = synced
        syncedModeButton.isEnabled = synced
        wordModeButton.isEnabled = synced
        karaokeModeButton.isEnabled = synced

        val accent = com.google.android.material.color.MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorPrimary,
            android.graphics.Color.WHITE
        )
        val muted = com.google.android.material.color.MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            android.graphics.Color.LTGRAY
        )
        val current = lyricsView.getMode()
        focusModeButton.setTextColor(if (current == LyricsDisplayMode.SIMPLE) accent else muted)
        syncedModeButton.setTextColor(if (current == LyricsDisplayMode.SYNCED) accent else muted)
        wordModeButton.setTextColor(if (current == LyricsDisplayMode.WORD_BY_WORD) accent else muted)
        karaokeModeButton.setTextColor(if (current == LyricsDisplayMode.KARAOKE) accent else muted)
        plainModeButton.setTextColor(if (current == LyricsDisplayMode.PLAIN) accent else muted)

        timingBadgeButton?.let { badge ->
            if (document == null) {
                badge.visibility = View.GONE
            } else {
                badge.visibility = View.VISIBLE
                val needsApproxPrompt = document.isSynced && !document.hasWordTimings &&
                    (current == LyricsDisplayMode.WORD_BY_WORD || current == LyricsDisplayMode.KARAOKE)
                when {
                    !document.isSynced -> {
                        badge.setText(R.string.lyrics_badge_not_synced)
                        badge.isClickable = false
                        badge.setTextColor(muted)
                    }
                    document.hasWordTimings -> {
                        badge.setText(R.string.lyrics_badge_word_synced)
                        badge.isClickable = false
                        badge.setTextColor(accent)
                    }
                    needsApproxPrompt -> {
                        badge.setText(
                            if (approximateWordTimingsEnabled) {
                                R.string.lyrics_badge_approx_on
                            } else {
                                R.string.lyrics_badge_approx_off
                            }
                        )
                        badge.isClickable = true
                        badge.setTextColor(accent)
                    }
                    else -> {
                        badge.setText(R.string.lyrics_badge_line_synced)
                        badge.isClickable = false
                        badge.setTextColor(muted)
                    }
                }
            }
        }

        sourceView.text = document?.let {
            getString(
                when (it.source) {
                    LyricsSource.EMBEDDED -> R.string.lyrics_source_embedded
                    LyricsSource.SIDECAR -> R.string.lyrics_source_sidecar
                    LyricsSource.CACHE -> R.string.lyrics_source_cache
                    LyricsSource.LRCLIB -> R.string.lyrics_source_lrclib
                    LyricsSource.USER -> R.string.lyrics_source_user
                }
            )
        }.orEmpty()
    }

    private fun selectMode(mode: LyricsDisplayMode) {
        val document = renderedDocument ?: return
        if (mode != LyricsDisplayMode.PLAIN && !document.isSynced) return
        val targetMode = if (!document.isSynced && mode != LyricsDisplayMode.PLAIN) {
            LyricsDisplayMode.PLAIN
        } else {
            mode
        }
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit()
            .putString(PREF_LYRICS_MODE, mode.name)
            .apply()
        lyricsView.setMode(targetMode)
        updateModeUi()
    }

    /** Lyrics text color: the user's setting, or one derived from the artwork when "default". */
    private fun applyLyricsColors(item: com.deniscerri.ytdl.playback.model.PlaybackItem?) {
        if (item == null) return
        if (coloredForMediaId == item.mediaId) return
        coloredForMediaId = item.mediaId
        lifecycleScope.launch {
            val palette = com.deniscerri.ytdl.util.PlaybackColors.lyricsPalette(
                requireContext(),
                item
            )
            if (viewModel.state.value.item?.mediaId == item.mediaId) {
                lyricsView.setColors(palette.base, palette.active)
            }
        }
    }

    private fun updateOffsetUi() {
        val document = renderedDocument
        val enabled = document != null
        earlierButton.isEnabled = enabled
        laterButton.isEnabled = enabled
        offsetResetButton.visibility =
            if ((document?.offsetMs ?: 0L) != 0L) View.VISIBLE else View.GONE
        offsetView.text = document?.let {
            getString(R.string.lyrics_offset_value, formatOffset(it.offsetMs))
        }.orEmpty()
    }

    private fun formatOffset(offsetMs: Long): String {
        if (offsetMs == 0L) return "0.0s"
        return String.format(Locale.US, "%+.1fs", offsetMs / 1000f)
    }

    private fun bindPlayback(state: PlaybackController.UiState) {
        val duration = currentDuration(state)
        val position = state.positionMs.coerceAtLeast(0L)
        positionView.text = position.toStringTimeStamp()
        durationView.text = duration.toStringTimeStamp()
        playPauseButton.setImageResource(
            if (state.playing) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play
        )
        playPauseButton.contentDescription = getString(
            if (state.playing) R.string.pause else R.string.play
        )
        lyricsView.setPlaybackState(state.playing, state.playbackSpeed)
        lyricsView.setPosition(position)
    }

    private fun currentDuration(
        state: PlaybackController.UiState = PlaybackController.state.value
    ): Long {
        return state.durationMs.takeIf { it > 0L }
            ?: state.currentItem?.durationMs?.takeIf { it > 0L }
            ?: 0L
    }

    private fun toggleSheet() {
        val dialog = dialog as? BottomSheetDialog ?: return
        val behavior = dialog.behavior
        behavior.state = if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            BottomSheetBehavior.STATE_HALF_EXPANDED
        } else {
            BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val behavior = dialog.behavior
        behavior.isFitToContents = false
        behavior.halfExpandedRatio = HALF_EXPANDED_RATIO
        behavior.skipCollapsed = true
        behavior.peekHeight =
            (resources.displayMetrics.heightPixels * HALF_EXPANDED_RATIO).toInt()

        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.layoutParams = sheet.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }

        if (!sheetConfigured) {
            sheetConfigured = true
            behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    updateSheetStateUi(newState)
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            })
            view?.post {
                behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                updateSheetStateUi(behavior.state)
            }
        } else {
            updateSheetStateUi(behavior.state)
        }
    }

    private fun updateSheetStateUi(state: Int) {
        if (!::lyricsView.isInitialized) return
        val expanded = state == BottomSheetBehavior.STATE_EXPANDED
        lyricsView.setFocusFraction(if (expanded) 0.5f else HALF_FOCUS_FRACTION)
        expandButton.setImageResource(
            if (expanded) R.drawable.baseline_expand_more_24 else R.drawable.baseline_expand_less_24
        )
        expandButton.contentDescription = getString(
            if (expanded) R.string.lyrics_collapse else R.string.lyrics_expand
        )
        root.setPadding(0, if (expanded) topInset else 0, 0, 0)
    }

    override fun onDestroyView() {
        renderedDocument = null
        coloredForMediaId = null
        super.onDestroyView()
    }

    companion object {
        private const val HALF_EXPANDED_RATIO = 0.58f
        private const val HALF_FOCUS_FRACTION = 0.33f
        private const val PREF_LYRICS_MODE = "lyrics_display_mode"
        private const val PREF_LYRICS_APPROX_WORDS = "lyrics_approx_word_timings"
    }
}
