package com.deniscerri.ytdl.ui.compose.observesources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.ui.compose.components.YtdlScaffold

/**
 * Observe sources screen (Phase 3).
 *
 * Hosts the grid of [ObserveSourceCard] items, the new source action chip,
 * and the overflow delete-all action.
 *
 * Replaces `fragment_observe_sources.xml`, `observe_sources_item.xml` and `ObserveSourcesAdapter`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObserveSourcesScreen(
    items: List<ObserveSourcesItem>,
    runningIds: Set<Long>,
    onBack: () -> Unit,
    onNewSource: () -> Unit,
    onDeleteAll: () -> Unit,
    onItemClick: (ObserveSourcesItem) -> Unit,
    onItemLongClick: (ObserveSourcesItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val gridSize = remember(context) {
        context.resources.getInteger(R.integer.grid_size).coerceAtLeast(1)
    }

    YtdlScaffold(
        title = stringResource(R.string.observe_sources),
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_more_vert_24),
                        contentDescription = null,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_all_sources)) },
                        onClick = {
                            menuExpanded = false
                            onDeleteAll()
                        },
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AssistChip(
                    onClick = onNewSource,
                    label = { Text(stringResource(R.string.new_source)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 4.dp),
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridSize),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        ObserveSourceCard(
                            item = item,
                            isRunning = runningIds.contains(item.id),
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) },
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.no_results),
                    iconRes = R.drawable.ic_no_results,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
