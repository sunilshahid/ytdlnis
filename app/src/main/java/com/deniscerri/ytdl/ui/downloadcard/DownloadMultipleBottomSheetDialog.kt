package com.deniscerri.ytdl.ui.downloadcard

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadMultipleCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.FormatViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.compose.downloadcard.MultiDownloadSheet
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DownloadMultipleBottomSheetDialog migrated to Jetpack Compose (eleXML §5 A6-A8, A15-A18, A42).
 * Replaces download_multiple_bottom_sheet.xml, adjust_video.xml, adjust_audio.xml, adjust_command.xml,
 * download_type_sheet.xml, processing_sort_sheet.xml, download_card.xml, bottom_app_bar_text.xml,
 * download_multiple_menu.xml, and select_multiple_items_menu_context.xml.
 */
class DownloadMultipleBottomSheetDialog : BottomSheetDialogFragment(),
    ConfigureDownloadBottomSheetDialog.OnDownloadItemUpdateListener {

    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var ytdlpViewModel: YTDLPViewModel
    private lateinit var formatViewModel: FormatViewModel
    private lateinit var downloadMultipleCardViewModel: DownloadMultipleCardViewModel
    private lateinit var sharedPreferences: SharedPreferences

    private var currentDownloadIDs: List<Long> = emptyList()
    private var currentHistoryIDs: List<Long> = emptyList()
    private var ignoreDuplicates: Boolean = false

    private var itemsList by mutableStateOf<List<DownloadItem>>(emptyList())
    private var incognitoState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        historyViewModel = ViewModelProvider(requireActivity())[HistoryViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        ytdlpViewModel = ViewModelProvider(requireActivity())[YTDLPViewModel::class.java]
        formatViewModel = ViewModelProvider(requireActivity())[FormatViewModel::class.java]
        commandTemplateViewModel = ViewModelProvider(requireActivity())[CommandTemplateViewModel::class.java]
        downloadMultipleCardViewModel = ViewModelProvider(this)[DownloadMultipleCardViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        currentDownloadIDs = arguments?.getLongArray("currentDownloadIDs")?.toList() ?: emptyList()
        currentHistoryIDs = arguments?.getLongArray("currentHistoryIDs")?.toList() ?: emptyList()
        ignoreDuplicates = arguments?.getBoolean("ignore_duplicates") == true
        incognitoState = sharedPreferences.getBoolean("incognito", false)
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
                    LaunchedEffect(Unit) {
                        val loaded = withContext(Dispatchers.IO) {
                            if (currentDownloadIDs.isNotEmpty()) {
                                downloadViewModel.getAllByIDs(currentDownloadIDs)
                            } else if (currentHistoryIDs.isNotEmpty()) {
                                val historyItems = historyViewModel.getAllByIDs(currentHistoryIDs)
                                historyItems.map { downloadViewModel.createDownloadItemFromHistory(it) }
                            } else {
                                emptyList()
                            }
                        }
                        itemsList = loaded
                    }

                    val totalBytes = itemsList.sumOf { it.format.filesize.coerceAtLeast(0L) }
                    val totalSizeStr = if (totalBytes > 0L) FileUtil.convertFileSize(totalBytes) else ""

                    MultiDownloadSheet(
                        items = itemsList,
                        totalFileSize = totalSizeStr,
                        incognito = incognitoState,
                        onToggleIncognito = {
                            incognitoState = !incognitoState
                            itemsList = itemsList.map { it.copy(incognito = incognitoState) }
                        },
                        onConfigureItem = { item ->
                            val sheet = ConfigureDownloadBottomSheetDialog(item, this@DownloadMultipleBottomSheetDialog)
                            sheet.show(parentFragmentManager, "configureDownload")
                        },
                        onRemoveItem = { item ->
                            itemsList = itemsList.filter { it.id != item.id }
                            if (itemsList.isEmpty()) {
                                dismiss()
                            }
                        },
                        onApplyBatchDownloadType = { newType ->
                            itemsList = itemsList.map { item ->
                                item.copy(type = newType)
                            }
                        },
                        onApplyBatchContainer = { newContainer ->
                            itemsList = itemsList.map { it.copy(container = newContainer) }
                        },
                        onApplyBatchAudioBitrate = { newBitrate ->
                            itemsList = itemsList.map { item ->
                                item.audioPreferences.bitrate = newBitrate
                                item
                            }
                        },
                        onApplyBatchSubtitles = { embed ->
                            itemsList = itemsList.map { item ->
                                item.videoPreferences.embedSubs = embed
                                item
                            }
                        },
                        onApplyBatchChapters = { embed ->
                            itemsList = itemsList.map { item ->
                                item.videoPreferences.addChapters = embed
                                item.audioPreferences.splitByChapters = embed
                                item
                            }
                        },
                        onApplyBatchThumbnail = { embed ->
                            itemsList = itemsList.map { item ->
                                item.videoPreferences.embedThumbnail = embed
                                item.audioPreferences.embedThumb = embed
                                item
                            }
                        },
                        onApplyBatchSponsorBlock = { enabled ->
                            itemsList = itemsList.map { item ->
                                val filters = if (enabled) arrayListOf("sponsor", "intro", "outro") else arrayListOf<String>()
                                item.videoPreferences.sponsorBlockFilters = filters
                                item.audioPreferences.sponsorBlockFilters = ArrayList(filters)
                                item
                            }
                        },
                        onSortItems = { sortBy ->
                            itemsList = when (sortBy) {
                                "title" -> itemsList.sortedBy { it.title }
                                "duration" -> itemsList.sortedBy { it.duration }
                                "author" -> itemsList.sortedBy { it.author }
                                "size" -> itemsList.sortedByDescending { it.format.filesize }
                                else -> itemsList
                            }
                        },
                        onScheduleClick = {
                            UiUtil.showDatePicker(parentFragmentManager, sharedPreferences) { calendar ->
                                lifecycleScope.launch {
                                    val items = itemsList.map { item ->
                                        item.copy(
                                            status = com.deniscerri.ytdl.database.repository.DownloadRepository.Status.Scheduled.toString(),
                                            downloadStartTime = calendar.timeInMillis
                                        )
                                    }
                                    withContext(Dispatchers.IO) {
                                        downloadViewModel.queueDownloads(items, ignoreDuplicates)
                                    }
                                    withContext(Dispatchers.Main) { dismiss() }
                                }
                            }
                        },
                        onDownloadClick = {
                            lifecycleScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    downloadViewModel.queueDownloads(itemsList, ignoreDuplicates)
                                }
                                if (result.message.isNotBlank()) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                                withContext(Dispatchers.Main) { dismiss() }
                            }
                        },
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }

    override fun onDownloadItemUpdated(downloadItem: DownloadItem) {
        itemsList = itemsList.map { if (it.id == downloadItem.id) downloadItem else it }
    }
}
