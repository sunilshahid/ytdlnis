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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.ui.compose.components.EmptyState

/**
 * Screen displaying the Queued Downloads tab (Phase 5).
 * Replaces `fragment_inqueue.xml` and `QueuedDownloadAdapter`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueDownloadScreen(
    items: PagingItems<DownloadItemSimple>,
    selectedIds: Set<Long>,
    isReorderMode: Boolean,
    enableSwipe: Boolean,
    modifier: Modifier = Modifier,
    onCardClick: (DownloadItemSimple) -> Unit = {},
    onCardLongClick: (DownloadItemSimple) -> Unit = {},
    onSelectToggle: (Long) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onInvertSelection: () -> Unit = {},
    onSelectBetween: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onMoveToTop: (Long) -> Unit = {},
    onMoveToBottom: (Long) -> Unit = {},
    onMoveUp: ((DownloadItemSimple) -> Unit)? = null,
    onMoveDown: ((DownloadItemSimple) -> Unit)? = null,
    onCancelItem: (DownloadItemSimple) -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onMoveSelectedToTop: () -> Unit = {},
    onMoveSelectedToBottom: () -> Unit = {},
    onCopySelectedUrls: () -> Unit = {},
    onSwipeLeft: (DownloadItemSimple) -> Unit = {},
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
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.close),
                        )
                    }

                    Text(
                        text = "${selectedIds.size} ${stringResource(R.string.selected)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(onClick = onMoveSelectedToTop) {
                        Icon(
                            painter = painterResource(R.drawable.ic_up),
                            contentDescription = stringResource(R.string.up),
                        )
                    }

                    IconButton(onClick = onMoveSelectedToBottom) {
                        Icon(
                            painter = painterResource(R.drawable.ic_down),
                            contentDescription = stringResource(R.string.down),
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
                                        painter = painterResource(R.drawable.ic_invert_selection),
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
                                        painter = painterResource(R.drawable.ic_copy_url),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteSelected()
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
                        }
                    }
                }
            }
        }

        // List or Empty State
        Box(modifier = Modifier.fillMaxSize()) {
            if (items.itemCount == 0) {
                EmptyState(
                    iconRes = R.drawable.ic_no_results,
                    title = stringResource(R.string.in_queue),
                    message = stringResource(R.string.queue_empty),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        count = items.itemCount,
                        key = { index -> items[index]?.id ?: index.toLong() },
                        contentType = { "queued_download" },
                    ) { index ->
                        val item = items[index]
                        if (item != null) {
                            var itemMenuExpanded by remember { mutableStateOf(false) }

                            DownloadItemCard(
                                item = item,
                                isSelected = selectedIds.contains(item.id),
                                isSelectionMode = isSelectionMode,
                                isReorderMode = isReorderMode,
                                enableSwipeLeft = enableSwipe,
                                onSwipeLeft = { onSwipeLeft(item) },
                                onClick = { onCardClick(item) },
                                onLongClick = { onCardLongClick(item) },
                                onSelectToggle = { onSelectToggle(item.id) },
                                onMoveUp = onMoveUp?.let { { it(item) } },
                                onMoveDown = onMoveDown?.let { { it(item) } },
                                trailingAction = {
                                    Box {
                                        IconButton(onClick = { itemMenuExpanded = true }) {
                                            Icon(
                                                painter = painterResource(R.drawable.baseline_more_vert_24),
                                                contentDescription = stringResource(R.string.more),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = itemMenuExpanded,
                                            onDismissRequest = { itemMenuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.up)) },
                                                onClick = {
                                                    itemMenuExpanded = false
                                                    onMoveToTop(item.id)
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_up),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.down)) },
                                                onClick = {
                                                    itemMenuExpanded = false
                                                    onMoveToBottom(item.id)
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_down),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    itemMenuExpanded = false
                                                    onCancelItem(item)
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
                                        }
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
