package com.deniscerri.ytdl.ui.more.settings.player

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.settings.PlayerSettingsScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.play.device.DeviceFilesPrefs

class PlayerSettingsFragment : ComposeFragment() {

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val context = requireContext()
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
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
            requireActivity().recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(R.string.player))
    }

    @Composable
    override fun Content() {
        PlayerSettingsScreen(
            onSelectDeviceFolder = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }
                folderPickerLauncher.launch(intent)
            },
            onReset = { resetPreferences() }
        )
    }

    private fun resetPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        listOf(
            "scrub_preview_mini",
            "show_device_files",
            "lyrics_text_color",
            "lyrics_prefer_word_sync",
            "lyrics_provider_betterlyrics",
            "lyrics_provider_lyricsplus",
            "lyrics_provider_lrclib",
            "lyrics_provider_youtube_subs"
        ).forEach { editor.remove(it) }
        editor.apply()
        DeviceFilesPrefs.clearFolder(requireContext())
        com.deniscerri.ytdl.util.DefaultPreferences.resetPlayer(requireContext())
        requireActivity().recreate()
    }
}
