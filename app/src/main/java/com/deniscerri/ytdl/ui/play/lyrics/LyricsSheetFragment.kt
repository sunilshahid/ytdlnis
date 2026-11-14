package com.deniscerri.ytdl.ui.play.lyrics

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.lyrics.LyricsDocument
import com.deniscerri.ytdl.lyrics.LyricsProviderRegistry
import com.deniscerri.ytdl.lyrics.LyricsSource
import com.deniscerri.ytdl.lyrics.model.LyricsCandidate
import com.deniscerri.ytdl.lyrics.romanize.Romanizer
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch

/**
 * Full-screen, smoothly animated lyrics experience with back navigation at the top-left,
 * immersion auto-hide, offline romanization, and floating offset adjusters.
 */
class LyricsSheetFragment : DialogFragment() {

    private lateinit var viewModel: LyricsViewModel

    private lateinit var root: View
    private lateinit var lyricsView: KaraokeLyricsView
    private lateinit var titleView: TextView
    private lateinit var artistView: TextView
    private lateinit var sourceView: TextView
    private lateinit var promptContainer: View
    private lateinit var promptProgress: CircularProgressIndicator
    private lateinit var promptIcon: ImageView
    private lateinit var promptMessage: TextView
    private lateinit var promptHint: TextView
    private lateinit var promptActions: View
    private lateinit var searchButton: MaterialButton
    private lateinit var importButton: MaterialButton
    private lateinit var romanizeButton: MaterialButton
    private var moreButton: ImageButton? = null
    private lateinit var focusModeButton: MaterialButton
    private lateinit var syncedModeButton: MaterialButton
    private lateinit var wordModeButton: MaterialButton
    private lateinit var karaokeModeButton: MaterialButton
    private var timingBadgeButton: MaterialButton? = null
    private var collapsibleHeader: View? = null

    // Floating offset pill views
    private var offsetPill: View? = null
    private var offsetMinusButton: MaterialButton? = null
    private var offsetResetButton: MaterialButton? = null
    private var offsetPlusButton: MaterialButton? = null
    private var offsetCloseButton: View? = null

    private var approximateWordTimingsEnabled = false
    private var romanizeEnabled = false
    private var isHeaderCollapsed = false
    private var isPlaying = false

    private val immersionHandler = Handler(Looper.getMainLooper())
    private val immersionHideRunnable = Runnable { hideHeaderImmersion() }

    private var renderedDocument: LyricsDocument? = null
    private var coloredForMediaId: String? = null

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
        promptContainer = view.findViewById(R.id.lyrics_sheet_prompt)
        promptProgress = view.findViewById(R.id.lyrics_sheet_progress)
        promptIcon = view.findViewById(R.id.lyrics_sheet_prompt_icon)
        promptMessage = view.findViewById(R.id.lyrics_sheet_message)
        promptHint = view.findViewById(R.id.lyrics_sheet_hint)
        promptActions = view.findViewById(R.id.lyrics_sheet_actions)
        searchButton = view.findViewById(R.id.lyrics_sheet_search)
        importButton = view.findViewById(R.id.lyrics_sheet_import)
        romanizeButton = view.findViewById(R.id.lyrics_sheet_romanize_toggle)
        moreButton = view.findViewById(R.id.lyrics_sheet_more)
        collapsibleHeader = view.findViewById(R.id.lyrics_sheet_collapsible_header)
        focusModeButton = view.findViewById(R.id.lyrics_sheet_mode_focus)
        syncedModeButton = view.findViewById(R.id.lyrics_sheet_mode_synced)
        wordModeButton = view.findViewById(R.id.lyrics_sheet_mode_word)
        karaokeModeButton = view.findViewById(R.id.lyrics_sheet_mode_karaoke)
        timingBadgeButton = view.findViewById(R.id.lyrics_sheet_timing_badge)

        // Floating offset pill views
        offsetPill = view.findViewById(R.id.lyrics_sheet_offset_pill)
        offsetMinusButton = view.findViewById(R.id.lyrics_offset_minus)
        offsetResetButton = view.findViewById(R.id.lyrics_offset_reset)
        offsetPlusButton = view.findViewById(R.id.lyrics_offset_plus)
        offsetCloseButton = view.findViewById(R.id.lyrics_offset_close)

        view.findViewById<View>(R.id.lyrics_sheet_back).setOnClickListener { dismiss() }
        searchButton.setOnClickListener { showManualSearchDialog() }
        importButton.setOnClickListener { importLyricsLauncher.launch(arrayOf("*/*")) }
        moreButton?.setOnClickListener { v -> showOptionsMenu(v) }
        focusModeButton.setOnClickListener { selectMode(LyricsDisplayMode.SIMPLE) }
        syncedModeButton.setOnClickListener { selectMode(LyricsDisplayMode.SYNCED) }
        wordModeButton.setOnClickListener { selectMode(LyricsDisplayMode.WORD_BY_WORD) }
        karaokeModeButton.setOnClickListener { selectMode(LyricsDisplayMode.KARAOKE) }

        val lyricsPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        approximateWordTimingsEnabled = lyricsPrefs.getBoolean(PREF_LYRICS_APPROX_WORDS, false)
        romanizeEnabled = lyricsPrefs.getBoolean(PREF_LYRICS_ROMANIZE, false)
        val scrollAnchor = lyricsPrefs.getFloat(PREF_LYRICS_SCROLL_ANCHOR, 0.35f)
        val fontScale = lyricsPrefs.getFloat(PREF_LYRICS_FONT_SCALE, 1.0f)
        lyricsView.setFocusFraction(scrollAnchor)
        lyricsView.setTextScale(fontScale)
        lyricsView.setApproximateWordTimings(approximateWordTimingsEnabled)
        lyricsView.setShowRomanization(romanizeEnabled)

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

        romanizeButton.setOnClickListener {
            val doc = renderedDocument ?: return@setOnClickListener
            romanizeEnabled = !romanizeEnabled
            lyricsPrefs.edit().putBoolean(PREF_LYRICS_ROMANIZE, romanizeEnabled).apply()
            applyRomanization(doc, romanizeEnabled)
            updateModeUi()
        }

        offsetMinusButton?.setOnClickListener { viewModel.changeOffset(-500L) }
        offsetPlusButton?.setOnClickListener { viewModel.changeOffset(500L) }
        offsetResetButton?.setOnClickListener { viewModel.resetOffset() }
        offsetCloseButton?.setOnClickListener { offsetPill?.visibility = View.GONE }

        lyricsView.onLineClickListener = { line ->
            val start = line.startMs
            if (start != null) {
                val offset = renderedDocument?.offsetMs ?: 0L
                PlaybackController.seekTo((start + offset).coerceAtLeast(0L))
            }
        }
        lyricsView.onLyricsClickListener = null
        lyricsView.onScrollDirectionListener = { isScrollingUpToHeader ->
            if (isScrollingUpToHeader) {
                showHeaderImmersion()
            } else {
                hideHeaderImmersion()
            }
        }
        view.findViewById<View>(R.id.lyrics_sheet_top_bar).setOnClickListener { showHeaderImmersion() }
        titleView.setOnClickListener { showHeaderImmersion() }
        artistView.setOnClickListener { showHeaderImmersion() }
        root.setOnClickListener { showHeaderImmersion() }

        val playback = PlaybackController.state.value
        viewModel.onItemChanged(playback.currentItem, playback.durationMs)
        observe()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setWindowAnimations(R.style.LyricsFullScreenAnimation)
            if (isPlaying) {
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
                        val wasPlaying = isPlaying
                        isPlaying = playback.playing
                        updateKeepScreenOn(isPlaying)
                        lyricsView.setPlaybackState(playback.playing, playback.playbackSpeed)
                        lyricsView.setPosition(playback.positionMs)

                        if (isPlaying && !wasPlaying) {
                            scheduleImmersionHide()
                        } else if (!isPlaying && wasPlaying) {
                            showHeaderImmersion()
                        }
                    }
                }
            }
        }
    }

    private fun showOptionsMenu(anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
        val offsetItem = popup.menu.add(0, 1, 0, R.string.lyrics_offset)
        val offsetMs = renderedDocument?.offsetMs ?: 0L
        if (offsetMs != 0L) {
            val sign = if (offsetMs > 0) "+" else ""
            val sec = String.format(java.util.Locale.US, "%.1fs", offsetMs / 1000.0)
            offsetItem.title = getString(R.string.lyrics_offset_value, "$sign$sec")
        }
        popup.menu.add(0, 2, 1, R.string.lyrics_search_online)
        popup.menu.add(0, 3, 2, R.string.lyrics_import_file)
        popup.menu.add(0, 4, 3, R.string.lyrics_settings)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    offsetPill?.let { pill ->
                        pill.visibility = if (pill.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    true
                }
                2 -> {
                    showManualSearchDialog()
                    true
                }
                3 -> {
                    importLyricsLauncher.launch(arrayOf("*/*"))
                    true
                }
                4 -> {
                    showLyricsSettingsDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun applyRomanization(doc: LyricsDocument, enabled: Boolean) {
        lyricsView.setShowRomanization(enabled)
        val preparedDoc = if (enabled && Romanizer.canRomanizeDocument(doc)) {
            Romanizer.romanizeDocument(doc)
        } else {
            doc
        }
        renderedDocument = preparedDoc
        lyricsView.setDocument(preparedDoc)
    }

    /** True when both documents hold the same lyrics text (only the offset differs). */
    private fun isSameLyricsContent(a: LyricsDocument?, b: LyricsDocument?): Boolean {
        if (a == null || b == null) return false
        if (a === b) return true
        if (a.mediaId != b.mediaId || a.lines.size != b.lines.size) return false
        return a.lines.zip(b.lines).all { (x, y) -> x.text == y.text }
    }

    private fun scheduleImmersionHide() {
        immersionHandler.removeCallbacks(immersionHideRunnable)
        if (!isPlaying || renderedDocument?.isSynced != true) return
        immersionHandler.postDelayed(immersionHideRunnable, 3500L)
    }

    private fun hideHeaderImmersion() {
        if (isHeaderCollapsed || !isPlaying || renderedDocument?.isSynced != true) return
        isHeaderCollapsed = true
        collapsibleHeader?.let { header ->
            header.animate()
                .translationY(-header.height.toFloat())
                .alpha(0f)
                .setDuration(350L)
                .withEndAction {
                    if (isHeaderCollapsed) {
                        header.visibility = View.GONE
                    }
                }
                .start()
        }
    }

    private fun showHeaderImmersion() {
        immersionHandler.removeCallbacks(immersionHideRunnable)
        if (!isHeaderCollapsed) {
            if (isPlaying) scheduleImmersionHide()
            return
        }
        isHeaderCollapsed = false
        collapsibleHeader?.let { header ->
            header.visibility = View.VISIBLE
            header.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(280L)
                .start()
        }
        if (isPlaying) {
            scheduleImmersionHide()
        }
    }

    private fun updateOffsetUi(offsetMs: Long) {
        val text = if (offsetMs == 0L) {
            getString(R.string.lyrics_offset)
        } else {
            val sign = if (offsetMs > 0) "+" else ""
            val sec = String.format(java.util.Locale.US, "%.1fs", offsetMs / 1000.0)
            getString(R.string.lyrics_offset_value, "$sign$sec")
        }
        offsetResetButton?.text = text
    }

    private fun renderLyrics(state: LyricsUiState) {
        val item = state.item
        titleView.text = item?.title?.takeIf { it.isNotBlank() } ?: getString(R.string.lyrics)
        artistView.text = item?.artist?.takeIf { it.isNotBlank() }.orEmpty()

        val document = state.document
        if (document !== renderedDocument) {
            if (document == null) {
                renderedDocument = null
                lyricsView.visibility = View.INVISIBLE
                lyricsView.setDocument(null)
                updateModeUi()
            } else if (isSameLyricsContent(document, renderedDocument)) {
                // Offset-only change: the text/wrap/romanization are identical, so keep the
                // layout, colors and scroll position and just re-anchor to the new offset.
                val prepared = renderedDocument!!.withOffset(document.offsetMs)
                renderedDocument = prepared
                lyricsView.updateOffset(prepared)
                updateOffsetUi(prepared.offsetMs)
            } else {
                lyricsView.visibility = View.VISIBLE
                val savedModeName = androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(requireContext())
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
                lyricsView.setMode(initialMode)
                applyRomanization(document, romanizeEnabled)
                updateModeUi()
                if (isPlaying) {
                    scheduleImmersionHide()
                }
            }
        }
        updateOffsetUi(document?.offsetMs ?: 0L)

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

        val canRomanize = document != null && Romanizer.canRomanizeDocument(document)
        romanizeButton.visibility = if (canRomanize) View.VISIBLE else View.GONE
        romanizeButton.setTextColor(if (romanizeEnabled) accent else muted)

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

        sourceView.text = document?.let { doc ->
            val providerDisplay = doc.providerName
                ?: doc.providerId?.let { pid -> LyricsProviderRegistry.getProvider(pid)?.displayName ?: pid }
            when {
                providerDisplay != null -> getString(R.string.lyrics_source_from, providerDisplay)
                doc.source == LyricsSource.ONLINE -> getString(R.string.lyrics_source_online)
                doc.source == LyricsSource.EMBEDDED -> getString(R.string.lyrics_source_embedded)
                doc.source == LyricsSource.SIDECAR -> getString(R.string.lyrics_source_sidecar)
                doc.source == LyricsSource.CACHE -> getString(R.string.lyrics_source_cache)
                doc.source == LyricsSource.LRCLIB -> getString(R.string.lyrics_source_lrclib)
                doc.source == LyricsSource.USER -> getString(R.string.lyrics_source_user)
                else -> ""
            }
        }.orEmpty()
    }

    private fun selectMode(mode: LyricsDisplayMode) {
        val document = renderedDocument ?: return
        if (!document.isSynced) return
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit()
            .putString(PREF_LYRICS_MODE, mode.name)
            .apply()
        lyricsView.setMode(mode)
        updateModeUi()
        scheduleImmersionHide()
    }

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

    private fun showManualSearchDialog() {
        val currentItem = viewModel.state.value.item
        val context = requireContext()
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }

        val titleInput = com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = getString(R.string.lyrics_search_title_hint)
            setText(currentItem?.title.orEmpty())
            isSingleLine = true
        }
        val titleLayout = com.google.android.material.textfield.TextInputLayout(context).apply {
            addView(titleInput)
        }
        container.addView(titleLayout)

        val artistInput = com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = getString(R.string.lyrics_search_artist_hint)
            setText(currentItem?.artist.orEmpty())
            isSingleLine = true
        }
        val artistLayout = com.google.android.material.textfield.TextInputLayout(context).apply {
            val topMargin = (8 * resources.displayMetrics.density).toInt()
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, topMargin, 0, 0) }
            addView(artistInput)
        }
        container.addView(artistLayout)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.lyrics_search)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.search) { _, _ ->
                val searchTitle = titleInput.text?.toString()?.trim().orEmpty()
                val searchArtist = artistInput.text?.toString()?.trim()
                if (searchTitle.isBlank()) return@setPositiveButton
                performSearch(searchTitle, searchArtist)
            }
            .show()
    }

    private fun performSearch(title: String, artist: String?) {
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.lyrics_searching_providers)
            .setCancelable(false)
            .create()
        progressDialog.show()

        // Dismiss the progress dialog when the view lifecycle dies (sheet closed mid-search),
        // otherwise the dialog window leaks.
        val lifecycleObserver = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                if (progressDialog.isShowing) progressDialog.dismiss()
                owner.lifecycle.removeObserver(this)
            }
        }
        viewLifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val candidates = viewModel.findCandidates(title, artist)

                if (!isAdded) return@launch

                if (candidates.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.lyrics_not_found, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                showCandidatesDialog(candidates)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // A cancelled search is not "not found": propagate so the scope stays healthy.
                throw e
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(requireContext(), R.string.lyrics_not_found, Toast.LENGTH_SHORT).show()
                }
            } finally {
                viewLifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                if (progressDialog.isShowing) {
                    progressDialog.dismiss()
                }
            }
        }
    }

    private fun showCandidatesDialog(candidates: List<LyricsCandidate>) {
        val currentDurationSec = (renderedDocument?.lines?.lastOrNull()?.endMs?.let { it / 1000L }
            ?: (PlaybackController.state.value.durationMs / 1000L)).toInt()

        val adapter = object : android.widget.ArrayAdapter<LyricsCandidate>(requireContext(), 0, candidates) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: LayoutInflater.from(context).inflate(R.layout.lyrics_search_result_item, parent, false)
                val c = getItem(position)!!

                row.findViewById<TextView>(R.id.result_title).text = c.trackName
                row.findViewById<TextView>(R.id.result_subtitle).text =
                    listOfNotNull(c.artistName?.takeIf { it.isNotBlank() }, c.albumName?.takeIf { it.isNotBlank() })
                        .joinToString(" • ")

                val info = mutableListOf<String>()
                info.add(c.providerName)
                info.add(
                    when {
                        c.hasWordTimings -> getString(R.string.lyrics_badge_word_synced)
                        c.isSynced -> getString(R.string.lyrics_synced)
                        else -> getString(R.string.lyrics_plain)
                    }
                )

                if (c.durationSec > 0) {
                    var runtime = (c.durationSec * 1000L).toStringTimeStamp()
                    if (currentDurationSec > 0) {
                        val diff = c.durationSec - currentDurationSec
                        runtime += if (diff == 0) " (=)" else " (${if (diff > 0) "+" else ""}${diff}s)"
                    }
                    info.add(runtime)
                }
                row.findViewById<TextView>(R.id.result_info).text = info.joinToString(" • ")

                row.findViewById<TextView>(R.id.result_preview).apply {
                    text = c.preview
                    visibility = if (c.preview.isNotBlank()) View.VISIBLE else View.GONE
                }
                return row
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lyrics)
            .setAdapter(adapter) { _, which ->
                viewModel.applyCandidate(candidates[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLyricsSettingsDialog() {
        val context = requireContext()
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val density = resources.displayMetrics.density

        val scrollView = android.widget.ScrollView(context)
        val rootLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (20 * density).toInt()
            setPadding(pad, pad / 2, pad, pad)
        }
        scrollView.addView(rootLayout)

        fun createHeader(titleRes: Int): TextView {
            return TextView(context).apply {
                setText(titleRes)
                setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                val primary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.WHITE)
                setTextColor(primary)
                val topMargin = (16 * density).toInt()
                val bottomMargin = (8 * density).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, topMargin, 0, bottomMargin) }
            }
        }

        // Section 1: Scroll Anchor
        rootLayout.addView(createHeader(R.string.lyrics_scroll_anchor).apply {
            (layoutParams as android.widget.LinearLayout.LayoutParams).topMargin = 0
        })

        val currentAnchor = prefs.getFloat(PREF_LYRICS_SCROLL_ANCHOR, 0.35f)
        val anchorGroup = android.widget.RadioGroup(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        val anchorOptions = listOf(
            Triple(0.35f, R.string.lyrics_scroll_anchor_center, 1001),
            Triple(0.20f, R.string.lyrics_scroll_anchor_top, 1002),
            Triple(0.50f, R.string.lyrics_scroll_anchor_middle, 1003)
        )
        anchorOptions.forEach { (fraction, labelRes, id) ->
            val rb = com.google.android.material.radiobutton.MaterialRadioButton(context).apply {
                this.id = id
                setText(labelRes)
                isChecked = kotlin.math.abs(currentAnchor - fraction) < 0.05f
            }
            anchorGroup.addView(rb)
        }
        anchorGroup.setOnCheckedChangeListener { _, checkedId ->
            val chosen = anchorOptions.firstOrNull { it.third == checkedId }?.first ?: 0.35f
            prefs.edit().putFloat(PREF_LYRICS_SCROLL_ANCHOR, chosen).apply()
            lyricsView.setFocusFraction(chosen)
        }
        rootLayout.addView(anchorGroup)

        // Section 2: Text Size
        rootLayout.addView(createHeader(R.string.lyrics_text_size))
        val currentScale = prefs.getFloat(PREF_LYRICS_FONT_SCALE, 1.0f)
        val sizeGroup = android.widget.RadioGroup(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        val sizeOptions = listOf(
            Triple(1.0f, R.string.lyrics_text_size_normal, 2001),
            Triple(1.15f, R.string.lyrics_text_size_large, 2002),
            Triple(1.30f, R.string.lyrics_text_size_xlarge, 2003)
        )
        sizeOptions.forEach { (scale, labelRes, id) ->
            val rb = com.google.android.material.radiobutton.MaterialRadioButton(context).apply {
                this.id = id
                setText(labelRes)
                isChecked = kotlin.math.abs(currentScale - scale) < 0.05f
            }
            sizeGroup.addView(rb)
        }
        sizeGroup.setOnCheckedChangeListener { _, checkedId ->
            val chosen = sizeOptions.firstOrNull { it.third == checkedId }?.first ?: 1.0f
            prefs.edit().putFloat(PREF_LYRICS_FONT_SCALE, chosen).apply()
            lyricsView.setTextScale(chosen)
        }
        rootLayout.addView(sizeGroup)

        // Section 3: Lyrics Providers
        rootLayout.addView(createHeader(R.string.lyrics_providers))

        val providerContainer = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        rootLayout.addView(providerContainer)

        val currentProviders = LyricsProviderRegistry.ordered(context).toMutableList()

        fun refreshProviders() {
            providerContainer.removeAllViews()
            currentProviders.forEachIndexed { index, provider ->
                val row = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
                }

                val toggle = com.google.android.material.materialswitch.MaterialSwitch(context).apply {
                    isChecked = provider.isEnabled(context)
                    setOnCheckedChangeListener { _, isChecked ->
                        prefs.edit().putBoolean("lyrics_provider_${provider.id}", isChecked).apply()
                    }
                }

                val info = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        marginStart = (12 * density).toInt()
                        marginEnd = (8 * density).toInt()
                    }
                }

                val nameView = TextView(context).apply {
                    text = provider.displayName
                    setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
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
                    val muted = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
                    setTextColor(muted)
                }
                info.addView(nameView)
                if (summaryText.isNotEmpty()) info.addView(summaryView)

                val upBtn = ImageButton(context).apply {
                    setImageResource(R.drawable.ic_up)
                    setBackgroundResource(android.R.color.transparent)
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
            }
        }
        refreshProviders()

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.lyrics_settings)
            .setView(scrollView)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        immersionHandler.removeCallbacks(immersionHideRunnable)
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
