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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.Format

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadConfigSheet(
    state: DownloadConfigState,
    incognito: Boolean,
    vaultMode: Boolean,
    onEvent: (DownloadConfigEvent) -> Unit,
    onDownloadTypeChange: (DownloadType) -> Unit,
    onToggleIncognito: () -> Unit,
    onToggleVault: () -> Unit,
    onDownloadClick: () -> Unit,
    onDismiss: () -> Unit,
    onOpenUrlClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(scrollState)
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

        // Header: Thumbnail + Title + Author
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.thumbUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(state.thumbUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 84.dp, height = 56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.ifBlank { stringResource(R.string.download) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.author.isNotBlank()) {
                    Text(
                        text = state.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Download Type Selector (Audio, Video, Command)
        val types = listOf(DownloadType.audio, DownloadType.video, DownloadType.command)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = state.downloadType == type,
                    onClick = { onDownloadTypeChange(type) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size)
                ) {
                    val label = when (type) {
                        DownloadType.audio -> stringResource(R.string.audio)
                        DownloadType.video -> stringResource(R.string.video)
                        DownloadType.command -> stringResource(R.string.command)
                        else -> type.name
                    }
                    Text(label)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy Row: Incognito & Vault Mode
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
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = vaultMode,
                onClick = onToggleVault,
                label = { Text(stringResource(R.string.private_vault)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Type-specific options
        when (state.downloadType) {
            DownloadType.audio -> {
                AudioOptionsSection(state = state, onEvent = onEvent)
            }
            DownloadType.video -> {
                VideoOptionsSection(state = state, onEvent = onEvent)
            }
            DownloadType.command -> {
                CommandOptionsSection(state = state, onEvent = onEvent)
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cut / Range Section (collapsible / toggleable)
        CutRangeSection(
            cutStart = state.cutStart,
            cutEnd = state.cutEnd,
            onRangeChanged = { start, end -> onEvent(DownloadConfigEvent.SetCutRange(start, end)) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons: Download + URL + Cancel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel))
            }

            Button(
                onClick = onDownloadClick,
                modifier = Modifier.weight(1.5f)
            ) {
                Text(stringResource(R.string.download))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.url.isNotBlank()) {
            TextButton(
                onClick = onOpenUrlClick,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = state.url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioOptionsSection(
    state: DownloadConfigState,
    onEvent: (DownloadConfigEvent) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.audio),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Container / Format selection
            Text(text = "Container", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            val containers = listOf("mp3", "m4a", "opus", "flac", "wav")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                containers.forEach { container ->
                    FilterChip(
                        selected = state.container == container,
                        onClick = { onEvent(DownloadConfigEvent.SelectContainer(container)) },
                        label = { Text(container) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bitrate selection
            Text(text = "Bitrate", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            val bitrates = listOf("320", "256", "192", "128")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                bitrates.forEach { bitrate ->
                    FilterChip(
                        selected = state.audioBitrate == bitrate,
                        onClick = { onEvent(DownloadConfigEvent.SelectAudioBitrate(bitrate)) },
                        label = { Text("${bitrate}k") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Toggles
            ConfigSwitchRow(
                title = "Embed Thumbnail",
                checked = state.embedThumbnail,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleEmbedThumbnail(it)) }
            )
            ConfigSwitchRow(
                title = "SponsorBlock",
                checked = state.sponsorBlock,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleSponsorBlock(it)) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoOptionsSection(
    state: DownloadConfigState,
    onEvent: (DownloadConfigEvent) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.video),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Container selection
            Text(text = "Container", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            val containers = listOf("mp4", "mkv", "webm")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                containers.forEach { container ->
                    FilterChip(
                        selected = state.container == container,
                        onClick = { onEvent(DownloadConfigEvent.SelectContainer(container)) },
                        label = { Text(container) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Toggles
            ConfigSwitchRow(
                title = "Embed Subtitles",
                checked = state.embedSubtitles,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleEmbedSubtitles(it)) }
            )
            ConfigSwitchRow(
                title = "Embed Chapters",
                checked = state.embedChapters,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleEmbedChapters(it)) }
            )
            ConfigSwitchRow(
                title = "Embed Thumbnail",
                checked = state.embedThumbnail,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleEmbedThumbnail(it)) }
            )
            ConfigSwitchRow(
                title = "SponsorBlock",
                checked = state.sponsorBlock,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleSponsorBlock(it)) }
            )
        }
    }
}

@Composable
private fun CommandOptionsSection(
    state: DownloadConfigState,
    onEvent: (DownloadConfigEvent) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.command),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.customCommand,
                onValueChange = { onEvent(DownloadConfigEvent.SetCustomCommand(it)) },
                label = { Text("Custom Command Options") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}

@Composable
private fun ConfigSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun CutRangeSection(
    cutStart: String,
    cutEnd: String,
    onRangeChanged: (start: String, end: String) -> Unit
) {
    var expanded by remember { mutableStateOf(cutStart.isNotBlank() || cutEnd.isNotBlank()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cut / Timestamp Range",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (expanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = cutStart,
                            onValueChange = { onRangeChanged(it, cutEnd) },
                            label = { Text("Start (00:00:00)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cutEnd,
                            onValueChange = { onRangeChanged(cutStart, it) },
                            label = { Text("End (00:00:00)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
