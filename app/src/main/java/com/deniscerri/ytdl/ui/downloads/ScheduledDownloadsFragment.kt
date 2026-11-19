package com.deniscerri.ytdl.ui.downloads

import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.deniscerri.ytdl.ui.compose.downloads.ScheduledDownloadScreen
import com.deniscerri.ytdl.ui.compose.downloads.collectAsPagingItems
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

typealias ScheduledDownloadFragment = ScheduledDownloadsFragment

/**
 * Scheduled downloads tab hosted in Jetpack Compose (Phase 5).
 * Replaces `fragment_scheduled_download.xml` and `ScheduledDownloadAdapter`.
 */
class ScheduledDownloadsFragment : ComposeFragment() {

    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var ytdlpViewModel: YTDLPViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var sharedPreferences: SharedPreferences

    private var selectedIds by mutableStateOf<Set<Long>>(emptySet())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        ytdlpViewModel = ViewModelProvider(this)[YTDLPViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    @Composable
    override fun Content() {
        val pagingItems = downloadViewModel.scheduledDownloads.collectAsPagingItems(DIFF_CALLBACK)
        val totalCount by downloadViewModel.scheduledDownloadsCount.collectAsStateWithLifecycle(0)
        val coroutineScope = rememberCoroutineScope()

        val enableSwipe = remember {
            val gestures = sharedPreferences.getStringSet(
                "swipe_gesture",
                requireContext().resources.getStringArray(R.array.swipe_gestures_values).toSet()
            ) ?: emptySet()
            gestures.contains("scheduled")
        }

        ScheduledDownloadScreen(
            items = pagingItems,
            totalCount = totalCount,
            selectedIds = selectedIds,
            enableSwipe = enableSwipe,
            onDownloadNowForAll = {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.resetScheduleItemForAllScheduledItemsAndStartDownload()
                    }
                }
            },
            onDeleteAll = {
                UiUtil.showGenericDeleteAllDialog(requireContext()) {
                    downloadViewModel.deleteScheduled()
                }
            },
            onCopyAllUrls = {
                coroutineScope.launch {
                    val urls = withContext(Dispatchers.IO) {
                        downloadViewModel.getURLsByStatus(listOf(DownloadRepository.Status.Scheduled))
                    }
                    UiUtil.copyToClipboard(urls.joinToString("\n"), requireActivity())
                }
            },
            onDownloadNow = { item ->
                coroutineScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            downloadViewModel.resetScheduleTimeForItemsAndStartDownload(listOf(item.id))
                        }
                    }.onFailure {
                        Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDeleteItem = { item ->
                coroutineScope.launch {
                    val deletedItem = withContext(Dispatchers.IO) {
                        downloadViewModel.getItemByID(item.id)
                    } ?: return@launch

                    downloadViewModel.deleteDownload(deletedItem.id)
                    view?.let { v ->
                        Snackbar.make(
                            v,
                            getString(R.string.you_are_going_to_delete) + ": " + deletedItem.title.ifEmpty { deletedItem.url },
                            Snackbar.LENGTH_INDEFINITE
                        ).setAction(getString(R.string.undo)) {
                            downloadViewModel.insert(deletedItem)
                        }.show()
                    }
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
                            listOf(DownloadRepository.Status.Scheduled)
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
                            listOf(DownloadRepository.Status.Scheduled)
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
                            downloadViewModel.getScheduledIDsBetweenTwoItems(sorted.first(), sorted.last())
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
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.resetScheduleTimeForItemsAndStartDownload(toDownload)
                    }
                }
            },
            onDeleteSelected = {
                val toDelete = selectedIds.toList()
                selectedIds = emptySet()
                val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
                deleteDialog.setNegativeButton(getString(R.string.cancel)) { d, _ -> d.cancel() }
                deleteDialog.setPositiveButton(getString(R.string.ok)) { _, _ ->
                    lifecycleScope.launch {
                        downloadViewModel.deleteAllWithID(toDelete)
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
                    sheet.hide()
                    val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                    deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + itm.title + "\"!")
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { d, _ -> d.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _, _ ->
                        downloadViewModel.deleteDownload(itm.id)
                    }
                    deleteDialog.show()
                },
                downloadItem = { itm ->
                    itm.downloadStartTime = 0
                    runBlocking {
                        downloadViewModel.queueDownloads(listOf(itm), ignoreDuplicates = true)
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
                scheduleButtonClick = { downloadItem ->
                    UiUtil.showDatePicker(parentFragmentManager, sharedPreferences) {
                        Toast.makeText(
                            context,
                            getString(R.string.download_rescheduled_to) + " " + it.time,
                            Toast.LENGTH_LONG
                        ).show()
                        downloadItem.downloadStartTime = it.timeInMillis
                        runBlocking {
                            downloadViewModel.queueDownloads(listOf(downloadItem), ignoreDuplicates = true)
                        }
                    }
                }
            )
        }
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
