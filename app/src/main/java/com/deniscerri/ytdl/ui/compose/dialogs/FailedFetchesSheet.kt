package com.deniscerri.ytdl.ui.compose.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel.FailedQuery
import com.deniscerri.ytdl.ui.compose.components.YtdlBottomSheet
import com.deniscerri.ytdl.util.Extensions.isURL

/**
 * Failed fetches bottom sheet composable.
 *
 * Replaces `failed_fetches_bottom_sheet.xml` and `failed_fetch_item.xml`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FailedFetchesSheet(
    failedQueries: List<FailedQuery>,
    onDismiss: () -> Unit,
    onDeleteAll: () -> Unit,
    onRetryAll: () -> Unit,
    onRetryItem: (FailedQuery) -> Unit,
    onDownloadAnyway: (FailedQuery) -> Unit,
    modifier: Modifier = Modifier,
) {
    YtdlBottomSheet(onDismissRequest = onDismiss) {
        FailedFetchesContent(
            failedQueries = failedQueries,
            onDeleteAll = onDeleteAll,
            onRetryAll = onRetryAll,
            onRetryItem = onRetryItem,
            onDownloadAnyway = onDownloadAnyway,
            modifier = modifier,
        )
    }
}

@Composable
fun FailedFetchesContent(
    failedQueries: List<FailedQuery>,
    onDeleteAll: () -> Unit,
    onRetryAll: () -> Unit,
    onRetryItem: (FailedQuery) -> Unit,
    onDownloadAnyway: (FailedQuery) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.failed_to_fetch),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.items_failed_to_fetch,
                        failedQueries.size,
                        failedQueries.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FilledIconButton(
                onClick = onDeleteAll,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_delete_24),
                    contentDescription = stringResource(R.string.remove_all)
                )
            }

            ElevatedButton(
                onClick = onRetryAll,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.retry_all))
            }
        }

        Text(
            text = stringResource(R.string.failed_to_fetch_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(failedQueries, key = { it.query }) { item ->
                FailedFetchItemCard(
                    item = item,
                    onRetry = { onRetryItem(item) },
                    onDownloadAnyway = { onDownloadAnyway(item) }
                )
            }
        }
    }
}

@Composable
fun FailedFetchItemCard(
    item: FailedQuery,
    onRetry: () -> Unit,
    onDownloadAnyway: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_error_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.query,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.error.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp)
                    .combinedClickable(
                        onClick = { isExpanded = !isExpanded },
                        onLongClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("error", item.error))
                        }
                    )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.query.isURL()) {
                    TextButton(onClick = onDownloadAnyway) {
                        Text(text = stringResource(R.string.download_anyway))
                    }
                }
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.retry))
                }
            }
        }
    }
}
