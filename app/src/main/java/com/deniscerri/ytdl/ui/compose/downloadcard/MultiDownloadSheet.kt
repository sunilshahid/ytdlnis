package com.deniscerri.ytdl.ui.compose.downloadcard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem

/**
 * Multi-download bottom sheet replacing download_multiple_bottom_sheet.xml, adjust_video.xml,
 * adjust_audio.xml, adjust_command.xml, download_type_sheet.xml, processing_sort_sheet.xml,
 * bottom_app_bar_text.xml, download_multiple_menu.xml, and select_multiple_items_menu_context.xml.
 * (A6-A8, A15-A17, A42).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiDownloadSheet(
    items: List<DownloadItem>,
    totalFileSize: String,
    incognito: Boolean,
    onToggleIncognito: () -> Unit,
    onConfigureItem: (DownloadItem) -> Unit,
    onRemoveItem: (DownloadItem) -> Unit,
    onApplyBatchDownloadType: (DownloadType) -> Unit,
    onApplyBatchContainer: (String) -> Unit,
    onApplyBatchAudioBitrate: (String) -> Unit,
    onApplyBatchSubtitles: (embed: Boolean) -> Unit,
    onApplyBatchChapters: (embed: Boolean) -> Unit,
    onApplyBatchThumbnail: (embed: Boolean) -> Unit,
    onApplyBatchSponsorBlock: (enabled: Boolean) -> Unit,
    onSortItems: (sortBy: String) -> Unit,
    onScheduleClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showAdjustMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var currentBatchType by remember { mutableStateOf(DownloadType.video) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Header: Title, count, estimated size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.download_multiple),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${items.size} ${stringResource(R.string.items)}" +
                            if (totalFileSize.isNotBlank()) " â€¢ $totalFileSize" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { isSelectionMode = !isSelectionMode }) {
                    Icon(
                        painter = painterResource(if (isSelectionMode) R.drawable.baseline_close_24 else R.drawable.ic_select_all),
                        contentDescription = "Selection Mode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_filter_alt),
                            contentDescription = "Sort",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sort by Title") },
                            onClick = {
                                showSortMenu = false
                                onSortItems("title")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Duration") },
                            onClick = {
                                showSortMenu = false
                                onSortItems("duration")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Author") },
                            onClick = {
                                showSortMenu = false
                                onSortItems("author")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Size") },
                            onClick = {
                                showSortMenu = false
                                onSortItems("size")
                            }
                        )
                    }
                }
            }
        }

        // Selection actions bar
        AnimatedVisibility(visible = isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { selectedIds = items.map { it.id }.toSet() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.select_all))
                }
                TextButton(
                    onClick = {
                        val allIds = items.map { it.id }.toSet()
                        selectedIds = allIds - selectedIds
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Invert")
                }
                TextButton(
                    onClick = { selectedIds = emptySet() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.deselect_all))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Batch Download Type Segmented Control
        val types = listOf(DownloadType.audio, DownloadType.video, DownloadType.command)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = currentBatchType == type,
                    onClick = {
                        currentBatchType = type
                        onApplyBatchDownloadType(type)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size)
                ) {
                    Text(when (type) {
                        DownloadType.audio -> stringResource(R.string.audio)
                        DownloadType.video -> stringResource(R.string.video)
                        else -> stringResource(R.string.command)
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Batch adjust row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = incognito,
                onClick = onToggleIncognito,
                label = { Text(stringResource(R.string.incognito)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_incognito),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.weight(1f)
            )

            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showAdjustMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Batch Options")
                }
                DropdownMenu(
                    expanded = showAdjustMenu,
                    onDismissRequest = { showAdjustMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Toggle Subtitles") },
                        onClick = {
                            showAdjustMenu = false
                            onApplyBatchSubtitles(true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Toggle Chapters") },
                        onClick = {
                            showAdjustMenu = false
                            onApplyBatchChapters(true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Toggle Thumbnail") },
                        onClick = {
                            showAdjustMenu = false
                            onApplyBatchThumbnail(true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Toggle SponsorBlock") },
                        onClick = {
                            showAdjustMenu = false
                            onApplyBatchSponsorBlock(true)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Item List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            items(items, key = { it.id }) { item ->
                DownloadCard(
                    title = item.title,
                    author = item.author,
                    duration = item.duration,
                    thumbUrl = item.thumb,
                    downloadType = item.type,
                    containerOrQuality = item.container.ifBlank { item.format.format_note },
                    fileSize = item.downloadSize,
                    isSelected = selectedIds.contains(item.id),
                    isSelectionMode = isSelectionMode,
                    onToggleSelect = {
                        selectedIds = if (selectedIds.contains(item.id)) {
                            selectedIds - item.id
                        } else {
                            selectedIds + item.id
                        }
                    },
                    onClick = { onConfigureItem(item) },
                    onRemove = { onRemoveItem(item) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons: Schedule + Download
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onScheduleClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.schedule))
            }

            Button(
                onClick = onDownloadClick,
                modifier = Modifier.weight(1.5f)
            ) {
                Text("${stringResource(R.string.download)} (${items.size})")
            }
        }
    }
}
