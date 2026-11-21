package com.deniscerri.ytdl.ui.compose.logs

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.LogItem
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.ui.compose.components.YtdlScaffold
import com.deniscerri.ytdl.util.FileUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Download-log list (Phase 3). Replaces `fragment_download_log_list.xml`, `log_card.xml` and
 * `DownloadLogsAdapter`.
 *
 * Behaviour kept 1:1: tap opens the log, long-press starts multi-select, tap while selecting toggles
 * the item, swipe-left deletes with an undo snackbar, and the overflow action clears everything.
 * Fast scroll has no Compose equivalent and is intentionally dropped (same as the other list ports).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadLogListScreen(
    logs: List<LogItem>,
    selectedIds: Set<Long>,
    swipeGestures: Boolean,
    onBack: () -> Unit,
    onOpen: (LogItem) -> Unit,
    onToggleSelection: (LogItem) -> Unit,
    onDeleteAll: () -> Unit,
    onDelete: (LogItem) -> Unit,
    onUndoDelete: (LogItem) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    YtdlScaffold(
        title = stringResource(R.string.logs),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = { LogListOverflow(onDeleteAll) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            ) {
                items(logs, key = { it.id }) { item ->
                    val card: @Composable () -> Unit = {
                        LogCard(
                            item = item,
                            selected = selectedIds.contains(item.id),
                            onClick = {
                                if (selectedIds.isEmpty()) onOpen(item) else onToggleSelection(item)
                            },
                            onLongClick = { onToggleSelection(item) },
                        )
                    }

                    if (swipeGestures) {
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    onDelete(item)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.you_are_going_to_delete) + ": " + item.title,
                                            actionLabel = context.getString(R.string.undo),
                                            duration = SnackbarDuration.Indefinite,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) onUndoDelete(item)
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = { SwipeDeleteBackground() },
                        ) {
                            card()
                        }
                    } else {
                        card()
                    }
                }
            }

            if (logs.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.no_results),
                    iconRes = R.drawable.ic_no_results,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun LogListOverflow(onDeleteAll: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.baseline_more_vert_24),
                contentDescription = null,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_all)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_all),
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onDeleteAll()
                },
            )
        }
    }
}

/** Red swipe background with the delete icon, matching the old `RecyclerViewSwipeDecorator`. */
@Composable
private fun SwipeDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Red)
            .padding(end = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            painter = painterResource(R.drawable.baseline_delete_24),
            contentDescription = stringResource(R.string.delete),
            tint = Color.White,
        )
    }
}

/**
 * 1:1 with `log_card.xml`: transparent card, 5 dp corners, 5 dp primary stroke while checked,
 * 20Ãƒâ€”10 dp content padding, 30 dp file icon, 15 sp bold title, 12 sp chips.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogCard(
    item: LogItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSecondary = MaterialTheme.colorScheme.onSecondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (selected) Modifier.border(5.dp, primary, shape) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_baseline_file_open_24),
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(5.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 5.dp),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val typeIcon = when (item.downloadType) {
                        DownloadType.audio -> R.drawable.ic_music
                        DownloadType.video -> R.drawable.ic_video
                        DownloadType.command -> R.drawable.ic_terminal
                        else -> null
                    }
                    val typeLabel = when (item.downloadType) {
                        DownloadType.audio -> R.string.audio
                        DownloadType.video -> R.string.video
                        DownloadType.command -> R.string.command
                        else -> null
                    }
                    if (typeIcon != null && typeLabel != null) {
                        Icon(
                            painter = painterResource(typeIcon),
                            contentDescription = stringResource(typeLabel),
                            modifier = Modifier.size(24.dp),
                            tint = secondary,
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier.padding(start = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val time = remember(item.downloadTime) {
                        SimpleDateFormat(
                            DateFormat.getBestDateTimePattern(
                                Locale.getDefault(),
                                "ddMMMyyyy - HH:mm",
                            ),
                            Locale.getDefault(),
                        ).format(item.downloadTime)
                    }
                    LogChip(text = time, container = primary, content = onPrimary)
                    Spacer(Modifier.width(5.dp))

                    if (item.format.format_note != "?" && item.format.format_note != "") {
                        LogChip(
                            text = item.format.format_note.uppercase(),
                            container = secondary,
                            content = onSecondary,
                        )
                        Spacer(Modifier.width(5.dp))
                    }

                    val codecText = when {
                        item.format.encoding != "" -> item.format.encoding.uppercase()
                        item.format.vcodec != "none" && item.format.vcodec != "" ->
                            item.format.vcodec.uppercase()
                        else -> item.format.acodec.uppercase()
                    }
                    if (codecText != "" && codecText != "none") {
                        LogChip(text = codecText, container = secondary, content = onSecondary)
                        Spacer(Modifier.width(5.dp))
                    }

                    val fileSize = FileUtil.convertFileSize(item.format.filesize)
                    if (fileSize != "?") {
                        LogChip(text = fileSize, container = secondary, content = onSecondary)
                    }
                }
            }
        }
    }
}

/** `rounded_corner.xml` is a 5 dp rounded background; the TextView styles supply 12 sp bold text. */
@Composable
private fun LogChip(text: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 30.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(container)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
