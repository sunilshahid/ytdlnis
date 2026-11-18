package com.deniscerri.ytdl.ui.home

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Patterns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.SearchSuggestionItem
import com.deniscerri.ytdl.database.models.SearchSuggestionType
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.compose.home.HomeScreen
import com.deniscerri.ytdl.ui.compose.home.HomeUiState
import com.deniscerri.ytdl.ui.compose.home.ProviderOption
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.util.Extensions.isURL
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HomeFragment hosting the Jetpack Compose [HomeScreen] (Phase 4).
 */
class HomeFragment : ComposeFragment(),
    FailedFetchesBottomSheetDialog.Listener, UpdatesDialog.Listener {

    private lateinit var mainActivity: MainActivity
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private var sharedPreferences: SharedPreferences? = null
    private var notificationUtil: NotificationUtil? = null

    private var uiState by mutableStateOf(HomeUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = activity as MainActivity
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        notificationUtil = NotificationUtil(requireContext())

        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]

        initProviders()
        initObservers()
        handleArguments()
    }

    override fun onResume() {
        super.onResume()
        checkClipboard()
    }

    private fun initProviders() {
        val providers = resources.getStringArray(R.array.search_engines)
        val providersValues = resources.getStringArray(R.array.search_engines_values)
        val providerOptions = providers.indices.map { ProviderOption(providers[it], providersValues[it]) }
        val currentProvider = sharedPreferences?.getString("search_engine", "ytsearch") ?: "ytsearch"
        uiState = uiState.copy(
            providers = providerOptions,
            selectedProvider = currentProvider,
        )
    }

    private fun initObservers() {
        // Collect DB results directly into state
        lifecycleScope.launch {
            resultViewModel.dao.getResultsFlow().collectLatest { results ->
                uiState = uiState.copy(results = results)
            }
        }

        // Collect total count
        lifecycleScope.launch {
            resultViewModel.totalCount.collectLatest { count ->
                val first = resultViewModel.firstResult.value
                val showDownloadAll = count > 1 && !first?.playlistTitle.isNullOrEmpty()
                uiState = uiState.copy(
                    totalCount = count,
                    showDownloadAllFab = showDownloadAll,
                )
            }
        }

        // Collect first result for playlist title
        lifecycleScope.launch {
            resultViewModel.firstResult.collectLatest { first ->
                val count = resultViewModel.totalCount.value
                val showDownloadAll = count > 1 && !first?.playlistTitle.isNullOrEmpty()
                uiState = uiState.copy(showDownloadAllFab = showDownloadAll)
            }
        }

        // Collect playlist categories
        lifecycleScope.launch {
            resultViewModel.playlistResults.collectLatest { playlists ->
                val filtered = playlists.filter { it != "YTDLNIS_SEARCH" }
                uiState = uiState.copy(playlistFilters = filtered)
            }
        }

        // Collect loading / processing state
        lifecycleScope.launch {
            resultViewModel.uiState.collectLatest { resUi ->
                uiState = uiState.copy(isLoading = resUi.processing)
            }
        }
    }

    private fun handleArguments() {
        arguments?.getString("url")?.let { url ->
            arguments?.remove("url")
            val queries = url.split("\n").filter { it.isURL() }.toMutableList()
            if (queries.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    resultViewModel.deleteAll()
                    resultViewModel.parseQueries(queries) {}
                }
            }
        }

        if (arguments?.getBoolean("showDownloadsWithUpdatedFormats") == true) {
            notificationUtil?.cancelDownloadNotification(NotificationUtil.FORMAT_UPDATING_FINISHED_NOTIFICATION_ID)
            arguments?.remove("showDownloadsWithUpdatedFormats")
            CoroutineScope(Dispatchers.IO).launch {
                val ids = arguments?.getLongArray("downloadIds") ?: return@launch
                downloadViewModel.turnDownloadItemsToProcessingDownloads(ids.toList(), deleteExisting = true)
                withContext(Dispatchers.Main) {
                    findNavController().navigate(R.id.downloadMultipleBottomSheetDialog2)
                }
            }
        }
    }

    private fun checkClipboard() {
        val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
        if (!clip.isNullOrBlank() && (Patterns.WEB_URL.matcher(clip).matches() || clip.isURL())) {
            uiState = uiState.copy(copiedUrl = clip)
        } else {
            uiState = uiState.copy(copiedUrl = null)
        }
    }

    private fun updateSearchSuggestions(query: String) {
        if (query.isBlank()) {
            uiState = uiState.copy(suggestions = emptyList())
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val suggestions = mutableListOf<SearchSuggestionItem>()

            // History
            val history = resultViewModel.getSearchHistory()
                .filter { it.query.contains(query, ignoreCase = true) }
                .map { SearchSuggestionItem(it.query, SearchSuggestionType.HISTORY) }
            suggestions.addAll(history)

            // Network Suggestions
            val network = resultViewModel.getSearchSuggestions(query)
                .map { SearchSuggestionItem(it, SearchSuggestionType.SUGGESTION) }
            suggestions.addAll(network)

            withContext(Dispatchers.Main) {
                uiState = uiState.copy(suggestions = suggestions)
            }
        }
    }

    private fun startSearch(query: String) {
        if (query.isBlank()) return

        // Stealth Vault Trigger
        if (com.deniscerri.ytdl.vault.VaultRepository.matchesStealthTrigger(requireContext(), query)) {
            startActivity(com.deniscerri.ytdl.vault.ui.VaultActivity.createIntent(requireContext()))
            return
        }

        uiState = uiState.copy(
            searchQuery = query,
            isSearchActive = false,
        )

        if (sharedPreferences?.getBoolean("incognito", false) != true) {
            resultViewModel.addSearchQueryToHistory(query)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            resultViewModel.deleteAll()

            val isUrl = Patterns.WEB_URL.matcher(query).matches()
            val quickDownload = sharedPreferences?.getBoolean("quick_download", false) == true
            val prefType = sharedPreferences?.getString("preferred_download_type", "video") ?: "video"

            if (quickDownload || prefType == "command") {
                if (isUrl) {
                    if (sharedPreferences?.getBoolean("download_card", true) == true) {
                        withContext(Dispatchers.Main) {
                            showSingleDownloadSheet(
                                resultItem = downloadViewModel.createEmptyResultItem(query),
                                type = DownloadType.valueOf(prefType),
                            )
                        }
                    } else {
                        val downloadItem = downloadViewModel.createDownloadItemFromResult(
                            result = downloadViewModel.createEmptyResultItem(query),
                            givenType = DownloadType.valueOf(prefType),
                        )
                        downloadViewModel.queueDownloads(listOf(downloadItem))
                    }
                } else {
                    resultViewModel.parseQueries(listOf(query)) {}
                }
            } else {
                resultViewModel.parseQueries(listOf(query)) {}
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun showSingleDownloadSheet(
        resultItem: ResultItem,
        type: DownloadType,
        disableUpdateData: Boolean = false,
    ) {
        if (findNavController().currentBackStack.value.firstOrNull { it.destination.id == R.id.downloadBottomSheetDialog } == null &&
            findNavController().currentDestination?.id == R.id.homeFragment
        ) {
            val bundle = Bundle()
            downloadCardViewModel.setResultItem(resultItem)
            downloadCardViewModel.setDownloadItem(null)
            bundle.putSerializable("type", downloadViewModel.getDownloadType(type, resultItem.url))
            if (disableUpdateData) {
                bundle.putBoolean("disableUpdateData", true)
            }
            findNavController().navigate(R.id.downloadBottomSheetDialog, bundle)
        }
    }

    private fun quickDownload(item: ResultItem, type: DownloadType) {
        lifecycleScope.launch(Dispatchers.IO) {
            val downloadItem = downloadViewModel.createDownloadItemFromResult(
                result = item,
                givenType = downloadViewModel.getDownloadType(type, item.url),
            )
            downloadViewModel.queueDownloads(listOf(downloadItem))
        }
    }

    @Composable
    override fun Content() {
        HomeScreen(
            state = uiState,
            onSearchQueryChange = { query ->
                uiState = uiState.copy(searchQuery = query)
                updateSearchSuggestions(query)
            },
            onSearch = { query ->
                startSearch(query)
            },
            onSearchActiveChange = { active ->
                uiState = uiState.copy(isSearchActive = active)
                if (active) {
                    updateSearchSuggestions(uiState.searchQuery)
                }
            },
            onProviderSelected = { provider ->
                sharedPreferences?.edit()?.putString("search_engine", provider)?.apply()
                uiState = uiState.copy(selectedProvider = provider)
            },
            onPlaylistFilterSelected = { filter ->
                resultViewModel.setPlaylistFilter(filter)
                uiState = uiState.copy(selectedPlaylistFilter = filter)
            },
            onSuggestionClick = { text ->
                startSearch(text)
            },
            onSuggestionLongClick = { text ->
                showDeleteHistoryDialog(text)
            },
            onItemClick = { item ->
                if (uiState.isSelectionMode) {
                    val updated = if (uiState.selectedItemIds.contains(item.id)) {
                        uiState.selectedItemIds - item.id
                    } else {
                        uiState.selectedItemIds + item.id
                    }
                    uiState = uiState.copy(
                        selectedItemIds = updated,
                        isSelectionMode = updated.isNotEmpty(),
                    )
                } else {
                    val prefType = sharedPreferences?.getString("preferred_download_type", "video") ?: "video"
                    showSingleDownloadSheet(item, DownloadType.valueOf(prefType))
                }
            },
            onItemLongClick = { item ->
                uiState = uiState.copy(
                    isSelectionMode = true,
                    selectedItemIds = uiState.selectedItemIds + item.id,
                )
            },
            onAudioClick = { item ->
                if (sharedPreferences?.getBoolean("download_card", true) == true) {
                    showSingleDownloadSheet(item, DownloadType.audio)
                } else {
                    quickDownload(item, DownloadType.audio)
                }
            },
            onAudioLongClick = { item ->
                quickDownload(item, DownloadType.audio)
            },
            onVideoClick = { item ->
                if (sharedPreferences?.getBoolean("download_card", true) == true) {
                    showSingleDownloadSheet(item, DownloadType.video)
                } else {
                    quickDownload(item, DownloadType.video)
                }
            },
            onVideoLongClick = { item ->
                quickDownload(item, DownloadType.video)
            },
            onDownloadAllClick = {
                lifecycleScope.launch(Dispatchers.IO) {
                    val allIds = resultViewModel.getAllIds()
                    downloadViewModel.turnDownloadItemsToProcessingDownloads(allIds, deleteExisting = false)
                    withContext(Dispatchers.Main) {
                        findNavController().navigate(R.id.downloadMultipleBottomSheetDialog2)
                    }
                }
            },
            onDownloadSelectedClick = {
                val selected = uiState.selectedItemIds.toList()
                if (selected.isNotEmpty()) {
                    downloadViewModel.turnDownloadItemsToProcessingDownloads(selected, deleteExisting = false)
                    findNavController().navigate(R.id.downloadMultipleBottomSheetDialog2)
                }
            },
            onCopiedUrlClick = { url ->
                startSearch(url)
            },
            onCloseSelectionMode = {
                uiState = uiState.copy(
                    isSelectionMode = false,
                    selectedItemIds = emptySet(),
                )
            },
            onSelectAllClick = {
                lifecycleScope.launch(Dispatchers.IO) {
                    val allIds = resultViewModel.getAllIds().toSet()
                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(selectedItemIds = allIds)
                    }
                }
            },
            onInvertSelectionClick = {
                lifecycleScope.launch(Dispatchers.IO) {
                    val allIds = resultViewModel.getAllIds().toSet()
                    val inverted = allIds - uiState.selectedItemIds
                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(selectedItemIds = inverted)
                    }
                }
            },
            onDeleteSelectedClick = {
                showDeleteSelectedDialog()
            },
        )
    }

    private fun showDeleteHistoryDialog(text: String) {
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + text + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            resultViewModel.removeSearchQueryFromHistory(text)
            updateSearchSuggestions(uiState.searchQuery)
        }
        deleteDialog.show()
    }

    private fun showDeleteSelectedDialog() {
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            val selected = uiState.selectedItemIds.toList()
            resultViewModel.deleteSelected(selected)
            uiState = uiState.copy(
                isSelectionMode = false,
                selectedItemIds = emptySet(),
            )
        }
        deleteDialog.show()
    }

    fun scrollToTop() {
        // Compose scroll-to-top hook
    }

    override fun onDownloadAnyway(url: String) {
        lifecycleScope.launch {
            val type = DownloadType.valueOf(sharedPreferences?.getString("preferred_download_type", "video") ?: "video")
            if (sharedPreferences?.getBoolean("download_card", true) == true) {
                withContext(Dispatchers.Main) {
                    showSingleDownloadSheet(
                        resultItem = downloadViewModel.createEmptyResultItem(url),
                        type = type,
                        disableUpdateData = true,
                    )
                }
            } else {
                val downloadItem = downloadViewModel.createDownloadItemFromResult(
                    result = downloadViewModel.createEmptyResultItem(url),
                    givenType = type,
                )
                downloadViewModel.queueDownloads(listOf(downloadItem))
            }
        }
    }

    override fun onUpdateSelected(update: ResultViewModel.AvailableUpdate) {
        val activity = requireActivity() as MainActivity
        when (update) {
            is ResultViewModel.AvailableUpdate.App -> UiUtil.showNewAppUpdateDialog(
                update.release, activity, update.updateUtil, activity,
                PreferenceManager.getDefaultSharedPreferences(activity), activity.installLauncher,
            )
            is ResultViewModel.AvailableUpdate.Package -> UiUtil.showNewReleaseUpdateDialog(
                update.release, update.item, activity, activity,
                activity.findViewById(R.id.frame_layout),
                activity.findViewById(R.id.bottomNavigationView),
                activity.installLauncher,
            ) { result ->
                result.onSuccess {
                    resultViewModel.dismissUpdate(update)
                    RuntimeManager.reInit(activity)
                }.onFailure { f ->
                    Snackbar.make(
                        activity.findViewById(R.id.frame_layout),
                        f.message ?: "",
                        Snackbar.LENGTH_LONG,
                    ).apply {
                        anchorView = activity.findViewById(R.id.bottomNavigationView)
                        show()
                    }
                }
            }
        }
    }

    private fun showFailedFetches() {
        if (childFragmentManager.findFragmentByTag("failedFetches") != null) return
        FailedFetchesBottomSheetDialog().show(childFragmentManager, "failedFetches")
    }
}
