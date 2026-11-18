package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.settings.MainSettingsScreen
import com.deniscerri.ytdl.ui.compose.settings.MainSettingsUiState
import com.deniscerri.ytdl.util.BackupSettingsUtil
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.LocaleUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainSettingsFragment : ComposeFragment() {

    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var preferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private var uiState by mutableStateOf(MainSettingsUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        editor = preferences.edit()
        updateState()
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    private fun updateState() {
        if (!isAdded) return
        val isRtl = TextUtilsCompat.getLayoutDirectionFromLocale(LocaleUtil.currentLocale()) == ViewCompat.LAYOUT_DIRECTION_RTL
        val separator = if (isRtl) "،" else ","
        uiState = MainSettingsUiState(
            appearanceSummary = "${getString(R.string.Theme)}$separator ${getString(R.string.accents)}$separator ${getString(R.string.app_icon)}",
            generalSummary = "${if (Build.VERSION.SDK_INT < 33) getString(R.string.language) + "$separator " else ""}${getString(R.string.preferred_search_engine)}$separator ${getString(R.string.preferred_home_screen)}",
            playerSummary = "${getString(R.string.device_files_title)}$separator ${getString(R.string.lyrics)}",
            foldersSummary = "${getString(R.string.music_directory)}$separator ${getString(R.string.video_directory)}$separator ${getString(R.string.command_directory)}",
            downloadingSummary = "${getString(R.string.quick_download)}$separator ${getString(R.string.concurrent_downloads)}$separator ${getString(R.string.limit_rate)}",
            processingSummary = "${getString(R.string.sponsorblock)}$separator ${getString(R.string.embed_subtitles)}$separator ${getString(R.string.add_chapters)}",
            updatingSummary = "${getString(R.string.update_ytdl)}$separator ${getString(R.string.update_app)}",
            advancedSummary = "PO Token$separator ${getString(R.string.other_youtube_extractor_args)}",
            automaticBackup = preferences.getBoolean("automatic_backup", true),
            backupPath = FileUtil.formatPath(FileUtil.getBackupPath(requireContext())),
            packageName = BuildConfig.APPLICATION_ID,
        )
    }

    @Composable
    override fun Content() {
        val navController = findNavController()
        MainSettingsScreen(
            state = uiState,
            onAppearanceClick = {
                navController.navigate(R.id.action_mainSettingsFragment_to_appearanceSettingsFragment)
            },
            onGeneralClick = {
                navController.navigate(R.id.action_mainSettingsFragment_to_generalSettingsFragment)
            },
            onPlayerClick = {
                navController.navigate(R.id.action_mainSettingsFragment_to_playerSettingsFragment)
            },
            onFoldersClick = {
                navController.navigate(R.id.action_mainSettingsFragment_to_folderSettingsFragment)
            },
            onDownloadingClick = {
                navController.navigate(R.id.action_mainSettingsFragment_to_downloadSettingsFragment)
            },
            onProcessingClick = {
                navController.navigate(R.id.action_mainSettingsFragment_to_processingSettingsFragment)
            },
            onUpdatingClick = {
                navController.navigate(R.id.action_mainSettingsFragment_to_updateSettingsFragment)
            },
            onAdvancedClick = {
                navController.navigate(R.id.action_mainSettingsFragment_to_advancedSettingsFragment)
            },
            onBackupClick = {
                showBackupDialog()
            },
            onRestoreClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    addFlags(
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }
                appRestoreResultLauncher.launch(intent)
            },
            onAutomaticBackupToggle = { enabled ->
                editor.putBoolean("automatic_backup", enabled).apply()
                uiState = uiState.copy(automaticBackup = enabled)
            },
            onBackupPathClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }
                backupPathResultLauncher.launch(intent)
            },
            onPackageNameClick = {
                UiUtil.copyToClipboard(BuildConfig.APPLICATION_ID, requireActivity())
            },
            onDiscordClick = {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/WW3KYWxAPm")))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onTelegramClick = {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/ytdlnis")))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onTranslatorsClick = {
                TranslatorsDialog.show(requireContext())
            },
        )
    }

    private fun showBackupDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.select_backup_categories))
        val values = resources.getStringArray(R.array.backup_category_values)
        val entries = resources.getStringArray(R.array.backup_category_entries)
        val checkedItems = BooleanArray(values.size) { true }

        builder.setMultiChoiceItems(entries, checkedItems) { _, which, isChecked ->
            checkedItems[which] = isChecked
        }

        builder.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            lifecycleScope.launch {
                if (checkedItems.none { it }) {
                    view?.let {
                        Snackbar.make(it, R.string.select_backup_categories, Snackbar.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val selectedItems = values.filterIndexed { index, _ -> checkedItems[index] }
                val pathResult = withContext(Dispatchers.IO) {
                    settingsViewModel.backup(selectedItems)
                }

                if (pathResult.isFailure) {
                    val errorMessage = pathResult.exceptionOrNull()?.message ?: getString(R.string.errored)
                    view?.let { v ->
                        val snack = Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG)
                        val snackbarView: View = snack.view
                        val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as? TextView
                        snackTextView?.maxLines = 9999999
                        snack.setAction(android.R.string.copy) {
                            UiUtil.copyToClipboard(errorMessage, requireActivity())
                        }
                        snack.show()
                    }
                } else {
                    view?.let { v ->
                        val s = Snackbar.make(
                            v,
                            getString(R.string.backup_created_successfully),
                            Snackbar.LENGTH_LONG
                        )
                        s.setAction(R.string.Open_File) {
                            FileUtil.openFileIntent(requireActivity(), pathResult.getOrNull()!!)
                        }
                        s.show()
                    }
                }
            }
        }

        builder.setNegativeButton(getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }
        builder.create().show()
    }

    private var backupPathResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            val path = result.data?.data?.toString().orEmpty()
            uiState = uiState.copy(backupPath = FileUtil.formatPath(path))
            editor.putString("backup_path", path).apply()
        }
    }

    private var appRestoreResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            lifecycleScope.launch {
                runCatching {
                    val (restoreData, summary) = BackupSettingsUtil.parse(requireContext(), result.data!!.data!!)
                    val parsedDataMessage = StringBuilder(summary)

                    showAppRestoreInfoDialog(
                        onMerge = {
                            lifecycleScope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    settingsViewModel.restoreData(restoreData, requireContext())
                                }
                                if (res) {
                                    showRestoreFinishedDialog(parsedDataMessage.toString())
                                } else {
                                    throw Error()
                                }
                            }
                        },
                        onReset = {
                            lifecycleScope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    settingsViewModel.restoreData(restoreData, requireContext(), true)
                                }
                                if (res) {
                                    showRestoreFinishedDialog(parsedDataMessage.toString())
                                } else {
                                    throw Error()
                                }
                            }
                        }
                    )
                }.onFailure {
                    it.printStackTrace()
                    view?.let { v ->
                        Snackbar.make(v, it.message.toString(), Snackbar.LENGTH_INDEFINITE).show()
                    }
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun showAppRestoreInfoDialog(onMerge: () -> Unit, onReset: () -> Unit) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.restore))
        builder.setMessage(getString(R.string.restore_info))

        builder.setNegativeButton(getString(R.string.cancel)) { dialog: DialogInterface?, _: Int ->
            dialog?.dismiss()
        }

        builder.setPositiveButton(getString(R.string.restore)) { dialog: DialogInterface?, _: Int ->
            onMerge()
            dialog?.dismiss()
        }

        builder.setNeutralButton(getString(R.string.reset)) { dialog: DialogInterface?, _: Int ->
            onReset()
            dialog?.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).gravity = Gravity.START
    }

    private fun showRestoreFinishedDialog(restoreDataMessage: String) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.restore))
        builder.setMessage("${getString(R.string.restore_complete)}\n\n$restoreDataMessage")
        builder.setCancelable(false)
        builder.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            ThemeUtil.recreateAllActivities()
        }
        builder.create().show()
    }
}
