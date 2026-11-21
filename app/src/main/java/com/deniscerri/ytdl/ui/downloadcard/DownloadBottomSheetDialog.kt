package com.deniscerri.ytdl.ui.downloadcard

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.receiver.ShareActivity
import com.deniscerri.ytdl.ui.compose.downloadcard.DownloadConfigEvent
import com.deniscerri.ytdl.ui.compose.downloadcard.DownloadConfigSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.DownloadConfigState
import com.deniscerri.ytdl.ui.compose.downloadcard.FormatDetailsSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.FormatSelectionSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.SubtitleLanguageDialog
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DownloadBottomSheetDialog migrated to Jetpack Compose (eleXML §5 A1).
 * Replaces download_bottom_sheet.xml.
 */
class DownloadBottomSheetDialog : BottomSheetDialogFragment() {
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel
    private lateinit var sharedPreferences: SharedPreferences

    private var resultItem: ResultItem? = null
    private var currentDownloadItem: DownloadItem? = null
    private var downloadType: DownloadType = DownloadType.video
    private var ignoreDuplicates: Boolean = false
    private var disableUpdateData: Boolean = false

    private var incognito: Boolean = false
    private var vaultMode: Boolean = false
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
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        commandTemplateViewModel = ViewModelProvider(requireActivity())[CommandTemplateViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        resultItem = downloadCardViewModel.resultItem
        currentDownloadItem = downloadCardViewModel.downloadItem

        downloadType = (arguments?.getSerializable("type") as? DownloadType) ?: DownloadType.video
        disableUpdateData = arguments?.getBoolean("disableUpdateData") == true
        ignoreDuplicates = arguments?.getBoolean("ignore_duplicates") == true

        if (resultItem == null && currentDownloadItem == null) {
            dismiss()
            return
        }

        vaultMode = currentDownloadItem?.vaultMode == true
        incognito = (currentDownloadItem?.incognito ?: sharedPreferences.getBoolean("incognito", false)) || vaultMode
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val baseItem = currentDownloadItem ?: downloadViewModel.createDownloadItemFromResult(
            result = resultItem,
            url = resultItem?.url ?: "",
            givenType = downloadType
        )

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    var state by remember {
                        mutableStateOf(DownloadConfigState.fromDownloadItem(baseItem))
                    }
                    var activeIncognito by remember { mutableStateOf(incognito) }
                    var activeVaultMode by remember { mutableStateOf(vaultMode) }
                    var showFormatPicker by remember { mutableStateOf(false) }
                    var detailFormat by remember { mutableStateOf<Format?>(null) }
                    var showSubtitlePicker by remember { mutableStateOf(false) }

                    val isFormatsUpdating by resultViewModel.updatingFormats.collectAsState()

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
                                _item = state.applyToDownloadItem(baseItem.copy()),
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
                                _item = state.applyToDownloadItem(baseItem.copy()),
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
                                item = state.applyToDownloadItem(baseItem.copy()),
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
                                val itemToQueue = state.applyToDownloadItem(baseItem.copy())
                                itemToQueue.status = com.deniscerri.ytdl.database.repository.DownloadRepository.Status.Scheduled.toString()
                                itemToQueue.downloadStartTime = calendar.timeInMillis
                                queueAndDismiss(listOf(itemToQueue))
                            }
                        },
                        onDownloadClick = {
                            val itemToQueue = state.applyToDownloadItem(baseItem.copy())
                            val items = mutableListOf(itemToQueue)
                            if (itemToQueue.videoPreferences.alsoDownloadAsAudio) {
                                val audioItem = itemToQueue.copy(type = DownloadType.audio, id = 0)
                                items.add(audioItem)
                            }
                            queueAndDismiss(items)
                        },
                        onSaveForLaterClick = {
                            if (activeVaultMode) {
                                Toast.makeText(requireContext(), R.string.vault_save_enabled, Toast.LENGTH_SHORT).show()
                            } else {
                                val item = state.applyToDownloadItem(baseItem.copy())
                                lifecycleScope.launch(Dispatchers.IO) {
                                    downloadViewModel.putToSaved(item)
                                    withContext(Dispatchers.Main) { dismiss() }
                                }
                            }
                        },
                        onDismiss = { dismiss() },
                        onOpenUrlClick = {
                            if (state.url.isNotBlank()) {
                                UiUtil.openLinkIntent(requireContext(), state.url)
                            }
                        }
                    )

                    // Format Selection Bottom Sheet Overlay
                    if (showFormatPicker) {
                        FormatSelectionSheet(
                            formats = state.availableFormats,
                            selectedFormat = state.selectedFormat,
                            isLoading = isFormatsUpdating,
                            onFormatSelected = { format ->
                                state = state.reduce(DownloadConfigEvent.SelectFormat(format))
                                showFormatPicker = false
                            },
                            onShowFormatDetails = { format -> detailFormat = format },
                            onDismiss = { showFormatPicker = false }
                        )
                    }

                    // Format Details Sheet Overlay
                    if (detailFormat != null) {
                        FormatDetailsSheet(
                            format = detailFormat!!,
                            onDismiss = { detailFormat = null }
                        )
                    }

                    // Subtitle Picker Overlay
                    if (showSubtitlePicker) {
                        SubtitleLanguageDialog(
                            currentValue = state.subtitleLanguages,
                            availableSubtitles = baseItem.availableSubtitles,
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

    private fun queueAndDismiss(items: List<DownloadItem>) {
        lifecycleScope.launch {
            resultViewModel.cancelUpdateItemData()
            resultViewModel.cancelUpdateFormatsItemData()
            val result = withContext(Dispatchers.IO) {
                downloadViewModel.queueDownloads(items, ignoreDuplicates)
            }
            if (result.message.isNotBlank()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
            withContext(Dispatchers.Main) {
                if (activity is ShareActivity && result.duplicateDownloadIDs.isNotEmpty()) {
                    val bundle = bundleOf(
                        Pair("duplicates", ArrayList(result.duplicateDownloadIDs))
                    )
                    findNavController().navigate(R.id.action_downloadBottomSheetDialog_to_downloadsAlreadyExistDialog2, bundle)
                } else {
                    dismiss()
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        lifecycleScope.launch {
            resultViewModel.cancelUpdateItemData()
            resultViewModel.cancelUpdateFormatsItemData()
            super.onDismiss(dialog)
        }
    }
}
