package com.deniscerri.ytdl.ui.downloads

import android.os.Bundle
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.compose.downloads.ActiveDownloadsContent
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.util.WorkerEventBus
import com.deniscerri.ytdl.work.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Active downloads list hosted in Jetpack Compose (Phase 5).
 *
 * Replaces `fragment_active.xml` and `ActiveDownloadAdapter`.
 */
class ActiveDownloadsFragment : ComposeFragment() {

    private lateinit var downloadViewModel: DownloadViewModel
    private var activeDownloads by mutableStateOf<List<DownloadItem>>(emptyList())
    private val progressEvents = mutableStateMapOf<Long, DownloadWorker.WorkerProgress>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]

        lifecycleScope.launch {
            downloadViewModel.activePausedDownloads.collectLatest { list ->
                activeDownloads = list
                if (list.isEmpty()) {
                    requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WorkerEventBus.events.collectLatest { event ->
                    progressEvents[event.downloadItemID] = event
                }
            }
        }
    }

    @Composable
    override fun Content() {
        ActiveDownloadsContent(
            items = activeDownloads,
            progressEvents = progressEvents,
            onPauseClick = { itemId ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.pauseDownload(itemId)
                    }
                }
            },
            onResumeClick = { itemId ->
                downloadViewModel.resumeDownload(itemId)
            },
            onCancelClick = { itemId ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadViewModel.cancelDownload(itemId)
                    }
                }
            },
            onOutputClick = { item ->
                if (item.logID != null && item.logID != 0L) {
                    val bundle = Bundle().apply {
                        putLong("logID", item.logID!!)
                    }
                    findNavController().navigate(R.id.downloadLogFragment, bundle)
                }
            },
        )
    }
}
