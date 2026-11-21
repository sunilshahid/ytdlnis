package com.deniscerri.ytdl.ui.compose.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import com.deniscerri.ytdl.util.FileUtil

@Composable
fun FolderSettingsScreen(
    onSelectMusicPath: () -> Unit,
    onSelectVideoPath: () -> Unit,
    onSelectCommandPath: () -> Unit,
    onSelectCachePath: () -> Unit,
    onEditFilenameTemplateVideo: () -> Unit,
    onEditFilenameTemplateAudio: () -> Unit,
    onNavigateTemporaryFiles: () -> Unit,
    temporaryFilesSizeText: String,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val musicPath = prefs.getString("music_path", "")?.ifEmpty { FileUtil.getDefaultAudioPath() }.orEmpty()
    val videoPath = prefs.getString("video_path", "")?.ifEmpty { FileUtil.getDefaultVideoPath() }.orEmpty()
    val commandPath = prefs.getString("command_path", "")?.ifEmpty { FileUtil.getDefaultCommandPath() }.orEmpty()
    val cachePath = prefs.getString("cache_path", "")?.ifEmpty { FileUtil.getCachePath(context) }.orEmpty()

    val fileNameTemplate = prefs.getString("file_name_template", "%(uploader).30B - %(title).170B").orEmpty()
    val fileNameTemplateAudio = prefs.getString("file_name_template_audio", "%(uploader).30B - %(title).170B").orEmpty()

    val subDirEntries = stringArrayResource(R.array.save_subdirectory).toList()
    val subDirValues = stringArrayResource(R.array.save_subdirectory_values).toList()
    var currentSubDirs by remember {
        mutableStateOf(prefs.getStringSet("save_subdirectory", emptySet()) ?: emptySet())
    }

    var noPart by remember {
        mutableStateOf(prefs.getBoolean("no_part", false))
    }

    val showAccessAllFiles = Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()

    SettingsScreenColumn(modifier = modifier) {
        // Music Directory
        SettingsPreference(
            title = stringResource(R.string.music_directory),
            summary = FileUtil.formatPath(musicPath),
            iconRes = R.drawable.ic_music_downloaded,
            showChevron = true,
            onClick = onSelectMusicPath,
        )

        // Video Directory
        SettingsPreference(
            title = stringResource(R.string.video_directory),
            summary = FileUtil.formatPath(videoPath),
            iconRes = R.drawable.ic_video_downloaded,
            showChevron = true,
            onClick = onSelectVideoPath,
        )

        // Command Directory
        SettingsPreference(
            title = stringResource(R.string.command_directory),
            summary = FileUtil.formatPath(commandPath),
            iconRes = R.drawable.ic_terminal,
            showChevron = true,
            onClick = onSelectCommandPath,
        )

        // Cache Directory
        SettingsPreference(
            title = stringResource(R.string.cache_directory),
            summary = FileUtil.formatPath(cachePath),
            iconRes = R.drawable.baseline_folder_24,
            showChevron = true,
            onClick = onSelectCachePath,
        )

        // All Files Access
        if (showAccessAllFiles) {
            SettingsPreference(
                title = stringResource(R.string.access_all_directories),
                summary = stringResource(R.string.access_all_directories_summary),
                iconRes = R.drawable.ic_access_folder,
                showChevron = true,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 30) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:" + context.packageName)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                }
            )
        }

        // Check Available Storage
        SettingsSwitchPreference(
            title = stringResource(R.string.check_available_storage),
            key = "check_available_storage",
            defaultValue = true,
            iconRes = R.drawable.outline_storage_24,
        )

        // Cache Downloads First
        SettingsSwitchPreference(
            title = stringResource(R.string.cache_first),
            summary = stringResource(R.string.cache_first_summary),
            key = "cache_downloads",
            defaultValue = true,
            enabled = FileUtil.hasAllFilesAccess(),
            iconRes = R.drawable.if_file_rename,
        )

        // No part files
        SettingsSwitchPreference(
            title = stringResource(R.string.no_download_fragments),
            summary = stringResource(R.string.no_download_fragments_summary),
            key = "no_part",
            defaultValue = false,
            iconRes = R.drawable.baseline_save_alt_24,
            onCheckedChange = { checked ->
                noPart = checked
                if (checked) {
                    prefs.edit().putBoolean("keep_cache", false).apply()
                }
            }
        )

        // Keep cache
        SettingsSwitchPreference(
            title = stringResource(R.string.keep_cache),
            summary = stringResource(R.string.keep_cache_summary),
            key = "keep_cache",
            defaultValue = false,
            enabled = !noPart,
            iconRes = R.drawable.baseline_save_24,
        )

        // Filename template (video)
        SettingsPreference(
            title = "${stringResource(R.string.file_name_template)} [${stringResource(R.string.video)}]",
            summary = fileNameTemplate,
            iconRes = R.drawable.ic_textformat,
            showChevron = true,
            onClick = onEditFilenameTemplateVideo,
        )

        // Filename template (audio)
        SettingsPreference(
            title = "${stringResource(R.string.file_name_template)} [${stringResource(R.string.audio)}]",
            summary = fileNameTemplateAudio,
            iconRes = R.drawable.ic_textformat,
            showChevron = true,
            onClick = onEditFilenameTemplateAudio,
        )

        // Save subdirectory
        SettingsMultiSelectListPreference(
            title = stringResource(R.string.save_subdirectory),
            dialogTitle = stringResource(R.string.save_subdirectory),
            summary = stringResource(R.string.save_subdirectory_summary),
            key = "save_subdirectory",
            defaultValue = emptySet(),
            entries = subDirEntries,
            entryValues = subDirValues,
            iconRes = R.drawable.ic_textformat,
            onValuesChange = { newSet ->
                currentSubDirs = newSet
                if (newSet.isNotEmpty()) {
                    prefs.edit().putBoolean("trim_filenames", false).apply()
                }
            }
        )

        // Trim filenames
        SettingsSwitchPreference(
            title = stringResource(R.string.trim_filenames),
            key = "trim_filenames",
            defaultValue = true,
            enabled = currentSubDirs.isEmpty(),
            iconRes = R.drawable.ic_cut,
        )

        // Restrict filenames
        SettingsSwitchPreference(
            title = stringResource(R.string.restrict_filenames),
            summary = stringResource(R.string.restrict_filenames_summary),
            key = "restrict_filenames",
            defaultValue = false,
            iconRes = R.drawable.if_file_rename,
        )

        // Temporary files
        SettingsPreference(
            title = stringResource(R.string.temporary_files),
            summary = temporaryFilesSizeText.ifBlank { null },
            iconRes = R.drawable.ic_folder_delete,
            showChevron = true,
            onClick = onNavigateTemporaryFiles,
        )

        // Reset
        ResetPreferencesPreference(onReset = onReset)
    }
}
