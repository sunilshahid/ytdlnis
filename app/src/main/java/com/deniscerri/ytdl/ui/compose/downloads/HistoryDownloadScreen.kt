package com.deniscerri.ytdl.ui.compose.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager.SORTING
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.repository.HistoryRepository.HistorySortType
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.util.SortCategory
import com.deniscerri.ytdl.util.SortOrderLabels
import kotlinx.coroutines.launch

/**
 * Screen displaying the History tab (Phase 5).
 * Replaces `fragment_history.xml`, `history_sort_sheet.xml`, `history_other_filters_sheet.xml`,
 * and `HistoryPaginatedAdapter`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryDownloadScreen(
    items: PagingItems<HistoryItem>,
    totalCount: Int,
    selectedIds: Set<Long>,
    searchQuery: String,
    sortType: HistorySortType,
    sortOrder: SORTING,
    typeFilter: String,
    statusFilter: HistoryViewModel.HistoryStatus,
    websiteFilter: String,
    websiteList: List<String>,
    filterBadgeCount: Int,
    useDownloadCardDesign: Boolean,
    enableSwipe: Boolean,
    showDownloadQueueButton: Boolean,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    onSearchQueryChange: (String) -> Unit = {},
    onSortChange: (HistorySortType) -> Unit = {},
    onTypeFilterChange: (String) -> Unit = {},
    onStatusFilterChange: (HistoryViewModel.HistoryStatus) -> Unit = {},
    onWebsiteFilterChange: (String) -> Unit = {},
    onToggleCardDesign: () -> Unit = {},
    onCardClick: (HistoryItem) -> Unit = {},
    onCardLongClick: (HistoryItem) -> Unit = {},
    onSelectToggle: (Long) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onInvertSelection: () -> Unit = {},
    onSelectBetween: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onActionClick: (HistoryItem) -> Unit = {},
    onSwipeLeft: (HistoryItem) -> Unit = {},
    onSwipeRight: (HistoryItem) -> Unit = {},
    onNavigateToQueue: () -> Unit = {},
    onClearAllHistory: (Boolean) -> Unit = {},
    onClearDeletedHistory: () -> Unit = {},
    onClearDuplicateHistory: () -> Unit = {},
    onCopyAllUrls: () -> Unit = {},
    onDeleteSelected: (Boolean) -> Unit = {},
    onShareSelected: () -> Unit = {},
    onCopySelectedUrls: () -> Unit = {},
    onMoveSelectedToVault: () -> Unit = {},
    onRedownloadSelected: () -> Unit = {},
) {
    val isSelectionMode = selectedIds.isNotEmpty()
    var isSearchActive by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showDeleteDeletedDialog by remember { mutableStateOf(false) }
    var showDeleteDuplicatesDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        // Top App Bar
        if (isSelectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClearSelection) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_close_24),
                            contentDescription = stringResource(R.string.close),
                        )
                    }

                    Text(
                        text = "${selectedIds.size} ${stringResource(R.string.selected)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(onClick = onRedownloadSelected) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.redownload),
                        )
                    }

                    IconButton(onClick = onShareSelected) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = stringResource(R.string.share),
                        )
                    }

                    IconButton(onClick = { showDeleteSelectedDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_delete_24),
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }

                    var selectionMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { selectionMenuExpanded = true }) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_more_vert_24),
                                contentDescription = stringResource(R.string.more),
                            )
                        }

                        DropdownMenu(
                            expanded = selectionMenuExpanded,
                            onDismissRequest = { selectionMenuExpanded = false },
                        ) {
                            if (selectedIds.size == 2) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.select_between)) },
                                    onClick = {
                                        selectionMenuExpanded = false
                                        onSelectBetween()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_select_between),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.select_all)) },
                                onClick = {
                                    selectionMenuExpanded = false
                                    onSelectAll()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_select_all),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.invert_selected)) },
                                onClick = {
                                    selectionMenuExpanded = false
                                    onInvertSelection()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_select_all),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_urls)) },
                                onClick = {
                                    selectionMenuExpanded = false
                                    onCopySelectedUrls()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_copy),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.private_vault)) },
                                onClick = {
                                    selectionMenuExpanded = false
                                    onMoveSelectedToVault()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lock),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        } else {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text(stringResource(R.string.search_history_hint)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.search_history),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                actions = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            onSearchQueryChange("")
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_close_24),
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = stringResource(R.string.search_history_hint),
                            )
                        }
                    }

                    if (showDownloadQueueButton) {
                        IconButton(onClick = onNavigateToQueue) {
                            Icon(
                                painter = painterResource(R.drawable.ic_downloads),
                                contentDescription = stringResource(R.string.downloads),
                            )
                        }
                    }

                    IconButton(onClick = { showFilterSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (filterBadgeCount > 0) {
                                    Badge { Text("$filterBadgeCount") }
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_filter_alt_24),
                                contentDescription = null,
                            )
                        }
                    }

                    var overflowExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_more_vert_24),
                                contentDescription = stringResource(R.string.more),
                            )
                        }

                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_history)) },
                                onClick = {
                                    overflowExpanded = false
                                    showDeleteAllDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.baseline_delete_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = {
                                    overflowExpanded = false
                                    showDeleteDeletedDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.baseline_delete_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_duplicates)) },
                                onClick = {
                                    overflowExpanded = false
                                    showDeleteDuplicatesDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.baseline_delete_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_urls)) },
                                onClick = {
                                    overflowExpanded = false
                                    onCopyAllUrls()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_copy),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sort chip
            val sortLabel = when (sortType) {
                HistorySortType.DATE -> stringResource(R.string.date_added)
                HistorySortType.TITLE -> stringResource(R.string.title)
                HistorySortType.AUTHOR -> stringResource(R.string.author)
                HistorySortType.FILESIZE -> stringResource(R.string.file_size)
            }
            val sortIcon = if (sortOrder == SORTING.DESC) R.drawable.ic_up else R.drawable.ic_down

            FilterChip(
                selected = true,
                onClick = { showSortSheet = true },
                label = { Text(sortLabel) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(sortIcon),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

            // Format filter chips
            FilterChip(
                selected = typeFilter == DownloadType.audio.toString(),
                onClick = {
                    if (typeFilter == DownloadType.audio.toString()) {
                        onTypeFilterChange("")
                    } else {
                        onTypeFilterChange(DownloadType.audio.toString())
                    }
                },
                label = { Text(stringResource(R.string.audio)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_music),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

            FilterChip(
                selected = typeFilter == DownloadType.video.toString(),
                onClick = {
                    if (typeFilter == DownloadType.video.toString()) {
                        onTypeFilterChange("")
                    } else {
                        onTypeFilterChange(DownloadType.video.toString())
                    }
                },
                label = { Text(stringResource(R.string.video)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_video),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

            FilterChip(
                selected = typeFilter == DownloadType.command.toString(),
                onClick = {
                    if (typeFilter == DownloadType.command.toString()) {
                        onTypeFilterChange("")
                    } else {
                        onTypeFilterChange(DownloadType.command.toString())
                    }
                },
                label = { Text(stringResource(R.string.command)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_terminal),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

            // Card design toggle chip
            FilterChip(
                selected = useDownloadCardDesign,
                onClick = onToggleCardDesign,
                label = { Text("Toggle View") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_view_grid),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }

        // List or Empty State
        Box(modifier = Modifier.fillMaxSize()) {
            if (totalCount == 0 && items.itemCount == 0) {
                EmptyState(
                    iconRes = R.drawable.ic_no_results,
                    title = stringResource(R.string.history_is_empty),
                    message = stringResource(R.string.no_downloads_yet),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                )
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        count = items.itemCount,
                        key = { index -> items[index]?.id ?: index.toLong() },
                        contentType = { "history_item" },
                    ) { index ->
                        val item = items[index]
                        if (item != null) {
                            DismissibleHistoryCard(
                                item = item,
                                isSelected = selectedIds.contains(item.id),
                                isSelectionMode = isSelectionMode,
                                useDownloadCardDesign = useDownloadCardDesign,
                                enableSwipe = enableSwipe,
                                onSwipeLeft = { onSwipeLeft(item) },
                                onSwipeRight = { onSwipeRight(item) },
                                onClick = { onCardClick(item) },
                                onLongClick = { onCardLongClick(item) },
                                onSelectToggle = { onSelectToggle(item.id) },
                                onActionClick = { onActionClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Sort Bottom Sheet
    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.sort_by),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                val sortOptions = listOf(
                    Triple(HistorySortType.DATE, R.string.date_added, SortCategory.DATE_TIME),
                    Triple(HistorySortType.TITLE, R.string.title, SortCategory.ALPHABETICAL),
                    Triple(HistorySortType.AUTHOR, R.string.author, SortCategory.ALPHABETICAL),
                    Triple(HistorySortType.FILESIZE, R.string.file_size, SortCategory.SIZE),
                )

                sortOptions.forEach { (type, labelRes, category) ->
                    val isActive = type == sortType
                    val iconRes = if (isActive) {
                        if (sortOrder == SORTING.DESC) R.drawable.ic_up else R.drawable.ic_down
                    } else null

                    Surface(
                        onClick = {
                            onSortChange(type)
                            showSortSheet = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (iconRes != null) {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Filter Bottom Sheet (Status & Websites)
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = statusFilter == HistoryViewModel.HistoryStatus.ALL,
                        onClick = { onStatusFilterChange(HistoryViewModel.HistoryStatus.ALL) },
                    )
                    Text(
                        text = stringResource(R.string.all),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = statusFilter == HistoryViewModel.HistoryStatus.NOT_DELETED,
                        onClick = { onStatusFilterChange(HistoryViewModel.HistoryStatus.NOT_DELETED) },
                    )
                    Text(
                        text = stringResource(R.string.not_deleted),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = statusFilter == HistoryViewModel.HistoryStatus.DELETED,
                        onClick = { onStatusFilterChange(HistoryViewModel.HistoryStatus.DELETED) },
                    )
                    Text(
                        text = stringResource(R.string.deleted),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                if (websiteList.size >= 2) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.websites),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        websiteList.forEach { site ->
                            FilterChip(
                                selected = websiteFilter.equals(site, ignoreCase = true),
                                onClick = {
                                    if (websiteFilter.equals(site, ignoreCase = true)) {
                                        onWebsiteFilterChange("")
                                    } else {
                                        onWebsiteFilterChange(site)
                                    }
                                },
                                label = { Text(site) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Delete All Confirmation Dialog
    if (showDeleteAllDialog) {
        var deleteFilesToo by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.you_are_going_to_delete_multiple_items)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Checkbox(
                        checked = deleteFilesToo,
                        onCheckedChange = { deleteFilesToo = it },
                    )
                    Text(
                        text = stringResource(R.string.delete_files_too),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDeleteAllDialog = false
                    onClearAllHistory(deleteFilesToo)
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Delete Selected Confirmation Dialog
    if (showDeleteSelectedDialog) {
        var deleteFilesToo by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(R.string.you_are_going_to_delete_multiple_items)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Checkbox(
                        checked = deleteFilesToo,
                        onCheckedChange = { deleteFilesToo = it },
                    )
                    Text(
                        text = stringResource(R.string.delete_files_too),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDeleteSelectedDialog = false
                    onDeleteSelected(deleteFilesToo)
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Remove Deleted History Confirmation Dialog
    if (showDeleteDeletedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDeletedDialog = false },
            title = { Text(stringResource(R.string.confirm_delete_history)) },
            text = { Text(stringResource(R.string.confirm_delete_history_desc)) },
            confirmButton = {
                Button(onClick = {
                    showDeleteDeletedDialog = false
                    onClearDeletedHistory()
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDeletedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Remove Duplicates Confirmation Dialog
    if (showDeleteDuplicatesDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDuplicatesDialog = false },
            title = { Text(stringResource(R.string.confirm_delete_history)) },
            text = { Text(stringResource(R.string.confirm_delete_history_desc)) },
            confirmButton = {
                Button(onClick = {
                    showDeleteDuplicatesDialog = false
                    onClearDuplicateHistory()
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDuplicatesDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
