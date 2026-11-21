package com.deniscerri.ytdl.ui.compose.observesources

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.ui.compose.components.YtdlBottomSheet
import com.deniscerri.ytdl.util.Extensions
import com.deniscerri.ytdl.util.Extensions.displayStatus
import com.deniscerri.ytdl.util.Extensions.scheduleSummary

/**
 * Source details sheet. Replaces `observe_sources_details_bottom_sheet.xml` and the imperative
 * `UiUtil.showObserveSourceDetailsCard`.
 *
 * Everything is kept 1:1: 25 sp title, "Observe sources" subtitle, pause/resume + check-now FABs
 * on the right, the status/stat chip row, the tappable (long-press copies) URL button and the
 * settings / remove / edit row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObserveSourceDetailsSheet(
    item: ObserveSourcesItem,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheckNow: () -> Unit,
    onPauseResume: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenLink: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onShowLinkList: (title: String, links: List<String>) -> Unit,
) {
    val context = LocalContext.current
    val status = item.displayStatus()

    YtdlBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            // header: name + pause/resume + check now
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 20.dp),
                ) {
                    Text(
                        text = item.name.ifEmpty { item.url },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 25.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.observe_sources),
                        modifier = Modifier.padding(top = 5.dp),
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetFab(
                        iconRes = if (status == Extensions.ObserveSourceDisplayStatus.ACTIVE) {
                            R.drawable.exomedia_ic_pause_white
                        } else {
                            R.drawable.exomedia_ic_play_arrow_white
                        },
                        contentDescription = stringResource(
                            if (status == Extensions.ObserveSourceDisplayStatus.ACTIVE) {
                                R.string.pause
                            } else {
                                R.string.resume
                            },
                        ),
                        onClick = onPauseResume,
                    )
                    SheetFab(
                        iconRes = R.drawable.ic_search,
                        contentDescription = stringResource(R.string.check_now),
                        onClick = onCheckNow,
                    )
                }
            }

            // status + stat chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val statusText = when (status) {
                    Extensions.ObserveSourceDisplayStatus.ACTIVE ->
                        "${item.scheduleSummary(context)} Ã‚Â· ${nextRunDate(item)}"

                    Extensions.ObserveSourceDisplayStatus.PAUSED -> stringResource(R.string.paused)
                    Extensions.ObserveSourceDisplayStatus.FINISHED ->
                        context.resources.getQuantityString(
                            R.plurals.finished_runs,
                            item.runCount,
                            item.runCount,
                        )
                }
                val statusIcon = when (status) {
                    Extensions.ObserveSourceDisplayStatus.ACTIVE -> R.drawable.baseline_loop_24
                    Extensions.ObserveSourceDisplayStatus.PAUSED -> R.drawable.exomedia_ic_pause_white
                    Extensions.ObserveSourceDisplayStatus.FINISHED -> R.drawable.ic_check
                }
                ObserveChip(
                    text = statusText,
                    iconRes = statusIcon,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                ObserveChip(
                    text = context.getString(R.string.observe_runs_chip, item.runCount),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                ObserveChip(
                    text = context.getString(
                        R.string.observe_processed_chip,
                        item.alreadyProcessedLinks.size,
                    ),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = if (item.alreadyProcessedLinks.isNotEmpty()) {
                        {
                            onShowLinkList(
                                context.getString(
                                    R.string.observe_processed_chip,
                                    item.alreadyProcessedLinks.size,
                                ),
                                item.alreadyProcessedLinks,
                            )
                        }
                    } else {
                        null
                    },
                )
                if (item.ignoredLinks.isNotEmpty()) {
                    ObserveChip(
                        text = context.getString(
                            R.string.observe_skipped_chip,
                            item.ignoredLinks.size,
                        ),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {
                            onShowLinkList(
                                context.getString(
                                    R.string.observe_skipped_chip,
                                    item.ignoredLinks.size,
                                ),
                                item.ignoredLinks,
                            )
                        },
                    )
                }
            }

            // URL: tap opens, long-press copies (the old button had both handlers)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .combinedClickable(
                        onClick = { onOpenLink(item.url) },
                        onLongClick = { onCopyLink(item.url) },
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.url,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // settings / remove / edit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.padding(end = 10.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.settings),
                        modifier = Modifier.size(18.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.padding(end = 10.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_delete_outline_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.Remove))
                }

                ElevatedButton(onClick = onEdit) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit))
                }
            }
        }
    }
}

/**
 * "Reset state" sheet. Replaces `observe_sources_settings_sheet.xml`; also closes the old
 * `UiUtil.showObserveSourceDetailsCard` settings sub-sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObserveSourceSettingsSheet(
    item: ObserveSourcesItem,
    onDismiss: () -> Unit,
    onReScanFromScratch: () -> Unit,
    onSkipBacklog: () -> Unit,
    onUnskipIgnored: () -> Unit,
) {
    YtdlBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 15.dp)) {
            Text(
                text = stringResource(R.string.reset_state),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )

            SettingsAction(
                text = stringResource(R.string.rescan_from_scratch),
                iconRes = R.drawable.ic_restore,
                onClick = onReScanFromScratch,
            )
            SettingsAction(
                text = stringResource(R.string.skip_backlog),
                iconRes = R.drawable.ic_delete_all,
                onClick = onSkipBacklog,
            )
            if (item.ignoredLinks.isNotEmpty()) {
                SettingsAction(
                    text = stringResource(R.string.unskip_ignored),
                    iconRes = R.drawable.ic_check,
                    onClick = onUnskipIgnored,
                )
            }
        }
    }
}

@Composable
private fun SettingsAction(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, fontSize = 14.sp)
    }
}

@Composable
private fun SheetFab(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = RoundedCornerShape(10.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Read-only link list ("processed" / "skipped" chips). Replaces `UiUtil.showFullTextDialog`
 * for these two chips: selectable text plus a copy action.
 */
@Composable
fun ObserveLinksDialog(
    title: String,
    links: List<String>,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val joined = links.joinToString("\n")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = joined,
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCopy(joined)
                    onDismiss()
                },
            ) {
                Text(stringResource(android.R.string.copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        },
    )
}
