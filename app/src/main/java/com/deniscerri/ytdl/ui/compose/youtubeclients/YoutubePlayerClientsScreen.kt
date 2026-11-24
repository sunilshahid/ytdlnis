package com.deniscerri.ytdl.ui.compose.youtubeclients

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.deniscerri.ytdl.ui.compose.components.YtdlSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.YoutubePlayerClientItem
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.ui.compose.components.YtdlActionChip

/**
 * Port of `fragment_youtube_player_clients.xml` + `player_client_item.xml`.
 *
 * The list order is significant: yt-dlp tries the configured player clients in this order, so the
 * drag handle reorder is preserved. There is no Compose equivalent of `ItemTouchHelper`, so the
 * vertical drag is handled by [detectDragGestures] on the handle of each row and the target index is
 * resolved from the visible `LazyColumn` items.
 */
@Composable
fun YoutubePlayerClientsScreen(
    items: List<YoutubePlayerClientItem>,
    showDragHandles: Boolean,
    onAddClick: () -> Unit,
    onToggleDragHandles: () -> Unit,
    onItemClick: (YoutubePlayerClientItem) -> Unit,
    onItemLongClick: (YoutubePlayerClientItem) -> Unit,
    onStatusToggle: (YoutubePlayerClientItem, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp)
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YtdlActionChip(
                label = stringResource(R.string.add),
                onClick = onAddClick,
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_plus),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )

            Spacer(Modifier.weight(1f))

            // `drag` in fragment_youtube_player_clients.xml is only shown for 2+ clients.
            if (items.size > 1) {
                IconButton(
                    onClick = onToggleDragHandles,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_drag_handle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.no_results),
                    iconRes = R.drawable.ic_no_results,
                )
            }
        } else {
            ReorderableClientList(
                items = items,
                showDragHandles = showDragHandles,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                onStatusToggle = onStatusToggle,
                onMove = onMove,
            )
        }
    }
}

@Composable
private fun ReorderableClientList(
    items: List<YoutubePlayerClientItem>,
    showDragHandles: Boolean,
    onItemClick: (YoutubePlayerClientItem) -> Unit,
    onItemLongClick: (YoutubePlayerClientItem) -> Unit,
    onStatusToggle: (YoutubePlayerClientItem, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items) { index, item ->
            val isDragging = draggingIndex == index

            PlayerClientRow(
                item = item,
                showDragHandle = showDragHandles,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else 0f
                    },
                onDragStart = {
                    draggingIndex = index
                    dragOffset = 0f
                },
                onDrag = { deltaY ->
                    val current = draggingIndex ?: return@PlayerClientRow
                    dragOffset += deltaY

                    val layoutInfo = listState.layoutInfo
                    val currentInfo = layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == current } ?: return@PlayerClientRow
                    val draggedCenter = currentInfo.offset + dragOffset + currentInfo.size / 2f
                    val targetInfo = layoutInfo.visibleItemsInfo.firstOrNull { info ->
                        info.index != current &&
                            draggedCenter >= info.offset &&
                            draggedCenter <= info.offset + info.size
                    }

                    if (targetInfo != null) {
                        onMove(current, targetInfo.index)
                        draggingIndex = targetInfo.index
                        // Keep the finger-to-row offset stable across the swap.
                        dragOffset += (currentInfo.offset - targetInfo.offset).toFloat()
                    }
                },
                onDragEnd = {
                    draggingIndex = null
                    dragOffset = 0f
                },
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
                onStatusToggle = { enabled -> onStatusToggle(item, enabled) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerClientRow(
    item: YoutubePlayerClientItem,
    showDragHandle: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStatusToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = if (item.enabled) 1f else 0.3f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(5.dp), // ShapeAppearanceOverlay.Avatar
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showDragHandle) {
                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.y)
                                },
                            )
                        }
                        .padding(vertical = 16.dp)
                        .padding(end = 20.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_drag_handle),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 20.dp),
            ) {
                Text(
                    text = item.playerClient,
                    modifier = Modifier.alpha(contentAlpha),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val chips = clientChips(item)
                if (chips.isNotEmpty()) {
                    ClientChipFlow(
                        chips = chips,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .alpha(contentAlpha),
                    )
                }
            }

            YtdlSwitch(
                checked = item.enabled,
                onCheckedChange = onStatusToggle,
            )
        }
    }
}

/**
 * Same chip order as `YoutubePlayerClientAdapter`: PO tokens are appended in reverse, then the URL
 * regex summary last.
 */
private fun clientChips(item: YoutubePlayerClientItem): List<String> {
    val chips = item.poTokens.asReversed().map { "PO Token (${it.context}): ${it.token}" }
        .toMutableList()
    if (item.urlRegex.isNotEmpty()) {
        chips.add("URL Regex: " + item.urlRegex.joinToString(", "))
    }
    return chips
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClientChipFlow(
    chips: List<String>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chips.forEach { text ->
            Text(
                text = text,
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .defaultMinSize(minWidth = 30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
