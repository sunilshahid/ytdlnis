package com.deniscerri.ytdl.ui.downloads

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.compose.downloads.DownloadQueueScreen
import com.deniscerri.ytdl.ui.compose.downloads.DownloadTab
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.util.NavbarUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.WorkerEventBus
import com.deniscerri.ytdl.work.DownloadWorker
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Main Download Queue screen hosted in Jetpack Compose (Phase 5).
 *
 * Replaces `fragment_download_queue_main_screen.xml` and ViewPager2 tabs.
 */
class DownloadQueueMainFragment : ComposeFragment() {

    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var ytdlpViewModel: YTDLPViewModel
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var sharedPreferences: SharedPreferences

    private var initialTab by mutableStateOf(DownloadTab.RUNNING)
    private var targetTab by mutableStateOf<DownloadTab?>(null)
    private var currentTab by mutableStateOf(DownloadTab.RUNNING)
    private val progressEvents = mutableStateMapOf<Long, DownloadWorker.WorkerProgress>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        ytdlpViewModel = ViewModelProvider(requireActivity())[YTDLPViewModel::class.java]
        notificationUtil = NotificationUtil(requireContext())
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        if (savedInstanceState != null) {
            val savedIndex = savedInstanceState.getInt("selected_download_tab", 0)
            if (savedIndex in DownloadTab.entries.indices) {
                initialTab = DownloadTab.entries[savedIndex]
                currentTab = initialTab
            }
        }

        // Handle navigation arguments (e.g. from error notification)
        if (arguments?.getString("tab") != null) {
            initialTab = DownloadTab.ERRORED
            currentTab = DownloadTab.ERRORED
            targetTab = DownloadTab.ERRORED

            val reconfigureID = arguments?.getLong("reconfigure")
            if (reconfigureID != null && reconfigureID != 0L) {
                notificationUtil.cancelErroredNotification(reconfigureID.toInt())
                lifecycleScope.launch {
                    kotlin.runCatching {
                        val item = withContext(Dispatchers.IO) {
                            downloadViewModel.getItemByID(reconfigureID)
                        } ?: return@launch
                        downloadCardViewModel.setResultItem(downloadViewModel.createResultItemFromDownload(item))
                        downloadCardViewModel.setDownloadItem(item)
                        findNavController().navigate(
                            R.id.downloadBottomSheetDialog,
                            bundleOf(Pair("type", item.type))
                        )
                    }
                }
            }
            arguments?.clear()
        }

        // Screen on flag while active downloads exist
        lifecycleScope.launch {
            downloadViewModel.activePausedDownloads.collectLatest { list ->
                if (list.isEmpty()) {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        // Collect progress events
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WorkerEventBus.events.collectLatest { event ->
                    progressEvents[event.downloadItemID] = event
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mainActivity = activity as? MainActivity
        val isInNavBar = NavbarUtil.getNavBarItems(requireActivity()).any { n -> n.itemId == R.id.downloadQueueMainFragment && n.isVisible }
        if (!isInNavBar) {
            mainActivity?.hideBottomNavigation()
        }
    }

    @Composable
    override fun Content() {
        val isInNavBar = NavbarUtil.getNavBarItems(requireActivity()).any { n -> n.itemId == R.id.downloadQueueMainFragment && n.isVisible }

        DownloadQueueScreen(
            downloadViewModel = downloadViewModel,
            initialTab = initialTab,
            targetTab = targetTab,
            progressEvents = progressEvents,
            preferences = sharedPreferences,
            onBack = if (isInNavBar) null else {
                {
                    (activity as? MainActivity)?.onBackPressedDispatcher?.onBackPressed()
                        ?: requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            },
            onTabChanged = { tab ->
                currentTab = tab
                if (targetTab == tab) {
                    targetTab = null
                }
            },
            onNavigateToLog = { logId ->
                val bundle = Bundle().apply {
                    putLong("logID", logId)
                }
                findNavController().navigate(R.id.downloadLogFragment, bundle)
            },
            onShowDetailsCard = { itemId ->
                showDetailsCard(itemId)
            }
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
                    downloadViewModel.cancelDownloadOnly(itm.id)
                    downloadViewModel.deleteDownload(itm.id)
                },
                downloadItem = { itm ->
                    downloadViewModel.deleteDownload(itm.id)
                    itm.downloadStartTime = 0
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
                scheduleButtonClick = { itm ->
                    UiUtil.showDatePicker(parentFragmentManager, sharedPreferences) { date ->
                        downloadViewModel.deleteDownload(itm.id)
                        itm.downloadStartTime = date.timeInMillis
                        runBlocking {
                            downloadViewModel.queueDownloads(listOf(itm))
                        }
                    }
                }
            )
        }
    }

    fun scrollToActive() {
        targetTab = DownloadTab.RUNNING
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected_download_tab", currentTab.ordinal)
    }
}
