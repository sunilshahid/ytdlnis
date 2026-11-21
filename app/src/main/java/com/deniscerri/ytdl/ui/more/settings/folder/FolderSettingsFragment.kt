package com.deniscerri.ytdl.ui.more.settings.folder

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.settings.FolderSettingsScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.TemporaryFilesUtil
import com.deniscerri.ytdl.util.UiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderSettingsFragment : ComposeFragment() {

    private var temporaryFilesSize by mutableStateOf("")

    private var pendingFolderKey: String? = null

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val key = pendingFolderKey ?: return@registerForActivityResult
        pendingFolderKey = null
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            prefs.edit().putString(key, uri.toString()).apply()
            requireActivity().recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(R.string.directories))
        updateTemporaryFilesSize()
    }

    private fun updateTemporaryFilesSize() {
        lifecycleScope.launch {
            val total = withContext(Dispatchers.IO) {
                TemporaryFilesUtil.Category.values().sumOf { TemporaryFilesUtil.getSize(requireContext(), it) }
            }
            temporaryFilesSize = TemporaryFilesUtil.formatSize(total)
        }
    }

    @Composable
    override fun Content() {
        FolderSettingsScreen(
            onSelectMusicPath = { pickDirectory("music_path") },
            onSelectVideoPath = { pickDirectory("video_path") },
            onSelectCommandPath = { pickDirectory("command_path") },
            onSelectCachePath = {
                val ctx = requireContext()
                UiUtil.showGenericConfirmDialog(
                    ctx,
                    ctx.getString(R.string.cache_directory),
                    ctx.getString(R.string.cache_directory_warning)
                ) {
                    pickDirectory("cache_path")
                }
            },
            onEditFilenameTemplateVideo = {
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                val current = prefs.getString("file_name_template", "%(uploader).30B - %(title).170B").orEmpty()
                UiUtil.showFilenameTemplateDialog(
                    requireActivity(),
                    current,
                    "${getString(R.string.file_name_template)} [${getString(R.string.video)}]"
                ) { newTemplate ->
                    prefs.edit().putString("file_name_template", newTemplate).apply()
                    requireActivity().recreate()
                }
            },
            onEditFilenameTemplateAudio = {
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                val current = prefs.getString("file_name_template_audio", "%(uploader).30B - %(title).170B").orEmpty()
                UiUtil.showFilenameTemplateDialog(
                    requireActivity(),
                    current,
                    "${getString(R.string.file_name_template)} [${getString(R.string.audio)}]"
                ) { newTemplate ->
                    prefs.edit().putString("file_name_template_audio", newTemplate).apply()
                    requireActivity().recreate()
                }
            },
            onNavigateTemporaryFiles = {
                findNavController().navigate(R.id.action_folderSettingsFragment_to_temporaryFilesFragment)
            },
            temporaryFilesSizeText = temporaryFilesSize,
            onReset = { resetPreferences() }
        )
    }

    private fun pickDirectory(key: String) {
        pendingFolderKey = key
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        folderPickerLauncher.launch(intent)
    }

    private fun resetPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        listOf(
            "music_path",
            "video_path",
            "command_path",
            "cache_path",
            "check_available_storage",
            "cache_downloads",
            "no_part",
            "keep_cache",
            "file_name_template",
            "file_name_template_audio",
            "save_subdirectory",
            "trim_filenames",
            "restrict_filenames"
        ).forEach { editor.remove(it) }
        editor.apply()
        com.deniscerri.ytdl.util.DefaultPreferences.resetFolders(requireContext())
        requireActivity().recreate()
    }
}
