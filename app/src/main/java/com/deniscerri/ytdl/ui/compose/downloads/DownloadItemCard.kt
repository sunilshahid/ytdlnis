package com.deniscerri.ytdl.ui.compose.downloads

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.ui.compose.components.PicassoImage
import com.deniscerri.ytdl.util.FileUtil

/**
 * Material 3 Download Item Card (Phase 5).
 *
 * Used for Queue, Scheduled, Saved, Cancelled, and Errored download tabs.
 * Supports:
 * - 16:9 thumbnail with duration badge
 * - Title and metadata pills (type, file size, format note, codec)
 * - Multi-select mode with selection border / checkbox
 * - Reorder mode with drag handles / up-down actions
 * - Swipe-to-dismiss (swipe left for delete/cancel, swipe right for restart/download)
 * - Scheduled time header
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadItemCard(
    item: DownloadItemSimple,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    isReorderMode: Boolean = false,
    scheduledTimeText: String? = null,
    enableSwipeLeft: Boolean = false,
    enableSwipeRight: Boolean = false,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onSelectToggle: () -> Unit = {},
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    trailingAction: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (!scheduledTimeText.isNullOrBlank()) {
            Text(
                text = scheduledTimeText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        if (enableSwipeLeft || enableSwipeRight) {
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    when (value) {
                        SwipeToDismissBoxValue.EndToStart -> {
                            if (enableSwipeLeft) {
                                onSwipeLeft()
                            }
                            false
                        }
                        SwipeToDismissBoxValue.StartToEnd -> {
                            if (enableSwipeRight) {
                                onSwipeRight()
                            }
                            false
                        }
                        SwipeToDismissBoxValue.Settled -> false
                    }
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = enableSwipeRight,
                enableDismissFromEndToStart = enableSwipeLeft,
                backgroundContent = {
                    val direction = dismissState.dismissDirection
                    val alignment = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }
                    val color = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                        else -> Color.Transparent
                    }
                    val icon = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> R.drawable.ic_downloads
                        SwipeToDismissBoxValue.EndToStart -> R.drawable.baseline_delete_24
                        else -> null
                    }
                    val iconTint = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimaryContainer
                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onErrorContainer
                        else -> Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(color)
                            .padding(horizontal = 24.dp),
                        contentAlignment = alignment,
                    ) {
                        if (icon != null) {
                            Icon(
                                painter = painterResource(icon),
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
                content = {
                    DownloadItemCardContent(
                        item = item,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        isReorderMode = isReorderMode,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onSelectToggle = onSelectToggle,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        trailingAction = trailingAction,
                    )
                }
            )
        } else {
            DownloadItemCardContent(
                item = item,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                isReorderMode = isReorderMode,
                onClick = onClick,
                onLongClick = onLongClick,
                onSelectToggle = onSelectToggle,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                trailingAction = trailingAction,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadItemCardContent(
    item: DownloadItemSimple,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectToggle: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    trailingAction: @Composable (() -> Unit)?,
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "cardScale",
    )

    val shape = RoundedCornerShape(16.dp)
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "cardColor",
    )
    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    val displayTitle = item.title.ifEmpty { item.playlistTitle.ifEmpty { item.url } }

    val formatPills = remember(item.format, item.type) {
        val pills = mutableListOf<String>()
        val note = item.format.format_note.uppercase().replace("\n", " ").trim()
        if (note.isNotEmpty()) pills.add(note)
        val container = item.format.container.uppercase().trim()
        if (container.isNotEmpty()) pills.add(container)
        val size = FileUtil.convertFileSize(item.format.filesize)
        if (size.isNotEmpty() && size != "?") pills.add(size)
        val codec = item.format.vcodec.ifEmpty { item.format.acodec }.uppercase().trim()
        if (codec.isNotEmpty() && codec != "NONE") pills.add(codec)
        pills
    }

    Surface(
        shape = shape,
        color = containerColor,
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = {
                    if (isSelectionMode) {
                        onSelectToggle()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onLongClick()
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Reorder actions or Multi-select checkbox
            if (isReorderMode) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    if (onMoveUp != null) {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_up),
                                contentDescription = stringResource(R.string.up),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_drag_handle),
                        contentDescription = stringResource(R.string.reorder_queue),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    if (onMoveDown != null) {
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_down),
                                contentDescription = stringResource(R.string.down),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            } else if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            // Thumbnail with 16:9 ratio + duration badge
            Box(
                modifier = Modifier
                    .width(108.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                PicassoImage(
                    url = item.thumb,
                    contentDescription = displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    placeholderRes = R.drawable.ic_thumbnail_placeholder,
                )

                if (item.duration.isNotEmpty() && item.duration != "-1") {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                    ) {
                        Text(
                            text = item.duration,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }

                if (item.incognito) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_nightly),
                            contentDescription = stringResource(R.string.incognito),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title + Metadata Pills
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.author.isNotEmpty()) {
                    Text(
                        text = item.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Pills row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Type pill
                    val typeIcon = when (item.type) {
                        DownloadType.audio -> R.drawable.ic_music
                        DownloadType.video -> R.drawable.ic_video
                        DownloadType.command -> R.drawable.ic_terminal
                    }
                    val typeLabel = when (item.type) {
                        DownloadType.audio -> stringResource(R.string.audio)
                        DownloadType.video -> stringResource(R.string.video)
                        DownloadType.command -> stringResource(R.string.command)
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                painter = painterResource(typeIcon),
                                contentDescription = typeLabel,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }

                    // Remaining pills (file size, resolution/note)
                    formatPills.take(2).forEach { pillText ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = pillText,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Trailing action
            if (trailingAction != null) {
                trailingAction()
            }
        }
    }
}
