package com.deniscerri.ytdl.ui.more.settings.player

import android.content.Intent
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.ui.play.device.DeviceFilesPrefs

/**
 * Player-related settings: device files library configuration and playback/lyrics options.
 */
object PlayerSettingsModule : SettingModule {
    override fun bindLogic(pref: Preference, host: SettingHost) {
        val context = pref.context

        when (pref.key) {
            "show_device_files" -> {
                (pref as SwitchPreferenceCompat).apply {
                    val enabled = DeviceFilesPrefs.isEnabled(context)
                    host.findPref("device_files_folder")?.isVisible = enabled
                    host.findPref("device_files_clear_folder")?.isVisible =
                        enabled && DeviceFilesPrefs.folderUri(context) != null

                    setOnPreferenceChangeListener { _, newValue ->
                        val showDeviceFiles = newValue as Boolean
                        host.findPref("device_files_folder")?.isVisible = showDeviceFiles
                        host.findPref("device_files_clear_folder")?.isVisible =
                            showDeviceFiles && DeviceFilesPrefs.folderUri(context) != null
                        host.refreshUI()
                        true
                    }
                }
            }
            "device_files_folder" -> {
                val folderPref = pref
                fun updateFolderSummary() {
                    val enabled = DeviceFilesPrefs.isEnabled(context)
                    folderPref.isVisible = enabled
                    folderPref.summary = if (DeviceFilesPrefs.folderUri(context) != null) {
                        context.getString(
                            R.string.device_files_folder_selected,
                            DeviceFilesPrefs.folderName(context)
                                ?: context.getString(R.string.device_files_all_folders)
                        )
                    } else {
                        context.getString(R.string.device_files_all_folders)
                    }
                    host.findPref("device_files_clear_folder")?.isVisible =
                        enabled && DeviceFilesPrefs.folderUri(context) != null
                }
                updateFolderSummary()

                folderPref.setOnPreferenceClickListener {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        )
                    }
                    host.activityResultDelegate.launch(intent) { result ->
                        val uri = result.data?.data ?: return@launch
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }.recoverCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        val path = DeviceFilesPrefs.relativePathFromTreeUri(uri)
                        val name = DeviceFilesPrefs.folderNameForPath(context, path)
                        DeviceFilesPrefs.setFolder(context, uri, path, name)
                        updateFolderSummary()
                        host.refreshUI()
                    }
                    true
                }
            }
            "device_files_clear_folder" -> {
                pref.apply {
                    isVisible = DeviceFilesPrefs.isEnabled(context) &&
                            DeviceFilesPrefs.folderUri(context) != null
                    setOnPreferenceClickListener {
                        DeviceFilesPrefs.clearFolder(context)
                        isVisible = false
                        host.findPref("device_files_folder")?.let { folderPref ->
                            folderPref.summary = context.getString(R.string.device_files_all_folders)
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "lyrics_text_color" -> {
                (pref as ListPreference).apply {
                    // Avoid ListPreference.entry/getEntry: with an unset value (or null entry
                    // elements) findIndexOfValue dereferences nulls and crashes. Compute the
                    // summary manually from entries/entryValues instead.
                    if (value == null && entryValues.isNotEmpty()) {
                        value = entryValues[0].toString()
                    }
                    summary = entries.getOrNull(
                        entryValues.indexOfFirst { it.toString() == value }
                    ) ?: context.getString(R.string.lyrics_color_default)
                    setOnPreferenceChangeListener { _, newValue ->
                        summary = entries.getOrNull(
                            entryValues.indexOfFirst { it.toString() == newValue.toString() }
                        ) ?: context.getString(R.string.lyrics_color_default)
                        host.refreshUI()
                        true
                    }
                }
            }
        }
    }
}
