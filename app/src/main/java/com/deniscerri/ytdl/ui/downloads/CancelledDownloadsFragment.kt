package com.deniscerri.ytdl.ui.downloads

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.compose.downloads.SavedDownloadScreen
import com.deniscerri.ytdl.ui.compose.downloads.collectAsPagingItems
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Cancelled downloads tab hosted in Jetpack Compose (Phase 5).
 * Replaces `generic_list.xml` usage in CancelledDownloadsFragment.
 */
class CancelledDownloadsFragment : ComposeFragment() {

    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var ytdlpViewModel: YTDLPViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var sharedPreferences: SharedPreferences

    private var selectedIds by mutableStateOf<Set<Long>>(emptySet())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        ytdlpViewModel = ViewModelProvider(requireActivity())[YTDLPViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    @Composable
    override fun Content() {
        val pagingItems = downloadViewModel.cancelledDownloads.collectAsPagingItems(DIFF_CALLBACK)
        val totalCount by downloadViewModel.cancelledDownloadsCount.collectAsStateWithLifecycle(0)
        val coroutineScope = rememberCoroutineScope()

        val enableSwipe = remember {
            val gestures = sharedPreferences.getStringSet(
                "swipe_gesture",
                requireContext().resources.getStringArray(R.array.swipe_gestures_values).toSet()
            ) ?: emptySet()
            gestures.contains("cancelled")
        }

        SavedDownloadScreen(
            items = pagingItems,
            totalCount = totalCount,
            selectedIds = selectedIds,
            tabTitle = stringResource(R.string.cancelled),
            enableSwipe = enableSwipe,
            onDownloadAll = {
                coroutineScope.launch {
                    val ids = withContext(Dispatchers.IO) {
                        downloadViewModel.getIDsByStatus(listOf(DownloadRepository.Status.Cancelled))
                    }
                    downloadViewModel.reQueueDownloadItems(ids)
                }
            },
            onDeleteAll = {
                UiUtil.showGenericDeleteAllDialog(requireContext()) {
                    downloadViewModel.deleteCancelled()
                }
            },
            onCopyAllUrls = {
                coroutineScope.launch {
                    val urls = withContext(Dispatchers.IO) {
                        downloadViewModel.getURLsByStatus(listOf(DownloadRepository.Status.Cancelled))
                    }
                    UiUtil.copyToClipboard(urls.joinToString("\n"), requireActivity())
                }
            },
            onActionClick = { item ->
                coroutineScope.launch {
                    val itm = withContext(Dispatchers.IO) {
                        downloadViewModel.getItemByID(item.id)
                    }
                    if (itm != null) {
                        runBlocking {
                            downloadViewModel.queueDownloads(listOf(itm))
                        }
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.error_restarting_download), Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDeleteItem = { item ->
                coroutineScope.launch {
                    val itm = withContext(Dispatchers.IO) {
                        downloadViewModel.getItemByID(item.id)
                    } ?: return@launch
                    removeItem(itm, null)
                }
            },
            onCardClick = { item ->
                showDetailsCard(item.id)
            },
            onCardLongClick = { item ->
                selectedIds = if (selectedIds.contains(item.id)) {
                    selectedIds - item.id
                } else {
                    selectedIds + item.id
                }
            },
            onSelectToggle = { id ->
                selectedIds = if (selectedIds.contains(id)) {
                    selectedIds - id
                } else {
                    selectedIds + id
                }
            },
            onSelectAll = {
                coroutineScope.launch {
                    val allIds = withContext(Dispatchers.IO) {
                        downloadViewModel.getItemIDsNotPresentIn(
                            emptyList(),
                            listOf(DownloadRepository.Status.Cancelled)
                        )
                    }
                    selectedIds = allIds.toSet()
                }
            },
            onInvertSelection = {
                coroutineScope.launch {
                    val inverted = withContext(Dispatchers.IO) {
                        downloadViewModel.getItemIDsNotPresentIn(
                            selectedIds.toList(),
                            listOf(DownloadRepository.Status.Cancelled)
                        )
                    }
                    selectedIds = inverted.toSet()
                }
            },
            onSelectBetween = {
                if (selectedIds.size == 2) {
                    coroutineScope.launch {
                        val sorted = selectedIds.sorted()
                        val middle = withContext(Dispatchers.IO) {
                            downloadViewModel.getIDsBetweenTwoItems(
                                sorted.first(),
                                sorted.last(),
                                listOf(DownloadRepository.Status.Cancelled).toListString()
                            )
                        }
                        selectedIds = selectedIds + middle
                    }
                }
            },
            onClearSelection = {
                selectedIds = emptySet()
            },
            onDownloadSelected = {
                val toDownload = selectedIds.toList()
                selectedIds = emptySet()
                downloadViewModel.reQueueDownloadItems(toDownload)
            },
            onDeleteSelected = {
                val toDelete = selectedIds.toList()
                selectedIds = emptySet()
                val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
                deleteDialog.setNegativeButton(getString(R.string.cancel)) { d, _ -> d.cancel() }
                deleteDialog.setPositiveButton(getString(R.string.ok)) { _, _ ->
                    coroutineScope.launch {
                        for (id in toDelete) {
                            downloadViewModel.deleteDownload(id)
                        }
                    }
                }
                deleteDialog.show()
            },
            onCopySelectedUrls = {
                val toCopy = selectedIds.toList()
                selectedIds = emptySet()
                coroutineScope.launch {
                    val urls = withContext(Dispatchers.IO) {
                        downloadViewModel.getURLsByIds(toCopy)
                    }
                    UiUtil.copyToClipboard(urls.joinToString("\n"), requireActivity())
                }
            },
        )
    }

    private fun showDetailsCard(itemId: Long) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) {
                downloadViewModel.getItemByID(itemId)
            } ?: return@launch

            UiUtil.showDownloadItemDetailsCard(
                item,
                requireActivity(),
                DownloadRepository.Status.valueOf(item.status),
                ytdlpViewModel,
                sharedPreferences,
                removeItem = { itm: DownloadItem, sheet: BottomSheetDialog ->
                    removeItem(itm, sheet)
                },
                downloadItem = { itm ->
                    runBlocking {
                        downloadViewModel.queueDownloads(listOf(itm))
                    }
                },
                longClickDownloadButton = { itm ->
                    downloadCardViewModel.setResultItem(downloadViewModel.createResultItemFromDownload(itm))
                    downloadCardViewModel.setDownloadItem(itm)
                    findNavController().navigate(
                        R.id.downloadBottomSheetDialog,
                        bundleOf(Pair("type", itm.type))
                    )
                },
                scheduleButtonClick = {}
            )
        }
    }

    private fun removeItem(item: DownloadItem, bottomSheet: BottomSheetDialog?) {
        bottomSheet?.hide()
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + item.title + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { d, _ -> d.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _, _ ->
            downloadViewModel.deleteDownload(item.id)
        }
        deleteDialog.show()
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DownloadItemSimple>() {
            override fun areItemsTheSame(oldItem: DownloadItemSimple, newItem: DownloadItemSimple): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DownloadItemSimple, newItem: DownloadItemSimple): Boolean =
                oldItem == newItem
        }
    }
}
