package com.deniscerri.ytdl.ui.compose.settings.bento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R

data class BentoCardSpec(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val metric: String? = null,
    val badge: Int? = null,
    val iconRes: Int,
    val hue: Float,          // 0..360, fixed per card
    val fullWidth: Boolean = false,
    val minHeight: Int = 150, // dp
)

fun orderedCards(all: List<BentoCardSpec>, saved: String?): List<BentoCardSpec> {
    val byId = all.associateBy { it.id }
    val savedIds = saved?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
    val ordered = savedIds.mapNotNull { byId[it] }
    return ordered + all.filterNot { it.id in savedIds }   // new cards append
}

@Composable
fun BentoGrid(
    cards: List<BentoCardSpec>,
    onCardClick: (BentoCardSpec) -> Unit,
    onCardLongClick: (BentoCardSpec) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        if (header != null) {
            item(span = StaggeredGridItemSpan.FullLine) {
                header()
            }
        }

        itemsIndexed(
            items = cards,
            key = { _, card -> card.id },
            span = { _, card ->
                if (card.fullWidth) StaggeredGridItemSpan.FullLine
                else StaggeredGridItemSpan.SingleLane
            },
        ) { _, card ->
            BentoCard(
                spec = card,
                modifier = Modifier.animateItem(),
                onClick = { onCardClick(card) },
                onLongClick = { onCardLongClick(card) },
            )
        }

        if (footer != null) {
            item(span = StaggeredGridItemSpan.FullLine) {
                footer()
            }
        }
    }
}

/**
 * Reorder sheet allowing users to move bento cards up and down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BentoReorderSheet(
    cards: List<BentoCardSpec>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.reorder),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(cards, key = { _, item -> item.id }) { index, item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.padding(horizontal = 8.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )

                            IconButton(
                                onClick = { onMove(index, index - 1) },
                                enabled = index > 0,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_up),
                                    contentDescription = "Move Up",
                                )
                            }
                            IconButton(
                                onClick = { onMove(index, index + 1) },
                                enabled = index < cards.size - 1,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_down),
                                    contentDescription = "Move Down",
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
