package com.deniscerri.ytdl.ui.downloadcard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.ui.compose.downloadcard.ExtraCommandsSheet
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * AddExtraCommandsDialog migrated to Jetpack Compose (eleXML §5 A30).
 * Replaces extra_commands_bottom_sheet.xml.
 */
class AddExtraCommandsDialog(
    private val item: DownloadItem? = null,
    private val callback: ExtraCommandsListener? = null
) : BottomSheetDialogFragment() {

    interface ExtraCommandsListener {
        fun onSave(commands: String)
    }

    private lateinit var commandTemplateViewModel: CommandTemplateViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]
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
                    ExtraCommandsSheet(
                        initialCommand = item?.extraCommands ?: "",
                        onPickTemplate = {
                            lifecycleScope.launch {
                                UiUtil.showCommandTemplates(requireActivity(), commandTemplateViewModel) { templates ->
                                    if (templates.isNotEmpty()) {
                                        val combined = templates.joinToString(" ") { it.content }
                                        callback?.onSave(combined)
                                    }
                                }
                            }
                        },
                        onPickShortcut = {
                            lifecycleScope.launch {
                                UiUtil.showShortcuts(requireActivity(), commandTemplateViewModel,
                                    itemSelected = { shortcut ->
                                        val current = item?.extraCommands ?: ""
                                        val updated = if (current.isBlank()) shortcut else "$current $shortcut"
                                        callback?.onSave(updated)
                                    },
                                    itemRemoved = {}
                                )
                            }
                        },
                        onConfirm = { commands ->
                            callback?.onSave(commands)
                            dismiss()
                        },
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }
}
