package com.deniscerri.ytdl.ui.compose.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.SearchSuggestionItem
import com.deniscerri.ytdl.database.models.SearchSuggestionType

/**
 * Compose row for search suggestions and history queries (Phase 4a).
 *
 * Replaces `res/layout/search_suggestion_item.xml` and [com.deniscerri.ytdl.ui.adapter.SearchSuggestionsAdapter].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchSuggestionRow(
    item: SearchSuggestionItem,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit = {},
    onLongClick: (String) -> Unit = {},
    onAppendToSearch: (String) -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconRes = when (item.type) {
        SearchSuggestionType.SUGGESTION -> R.drawable.ic_search
        SearchSuggestionType.HISTORY -> R.drawable.ic_restore
        SearchSuggestionType.CLIPBOARD -> R.drawable.ic_clipboard
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = { onClick(item.text) },
                onLongClick = { onLongClick(item.text) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.width(16.dp))

        Text(
            text = item.text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (item.type == SearchSuggestionType.SUGGESTION) {
            IconButton(
                onClick = { onAppendToSearch(item.text) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_outward),
                    contentDescription = stringResource(android.R.string.selectTextMode),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
