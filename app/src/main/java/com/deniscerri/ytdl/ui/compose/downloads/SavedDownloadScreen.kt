package com.deniscerri.ytdl.ui.compose.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.ui.compose.components.EmptyState

/**
 * Screen displaying Saved, Cancelled, or Errored Downloads tabs (Phase 5).
 * Replaces `generic_list.xml` usage in Saved, Cancelled, and Errored fragments.
 */
@Composable
fun SavedDownloadScreen(
    items: PagingItems<DownloadItemSimple>,
    totalCount: Int,
    selectedIds: Set<Long>,
    tabTitle: String,
    enableSwipe: Boolean,
    modifier: Modifier = Modifier,
    isErroredTab: Boolean = false,
    onDownloadAll: () -> Unit = {},
    onDeleteAll: () -> Unit = {},
    onCopyAllUrls: () -> Unit = {},
    onActionClick: (DownloadItemSimple) -> Unit = {},
    onDeleteItem: (DownloadItemSimple) -> Unit = {},
    onCardClick: (DownloadItemSimple) -> Unit = {},
    onCardLongClick: (DownloadItemSimple) -> Unit = {},
    onSelectToggle: (Long) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onInvertSelection: () -> Unit = {},
    onSelectBetween: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDownloadSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onCopySelectedUrls: () -> Unit = {},
) {
    val isSelectionMode = selectedIds.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        // Selection Action Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
        ) {
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

                    IconButton(onClick = onDownloadSelected) {
                        Icon(
                            painter = painterResource(R.drawable.ic_downloads),
                            contentDescription = stringResource(R.string.download),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    IconButton(onClick = onDeleteSelected) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_delete_24),
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }

                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_more_vert_24),
                                contentDescription = stringResource(R.string.more),
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (selectedIds.size == 2) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.select_between)) },
                                    onClick = {
                                        menuExpanded = false
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
                                    menuExpanded = false
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
                                    menuExpanded = false
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
                                    menuExpanded = false
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
                        }
                    }
                }
            }
        }

        // Header (when not in multi-select mode)
        if (!isSelectionMode && totalCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$totalCount ${stringResource(R.string.items)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                var headerMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { headerMenuExpanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_more_vert_24),
                            contentDescription = stringResource(R.string.more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    DropdownMenu(
                        expanded = headerMenuExpanded,
                        onDismissRequest = { headerMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download_all)) },
                            onClick = {
                                headerMenuExpanded = false
                                onDownloadAll()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_downloads),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                headerMenuExpanded = false
                                onDeleteAll()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_delete_24),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_urls)) },
                            onClick = {
                                headerMenuExpanded = false
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
            }
        }

        // List or Empty State
        Box(modifier = Modifier.fillMaxSize()) {
            if (totalCount == 0 && items.itemCount == 0) {
                EmptyState(
                    iconRes = R.drawable.ic_no_results,
                    title = tabTitle,
                    message = stringResource(R.string.queue_empty),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        count = items.itemCount,
                        key = { index -> items[index]?.id ?: index.toLong() },
                        contentType = { "saved_download" },
                    ) { index ->
                        val item = items[index]
                        if (item != null) {
                            val actionIcon = if (isErroredTab && item.logID != null && item.logID != 0L) {
                                R.drawable.ic_terminal
                            } else {
                                R.drawable.ic_refresh
                            }

                            DownloadItemCard(
                                item = item,
                                isSelected = selectedIds.contains(item.id),
                                isSelectionMode = isSelectionMode,
                                enableSwipeLeft = enableSwipe,
                                enableSwipeRight = enableSwipe,
                                onSwipeLeft = { onDeleteItem(item) },
                                onSwipeRight = { onActionClick(item) },
                                onClick = { onCardClick(item) },
                                onLongClick = { onCardLongClick(item) },
                                onSelectToggle = { onSelectToggle(item.id) },
                                trailingAction = {
                                    IconButton(onClick = { onActionClick(item) }) {
                                        Icon(
                                            painter = painterResource(actionIcon),
                                            contentDescription = stringResource(R.string.download),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
