package com.deniscerri.ytdl.ui.compose.downloadcard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.ui.compose.components.PicassoImage
import com.deniscerri.ytdl.util.FileUtil

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
    onPickFormat: () -> Unit = {},
    onPickCut: () -> Unit = {},
    onPickCrop: () -> Unit = {},
    onPickExtraCommands: () -> Unit = {},
    onPickSubtitles: () -> Unit = {},
    onScheduleClick: () -> Unit = {},
    onDownloadClick: () -> Unit,
    onSaveForLaterClick: () -> Unit = {},
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
                PicassoImage(
                    url = state.thumbUrl,
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
        val incognitoCorner by animateDpAsState(
            targetValue = if (incognito) 24.dp else 10.dp,
            label = "incognito_corner"
        )
        val vaultCorner by animateDpAsState(
            targetValue = if (vaultMode) 24.dp else 10.dp,
            label = "vault_corner"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = incognito,
                onClick = onToggleIncognito,
                label = { Text(stringResource(R.string.incognito)) },
                shape = RoundedCornerShape(incognitoCorner),
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_incognito),
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
                shape = RoundedCornerShape(vaultCorner),
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
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
                AudioOptionsSection(
                    state = state,
                    onEvent = onEvent,
                    onPickFormat = onPickFormat,
                    onPickExtraCommands = onPickExtraCommands
                )
            }
            DownloadType.video -> {
                VideoOptionsSection(
                    state = state,
                    onEvent = onEvent,
                    onPickFormat = onPickFormat,
                    onPickCrop = onPickCrop,
                    onPickExtraCommands = onPickExtraCommands,
                    onPickSubtitles = onPickSubtitles
                )
            }
            DownloadType.command -> {
                CommandOptionsSection(
                    state = state,
                    onEvent = onEvent,
                    onPickExtraCommands = onPickExtraCommands
                )
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cut / Range Section (collapsible / toggleable)
        CutRangeSection(
            cutStart = state.cutStart,
            cutEnd = state.cutEnd,
            mergeCuts = state.mergeCuts,
            onRangeChanged = { start, end -> onEvent(DownloadConfigEvent.SetCutRange(start, end)) },
            onToggleMergeCuts = { onEvent(DownloadConfigEvent.ToggleMergeCuts(it)) },
            onOpenCutPlayer = onPickCut
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons: Schedule (Icon) + Cancel + Download
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onScheduleClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_schedule_m3),
                    contentDescription = stringResource(R.string.schedule),
                    modifier = Modifier.size(24.dp)
                )
            }

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
    onEvent: (DownloadConfigEvent) -> Unit,
    onPickFormat: () -> Unit,
    onPickExtraCommands: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.audio),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onPickFormat) {
                    val format = state.selectedFormat
                    Text(
                        text = format?.let { f -> f.format_note.ifBlank { f.format_id } } ?: "Select Format",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                title = "Embed Lyrics",
                checked = state.embedLyrics,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleEmbedLyrics(it)) }
            )
            ConfigSwitchRow(
                title = "Embed Thumbnail",
                checked = state.embedThumbnail,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleEmbedThumbnail(it)) }
            )
            ConfigSwitchRow(
                title = "Crop Thumbnail",
                checked = state.cropThumbnail,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleCropThumbnail(it)) }
            )
            ConfigSwitchRow(
                title = "Split by Chapters",
                checked = state.splitByChapters,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleSplitByChapters(it)) }
            )
            ConfigSwitchRow(
                title = "SponsorBlock",
                checked = state.sponsorBlock,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleSponsorBlock(it)) }
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPickExtraCommands,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.extraCommands.isNotBlank()) "Extra Commands: ${state.extraCommands}" else stringResource(R.string.extra_commands))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoOptionsSection(
    state: DownloadConfigState,
    onEvent: (DownloadConfigEvent) -> Unit,
    onPickFormat: () -> Unit,
    onPickCrop: () -> Unit,
    onPickExtraCommands: () -> Unit,
    onPickSubtitles: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.video),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onPickFormat) {
                    val format = state.selectedFormat
                    Text(
                        text = format?.let { f -> f.format_note.ifBlank { f.format_id } } ?: "Select Format",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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

            // Subtitles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Embed Subtitles", style = MaterialTheme.typography.bodyMedium)
                    if (state.subtitleLanguages.isNotBlank()) {
                        Text(
                            text = "Lang: ${state.subtitleLanguages}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPickSubtitles) {
                        Text("Languages")
                    }
                    Switch(
                        checked = state.embedSubtitles,
                        onCheckedChange = { onEvent(DownloadConfigEvent.ToggleEmbedSubtitles(it)) }
                    )
                }
            }

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
                title = "Recode Video",
                checked = state.recodeVideo,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleRecodeVideo(it)) }
            )
            ConfigSwitchRow(
                title = "Compatibility Mode",
                checked = state.compatibilityMode,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleCompatibilityMode(it)) }
            )
            ConfigSwitchRow(
                title = "Also Download As Audio",
                checked = state.alsoDownloadAsAudio,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleAlsoDownloadAsAudio(it)) }
            )
            ConfigSwitchRow(
                title = "Remove Audio",
                checked = state.removeAudio,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleRemoveAudio(it)) }
            )
            ConfigSwitchRow(
                title = "SponsorBlock",
                checked = state.sponsorBlock,
                onCheckedChange = { onEvent(DownloadConfigEvent.ToggleSponsorBlock(it)) }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPickCrop,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.cropValues.isNotBlank()) "Cropped" else stringResource(R.string.crop))
                }
                OutlinedButton(
                    onClick = onPickExtraCommands,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.extra_commands))
                }
            }
        }
    }
}

@Composable
private fun CommandOptionsSection(
    state: DownloadConfigState,
    onEvent: (DownloadConfigEvent) -> Unit,
    onPickExtraCommands: () -> Unit
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
                label = { Text("Command Template / Options") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPickExtraCommands,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.extra_commands))
            }
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
    mergeCuts: Boolean,
    onRangeChanged: (start: String, end: String) -> Unit,
    onToggleMergeCuts: (Boolean) -> Unit,
    onOpenCutPlayer: () -> Unit
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
                        painter = painterResource(R.drawable.ic_cut),
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.merge_cuts), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = mergeCuts,
                            onCheckedChange = onToggleMergeCuts
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenCutPlayer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Cut Player")
                    }
                }
            }
        }
    }
}
