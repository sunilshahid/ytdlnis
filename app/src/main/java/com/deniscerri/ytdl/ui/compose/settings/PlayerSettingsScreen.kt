package com.deniscerri.ytdl.ui.compose.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.play.device.DeviceFilesPrefs

@Composable
fun PlayerSettingsScreen(
    onSelectDeviceFolder: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var showDeviceFiles by remember {
        mutableStateOf(DeviceFilesPrefs.isEnabled(context))
    }
    var folderUri by remember {
        mutableStateOf(DeviceFilesPrefs.folderUri(context))
    }
    var folderName by remember {
        mutableStateOf(DeviceFilesPrefs.folderName(context))
    }

    val lyricsColors = stringArrayResource(R.array.lyrics_text_colors).toList()
    val lyricsColorValues = stringArrayResource(R.array.lyrics_text_color_values).toList()

    val folderSummary = if (folderUri != null) {
        stringResource(
            R.string.device_files_folder_selected,
            folderName ?: stringResource(R.string.device_files_all_folders)
        )
    } else {
        stringResource(R.string.device_files_all_folders)
    }

    SettingsScreenColumn(modifier = modifier) {
        // Video playback
        SettingsCategory(title = stringResource(R.string.video_playback))

        SettingsSwitchPreference(
            title = stringResource(R.string.scrub_preview_mini),
            summary = stringResource(R.string.scrub_preview_mini_summary),
            key = "scrub_preview_mini",
            defaultValue = false,
            iconRes = R.drawable.ic_video,
        )

        // Device Files
        SettingsCategory(title = stringResource(R.string.device_files_title))

        SettingsSwitchPreference(
            title = stringResource(R.string.show_device_files),
            summary = stringResource(R.string.show_device_files_summary),
            key = "show_device_files",
            defaultValue = false,
            iconRes = R.drawable.baseline_folder_24,
            onCheckedChange = { checked ->
                showDeviceFiles = checked
            }
        )

        if (showDeviceFiles) {
            SettingsPreference(
                title = stringResource(R.string.device_files_folder),
                summary = folderSummary,
                iconRes = R.drawable.baseline_folder_24,
                showChevron = true,
                onClick = onSelectDeviceFolder,
            )

            if (folderUri != null) {
                SettingsPreference(
                    title = stringResource(R.string.device_files_clear_folder),
                    summary = stringResource(R.string.device_files_clear_folder_summary),
                    iconRes = R.drawable.ic_folder_delete,
                    onClick = {
                        DeviceFilesPrefs.clearFolder(context)
                        folderUri = null
                        folderName = null
                    }
                )
            }
        }

        // Lyrics
        SettingsCategory(title = stringResource(R.string.lyrics))

        SettingsListPreference(
            title = stringResource(R.string.lyrics_text_color),
            dialogTitle = stringResource(R.string.lyrics_text_color),
            key = "lyrics_text_color",
            defaultValue = "default",
            entries = lyricsColors,
            entryValues = lyricsColorValues,
            iconRes = R.drawable.baseline_color_lens_24,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.lyrics_prefer_word_sync),
            summary = stringResource(R.string.lyrics_prefer_word_sync_summary),
            key = "lyrics_prefer_word_sync",
            defaultValue = true,
            iconRes = R.drawable.ic_lyrics,
        )

        // Lyrics providers
        SettingsCategory(title = stringResource(R.string.lyrics_providers))

        SettingsSwitchPreference(
            title = stringResource(R.string.lyrics_provider_betterlyrics),
            summary = stringResource(R.string.lyrics_provider_betterlyrics_summary),
            key = "lyrics_provider_betterlyrics",
            defaultValue = true,
            iconRes = R.drawable.ic_lyrics,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.lyrics_provider_lyricsplus),
            summary = stringResource(R.string.lyrics_provider_lyricsplus_summary),
            key = "lyrics_provider_lyricsplus",
            defaultValue = true,
            iconRes = R.drawable.ic_subtitles,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.lyrics_provider_lrclib),
            summary = stringResource(R.string.lyrics_provider_lrclib_summary),
            key = "lyrics_provider_lrclib",
            defaultValue = true,
            iconRes = R.drawable.ic_lyrics,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.lyrics_provider_youtube_subs),
            summary = stringResource(R.string.lyrics_provider_youtube_subs_summary),
            key = "lyrics_provider_youtube_subs",
            defaultValue = true,
            iconRes = R.drawable.ic_subtitles_alternative,
        )

        // Reset
        ResetPreferencesPreference(onReset = onReset)
    }
}
