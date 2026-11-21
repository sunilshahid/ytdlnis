package com.deniscerri.ytdl.ui.downloadcard

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.compose.downloadcard.DownloadConfigEvent
import com.deniscerri.ytdl.ui.compose.downloadcard.DownloadConfigSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.DownloadConfigState
import com.deniscerri.ytdl.ui.compose.downloadcard.FormatDetailsSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.FormatSelectionSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.SubtitleLanguageDialog
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * ConfigureDownloadBottomSheetDialog migrated to Jetpack Compose (eleXML §5 A2).
 * Replaces configure_download_bottom_sheet.xml.
 */
class ConfigureDownloadBottomSheetDialog(
    private val currentDownloadItem: DownloadItem,
    private val listener: OnDownloadItemUpdateListener
) : BottomSheetDialogFragment() {

    interface OnDownloadItemUpdateListener {
        fun onDownloadItemUpdated(downloadItem: DownloadItem)
    }

    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private var vaultMode: Boolean = currentDownloadItem.vaultMode
    private var incognito: Boolean = currentDownloadItem.incognito || vaultMode
    private var pendingVaultToggleApply: (() -> Unit)? = null

    private val vaultSetupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            vaultMode = true
            pendingVaultToggleApply?.invoke()
        }
        pendingVaultToggleApply = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    var state by remember {
                        mutableStateOf(DownloadConfigState.fromDownloadItem(currentDownloadItem))
                    }
                    var activeIncognito by remember { mutableStateOf(incognito) }
                    var activeVaultMode by remember { mutableStateOf(vaultMode) }
                    var showFormatPicker by remember { mutableStateOf(false) }
                    var detailFormat by remember { mutableStateOf<Format?>(null) }
                    var showSubtitlePicker by remember { mutableStateOf(false) }

                    DownloadConfigSheet(
                        state = state,
                        incognito = activeIncognito,
                        vaultMode = activeVaultMode,
                        onEvent = { event -> state = state.reduce(event) },
                        onDownloadTypeChange = { newType ->
                            state = state.reduce(DownloadConfigEvent.SelectDownloadType(newType))
                        },
                        onToggleIncognito = {
                            if (activeVaultMode) {
                                Toast.makeText(requireContext(), R.string.vault_save_enabled, Toast.LENGTH_SHORT).show()
                            } else {
                                activeIncognito = !activeIncognito
                                state = state.reduce(DownloadConfigEvent.ToggleIncognito(activeIncognito))
                            }
                        },
                        onToggleVault = {
                            if (!activeVaultMode && !com.deniscerri.ytdl.vault.VaultRepository.isInitialized(requireContext())) {
                                pendingVaultToggleApply = {
                                    activeVaultMode = true
                                    state = state.reduce(DownloadConfigEvent.ToggleVaultMode(true))
                                }
                                Toast.makeText(requireContext(), R.string.vault_setup_required_prompt, Toast.LENGTH_SHORT).show()
                                vaultSetupLauncher.launch(
                                    com.deniscerri.ytdl.vault.ui.VaultActivity.createSetupIntent(requireContext())
                                )
                            } else {
                                activeVaultMode = !activeVaultMode
                                state = state.reduce(DownloadConfigEvent.ToggleVaultMode(activeVaultMode))
                            }
                        },
                        onPickFormat = { showFormatPicker = true },
                        onPickCut = {
                            val cutDialog = CutVideoBottomSheetDialog(
                                _item = state.applyToDownloadItem(currentDownloadItem.copy()),
                                _listener = object : CutVideoBottomSheetDialog.VideoCutListener {
                                    override fun onSaveCut(cutStart: String, cutEnd: String, mergeCuts: Boolean) {
                                        state = state.reduce(DownloadConfigEvent.SetCutRange(cutStart, cutEnd))
                                            .reduce(DownloadConfigEvent.ToggleMergeCuts(mergeCuts))
                                    }
                                    override fun onClearCut() {
                                        state = state.reduce(DownloadConfigEvent.SetCutRange("", ""))
                                    }
                                }
                            )
                            cutDialog.show(parentFragmentManager, "cutVideoSheet")
                        },
                        onPickCrop = {
                            val cropDialog = com.deniscerri.ytdl.ui.downloadcard.crop.CropVideoBottomSheetDialog(
                                _item = state.applyToDownloadItem(currentDownloadItem.copy()),
                                _listener = object : com.deniscerri.ytdl.ui.downloadcard.crop.VideoCropListener {
                                    override fun onChangeCrop(x: Int, y: Int, w: Int, h: Int, refW: Int, refH: Int) {
                                        state = state.reduce(DownloadConfigEvent.SetCropValues("$x:$y:$w:$h:$refW:$refH"))
                                    }
                                    override fun onClearCrop() {
                                        state = state.reduce(DownloadConfigEvent.SetCropValues(""))
                                    }
                                }
                            )
                            cropDialog.show(parentFragmentManager, "cropVideoSheet")
                        },
                        onPickExtraCommands = {
                            val extraDialog = AddExtraCommandsDialog(
                                item = state.applyToDownloadItem(currentDownloadItem.copy()),
                                callback = object : AddExtraCommandsDialog.ExtraCommandsListener {
                                    override fun onSave(commands: String) {
                                        state = state.reduce(DownloadConfigEvent.SetExtraCommands(commands))
                                    }
                                }
                            )
                            extraDialog.show(parentFragmentManager, "addExtraCommands")
                        },
                        onPickSubtitles = { showSubtitlePicker = true },
                        onScheduleClick = {
                            UiUtil.showDatePicker(parentFragmentManager, sharedPreferences) { calendar ->
                                val updated = state.applyToDownloadItem(currentDownloadItem.copy())
                                updated.status = com.deniscerri.ytdl.database.repository.DownloadRepository.Status.Scheduled.toString()
                                updated.downloadStartTime = calendar.timeInMillis
                                listener.onDownloadItemUpdated(updated)
                                dismiss()
                            }
                        },
                        onDownloadClick = {
                            val updated = state.applyToDownloadItem(currentDownloadItem.copy())
                            listener.onDownloadItemUpdated(updated)
                            dismiss()
                        },
                        onDismiss = { dismiss() },
                        onOpenUrlClick = {
                            if (state.url.isNotBlank()) {
                                UiUtil.openLinkIntent(requireContext(), state.url)
                            }
                        }
                    )

                    if (showFormatPicker) {
                        FormatSelectionSheet(
                            formats = state.availableFormats,
                            selectedFormat = state.selectedFormat,
                            onFormatSelected = { format ->
                                state = state.reduce(DownloadConfigEvent.SelectFormat(format))
                                showFormatPicker = false
                            },
                            onShowFormatDetails = { format -> detailFormat = format },
                            onDismiss = { showFormatPicker = false }
                        )
                    }

                    if (detailFormat != null) {
                        FormatDetailsSheet(
                            format = detailFormat!!,
                            onDismiss = { detailFormat = null }
                        )
                    }

                    if (showSubtitlePicker) {
                        SubtitleLanguageDialog(
                            currentValue = state.subtitleLanguages,
                            availableSubtitles = currentDownloadItem.availableSubtitles,
                            onConfirm = { langs ->
                                state = state.reduce(DownloadConfigEvent.SetSubtitleLanguages(langs))
                                showSubtitlePicker = false
                            },
                            onDismiss = { showSubtitlePicker = false }
                        )
                    }
                }
            }
        }
    }
}
