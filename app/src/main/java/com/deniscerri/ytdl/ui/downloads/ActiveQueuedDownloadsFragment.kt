package com.deniscerri.ytdl.ui.downloads

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.compose.downloads.DownloadQueueScreen
import com.deniscerri.ytdl.ui.compose.downloads.DownloadTab
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment

/**
 * Active/Queued downloads shell hosted in Jetpack Compose (Phase 5).
 *
 * Replaces `fragment_downloads.xml` and legacy CoordinatorLayout peek sheet.
 */
class ActiveQueuedDownloadsFragment : ComposeFragment() {

    private lateinit var downloadViewModel: DownloadViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
    }

    @Composable
    override fun Content() {
        DownloadQueueScreen(
            downloadViewModel = downloadViewModel,
            initialTab = DownloadTab.RUNNING,
        )
    }
}
