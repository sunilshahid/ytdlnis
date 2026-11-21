package com.deniscerri.ytdl.ui.compose.downloads

import android.app.Activity
import android.content.SharedPreferences
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import androidx.lifecycle.ViewModelProvider
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.playback.PlaybackLauncher
import com.deniscerri.ytdl.ui.compose.components.YtdlBottomSheet
import com.deniscerri.ytdl.ui.compose.host.ComposeSheetFragment
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.vault.ui.VaultImportActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Bottom sheet displaying detailed information and actions for a history download item (Phase 5).
 *
 * Replaces `history_item_details_bottom_sheet.xml` previously inflated by [UiUtil.showHistoryItemDetailsCard].
 */
@Composable
fun HistoryDetailSheetContent(
    item: HistoryItem,
    isPresent: Boolean,
    preferences: SharedPreferences,
    onDismiss: () -> Unit,
    onRemoveItem: (HistoryItem, Boolean) -> Unit,
    onRedownloadItem: (HistoryItem) -> Unit,
    onRedownloadShowDownloadCard: (HistoryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Top section: Title, Author, Type/File button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = item.title.ifEmpty { item.url },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.pointerInput(item.title) {
                        detectTapGestures(
                            onLongPress = {
                                activity?.let {
                                    UiUtil.showFullTextDialog(
                                        it,
                                        item.title.ifEmpty { item.url },
                                        it.getString(R.string.title)
                                    )
                                }
                            }
                        )
                    }
                )

                if (item.author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.pointerInput(item.author) {
                            detectTapGestures(
                                onLongPress = {
                                    activity?.let {
                                        UiUtil.showFullTextDialog(
                                            it,
                                            item.author,
                                            it.getString(R.string.author)
                                        )
                                    }
                                }
                            )
                        }
                    )
                }
            }

            val typeImageResource = when (item.type) {
                DownloadType.audio -> if (isPresent) R.drawable.ic_music_downloaded else R.drawable.ic_music
                DownloadType.video -> if (isPresent) R.drawable.ic_video_downloaded else R.drawable.ic_video
                else -> R.drawable.ic_terminal
            }

            if (isPresent) {
                if (item.downloadPath.size > 1) {
                    BadgedBox(
                        badge = {
                            Badge {
                                Text(item.downloadPath.size.toString())
                            }
                        }
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                activity?.let { UiUtil.openOrShareFiles(it, item.downloadPath, false) }
                            }
                        ) {
                            Icon(
                                painter = painterResource(typeImageResource),
                                contentDescription = stringResource(R.string.share)
                            )
                        }
                    }
                } else {
                    FilledTonalIconButton(
                        onClick = {
                            activity?.let { UiUtil.openOrShareFiles(it, item.downloadPath, false) }
                        }
                    ) {
                        Icon(
                            painter = painterResource(typeImageResource),
                            contentDescription = stringResource(R.string.share)
                        )
                    }
                }
            } else {
                FilledTonalIconButton(
                    onClick = {},
                    enabled = false
                ) {
                    Icon(
                        painter = painterResource(typeImageResource),
                        contentDescription = null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Horizontal scrolling metadata chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time chip
            val calendar = remember(item.time) {
                Calendar.getInstance().apply { timeInMillis = item.time * 1000L }
            }
            val timeString = remember(calendar) {
                val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm")
                SimpleDateFormat(pattern, Locale.getDefault()).format(calendar.time)
            }
            AssistChip(
                onClick = {},
                label = { Text(timeString) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_clock),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            // Thumbnail chip
            if (item.thumb.isNotBlank()) {
                AssistChip(
                    onClick = { UiUtil.openLinkIntent(context, item.thumb) },
                    label = { Text(stringResource(R.string.thumbnail)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_image),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // Format note chip
            if (item.type != DownloadType.command && item.format.format_note != "?" && item.format.format_note.isNotEmpty()) {
                AssistChip(
                    onClick = {},
                    label = { Text(item.format.format_note) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_format),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // Container chip
            val firstFile = remember(item.downloadPath) {
                item.downloadPath.firstOrNull()?.let { File(it) }
            }
            val typeImageResource = when (item.type) {
                DownloadType.audio -> if (isPresent) R.drawable.ic_music_downloaded else R.drawable.ic_music
                DownloadType.video -> if (isPresent) R.drawable.ic_video_downloaded else R.drawable.ic_video
                else -> R.drawable.ic_terminal
            }
            if (item.format.container.isNotEmpty() && item.downloadPath.size == 1) {
                val containerText = if (firstFile?.exists() == true) {
                    firstFile.extension.uppercase()
                } else {
                    item.format.container.uppercase()
                }
                AssistChip(
                    onClick = {},
                    label = { Text(containerText) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(typeImageResource),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // Codec chip
            val codecText = remember(item.format) {
                if (item.format.encoding.isNotEmpty()) {
                    item.format.encoding.uppercase()
                } else if (item.format.vcodec != "none" && item.format.vcodec.isNotEmpty()) {
                    item.format.vcodec.uppercase()
                } else {
                    item.format.acodec.uppercase()
                }
            }
            if (codecText.isNotEmpty() && codecText != "NONE" && item.downloadPath.size <= 1) {
                AssistChip(
                    onClick = {},
                    label = { Text(codecText) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_code),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // File size chip
            val fileSizeReadable = remember(item.downloadPath, item.format.filesize) {
                val sizeBytes = if (firstFile?.exists() == true) firstFile.length() else item.format.filesize
                FileUtil.convertFileSize(sizeBytes)
            }
            if (fileSizeReadable != "?" && item.downloadPath.size <= 1) {
                AssistChip(
                    onClick = {},
                    label = { Text(fileSizeReadable) }
                )
            }

            // Command chip
            if (item.command.isNotBlank()) {
                AssistChip(
                    onClick = { activity?.let { UiUtil.showGeneratedCommand(it, preferences, item.command) } },
                    label = { Text(stringResource(R.string.command)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // Location chip
            val availableFiles = remember(item.downloadPath) {
                item.downloadPath.filter { FileUtil.exists(it) }
            }
            if (availableFiles.isNotEmpty()) {
                AssistChip(
                    onClick = {
                        activity?.let {
                            UiUtil.showFullTextDialog(
                                it,
                                availableFiles.joinToString("\n"),
                                it.getString(R.string.location)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.location)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.baseline_folder_24),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Link button
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(item.url) {
                    detectTapGestures(
                        onTap = {
                            onDismiss()
                            UiUtil.openLinkIntent(context, item.url)
                        },
                        onLongPress = {
                            onDismiss()
                            UiUtil.copyLinkToClipBoard(context, item.url)
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play Button
            Button(
                onClick = {
                    onDismiss()
                    runCatching {
                        PlaybackLauncher.playHistory(context, item)
                    }.onFailure {
                        Toast.makeText(context, R.string.could_not_resolve, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = item.type != DownloadType.command
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lyrics_play),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.play))
            }

            // Move to Vault Button
            val availableFiles = remember(item.downloadPath) { item.downloadPath.filter { FileUtil.exists(it) } }
            val videoPath = availableFiles.firstOrNull()
            if (isPresent && item.type == DownloadType.video && !videoPath.isNullOrBlank()) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        context.startActivity(
                            VaultImportActivity.createIntent(
                                context = context,
                                title = item.title,
                                filePath = videoPath,
                                originalFileName = File(videoPath).name,
                                playbackMediaId = "dl:${item.id}",
                                historyId = item.id
                            )
                        )
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.move_to_vault))
                }
            }

            // Remove Button
            OutlinedButton(
                onClick = {
                    onDismiss()
                    activity?.let { UiUtil.showRemoveHistoryItemDialog(item, it, delete = onRemoveItem) }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_delete_outline_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.Remove))
            }

            // Open file / Redownload button
            if (isPresent) {
                ElevatedButton(
                    onClick = {
                        onDismiss()
                        activity?.let { UiUtil.openOrShareFiles(it, item.downloadPath, true) }
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_open_file),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.Open_File))
                }
            } else {
                ElevatedButton(
                    onClick = {
                        onDismiss()
                        onRedownloadItem(item)
                    },
                    modifier = Modifier.pointerInput(item.id) {
                        detectTapGestures(
                            onTap = {
                                onDismiss()
                                onRedownloadItem(item)
                            },
                            onLongPress = {
                                onDismiss()
                                onRedownloadShowDownloadCard(item)
                            }
                        )
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.redownload))
                }
            }
        }
    }
}

/**
 * Modal bottom sheet displaying detailed information and actions for a history download item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailSheet(
    item: HistoryItem?,
    isPresent: Boolean,
    onDismissRequest: () -> Unit,
    onRemoveItem: (HistoryItem, Boolean) -> Unit,
    onRedownloadItem: (HistoryItem) -> Unit,
    onRedownloadShowDownloadCard: (HistoryItem) -> Unit,
    modifier: Modifier = Modifier,
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
) {
    if (item == null) return
    YtdlBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        HistoryDetailSheetContent(
            item = item,
            isPresent = isPresent,
            preferences = preferences,
            onDismiss = onDismissRequest,
            onRemoveItem = onRemoveItem,
            onRedownloadItem = onRedownloadItem,
            onRedownloadShowDownloadCard = onRedownloadShowDownloadCard,
        )
    }
}

/**
 * Fragment hosting [HistoryDetailSheetContent] using [ComposeSheetFragment].
 */
class HistoryDetailSheetDialogFragment : ComposeSheetFragment() {
    var historyItem: HistoryItem? = null
    var isPresent: Boolean = false
    var onRemoveItem: ((HistoryItem, Boolean) -> Unit)? = null
    var onRedownloadItem: ((HistoryItem) -> Unit)? = null
    var onRedownloadShowDownloadCard: ((HistoryItem) -> Unit)? = null

    @Composable
    override fun Content() {
        val item = historyItem ?: return
        val preferences = remember { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
        HistoryDetailSheetContent(
            item = item,
            isPresent = isPresent,
            preferences = preferences,
            onDismiss = { dismiss() },
            onRemoveItem = { itm, delFiles ->
                onRemoveItem?.invoke(itm, delFiles)
                dismiss()
            },
            onRedownloadItem = { itm ->
                onRedownloadItem?.invoke(itm)
                dismiss()
            },
            onRedownloadShowDownloadCard = { itm ->
                onRedownloadShowDownloadCard?.invoke(itm)
                dismiss()
            }
        )
    }

    companion object {
        fun newInstance(
            item: HistoryItem,
            isPresent: Boolean,
            onRemoveItem: (HistoryItem, Boolean) -> Unit,
            onRedownloadItem: (HistoryItem) -> Unit,
            onRedownloadShowDownloadCard: (HistoryItem) -> Unit,
        ): HistoryDetailSheetDialogFragment = HistoryDetailSheetDialogFragment().apply {
            this.historyItem = item
            this.isPresent = isPresent
            this.onRemoveItem = onRemoveItem
            this.onRedownloadItem = onRedownloadItem
            this.onRedownloadShowDownloadCard = onRedownloadShowDownloadCard
        }
    }
}

/**
 * Bottom sheet displaying detailed information and actions for a download item (Phase 5).
 * Replaces `history_item_details_bottom_sheet.xml` previously inflated by [UiUtil.showDownloadItemDetailsCard].
 */
@Composable
fun DownloadDetailSheetContent(
    item: DownloadItem,
    status: DownloadRepository.Status,
    ytdlpViewModel: YTDLPViewModel,
    preferences: SharedPreferences,
    onDismiss: () -> Unit,
    onRemoveItem: (DownloadItem) -> Unit,
    onDownloadItem: (DownloadItem) -> Unit,
    onLongClickDownloadButton: (DownloadItem) -> Unit,
    onScheduleButtonClick: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Top section: Title, Author, Type icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = item.title.ifEmpty { item.playlistTitle.ifEmpty { item.url } },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.pointerInput(item.title) {
                        detectTapGestures(
                            onLongPress = {
                                activity?.let {
                                    UiUtil.showFullTextDialog(
                                        it,
                                        item.title.ifEmpty { item.playlistTitle.ifEmpty { item.url } },
                                        it.getString(R.string.title)
                                    )
                                }
                            }
                        )
                    }
                )

                if (item.author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.pointerInput(item.author) {
                            detectTapGestures(
                                onLongPress = {
                                    activity?.let {
                                        UiUtil.showFullTextDialog(
                                            it,
                                            item.author,
                                            it.getString(R.string.author)
                                        )
                                    }
                                }
                            )
                        }
                    )
                }
            }

            val typeImageResource = when (item.type) {
                DownloadType.audio -> R.drawable.ic_music
                DownloadType.video -> R.drawable.ic_video
                else -> R.drawable.ic_terminal
            }

            FilledTonalIconButton(
                onClick = {},
                enabled = false
            ) {
                Icon(
                    painter = painterResource(typeImageResource),
                    contentDescription = null
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Horizontal metadata chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time chip for scheduled
            if (status == DownloadRepository.Status.Scheduled && item.downloadStartTime > System.currentTimeMillis() / 1000) {
                val calendar = remember(item.downloadStartTime) {
                    Calendar.getInstance().apply { timeInMillis = item.downloadStartTime }
                }
                val timeString = remember(calendar) {
                    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm")
                    SimpleDateFormat(pattern, Locale.getDefault()).format(calendar.time)
                }
                AssistChip(
                    onClick = {
                        onDismiss()
                        onScheduleButtonClick(item)
                    },
                    label = { Text(timeString) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_clock),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // Thumbnail chip
            if (item.thumb.isNotBlank()) {
                AssistChip(
                    onClick = { UiUtil.openLinkIntent(context, item.thumb) },
                    label = { Text(stringResource(R.string.thumbnail)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_image),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // Format note chip
            if (item.type != DownloadType.command && item.format.format_note != "?" && item.format.format_note.isNotEmpty()) {
                AssistChip(
                    onClick = {},
                    label = { Text(item.format.format_note) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_format),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // Container chip
            if (item.format.container.isNotEmpty()) {
                val typeRes = when (item.type) {
                    DownloadType.audio -> R.drawable.ic_music
                    DownloadType.video -> R.drawable.ic_video
                    else -> R.drawable.ic_terminal
                }
                AssistChip(
                    onClick = {},
                    label = { Text(item.format.container.uppercase()) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(typeRes),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // Codec chip
            val codecText = remember(item.format) {
                if (item.format.encoding.isNotEmpty()) {
                    item.format.encoding.uppercase()
                } else if (item.format.vcodec != "none" && item.format.vcodec.isNotEmpty()) {
                    item.format.vcodec.uppercase()
                } else {
                    item.format.acodec.uppercase()
                }
            }
            if (codecText.isNotEmpty() && codecText != "NONE") {
                AssistChip(
                    onClick = {},
                    label = { Text(codecText) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_format),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // File size chip
            val fileSizeReadable = remember(item.format.filesize) {
                FileUtil.convertFileSize(item.format.filesize)
            }
            if (fileSizeReadable != "?") {
                AssistChip(
                    onClick = {},
                    label = { Text(fileSizeReadable) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_format),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // Command chip
            AssistChip(
                onClick = {
                    activity?.let {
                        UiUtil.showGeneratedCommand(
                            it,
                            preferences,
                            ytdlpViewModel.parseYTDLRequestString(item)
                        )
                    }
                },
                label = { Text(stringResource(R.string.command)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_terminal),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Link button
        OutlinedButton(
            onClick = {
                onDismiss()
                UiUtil.openLinkIntent(context, item.url)
            },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(item.url) {
                    detectTapGestures(
                        onTap = {
                            onDismiss()
                            UiUtil.openLinkIntent(context, item.url)
                        },
                        onLongPress = {
                            onDismiss()
                            UiUtil.copyLinkToClipBoard(context, item.url)
                        }
                    )
                }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_link),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons: Remove and Download
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Remove Button
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onRemoveItem(item)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_delete_outline_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.Remove))
            }

            // Download / Action Button based on status
            val (buttonTextRes, buttonIconRes) = when (status) {
                DownloadRepository.Status.Cancelled, DownloadRepository.Status.Saved ->
                    Pair(R.string.download, R.drawable.ic_downloads)
                DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused ->
                    Pair(R.string.configure_download, R.drawable.ic_retries)
                DownloadRepository.Status.Scheduled ->
                    Pair(R.string.download_now, R.drawable.ic_downloads)
                else ->
                    Pair(R.string.download, R.drawable.ic_downloads)
            }

            ElevatedButton(
                onClick = {
                    when (status) {
                        DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused, DownloadRepository.Status.Error -> {
                            onDismiss()
                            onLongClickDownloadButton(item)
                        }
                        else -> {
                            onDismiss()
                            onDownloadItem(item)
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onTap = {
                                when (status) {
                                    DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused, DownloadRepository.Status.Error -> {
                                        onDismiss()
                                        onLongClickDownloadButton(item)
                                    }
                                    else -> {
                                        onDismiss()
                                        onDownloadItem(item)
                                    }
                                }
                            },
                            onLongPress = {
                                onDismiss()
                                when (status) {
                                    DownloadRepository.Status.Error -> onDownloadItem(item)
                                    else -> onLongClickDownloadButton(item)
                                }
                            }
                        )
                    }
            ) {
                Icon(
                    painter = painterResource(buttonIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(buttonTextRes))
            }
        }
    }
}

/**
 * Fragment hosting [DownloadDetailSheetContent] using [ComposeSheetFragment].
 */
class DownloadDetailSheetDialogFragment : ComposeSheetFragment() {
    var downloadItem: DownloadItem? = null
    var status: DownloadRepository.Status = DownloadRepository.Status.Queued
    var onRemoveItem: ((DownloadItem) -> Unit)? = null
    var onDownloadItem: ((DownloadItem) -> Unit)? = null
    var onLongClickDownloadButton: ((DownloadItem) -> Unit)? = null
    var onScheduleButtonClick: ((DownloadItem) -> Unit)? = null

    @Composable
    override fun Content() {
        val item = downloadItem ?: return
        val preferences = remember { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
        val ytdlpViewModel = ViewModelProvider(requireActivity())[YTDLPViewModel::class.java]
        DownloadDetailSheetContent(
            item = item,
            status = status,
            ytdlpViewModel = ytdlpViewModel,
            preferences = preferences,
            onDismiss = { dismiss() },
            onRemoveItem = { itm ->
                dismiss()
                onRemoveItem?.invoke(itm)
            },
            onDownloadItem = { itm ->
                dismiss()
                onDownloadItem?.invoke(itm)
            },
            onLongClickDownloadButton = { itm ->
                dismiss()
                onLongClickDownloadButton?.invoke(itm)
            },
            onScheduleButtonClick = { itm ->
                dismiss()
                onScheduleButtonClick?.invoke(itm)
            }
        )
    }

    companion object {
        fun newInstance(
            item: DownloadItem,
            status: DownloadRepository.Status,
            onRemoveItem: (DownloadItem) -> Unit,
            onDownloadItem: (DownloadItem) -> Unit,
            onLongClickDownloadButton: (DownloadItem) -> Unit,
            onScheduleButtonClick: (DownloadItem) -> Unit = {},
        ): DownloadDetailSheetDialogFragment = DownloadDetailSheetDialogFragment().apply {
            this.downloadItem = item
            this.status = status
            this.onRemoveItem = onRemoveItem
            this.onDownloadItem = onDownloadItem
            this.onLongClickDownloadButton = onLongClickDownloadButton
            this.onScheduleButtonClick = onScheduleButtonClick
        }
    }
}
