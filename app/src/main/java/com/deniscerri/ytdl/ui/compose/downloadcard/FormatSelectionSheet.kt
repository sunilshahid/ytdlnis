package com.deniscerri.ytdl.ui.compose.downloadcard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.util.FileUtil

/**
 * Format selection bottom sheet (Phase 4c / eleXML §5 A9-A14).
 * Replaces format_select_bottom_sheet.xml, format_category_sheet.xml, format_item.xml,
 * format_item_shimmer.xml, format_type_label.xml, selectable_textview_filter.xml, and FormatAdapter.kt.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormatSelectionSheet(
    formats: List<Format>,
    selectedFormat: Format?,
    onFormatSelected: (Format) -> Unit,
    onShowFormatDetails: (Format) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    canMultiSelectAudio: Boolean = false,
    selectedAudioFormats: List<Format> = emptyList(),
    onAudioFormatsSelected: ((List<Format>) -> Unit)? = null
) {
    var categoryFilter by remember { mutableStateOf("ALL") } // ALL, VIDEO, AUDIO, SUGGESTED, SMALLEST
    var sortOrder by remember { mutableStateOf("filesize") } // filesize, resolution, codec, id

    val filteredFormats = remember(formats, categoryFilter, sortOrder) {
        var list = when (categoryFilter) {
            "VIDEO" -> formats.filter { !it.format_note.contains("audio", ignoreCase = true) }
            "AUDIO" -> formats.filter { it.format_note.contains("audio", ignoreCase = true) || it.vcodec == "none" }
            "SMALLEST" -> formats.filter { it.filesize > 0L }.sortedBy { it.filesize }
            else -> formats
        }
        when (sortOrder) {
            "filesize" -> list.sortedByDescending { it.filesize }
            "resolution" -> list.sortedByDescending { (it.width ?: 0) * (it.height ?: 0) }
            "codec" -> list.sortedBy { it.vcodec }
            "id" -> list.sortedBy { it.format_id }
            else -> list
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Drag handle bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.select_format),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${filteredFormats.size} formats",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Categories
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("ALL", "VIDEO", "AUDIO", "SMALLEST").forEach { cat ->
                FilterChip(
                    selected = categoryFilter == cat,
                    onClick = { categoryFilter = cat },
                    label = { Text(cat) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Format list
        if (isLoading) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                items(filteredFormats, key = { it.format_id }) { format ->
                    val isSelected = selectedFormat?.format_id == format.format_id
                    FormatRow(
                        format = format,
                        isSelected = isSelected,
                        onSelect = { onFormatSelected(format) },
                        onDetails = { onShowFormatDetails(format) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
}

@Composable
fun FormatRow(
    format: Format,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = format.format_note.ifBlank { format.format_id },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (format.container.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = format.container.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val codec = format.vcodec.takeIf { it != "none" && it.isNotBlank() } ?: format.acodec
                    if (codec.isNotBlank()) {
                        Text(
                            text = codec,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val fps = format.fps.orEmpty()
                    if (fps.isNotBlank() && fps != "0") {
                        Text(
                            text = "${fps}fps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (format.filesize > 0L) {
                        Text(
                            text = FileUtil.convertFileSize(format.filesize),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            IconButton(onClick = onDetails) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
