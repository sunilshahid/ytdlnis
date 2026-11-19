package com.deniscerri.ytdl.ui.downloadcard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.compose.downloadcard.PlaylistPickerSheet
import com.deniscerri.ytdl.ui.compose.theme.YtdlTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectPlaylistItemsDialog : BottomSheetDialogFragment() {
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel

    private var resultItemIDs: List<Long> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]

        arguments?.getLongArray("resultIDs")?.let {
            resultItemIDs = it.toList()
        } ?: run {
            dismiss()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlTheme {
                    var items by remember { mutableStateOf<List<ResultItem>>(emptyList()) }
                    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

                    androidx.compose.runtime.LaunchedEffect(resultItemIDs) {
                        if (resultItemIDs.isNotEmpty()) {
                            val loaded = withContext(Dispatchers.IO) {
                                resultViewModel.dao.getAllByIDs(resultItemIDs)
                            }
                            items = loaded
                        }
                    }

                    PlaylistPickerSheet(
                        items = items,
                        selectedIds = selectedIds,
                        onToggleSelect = { id ->
                            selectedIds = if (selectedIds.contains(id)) {
                                selectedIds - id
                            } else {
                                selectedIds + id
                            }
                        },
                        onSelectRange = { from, to ->
                            val start = (from - 1).coerceAtLeast(0)
                            val end = to.coerceAtMost(items.size)
                            if (start < end) {
                                val rangeIds = items.subList(start, end).map { it.id }.toSet()
                                selectedIds = selectedIds + rangeIds
                            }
                        },
                        onSelectAll = {
                            selectedIds = items.map { it.id }.toSet()
                        },
                        onClearSelection = {
                            selectedIds = emptySet()
                        },
                        onConfirm = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                if (selectedIds.size == 1) {
                                    val resultItem = resultViewModel.getByID(selectedIds.first())
                                    if (resultItem != null) {
                                        downloadCardViewModel.setResultItem(resultItem)
                                        downloadCardViewModel.setDownloadItem(null)
                                        withContext(Dispatchers.Main) {
                                            findNavController().navigate(
                                                R.id.action_selectPlaylistItemsDialog_to_downloadBottomSheetDialog,
                                                bundleOf(
                                                    Pair("result", resultItem),
                                                    Pair("type", downloadViewModel.getDownloadType(url = resultItem.url))
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    downloadViewModel.turnResultItemsToProcessingDownloads(selectedIds.toList())
                                    withContext(Dispatchers.Main) {
                                        findNavController().navigate(
                                            R.id.action_selectPlaylistItemsDialog_to_downloadMultipleBottomSheetDialog
                                        )
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    dismiss()
                                }
                            }
                        },
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }
}
