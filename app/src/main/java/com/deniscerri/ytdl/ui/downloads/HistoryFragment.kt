package com.deniscerri.ytdl.ui.downloads

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager.SORTING
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.repository.HistoryRepository.HistorySortType
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.ui.compose.downloads.HistoryDownloadScreen
import com.deniscerri.ytdl.ui.compose.downloads.collectAsPagingItems
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NavbarUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.vault.ingest.VaultImporter
import com.deniscerri.ytdl.vault.ui.VaultImportActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Paged download history hosted in Jetpack Compose (Phase 5).
 * Replaces `fragment_history.xml` and `HistoryPaginatedAdapter`.
 */
class HistoryFragment : ComposeFragment() {

    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var sharedPreferences: SharedPreferences

    private var selectedIds by mutableStateOf<Set<Long>>(emptySet())
    private var searchQuery by mutableStateOf("")
    private var useDownloadCardDesign by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        useDownloadCardDesign = sharedPreferences.getBoolean("history_use_download_card_design", false)

        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]

        lifecycleScope.launch {
            downloadViewModel.alreadyExistsUiState.collectLatest { res ->
                if (res.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val bundle = bundleOf(Pair("duplicates", res))
                        findNavController().navigate(R.id.action_historyFragment_to_downloadsAlreadyExistDialog, bundle)
                    }
                    downloadViewModel.alreadyExistsUiState.value = mutableListOf()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mainActivity = activity as? MainActivity
        val bottomNav = mainActivity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigationView)
        val hasNavTab = bottomNav?.menu?.findItem(R.id.historyFragment)?.isVisible == true
        val isInNavBar = hasNavTab || NavbarUtil.getNavBarItems(requireActivity()).any { n -> n.itemId == R.id.historyFragment && n.isVisible }
        if (!isInNavBar) {
            mainActivity?.hideBottomNavigation()
        }
    }

    fun scrollToTop() {
        // Compose scroll handled within screen state
    }

    fun openSearchView() {
        // Trigger search UI
    }

    @Composable
    override fun Content() {
        val pagingItems = historyViewModel.paginatedItems.collectAsPagingItems(DIFF_CALLBACK)
        val totalCount by historyViewModel.totalCount.collectAsStateWithLifecycle(0)
        val sortOrder by historyViewModel.sortOrder.collectAsStateWithLifecycle(SORTING.DESC)
        val sortType by historyViewModel.sortType.collectAsStateWithLifecycle(HistorySortType.DATE)
        val statusFilter by historyViewModel.statusFilter.collectAsStateWithLifecycle(HistoryViewModel.HistoryStatus.ALL)
        val websiteFilter by historyViewModel.websiteFilter.collectAsStateWithLifecycle("")
        val filterBadgeCount by historyViewModel.filterCount.collectAsStateWithLifecycle(0)
        val websitesRaw by historyViewModel.websites.collectAsStateWithLifecycle(emptyList())

        val websiteList = remember(websitesRaw) {
            websitesRaw.filter { it.isNotBlank() && it != "null" }.distinct()
        }

        val showingDownloadQueue = remember {
            NavbarUtil.getNavBarItems(requireContext()).any { n -> n.itemId == R.id.downloadQueueMainFragment && n.isVisible }
        }

        val enableSwipe = remember {
            val gestures = sharedPreferences.getStringSet(
                "swipe_gesture",
                requireContext().resources.getStringArray(R.array.swipe_gestures_values).toSet()
            ) ?: emptySet()
            gestures.contains("history")
        }

        val coroutineScope = rememberCoroutineScope()

        HistoryDownloadScreen(
            items = pagingItems,
            totalCount = totalCount,
            selectedIds = selectedIds,
            searchQuery = searchQuery,
            sortType = sortType,
            sortOrder = sortOrder,
            typeFilter = "",
            statusFilter = statusFilter,
            websiteFilter = websiteFilter,
            websiteList = websiteList,
            filterBadgeCount = filterBadgeCount,
            useDownloadCardDesign = useDownloadCardDesign,
            enableSwipe = enableSwipe,
            showDownloadQueueButton = !showingDownloadQueue,
            onSearchQueryChange = { query ->
                searchQuery = query
                historyViewModel.setQueryFilter(query)
            },
            onSortChange = { sort ->
                historyViewModel.setSorting(sort)
            },
            onTypeFilterChange = { type ->
                historyViewModel.setTypeFilter(type)
            },
            onStatusFilterChange = { status ->
                historyViewModel.setStatusFilter(status)
            },
            onWebsiteFilterChange = { site ->
                historyViewModel.setWebsiteFilter(site)
            },
            onToggleCardDesign = {
                useDownloadCardDesign = !useDownloadCardDesign
                sharedPreferences.edit().putBoolean("history_use_download_card_design", useDownloadCardDesign).apply()
            },
            onCardClick = { item ->
                val filePresent = item.downloadPath.isNotEmpty() && item.downloadPath.any { File(it).exists() }
                UiUtil.showHistoryItemDetailsCard(
                    item,
                    requireActivity(),
                    filePresent,
                    sharedPreferences,
                    removeItem = { itm, deleteFile ->
                        historyViewModel.delete(itm, deleteFile)
                    },
                    redownloadItem = { itm ->
                        val downloadItem = downloadViewModel.createDownloadItemFromHistory(itm)
                        runBlocking {
                            if (!filePresent) {
                                historyViewModel.delete(itm, false)
                            }
                            downloadViewModel.queueDownloads(listOf(downloadItem), ignoreDuplicates = true)
                        }
                        historyViewModel.delete(itm, false)
                    },
                    redownloadShowDownloadCard = { itm ->
                        downloadCardViewModel.setResultItem(downloadViewModel.createResultItemFromHistory(itm))
                        downloadCardViewModel.setDownloadItem(null)
                        findNavController().navigate(
                            R.id.downloadBottomSheetDialog,
                            bundleOf(
                                Pair("type", itm.type),
                                Pair("ignore_duplicates", true),
                            )
                        )
                    }
                )
            },
            onCardLongClick = { item ->
                selectedIds = if (selectedIds.contains(item.id)) {
                    selectedIds - item.id
                } else {
                    selectedIds + item.id
                }
            },
            onSelectToggle = { id ->
                selectedIds = if (selectedIds.contains(id)) {
                    selectedIds - id
                } else {
                    selectedIds + id
                }
            },
            onSelectAll = {
                coroutineScope.launch {
                    val allIds = withContext(Dispatchers.IO) {
                        historyViewModel.getItemIDsNotPresentIn(emptyList())
                    }
                    selectedIds = allIds.toSet()
                }
            },
            onInvertSelection = {
                coroutineScope.launch {
                    val inverted = withContext(Dispatchers.IO) {
                        historyViewModel.getItemIDsNotPresentIn(selectedIds.toList())
                    }
                    selectedIds = inverted.toSet()
                }
            },
            onSelectBetween = {
                if (selectedIds.size == 2) {
                    coroutineScope.launch {
                        val sorted = selectedIds.sorted()
                        val middle = withContext(Dispatchers.IO) {
                            historyViewModel.getIDsBetweenTwoItems(sorted.first(), sorted.last())
                        }
                        selectedIds = selectedIds + middle
                    }
                }
            },
            onClearSelection = {
                selectedIds = emptySet()
            },
            onActionClick = { item ->
                val quickAction = sharedPreferences.getString("history_quick_action", "share")
                UiUtil.openOrShareFiles(requireActivity(), item.downloadPath, quickAction == "open")
            },
            onSwipeLeft = { item ->
                UiUtil.showRemoveHistoryItemDialog(
                    item,
                    requireActivity(),
                    delete = { itm, deleteFile ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                historyViewModel.delete(itm, deleteFile)
                            }
                            if (!deleteFile) {
                                view?.let { v ->
                                    Snackbar.make(
                                        v,
                                        getString(R.string.you_are_going_to_delete) + ": " + itm.title,
                                        Snackbar.LENGTH_INDEFINITE
                                    ).setAction(getString(R.string.undo)) {
                                        historyViewModel.insert(itm)
                                    }.show()
                                }
                            }
                        }
                    }
                )
            },
            onSwipeRight = { item ->
                downloadCardViewModel.setResultItem(downloadViewModel.createResultItemFromHistory(item))
                downloadCardViewModel.setDownloadItem(null)
                findNavController().navigate(
                    R.id.downloadBottomSheetDialog,
                    bundleOf(
                        Pair("type", item.type),
                        Pair("ignore_duplicates", true)
                    )
                )
            },
            onNavigateToQueue = {
                findNavController().navigate(R.id.downloadQueueMainFragment)
            },
            onClearAllHistory = { deleteFiles ->
                historyViewModel.deleteAll(deleteFiles)
            },
            onClearDeletedHistory = {
                historyViewModel.clearDeleted()
            },
            onClearDuplicateHistory = {
                historyViewModel.deleteDuplicates()
            },
            onCopyAllUrls = {
                coroutineScope.launch {
                    val urls = withContext(Dispatchers.IO) {
                        historyViewModel.getURLS()
                    }
                    UiUtil.copyToClipboard(urls.distinct().joinToString("\n"), requireActivity())
                }
            },
            onDeleteSelected = { deleteFiles ->
                val toDelete = selectedIds.toList()
                selectedIds = emptySet()
                coroutineScope.launch {
                    historyViewModel.deleteAllWithIDs(toDelete, deleteFiles)
                }
            },
            onShareSelected = {
                val toShare = selectedIds.toList()
                selectedIds = emptySet()
                coroutineScope.launch {
                    val paths = withContext(Dispatchers.IO) {
                        historyViewModel.getDownloadPathsFromIDs(toShare)
                    }
                    FileUtil.shareFileIntent(requireContext(), paths.flatten())
                }
            },
            onCopySelectedUrls = {
                val toCopy = selectedIds.toList()
                selectedIds = emptySet()
                coroutineScope.launch {
                    val urls = withContext(Dispatchers.IO) {
                        historyViewModel.getURLsFromIDs(toCopy)
                    }
                    UiUtil.copyToClipboard(urls.distinct().joinToString("\n"), requireActivity())
                }
            },
            onMoveSelectedToVault = {
                val toMove = selectedIds.toList()
                selectedIds = emptySet()
                coroutineScope.launch {
                    val requests = withContext(Dispatchers.IO) {
                        toMove.mapNotNull { id ->
                            val itm = historyViewModel.getByID(id) ?: return@mapNotNull null
                            if (itm.type != DownloadType.video) return@mapNotNull null
                            val localPath = itm.downloadPath.firstOrNull { path ->
                                !path.startsWith("content://") && File(path).let { f -> f.exists() && f.isFile }
                            }
                            val contentUri = if (localPath == null) {
                                itm.downloadPath.firstOrNull { it.startsWith("content://") }
                                    ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                            } else null
                            if (localPath == null && contentUri == null) return@mapNotNull null
                            VaultImporter.MoveSourceRequest(
                                title = itm.title,
                                filePath = localPath,
                                contentUri = contentUri,
                                originalFileName = localPath?.let { File(it).name },
                                playbackMediaId = "dl:${itm.id}",
                                historyId = itm.id
                            )
                        }
                    }
                    if (requests.isEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            R.string.vault_no_local_videos_selected,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        startActivity(VaultImportActivity.createBatchIntent(requireContext(), requests))
                    }
                }
            },
            onRedownloadSelected = {
                val toRedownload = selectedIds.toList()
                selectedIds = emptySet()
                coroutineScope.launch {
                    if (toRedownload.size == 1) {
                        val tmp = withContext(Dispatchers.IO) {
                            historyViewModel.getByID(toRedownload.first())
                        }
                        tmp?.let {
                            downloadCardViewModel.setResultItem(downloadViewModel.createResultItemFromHistory(it))
                            downloadCardViewModel.setDownloadItem(null)
                            findNavController().navigate(
                                R.id.downloadBottomSheetDialog,
                                bundleOf(
                                    Pair("type", it.type),
                                    Pair("ignore_duplicates", true)
                                )
                            )
                        }
                    } else if (toRedownload.size > 1) {
                        val showDownloadCard = sharedPreferences.getBoolean("download_card", true)
                        downloadViewModel.turnHistoryItemsToProcessingDownloads(toRedownload, downloadNow = !showDownloadCard)
                        if (showDownloadCard) {
                            val bundle = Bundle().apply {
                                putLongArray("currentHistoryIDs", toRedownload.toLongArray())
                                putBoolean("ignore_duplicates", true)
                            }
                            findNavController().navigate(R.id.downloadMultipleBottomSheetDialog2, bundle)
                        }
                    }
                }
            },
        )
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean =
                oldItem == newItem
        }
    }
}
