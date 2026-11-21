package com.deniscerri.ytdl.ui.compose.downloads

import android.app.Activity
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.compose.components.YtdlScaffold
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.DownloadWorker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tabs available in the Download Queue Screen (Phase 5).
 */
enum class DownloadTab(val titleRes: Int) {
    RUNNING(R.string.running),
    QUEUED(R.string.in_queue),
    SCHEDULED(R.string.scheduled),
    CANCELLED(R.string.cancelled),
    ERRORED(R.string.errored),
    SAVED(R.string.saved)
}

private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DownloadItemSimple>() {
    override fun areItemsTheSame(oldItem: DownloadItemSimple, newItem: DownloadItemSimple): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: DownloadItemSimple, newItem: DownloadItemSimple): Boolean =
        oldItem == newItem
}

/**
 * Main Download Queue screen hosting tabs for Active, Queued, Scheduled, Cancelled, Errored, and Saved (Phase 5).
 *
 * Replaces `fragment_download_queue_main_screen.xml` and `fragment_downloads.xml`.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(
    downloadViewModel: DownloadViewModel,
    modifier: Modifier = Modifier,
    initialTab: DownloadTab = DownloadTab.RUNNING,
    targetTab: DownloadTab? = null,
    progressEvents: Map<Long, DownloadWorker.WorkerProgress> = emptyMap(),
    onBack: (() -> Unit)? = null,
    onTabChanged: ((DownloadTab) -> Unit)? = null,
    onNavigateToLog: (Long) -> Unit = {},
    onShowDetailsCard: (Long) -> Unit = {},
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialTab.ordinal) { DownloadTab.entries.size }

    // Synchronize programmatic tab switches
    LaunchedEffect(targetTab) {
        targetTab?.let {
            pagerState.animateScrollToPage(it.ordinal)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        onTabChanged?.invoke(DownloadTab.entries[pagerState.currentPage])
    }

    // Counts for badging
    val activeCount by downloadViewModel.activePausedDownloadsCount.collectAsStateWithLifecycle(0)
    val queuedCount by downloadViewModel.queuedDownloadsCount.collectAsStateWithLifecycle(0)
    val scheduledCount by downloadViewModel.scheduledDownloadsCount.collectAsStateWithLifecycle(0)
    val cancelledCount by downloadViewModel.cancelledDownloadsCount.collectAsStateWithLifecycle(0)
    val erroredCount by downloadViewModel.erroredDownloadsCount.collectAsStateWithLifecycle(0)
    val savedCount by downloadViewModel.savedDownloadsCount.collectAsStateWithLifecycle(0)

    val showCounts = remember(preferences) {
        preferences.getBoolean("show_count_downloads", false)
    }

    var showMenu by remember { mutableStateOf(false) }

    // Pause / Resume All state
    val pausedAllState by downloadViewModel.pausedAllDownloadsFlow.collectAsStateWithLifecycle(
        DownloadViewModel.PausedAllDownloadsState.HIDDEN
    )

    YtdlScaffold(
        title = stringResource(R.string.download_queue),
        modifier = modifier,
        onBack = onBack,
        actions = {
            // Pause/Resume All button
            if (pausedAllState == DownloadViewModel.PausedAllDownloadsState.RESUME) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            downloadViewModel.resumeAllDownloads()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.exomedia_ic_play_arrow_white),
                        contentDescription = stringResource(R.string.resume_all),
                    )
                }
            } else if (pausedAllState == DownloadViewModel.PausedAllDownloadsState.PAUSE) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            downloadViewModel.pauseAllDownloads()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.exomedia_ic_pause_white),
                        contentDescription = stringResource(R.string.pause_all),
                    )
                }
            }

            // Overflow menu for Clear Queue / Clear All
            Box {
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_more_vert_24),
                        contentDescription = stringResource(R.string.more),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_downloading)) },
                        onClick = {
                            showMenu = false
                            UiUtil.showGenericDeleteAllDialog(context) {
                                downloadViewModel.cancelAllDownloads()
                            }
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete_all),
                                contentDescription = null,
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_all)) },
                        onClick = {
                            showMenu = false
                            UiUtil.showGenericDeleteAllDialog(context) {
                                downloadViewModel.deleteAll()
                            }
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete_all),
                                contentDescription = null,
                            )
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                DownloadTab.entries.forEachIndexed { index, tab ->
                    val count = when (tab) {
                        DownloadTab.RUNNING -> activeCount
                        DownloadTab.QUEUED -> queuedCount
                        DownloadTab.SCHEDULED -> scheduledCount
                        DownloadTab.CANCELLED -> cancelledCount
                        DownloadTab.ERRORED -> erroredCount
                        DownloadTab.SAVED -> savedCount
                    }
                    val isSelected = pagerState.currentPage == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            if (showCounts && count > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(count.toString())
                                        }
                                    }
                                ) {
                                    Text(
                                        text = stringResource(tab.titleRes),
                                        maxLines = 1,
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(tab.titleRes),
                                    maxLines = 1,
                                )
                            }
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (DownloadTab.entries[page]) {
                    DownloadTab.RUNNING -> {
                        val activeDownloads by downloadViewModel.activePausedDownloads.collectAsStateWithLifecycle(emptyList())
                        ActiveDownloadsContent(
                            items = activeDownloads,
                            progressEvents = progressEvents,
                            onPauseClick = { itemId ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    downloadViewModel.pauseDownload(itemId)
                                }
                            },
                            onResumeClick = { itemId ->
                                downloadViewModel.resumeDownload(itemId)
                            },
                            onCancelClick = { itemId ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    downloadViewModel.cancelDownload(itemId)
                                }
                            },
                            onOutputClick = { item ->
                                if (item.logID != null && item.logID != 0L) {
                                    onNavigateToLog(item.logID!!)
                                }
                            }
                        )
                    }

                    DownloadTab.QUEUED -> {
                        QueuedTabContent(
                            downloadViewModel = downloadViewModel,
                            preferences = preferences,
                            onShowDetailsCard = onShowDetailsCard,
                        )
                    }

                    DownloadTab.SCHEDULED -> {
                        ScheduledTabContent(
                            downloadViewModel = downloadViewModel,
                            scheduledCount = scheduledCount,
                            preferences = preferences,
                            onShowDetailsCard = onShowDetailsCard,
                        )
                    }

                    DownloadTab.CANCELLED -> {
                        CancelledTabContent(
                            downloadViewModel = downloadViewModel,
                            cancelledCount = cancelledCount,
                            preferences = preferences,
                            onShowDetailsCard = onShowDetailsCard,
                        )
                    }

                    DownloadTab.ERRORED -> {
                        ErroredTabContent(
                            downloadViewModel = downloadViewModel,
                            erroredCount = erroredCount,
                            preferences = preferences,
                            onShowDetailsCard = onShowDetailsCard,
                        )
                    }

                    DownloadTab.SAVED -> {
                        SavedTabContent(
                            downloadViewModel = downloadViewModel,
                            savedCount = savedCount,
                            preferences = preferences,
                            onShowDetailsCard = onShowDetailsCard,
                        )
                    }
                }
            }
        }
    }
}

/** Alias for callers expecting [DownloadsMainScreen]. */
@Composable
fun DownloadsMainScreen(
    downloadViewModel: DownloadViewModel,
    modifier: Modifier = Modifier,
    initialTab: DownloadTab = DownloadTab.RUNNING,
    targetTab: DownloadTab? = null,
    progressEvents: Map<Long, DownloadWorker.WorkerProgress> = emptyMap(),
    onBack: (() -> Unit)? = null,
    onTabChanged: ((DownloadTab) -> Unit)? = null,
    onNavigateToLog: (Long) -> Unit = {},
    onShowDetailsCard: (Long) -> Unit = {},
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
) {
    DownloadQueueScreen(
        downloadViewModel = downloadViewModel,
        modifier = modifier,
        initialTab = initialTab,
        targetTab = targetTab,
        progressEvents = progressEvents,
        onBack = onBack,
        onTabChanged = onTabChanged,
        onNavigateToLog = onNavigateToLog,
        onShowDetailsCard = onShowDetailsCard,
        preferences = preferences,
    )
}

@Composable
private fun QueuedTabContent(
    downloadViewModel: DownloadViewModel,
    preferences: SharedPreferences,
    onShowDetailsCard: (Long) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagingItems = downloadViewModel.queuedDownloads.collectAsPagingItems(DIFF_CALLBACK)

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isReorderMode by remember { mutableStateOf(false) }

    val enableSwipe = remember(preferences) {
        val gestures = preferences.getStringSet(
            "swipe_gesture",
            context.resources.getStringArray(R.array.swipe_gestures_values).toSet()
        ) ?: emptySet()
        gestures.contains("queued")
    }

    QueueDownloadScreen(
        items = pagingItems,
        selectedIds = selectedIds,
        isReorderMode = isReorderMode,
        enableSwipe = enableSwipe,
        onCardClick = { item ->
            onShowDetailsCard(item.id)
        },
        onCardLongClick = { item ->
            selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
        },
        onSelectToggle = { id ->
            selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
        },
        onSelectAll = {
            coroutineScope.launch {
                val allIds = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemIDsNotPresentIn(
                        emptyList(),
                        listOf(DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused)
                    )
                }
                selectedIds = allIds.toSet()
            }
        },
        onInvertSelection = {
            coroutineScope.launch {
                val inverted = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemIDsNotPresentIn(
                        selectedIds.toList(),
                        listOf(DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused)
                    )
                }
                selectedIds = inverted.toSet()
            }
        },
        onSelectBetween = {
            if (selectedIds.size == 2) {
                coroutineScope.launch {
                    val sorted = selectedIds.sorted()
                    val middle = withContext(Dispatchers.IO) {
                        downloadViewModel.getIDsBetweenTwoItems(
                            sorted.first(),
                            sorted.last(),
                            listOf(DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused).toListString()
                        )
                    }
                    selectedIds = selectedIds + middle
                }
            }
        },
        onClearSelection = {
            selectedIds = emptySet()
        },
        onMoveToTop = { id ->
            coroutineScope.launch(Dispatchers.IO) {
                downloadViewModel.putAtTopOfQueue(listOf(id))
            }
        },
        onMoveToBottom = { id ->
            coroutineScope.launch(Dispatchers.IO) {
                downloadViewModel.putAtBottomOfQueue(listOf(id))
            }
        },
        onMoveUp = { item ->
            coroutineScope.launch(Dispatchers.IO) {
                downloadViewModel.putAtTopOfQueue(listOf(item.id))
            }
        },
        onMoveDown = { item ->
            coroutineScope.launch(Dispatchers.IO) {
                downloadViewModel.putAtTopOfQueue(listOf(item.id))
            }
        },
        onCancelItem = { item ->
            coroutineScope.launch(Dispatchers.IO) {
                downloadViewModel.cancelDownload(item.id)
            }
        },
        onDeleteSelected = {
            val toDelete = selectedIds.toList()
            selectedIds = emptySet()
            val deleteDialog = MaterialAlertDialogBuilder(context)
            deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete_multiple_items))
            deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { d, _ -> d.cancel() }
            deleteDialog.setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                coroutineScope.launch {
                    for (id in toDelete) {
                        downloadViewModel.cancelDownloadOnly(id)
                    }
                    downloadViewModel.deleteAllWithID(toDelete)
                }
            }
            deleteDialog.show()
        },
        onMoveSelectedToTop = {
            val toMove = selectedIds.toList()
            selectedIds = emptySet()
            coroutineScope.launch(Dispatchers.IO) {
                downloadViewModel.putAtTopOfQueue(toMove)
            }
        },
        onMoveSelectedToBottom = {
            val toMove = selectedIds.toList()
            selectedIds = emptySet()
            coroutineScope.launch(Dispatchers.IO) {
                downloadViewModel.putAtTopOfQueue(toMove)
            }
        },
        onCopySelectedUrls = {
            val toCopy = selectedIds.toList()
            selectedIds = emptySet()
            coroutineScope.launch {
                val urls = withContext(Dispatchers.IO) {
                    downloadViewModel.getURLsByIds(toCopy)
                }
                (context as? Activity)?.let { UiUtil.copyToClipboard(urls.joinToString("\n"), it) }
            }
        },
        onSwipeLeft = { item ->
            val deleteDialog = MaterialAlertDialogBuilder(context)
            deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete) + ": " + item.title.ifEmpty { item.url })
            deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { d, _ -> d.cancel() }
            deleteDialog.setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                coroutineScope.launch {
                    downloadViewModel.cancelDownloadOnly(item.id)
                    downloadViewModel.deleteDownload(item.id)
                }
            }
            deleteDialog.show()
        }
    )
}

@Composable
private fun ScheduledTabContent(
    downloadViewModel: DownloadViewModel,
    scheduledCount: Int,
    preferences: SharedPreferences,
    onShowDetailsCard: (Long) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagingItems = downloadViewModel.scheduledDownloads.collectAsPagingItems(DIFF_CALLBACK)

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val enableSwipe = remember(preferences) {
        val gestures = preferences.getStringSet(
            "swipe_gesture",
            context.resources.getStringArray(R.array.swipe_gestures_values).toSet()
        ) ?: emptySet()
        gestures.contains("scheduled")
    }

    ScheduledDownloadScreen(
        items = pagingItems,
        totalCount = scheduledCount,
        selectedIds = selectedIds,
        enableSwipe = enableSwipe,
        onDownloadNowForAll = {
            coroutineScope.launch(Dispatchers.IO) {
                downloadViewModel.resetScheduleItemForAllScheduledItemsAndStartDownload()
            }
        },
        onDeleteAll = {
            UiUtil.showGenericDeleteAllDialog(context) {
                downloadViewModel.deleteScheduled()
            }
        },
        onCopyAllUrls = {
            coroutineScope.launch {
                val urls = withContext(Dispatchers.IO) {
                    downloadViewModel.getURLsByStatus(listOf(DownloadRepository.Status.Scheduled))
                }
                (context as? Activity)?.let { UiUtil.copyToClipboard(urls.joinToString("\n"), it) }
            }
        },
        onDownloadNow = { item ->
            coroutineScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.resetScheduleTimeForItemsAndStartDownload(listOf(item.id))
                    }
                }.onFailure {
                    Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                }
            }
        },
        onDeleteItem = { item ->
            coroutineScope.launch {
                val deletedItem = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemByID(item.id)
                } ?: return@launch
                downloadViewModel.deleteDownload(deletedItem.id)
            }
        },
        onCardClick = { item ->
            onShowDetailsCard(item.id)
        },
        onCardLongClick = { item ->
            selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
        },
        onSelectToggle = { id ->
            selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
        },
        onSelectAll = {
            coroutineScope.launch {
                val allIds = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemIDsNotPresentIn(
                        emptyList(),
                        listOf(DownloadRepository.Status.Scheduled)
                    )
                }
                selectedIds = allIds.toSet()
            }
        },
        onInvertSelection = {
            coroutineScope.launch {
                val inverted = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemIDsNotPresentIn(
                        selectedIds.toList(),
                        listOf(DownloadRepository.Status.Scheduled)
                    )
                }
                selectedIds = inverted.toSet()
            }
        },
        onSelectBetween = {
            if (selectedIds.size == 2) {
                coroutineScope.launch {
                    val sorted = selectedIds.sorted()
                    val middle = withContext(Dispatchers.IO) {
                        downloadViewModel.getIDsBetweenTwoItems(
                            sorted.first(),
                            sorted.last(),
                            listOf(DownloadRepository.Status.Scheduled).toListString()
                        )
                    }
                    selectedIds = selectedIds + middle
                }
            }
        },
        onClearSelection = {
            selectedIds = emptySet()
        },
        onDownloadSelected = {
            val toDownload = selectedIds.toList()
            selectedIds = emptySet()
            coroutineScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.resetScheduleTimeForItemsAndStartDownload(toDownload)
                    }
                }.onFailure {
                    Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                }
            }
        },
        onDeleteSelected = {
            val toDelete = selectedIds.toList()
            selectedIds = emptySet()
            val deleteDialog = MaterialAlertDialogBuilder(context)
            deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete_multiple_items))
            deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { d, _ -> d.cancel() }
            deleteDialog.setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                coroutineScope.launch {
                    downloadViewModel.deleteAllWithID(toDelete)
                }
            }
            deleteDialog.show()
        },
        onCopySelectedUrls = {
            val toCopy = selectedIds.toList()
            selectedIds = emptySet()
            coroutineScope.launch {
                val urls = withContext(Dispatchers.IO) {
                    downloadViewModel.getURLsByIds(toCopy)
                }
                (context as? Activity)?.let { UiUtil.copyToClipboard(urls.joinToString("\n"), it) }
            }
        },
    )
}

@Composable
private fun CancelledTabContent(
    downloadViewModel: DownloadViewModel,
    cancelledCount: Int,
    preferences: SharedPreferences,
    onShowDetailsCard: (Long) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagingItems = downloadViewModel.cancelledDownloads.collectAsPagingItems(DIFF_CALLBACK)

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val enableSwipe = remember(preferences) {
        val gestures = preferences.getStringSet(
            "swipe_gesture",
            context.resources.getStringArray(R.array.swipe_gestures_values).toSet()
        ) ?: emptySet()
        gestures.contains("cancelled")
    }

    SavedDownloadScreen(
        items = pagingItems,
        totalCount = cancelledCount,
        selectedIds = selectedIds,
        tabTitle = stringResource(R.string.cancelled),
        enableSwipe = enableSwipe,
        onDownloadAll = {
            coroutineScope.launch {
                val ids = withContext(Dispatchers.IO) {
                    downloadViewModel.getIDsByStatus(listOf(DownloadRepository.Status.Cancelled))
                }
                downloadViewModel.reQueueDownloadItems(ids)
            }
        },
        onDeleteAll = {
            UiUtil.showGenericDeleteAllDialog(context) {
                downloadViewModel.deleteCancelled()
            }
        },
        onCopyAllUrls = {
            coroutineScope.launch {
                val urls = withContext(Dispatchers.IO) {
                    downloadViewModel.getURLsByStatus(listOf(DownloadRepository.Status.Cancelled))
                }
                (context as? Activity)?.let { UiUtil.copyToClipboard(urls.joinToString("\n"), it) }
            }
        },
        onActionClick = { item ->
            coroutineScope.launch {
                val itm = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemByID(item.id)
                }
                if (itm != null) {
                    downloadViewModel.queueDownloads(listOf(itm))
                } else {
                    Toast.makeText(context, context.getString(R.string.error_restarting_download), Toast.LENGTH_LONG).show()
                }
            }
        },
        onDeleteItem = { item ->
            coroutineScope.launch {
                val itm = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemByID(item.id)
                } ?: return@launch
                downloadViewModel.deleteDownload(itm.id)
            }
        },
        onCardClick = { item ->
            onShowDetailsCard(item.id)
        },
        onCardLongClick = { item ->
            selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
        },
        onSelectToggle = { id ->
            selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
        },
        onSelectAll = {
            coroutineScope.launch {
                val allIds = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemIDsNotPresentIn(
                        emptyList(),
                        listOf(DownloadRepository.Status.Cancelled)
                    )
                }
                selectedIds = allIds.toSet()
            }
        },
        onInvertSelection = {
            coroutineScope.launch {
                val inverted = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemIDsNotPresentIn(
                        selectedIds.toList(),
                        listOf(DownloadRepository.Status.Cancelled)
                    )
                }
                selectedIds = inverted.toSet()
            }
        },
        onSelectBetween = {
            if (selectedIds.size == 2) {
                coroutineScope.launch {
                    val sorted = selectedIds.sorted()
                    val middle = withContext(Dispatchers.IO) {
                        downloadViewModel.getIDsBetweenTwoItems(
                            sorted.first(),
                            sorted.last(),
                            listOf(DownloadRepository.Status.Cancelled).toListString()
                        )
                    }
                    selectedIds = selectedIds + middle
                }
            }
        },
        onClearSelection = {
            selectedIds = emptySet()
        },
        onDownloadSelected = {
            val toDownload = selectedIds.toList()
            selectedIds = emptySet()
            coroutineScope.launch {
                downloadViewModel.reQueueDownloadItems(toDownload)
            }
        },
        onDeleteSelected = {
            val toDelete = selectedIds.toList()
            selectedIds = emptySet()
            val deleteDialog = MaterialAlertDialogBuilder(context)
            deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete_multiple_items))
            deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { d, _ -> d.cancel() }
            deleteDialog.setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                coroutineScope.launch {
                    downloadViewModel.deleteAllWithID(toDelete)
                }
            }
            deleteDialog.show()
        },
        onCopySelectedUrls = {
            val toCopy = selectedIds.toList()
            selectedIds = emptySet()
            coroutineScope.launch {
                val urls = withContext(Dispatchers.IO) {
                    downloadViewModel.getURLsByIds(toCopy)
                }
                (context as? Activity)?.let { UiUtil.copyToClipboard(urls.joinToString("\n"), it) }
            }
        },
    )
}

@Composable
private fun ErroredTabContent(
    downloadViewModel: DownloadViewModel,
    erroredCount: Int,
    preferences: SharedPreferences,
    onShowDetailsCard: (Long) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagingItems = downloadViewModel.erroredDownloads.collectAsPagingItems(DIFF_CALLBACK)

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val enableSwipe = remember(preferences) {
        val gestures = preferences.getStringSet(
            "swipe_gesture",
            context.resources.getStringArray(R.array.swipe_gestures_values).toSet()
        ) ?: emptySet()
        gestures.contains("errored")
    }

    SavedDownloadScreen(
        items = pagingItems,
        totalCount = erroredCount,
        selectedIds = selectedIds,
        tabTitle = stringResource(R.string.errored),
        enableSwipe = enableSwipe,
        isErroredTab = true,
        onDownloadAll = {
            coroutineScope.launch {
                val ids = withContext(Dispatchers.IO) {
                    downloadViewModel.getIDsByStatus(listOf(DownloadRepository.Status.Error))
                }
                downloadViewModel.reQueueDownloadItems(ids)
            }
        },
        onDeleteAll = {
            UiUtil.showGenericDeleteAllDialog(context) {
                downloadViewModel.deleteErrored()
            }
        },
        onCopyAllUrls = {
            coroutineScope.launch {
                val urls = withContext(Dispatchers.IO) {
                    downloadViewModel.getURLsByStatus(listOf(DownloadRepository.Status.Error))
                }
                (context as? Activity)?.let { UiUtil.copyToClipboard(urls.joinToString("\n"), it) }
            }
        },
        onActionClick = { item ->
            coroutineScope.launch {
                val itm = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemByID(item.id)
                }
                if (itm != null) {
                    downloadViewModel.queueDownloads(listOf(itm))
                } else {
                    Toast.makeText(context, context.getString(R.string.error_restarting_download), Toast.LENGTH_LONG).show()
                }
            }
        },
        onDeleteItem = { item ->
            coroutineScope.launch {
                val itm = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemByID(item.id)
                } ?: return@launch
                downloadViewModel.deleteDownload(itm.id)
            }
        },
        onCardClick = { item ->
            onShowDetailsCard(item.id)
        },
        onCardLongClick = { item ->
            selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
        },
        onSelectToggle = { id ->
            selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
        },
        onSelectAll = {
            coroutineScope.launch {
                val allIds = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemIDsNotPresentIn(
                        emptyList(),
                        listOf(DownloadRepository.Status.Error)
                    )
                }
                selectedIds = allIds.toSet()
            }
        },
        onInvertSelection = {
            coroutineScope.launch {
                val inverted = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemIDsNotPresentIn(
                        selectedIds.toList(),
                        listOf(DownloadRepository.Status.Error)
                    )
                }
                selectedIds = inverted.toSet()
            }
        },
        onSelectBetween = {
            if (selectedIds.size == 2) {
                coroutineScope.launch {
                    val sorted = selectedIds.sorted()
                    val middle = withContext(Dispatchers.IO) {
                        downloadViewModel.getIDsBetweenTwoItems(
                            sorted.first(),
                            sorted.last(),
                            listOf(DownloadRepository.Status.Error).toListString()
                        )
                    }
                    selectedIds = selectedIds + middle
                }
            }
        },
        onClearSelection = {
            selectedIds = emptySet()
        },
        onDownloadSelected = {
            val toDownload = selectedIds.toList()
            selectedIds = emptySet()
            coroutineScope.launch {
                downloadViewModel.reQueueDownloadItems(toDownload)
            }
        },
        onDeleteSelected = {
            val toDelete = selectedIds.toList()
            selectedIds = emptySet()
            val deleteDialog = MaterialAlertDialogBuilder(context)
            deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete_multiple_items))
            deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { d, _ -> d.cancel() }
            deleteDialog.setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                coroutineScope.launch {
                    downloadViewModel.deleteAllWithID(toDelete)
                }
            }
            deleteDialog.show()
        },
        onCopySelectedUrls = {
            val toCopy = selectedIds.toList()
            selectedIds = emptySet()
            coroutineScope.launch {
                val urls = withContext(Dispatchers.IO) {
                    downloadViewModel.getURLsByIds(toCopy)
                }
                (context as? Activity)?.let { UiUtil.copyToClipboard(urls.joinToString("\n"), it) }
            }
        },
    )
}

@Composable
private fun SavedTabContent(
    downloadViewModel: DownloadViewModel,
    savedCount: Int,
    preferences: SharedPreferences,
    onShowDetailsCard: (Long) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagingItems = downloadViewModel.savedDownloads.collectAsPagingItems(DIFF_CALLBACK)

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val enableSwipe = remember(preferences) {
        val gestures = preferences.getStringSet(
            "swipe_gesture",
            context.resources.getStringArray(R.array.swipe_gestures_values).toSet()
        ) ?: emptySet()
        gestures.contains("saved")
    }

    SavedDownloadScreen(
        items = pagingItems,
        totalCount = savedCount,
        selectedIds = selectedIds,
        tabTitle = stringResource(R.string.saved),
        enableSwipe = enableSwipe,
        onDownloadAll = {
            coroutineScope.launch {
                val ids = withContext(Dispatchers.IO) {
                    downloadViewModel.getIDsByStatus(listOf(DownloadRepository.Status.Saved))
                }
                downloadViewModel.reQueueDownloadItems(ids)
            }
        },
        onDeleteAll = {
            UiUtil.showGenericDeleteAllDialog(context) {
                downloadViewModel.deleteSaved()
            }
        },
        onCopyAllUrls = {
            coroutineScope.launch {
                val urls = withContext(Dispatchers.IO) {
                    downloadViewModel.getURLsByStatus(listOf(DownloadRepository.Status.Saved))
                }
                (context as? Activity)?.let { UiUtil.copyToClipboard(urls.joinToString("\n"), it) }
            }
        },
        onActionClick = { item ->
            coroutineScope.launch {
                val itm = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemByID(item.id)
                }
                if (itm != null) {
                    downloadViewModel.queueDownloads(listOf(itm))
                } else {
                    Toast.makeText(context, context.getString(R.string.error_restarting_download), Toast.LENGTH_LONG).show()
                }
            }
        },
        onDeleteItem = { item ->
            coroutineScope.launch {
                val itm = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemByID(item.id)
                } ?: return@launch
                downloadViewModel.deleteDownload(itm.id)
            }
        },
        onCardClick = { item ->
            onShowDetailsCard(item.id)
        },
        onCardLongClick = { item ->
            selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
        },
        onSelectToggle = { id ->
            selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
        },
        onSelectAll = {
            coroutineScope.launch {
                val allIds = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemIDsNotPresentIn(
                        emptyList(),
                        listOf(DownloadRepository.Status.Saved)
                    )
                }
                selectedIds = allIds.toSet()
            }
        },
        onInvertSelection = {
            coroutineScope.launch {
                val inverted = withContext(Dispatchers.IO) {
                    downloadViewModel.getItemIDsNotPresentIn(
                        selectedIds.toList(),
                        listOf(DownloadRepository.Status.Saved)
                    )
                }
                selectedIds = inverted.toSet()
            }
        },
        onSelectBetween = {
            if (selectedIds.size == 2) {
                coroutineScope.launch {
                    val sorted = selectedIds.sorted()
                    val middle = withContext(Dispatchers.IO) {
                        downloadViewModel.getIDsBetweenTwoItems(
                            sorted.first(),
                            sorted.last(),
                            listOf(DownloadRepository.Status.Saved).toListString()
                        )
                    }
                    selectedIds = selectedIds + middle
                }
            }
        },
        onClearSelection = {
            selectedIds = emptySet()
        },
        onDownloadSelected = {
            val toDownload = selectedIds.toList()
            selectedIds = emptySet()
            coroutineScope.launch {
                downloadViewModel.reQueueDownloadItems(toDownload)
            }
        },
        onDeleteSelected = {
            val toDelete = selectedIds.toList()
            selectedIds = emptySet()
            val deleteDialog = MaterialAlertDialogBuilder(context)
            deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete_multiple_items))
            deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { d, _ -> d.cancel() }
            deleteDialog.setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                coroutineScope.launch {
                    downloadViewModel.deleteAllWithID(toDelete)
                }
            }
            deleteDialog.show()
        },
        onCopySelectedUrls = {
            val toCopy = selectedIds.toList()
            selectedIds = emptySet()
            coroutineScope.launch {
                val urls = withContext(Dispatchers.IO) {
                    downloadViewModel.getURLsByIds(toCopy)
                }
                (context as? Activity)?.let { UiUtil.copyToClipboard(urls.joinToString("\n"), it) }
            }
        },
    )
}
