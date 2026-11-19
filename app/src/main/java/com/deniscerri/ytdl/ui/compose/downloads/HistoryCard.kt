package com.deniscerri.ytdl.ui.compose.downloads

import android.text.format.DateFormat
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.ui.compose.components.PicassoImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Material 3 History Card (Phase 5).
 * Supports both the standard hero/backdrop design and the compact download card style.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DismissibleHistoryCard(
    item: HistoryItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    useDownloadCardDesign: Boolean,
    enableSwipe: Boolean,
    modifier: Modifier = Modifier,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onSelectToggle: () -> Unit = {},
    onActionClick: () -> Unit = {},
) {
    if (enableSwipe) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        onSwipeLeft()
                        false
                    }
                    SwipeToDismissBoxValue.StartToEnd -> {
                        onSwipeRight()
                        false
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            modifier = modifier,
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
                    SwipeToDismissBoxValue.StartToEnd -> R.drawable.ic_refresh
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
                        .clip(RoundedCornerShape(20.dp))
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
                if (useDownloadCardDesign) {
                    HistoryDownloadCardDesign(
                        item = item,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onSelectToggle = onSelectToggle,
                        onActionClick = onActionClick,
                    )
                } else {
                    HistoryStandardCardDesign(
                        item = item,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onSelectToggle = onSelectToggle,
                        onActionClick = onActionClick,
                    )
                }
            }
        )
    } else {
        if (useDownloadCardDesign) {
            HistoryDownloadCardDesign(
                item = item,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onClick = onClick,
                onLongClick = onLongClick,
                onSelectToggle = onSelectToggle,
                onActionClick = onActionClick,
                modifier = modifier,
            )
        } else {
            HistoryStandardCardDesign(
                item = item,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onClick = onClick,
                onLongClick = onLongClick,
                onSelectToggle = onSelectToggle,
                onActionClick = onActionClick,
                modifier = modifier,
            )
        }
    }
}

/**
 * Standard hero-backdrop history card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryStandardCardDesign(
    item: HistoryItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectToggle: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dateTimeFormat = remember(context) {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm")
        SimpleDateFormat(pattern, Locale.getDefault())
    }

    val cardInteractionSource = remember { MutableInteractionSource() }
    val isPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "historyStandardScale",
    )

    val filePresent = remember(item.downloadPath) {
        item.downloadPath.isNotEmpty() && item.downloadPath.any { path ->
            path.isNotBlank() && File(path).exists()
        }
    }

    val shape = RoundedCornerShape(20.dp)
    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else null

    val displayTitle = remember(item.title, item.url) {
        val raw = item.title.ifEmpty { item.url }
        if (raw.length > 100) raw.take(40) + "..." else raw
    }

    val dateFormatted = remember(item.time) {
        dateTimeFormat.format(Date(item.time * 1000L))
    }

    val typeIcon = when (item.type) {
        DownloadType.audio -> if (filePresent) R.drawable.ic_music_downloaded else R.drawable.ic_music
        DownloadType.video -> if (filePresent) R.drawable.ic_video_downloaded else R.drawable.ic_video
        DownloadType.command -> if (filePresent) R.drawable.ic_terminal else R.drawable.baseline_code_off_24
        else -> if (filePresent) R.drawable.ic_video_downloaded else R.drawable.ic_video
    }

    Surface(
        shape = shape,
        border = borderStroke,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2.5f)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = {
                    if (isSelectionMode) onSelectToggle() else onClick()
                },
                onLongClick = onLongClick,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background thumbnail
            PicassoImage(
                url = item.thumb,
                contentDescription = displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (filePresent) 1f else 0.65f
                    },
                placeholderRes = R.drawable.ic_video,
            )

            // Scrim gradient for text contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                    ),
            )

            // Card content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top row: Checkbox / Title & Action Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectToggle() },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    ) {
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                shadow = Shadow(
                                    color = Color.Black,
                                    offset = Offset(2f, 2f),
                                    blurRadius = 3f,
                                ),
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (item.author.isNotEmpty()) {
                            Text(
                                text = item.author,
                                color = Color.White.copy(alpha = 0.9f),
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    shadow = Shadow(
                                        color = Color.Black,
                                        offset = Offset(2f, 2f),
                                        blurRadius = 3f,
                                    ),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (filePresent) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.size(38.dp),
                        onClick = {
                            if (filePresent) {
                                onActionClick()
                            }
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(typeIcon),
                                contentDescription = stringResource(R.string.share),
                                tint = if (filePresent) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Bottom row: Duration and Date/Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val durationText = if (item.downloadPath.size == 1) item.duration else "${item.downloadPath.size} files"
                    Text(
                        text = durationText,
                        color = Color.White.copy(alpha = 0.9f),
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(
                                color = Color.Black,
                                offset = Offset(2f, 2f),
                                blurRadius = 3f,
                            ),
                        ),
                    )

                    Text(
                        text = dateFormatted,
                        color = Color.White.copy(alpha = 0.9f),
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(
                                color = Color.Black,
                                offset = Offset(2f, 2f),
                                blurRadius = 3f,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Compact download-card history design.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryDownloadCardDesign(
    item: HistoryItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectToggle: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dateTimeFormat = remember(context) {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm")
        SimpleDateFormat(pattern, Locale.getDefault())
    }

    val cardInteractionSource = remember { MutableInteractionSource() }
    val isPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "historyCompactScale",
    )

    val filePresent = remember(item.downloadPath) {
        item.downloadPath.isNotEmpty() && item.downloadPath.any { path ->
            path.isNotBlank() && File(path).exists()
        }
    }

    val shape = RoundedCornerShape(16.dp)
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "historyCardColor",
    )
    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    val displayTitle = item.title.ifEmpty { item.url }
    val dateFormatted = remember(item.time) { dateTimeFormat.format(Date(item.time * 1000L)) }

    val typeIcon = when (item.type) {
        DownloadType.audio -> if (filePresent) R.drawable.ic_music_downloaded else R.drawable.ic_music
        DownloadType.video -> if (filePresent) R.drawable.ic_video_downloaded else R.drawable.ic_video
        DownloadType.command -> if (filePresent) R.drawable.ic_terminal else R.drawable.baseline_code_off_24
        else -> if (filePresent) R.drawable.ic_video_downloaded else R.drawable.ic_video
    }

    Surface(
        shape = shape,
        color = containerColor,
        border = borderStroke,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = {
                    if (isSelectionMode) onSelectToggle() else onClick()
                },
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            // Thumbnail
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
                    placeholderRes = R.drawable.ic_video,
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
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
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

                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            IconButton(onClick = onActionClick) {
                Icon(
                    painter = painterResource(typeIcon),
                    contentDescription = stringResource(R.string.share),
                    tint = if (filePresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
