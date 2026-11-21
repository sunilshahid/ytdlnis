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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.CommandTemplate

/**
 * Template and Shortcut sheets replacing command_template_list.xml, create_command_template.xml,
 * template_shortcuts.xml, template_shortcuts_list.xml, command_template_item.xml,
 * filter_chip.xml, input_chip.xml, suggestion_chip.xml (A32-A36, A40-A41, A43).
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorSheet(
    template: CommandTemplate?,
    availableShortcuts: List<String> = emptyList(),
    enableDataFetchingOption: Boolean = false,
    onSave: (CommandTemplate) -> Unit,
    onDelete: ((CommandTemplate) -> Unit)? = null,
    onEditShortcuts: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(template?.title ?: "") }
    var content by remember { mutableStateOf(template?.content ?: "") }
    var useAsExtraCommand by remember { mutableStateOf(template?.useAsExtraCommand ?: false) }
    var useAsExtraCommandAudio by remember { mutableStateOf(template?.useAsExtraCommandAudio ?: true) }
    var useAsExtraCommandVideo by remember { mutableStateOf(template?.useAsExtraCommandVideo ?: true) }
    var useAsExtraCommandDataFetching by remember { mutableStateOf(template?.useAsExtraCommandDataFetching ?: false) }
    var preferredCommandTemplate by remember { mutableStateOf(template?.preferredCommandTemplate ?: false) }
    var urlRegexInput by remember { mutableStateOf("") }
    var urlRegexList by remember { mutableStateOf(template?.urlRegex?.toList() ?: emptyList()) }

    val canUseURLRegex = useAsExtraCommand || useAsExtraCommandDataFetching || preferredCommandTemplate
    val isValid = title.isNotBlank() && content.isNotBlank() && (!useAsExtraCommand || (useAsExtraCommandAudio || useAsExtraCommandVideo))

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(scrollState)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (template == null) stringResource(R.string.new_template) else stringResource(R.string.update_template),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text(stringResource(R.string.command)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        if (availableShortcuts.isNotEmpty() || onEditShortcuts != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.shortcuts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onEditShortcuts != null) {
                    OutlinedButton(onClick = onEditShortcuts) {
                        Text(stringResource(R.string.edit_shortcuts), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                availableShortcuts.forEach { shortcut ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            content = if (content.isBlank()) shortcut else "$content $shortcut"
                        },
                        label = { Text(shortcut) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preferred command template switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.preferred_command_template),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = preferredCommandTemplate,
                onCheckedChange = { preferredCommandTemplate = it }
            )
        }

        // Extra commands switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.use_as_extra_command),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = useAsExtraCommand,
                onCheckedChange = {
                    useAsExtraCommand = it
                    if (it) {
                        useAsExtraCommandAudio = true
                        useAsExtraCommandVideo = true
                    }
                }
            )
        }

        AnimatedVisibility(visible = useAsExtraCommand) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useAsExtraCommandAudio,
                        onCheckedChange = { useAsExtraCommandAudio = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.audio), style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useAsExtraCommandVideo,
                        onCheckedChange = { useAsExtraCommandVideo = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.video), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (enableDataFetchingOption) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.use_as_extra_command_data_fetching),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = useAsExtraCommandDataFetching,
                    onCheckedChange = { useAsExtraCommandDataFetching = it }
                )
            }
        }

        AnimatedVisibility(visible = canUseURLRegex) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.url_regex),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = urlRegexInput,
                        onValueChange = { urlRegexInput = it },
                        label = { Text("Regex (e.g. .*youtube.*)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (urlRegexInput.isNotBlank()) {
                                urlRegexList = urlRegexList + urlRegexInput.trim()
                                urlRegexInput = ""
                            }
                        }
                    ) {
                        Text(stringResource(R.string.add))
                    }
                }

                if (urlRegexList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        urlRegexList.forEach { regex ->
                            InputChip(
                                selected = false,
                                onClick = { urlRegexList = urlRegexList - regex },
                                label = { Text(regex) },
                                trailingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.baseline_close_24),
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (template != null && onDelete != null) {
                OutlinedButton(
                    onClick = { onDelete(template) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = {
                    val saved = (template ?: CommandTemplate(
                        0,
                        title,
                        content,
                        useAsExtraCommand,
                        useAsExtraCommandAudio,
                        useAsExtraCommandVideo,
                        useAsExtraCommandDataFetching,
                        preferredCommandTemplate,
                        urlRegexList.toMutableList()
                    )).apply {
                        this.title = title
                        this.content = content
                        this.useAsExtraCommand = useAsExtraCommand
                        this.useAsExtraCommandAudio = useAsExtraCommandAudio
                        this.useAsExtraCommandVideo = useAsExtraCommandVideo
                        this.useAsExtraCommandDataFetching = useAsExtraCommandDataFetching
                        this.preferredCommandTemplate = preferredCommandTemplate
                        this.urlRegex = urlRegexList.toMutableList()
                    }
                    onSave(saved)
                },
                enabled = isValid,
                modifier = Modifier.weight(1.5f)
            ) {
                Text(stringResource(if (template == null) R.string.create else R.string.update))
            }
        }
    }
}

@Composable
fun TemplatePickerSheet(
    templates: List<CommandTemplate>,
    onlyOne: Boolean = false,
    onSelect: (List<CommandTemplate>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTemplates by remember { mutableStateOf(setOf<CommandTemplate>()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.templates),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (templates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_templates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                items(templates, key = { it.id }) { template ->
                    val isSelected = selectedTemplates.contains(template)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (onlyOne) {
                                    onSelect(listOf(template))
                                } else {
                                    selectedTemplates = if (isSelected) {
                                        selectedTemplates - template
                                    } else {
                                        selectedTemplates + template
                                    }
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = template.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = template.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
            if (!onlyOne) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSelect(selectedTemplates.toList()) },
                    enabled = selectedTemplates.isNotEmpty()
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutsSheet(
    shortcuts: List<String>,
    onAddShortcut: (String) -> Unit,
    onRemoveShortcut: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newShortcut by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.shortcuts),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newShortcut,
                onValueChange = { newShortcut = it },
                label = { Text("New Shortcut (e.g. --embed-subs)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newShortcut.isNotBlank()) {
                        onAddShortcut(newShortcut.trim())
                        newShortcut = ""
                    }
                }
            ) {
                Text(stringResource(R.string.add))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            shortcuts.forEach { shortcut ->
                InputChip(
                    selected = false,
                    onClick = { onRemoveShortcut(shortcut) },
                    label = { Text(shortcut) },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.baseline_close_24),
                            contentDescription = "Remove",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutsListSheet(
    shortcuts: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.shortcuts),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            shortcuts.forEach { shortcut ->
                FilterChip(
                    selected = false,
                    onClick = { onSelect(shortcut) },
                    label = { Text(shortcut) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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
