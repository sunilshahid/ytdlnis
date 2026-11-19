package com.deniscerri.ytdl.ui.compose.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.work.DownloadWorker

/**
 * Screen displaying the active / downloading items list in Jetpack Compose (Phase 5).
 */
@Composable
fun ActiveDownloadsContent(
    items: List<DownloadItem>,
    progressEvents: Map<Long, DownloadWorker.WorkerProgress>,
    modifier: Modifier = Modifier,
    onPauseClick: (Long) -> Unit = {},
    onResumeClick: (Long) -> Unit = {},
    onCancelClick: (Long) -> Unit = {},
    onOutputClick: (DownloadItem) -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            EmptyState(
                iconRes = R.drawable.ic_no_results,
                title = stringResource(R.string.no_downloads_yet_title),
                message = stringResource(R.string.queue_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = items,
                    key = { "${it.id}_${it.url}" },
                    contentType = { "active_download" },
                ) { item ->
                    val event = progressEvents[item.id]
                    ActiveDownloadCard(
                        item = item,
                        progress = event?.progress ?: 0,
                        output = event?.output,
                        onPauseClick = { onPauseClick(item.id) },
                        onResumeClick = { onResumeClick(item.id) },
                        onCancelClick = { onCancelClick(item.id) },
                        onOutputClick = { onOutputClick(item) },
                    )
                }
            }
        }
    }
}
