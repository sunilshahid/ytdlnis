package com.deniscerri.ytdl.ui.compose.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R

@Composable
fun AdvancedSettingsScreen(
    onNavigatePlayerClients: () -> Unit,
    onNavigatePoTokens: () -> Unit,
    onSelectFormatImportanceVideo: (() -> Unit)? = null,
    formatImportanceVideoSummary: String,
    onSelectFormatImportanceAudio: (() -> Unit)? = null,
    formatImportanceAudioSummary: String,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var activeSortDialog by remember { mutableStateOf<String?>(null) }

    var useFormatSorting by remember {
        mutableStateOf(prefs.getBoolean("use_format_sorting", false))
    }

    SettingsScreenColumn(modifier = modifier) {
        // YouTube
        SettingsCategory(title = "YouTube")

        SettingsPreference(
            title = stringResource(R.string.player_client),
            summary = stringResource(R.string.player_client_summary),
            iconRes = R.drawable.baseline_person_24,
            showChevron = true,
            onClick = onNavigatePlayerClients,
        )

        SettingsPreference(
            title = stringResource(R.string.generate_potokens),
            iconRes = R.drawable.baseline_stars_24,
            showChevron = true,
            onClick = onNavigatePoTokens,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.use_app_language_for_metadata),
            summary = stringResource(R.string.use_app_language_for_metadata_summary),
            key = "use_app_language_for_metadata",
            defaultValue = false,
            iconRes = R.drawable.ic_language,
        )

        SettingsEditTextPreference(
            title = stringResource(R.string.other_youtube_extractor_args),
            key = "youtube_other_extractor_args",
            defaultValue = "",
            iconRes = R.drawable.ic_code,
        )

        // Command templates
        SettingsCategory(title = stringResource(R.string.command_templates))

        SettingsSwitchPreference(
            title = stringResource(R.string.data_fetching_extra_command),
            summary = stringResource(R.string.data_fetching_extra_command_summary),
            key = "enable_data_fetching_extra_commands",
            defaultValue = false,
            iconRes = R.drawable.ic_terminal,
        )

        // Format
        SettingsCategory(title = stringResource(R.string.format))

        SettingsSwitchPreference(
            title = stringResource(R.string.use_format_sorting),
            key = "use_format_sorting",
            defaultValue = false,
            iconRes = R.drawable.baseline_arrow_upward_24,
            onCheckedChange = { useFormatSorting = it }
        )

        SettingsPreference(
            title = "${stringResource(R.string.format_importance)} [${stringResource(R.string.video)}]",
            summary = formatImportanceVideoSummary.ifBlank { null },
            iconRes = R.drawable.ic_format,
            enabled = useFormatSorting,
            showChevron = true,
            onClick = {
                if (onSelectFormatImportanceVideo != null) onSelectFormatImportanceVideo() else activeSortDialog = "video"
            },
        )

        SettingsPreference(
            title = "${stringResource(R.string.format_importance)} [${stringResource(R.string.audio)}]",
            summary = formatImportanceAudioSummary.ifBlank { null },
            iconRes = R.drawable.ic_format,
            enabled = useFormatSorting,
            showChevron = true,
            onClick = {
                if (onSelectFormatImportanceAudio != null) onSelectFormatImportanceAudio() else activeSortDialog = "audio"
            },
        )

        // Downloading
        SettingsCategory(title = stringResource(R.string.downloading))

        SettingsSwitchPreference(
            title = stringResource(R.string.disable_write_info_json),
            summary = stringResource(R.string.disable_write_info_json_summary),
            key = "disable_write_info_json",
            defaultValue = false,
            iconRes = R.drawable.if_file_rename,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.no_check_certificates),
            key = "no_check_certificates",
            defaultValue = false,
            iconRes = R.drawable.baseline_note_24,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.user_agent_header),
            key = "use_header",
            defaultValue = false,
            iconRes = R.drawable.if_file_rename,
        )

        // Misc
        SettingsCategory(title = stringResource(R.string.misc))

        SettingsSwitchPreference(
            title = stringResource(R.string.disable_flat_playlist),
            summary = stringResource(R.string.disable_flat_playlist_summary),
            key = "no_flat_playlist",
            defaultValue = false,
            iconRes = R.drawable.ic_search,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.use_original_url_playlist),
            summary = stringResource(R.string.use_original_url_playlist_summary),
            key = "use_original_url_playlist",
            defaultValue = false,
            iconRes = R.drawable.ic_lines,
        )

        // Reset
        ResetPreferencesPreference(onReset = onReset)
    }

    if (activeSortDialog == "video") {
        SortableFormatImportanceDialog(
            title = "${stringResource(R.string.format_importance)} [${stringResource(R.string.video)}]",
            key = "format_importance_video",
            itemsArray = stringArrayResource(R.array.format_importance_video).toList(),
            valuesArray = stringArrayResource(R.array.format_importance_video_values).toList(),
            onDismiss = { activeSortDialog = null }
        )
    } else if (activeSortDialog == "audio") {
        SortableFormatImportanceDialog(
            title = "${stringResource(R.string.format_importance)} [${stringResource(R.string.audio)}]",
            key = "format_importance_audio",
            itemsArray = stringArrayResource(R.array.format_importance_audio).toList(),
            valuesArray = stringArrayResource(R.array.format_importance_audio_values).toList(),
            onDismiss = { activeSortDialog = null }
        )
    }
}

@Composable
fun SortableFormatImportanceDialog(
    title: String,
    key: String,
    itemsArray: List<String>,
    valuesArray: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val itemValues = remember { valuesArray.toSet() }
    val prefValue = remember { prefs.getString(key, itemValues.joinToString(",")).orEmpty() }
    val prefArr = remember { prefValue.split(",") }
    var currentItems by remember {
        mutableStateOf(
            itemValues.sortedBy { prefArr.indexOf(it) }.map {
                Pair(it, itemsArray.getOrNull(valuesArray.indexOf(it)) ?: it)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.format_importance_note),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                currentItems.forEachIndexed { index, (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val list = currentItems.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index - 1]
                                    list[index - 1] = temp
                                    currentItems = list
                                }
                            },
                            enabled = index > 0
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_expand_less_24),
                                contentDescription = "Move Up"
                            )
                        }

                        IconButton(
                            onClick = {
                                if (index < currentItems.size - 1) {
                                    val list = currentItems.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index + 1]
                                    list[index + 1] = temp
                                    currentItems = list
                                }
                            },
                            enabled = index < currentItems.size - 1
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_expand_more_24),
                                contentDescription = "Move Down"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newValues = currentItems.map { it.first }.joinToString(",")
                    prefs.edit().putString(key, newValues).apply()
                    (context as? android.app.Activity)?.recreate()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
