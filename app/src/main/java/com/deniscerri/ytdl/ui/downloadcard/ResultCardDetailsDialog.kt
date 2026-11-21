package com.deniscerri.ytdl.ui.downloadcard

import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.compose.components.PicassoImage
import com.deniscerri.ytdl.ui.compose.components.YtdlBottomSheet
import com.deniscerri.ytdl.ui.compose.downloads.ActiveDownloadRow
import com.deniscerri.ytdl.ui.compose.host.ComposeSheetFragment
import com.deniscerri.ytdl.util.UiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Result card details dialog (Phase 5).
 *
 * Replaces `result_card_details.xml`, `active_download_card_minified.xml`, and
 * `ActiveDownloadMinifiedAdapter.kt`.
 */
class ResultCardDetailsDialog : ComposeSheetFragment() {

    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var ytdlpViewModel: YTDLPViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private var item: ResultItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        ytdlpViewModel = ViewModelProvider(this)[YTDLPViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]

        item = if (Build.VERSION.SDK_INT >= 33) {
            arguments?.getParcelable("result", ResultItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("result")
        }
    }

    @Composable
    override fun Content() {
        val resultItem = item ?: run {
            dismiss()
            return
        }

        val activeDownloads by remember(resultItem.url) {
            downloadViewModel.activeDownloads.map { list ->
                list.filter { it.url == resultItem.url }
            }
        }.collectAsState(initial = emptyList())

        ResultCardSheet(
            item = resultItem,
            activeDownloads = activeDownloads,
            onDismiss = { dismiss() },
            onDownloadMusic = {
                onButtonClick(resultItem, DownloadType.audio)
            },
            onDownloadVideo = {
                onButtonClick(resultItem, DownloadType.video)
            },
            onOpenLink = { url ->
                UiUtil.openLinkIntent(requireContext(), url)
            },
            onCopyLink = { url ->
                UiUtil.copyLinkToClipBoard(requireContext(), url)
            },
            onPauseDownload = { id ->
                lifecycleScope.launch(Dispatchers.IO) {
                    downloadViewModel.pauseDownload(id)
                }
            },
            onResumeDownload = { id ->
                downloadViewModel.resumeDownload(id)
            },
            onCancelDownload = { id ->
                lifecycleScope.launch(Dispatchers.IO) {
                    downloadViewModel.cancelDownload(id)
                }
            },
            onNavigateToQueue = {
                dismiss()
                findNavController().navigate(R.id.downloadQueueMainFragment)
            }
        )
    }

    private fun onButtonClick(resultItem: ResultItem, type: DownloadType) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (sharedPreferences.getBoolean("download_card", true)) {
            downloadCardViewModel.setResultItem(resultItem)
            downloadCardViewModel.setDownloadItem(null)
            findNavController().navigateUp()
            findNavController().navigate(
                R.id.downloadBottomSheetDialog,
                bundleOf("type" to type)
            )
        } else {
            lifecycleScope.launch {
                val downloadItem = withContext(Dispatchers.IO) {
                    downloadViewModel.createDownloadItemFromResult(
                        result = resultItem,
                        givenType = type
                    )
                }
                downloadViewModel.queueDownloads(listOf(downloadItem))
                findNavController().navigateUp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultCardSheet(
    item: ResultItem,
    activeDownloads: List<DownloadItem>,
    onDismiss: () -> Unit,
    onDownloadMusic: () -> Unit,
    onDownloadVideo: () -> Unit,
    onOpenLink: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onCancelDownload: (Long) -> Unit,
    onNavigateToQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YtdlBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Thumbnail preview if available
            if (item.thumb.isNotBlank()) {
                PicassoImage(
                    url = item.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Title
            Text(
                text = item.title.ifEmpty { item.url },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Author
            if (item.author.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // URL button (tap open, long tap copy)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onOpenLink(item.url) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Download buttons (Audio / Video)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDownloadMusic,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_music),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.audio))
                }

                Button(
                    onClick = onDownloadVideo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_video),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.video))
                }
            }

            // Active downloads section
            if (activeDownloads.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.running),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                activeDownloads.forEach { downloadItem ->
                    ActiveDownloadRow(
                        item = downloadItem,
                        progress = 0,
                        output = null,
                        onPauseClick = { onPauseDownload(downloadItem.id) },
                        onResumeClick = { onResumeDownload(downloadItem.id) },
                        onCancelClick = { onCancelDownload(downloadItem.id) },
                        onCardClick = onNavigateToQueue
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
