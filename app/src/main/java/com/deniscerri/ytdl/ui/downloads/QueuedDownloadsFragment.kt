package com.deniscerri.ytdl.ui.downloads

import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
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
import com.deniscerri.ytdl.ui.compose.downloads.QueueDownloadScreen
import com.deniscerri.ytdl.ui.compose.downloads.collectAsPagingItems
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

typealias InQueueFragment = QueuedDownloadsFragment

/**
 * In-queue downloads tab hosted in Jetpack Compose (Phase 5).
 * Replaces `fragment_inqueue.xml` and `QueuedDownloadAdapter`.
 */
class QueuedDownloadsFragment : ComposeFragment() {

    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var ytdlpViewModel: YTDLPViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var sharedPreferences: SharedPreferences

    private var selectedIds by mutableStateOf<Set<Long>>(emptySet())
    private var isReorderMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        ytdlpViewModel = ViewModelProvider(this)[YTDLPViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    /**
     * Toggles reorder drag handles/controls. Invoked from the queue sheet header in [ActiveQueuedDownloadsFragment].
     */
    fun toggleDragHandles() {
        isReorderMode = !isReorderMode
    }

    @Composable
    override fun Content() {
        val pagingItems = downloadViewModel.queuedDownloads.collectAsPagingItems(DIFF_CALLBACK)
        val coroutineScope = rememberCoroutineScope()

        val enableSwipe = remember {
            val gestures = sharedPreferences.getStringSet(
                "swipe_gesture",
                requireContext().resources.getStringArray(R.array.swipe_gestures_values).toSet()
            ) ?: emptySet()
            gestures.contains("queued")
        }

        QueueDownloadScreen(
            items = pagingItems,
            selectedIds = selectedIds,
            isReorderMode = isReorderMode,
            enableSwipe = enableSwipe,
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
                            listOf(DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused)
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
                            listOf(DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused)
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
                                listOf(DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused).toListString()
                            )
                        }
                        selectedIds = selectedIds + middle
                    }
                }
            },
            onClearSelection = {
                selectedIds = emptySet()
            },
            onMoveToTop = { id ->
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.putAtTopOfQueue(listOf(id))
                    }
                }
            },
            onMoveToBottom = { id ->
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.putAtBottomOfQueue(listOf(id))
                    }
                }
            },
            onMoveUp = { item ->
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.putAtTopOfQueue(listOf(item.id))
                    }
                }
            },
            onMoveDown = { item ->
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.putAtBottomOfQueue(listOf(item.id))
                    }
                }
            },
            onCancelItem = { item ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.cancelDownload(item.id)
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
                        for (id in toDelete) {
                            downloadViewModel.cancelDownloadOnly(id)
                        }
                        downloadViewModel.deleteAllWithID(toDelete)
                    }
                }
                deleteDialog.show()
            },
            onMoveSelectedToTop = {
                val toMove = selectedIds.toList()
                selectedIds = emptySet()
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.putAtTopOfQueue(toMove)
                    }
                }
            },
            onMoveSelectedToBottom = {
                val toMove = selectedIds.toList()
                selectedIds = emptySet()
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.putAtBottomOfQueue(toMove)
                    }
                }
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
            onSwipeLeft = { item ->
                removeItem(item.id)
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
                    removeItem(itm.id)
                },
                downloadItem = { itm ->
                    downloadViewModel.deleteDownload(itm.id)
                    itm.downloadStartTime = 0
                    runBlocking {
                        downloadViewModel.queueDownloads(listOf(itm))
                    }
                },
                longClickDownloadButton = { itm ->
                    lifecycleScope.launch {
                        itm.status = DownloadRepository.Status.Saved.toString()
                        withContext(Dispatchers.IO) {
                            downloadViewModel.updateToStatus(itm.id, DownloadRepository.Status.Saved)
                        }
                        downloadCardViewModel.setResultItem(downloadViewModel.createResultItemFromDownload(itm))
                        downloadCardViewModel.setDownloadItem(itm)
                        findNavController().navigate(
                            R.id.downloadBottomSheetDialog,
                            bundleOf(Pair("type", itm.type))
                        )
                    }
                },
                scheduleButtonClick = { downloadItem ->
                    UiUtil.showDatePicker(parentFragmentManager, sharedPreferences) {
                        downloadViewModel.deleteDownload(downloadItem.id)
                        downloadItem.downloadStartTime = it.timeInMillis
                        runBlocking {
                            downloadViewModel.queueDownloads(listOf(downloadItem))
                        }
                    }
                }
            )
        }
    }

    private fun removeItem(id: Long) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) {
                downloadViewModel.getItemByID(id)
            } ?: return@launch

            val deleteDialog = MaterialAlertDialogBuilder(requireContext())
            deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + item.title.ifEmpty { item.url } + "\"!")
            deleteDialog.setNegativeButton(getString(R.string.cancel)) { d, _ -> d.cancel() }
            deleteDialog.setPositiveButton(getString(R.string.ok)) { _, _ ->
                item.status = DownloadRepository.Status.Cancelled.toString()
                lifecycleScope.launch(Dispatchers.IO) {
                    downloadViewModel.updateDownload(item)
                }
                view?.let { v ->
                    Snackbar.make(
                        v,
                        getString(R.string.cancelled) + ": " + item.title.ifEmpty { item.url },
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction(getString(R.string.undo)) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            downloadViewModel.deleteDownload(item.id)
                            downloadViewModel.queueDownloads(listOf(item))
                        }
                    }.show()
                }
            }
            deleteDialog.show()
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
