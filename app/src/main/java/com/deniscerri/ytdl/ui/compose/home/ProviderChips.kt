package com.deniscerri.ytdl.ui.compose.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.ui.compose.components.YtdlChip

data class ProviderOption(
    val name: String,
    val value: String,
)

/**
 * Horizontal provider chips row (Phase 4a).
 *
 * Replaces the `HorizontalScrollView` + `ChipGroup` providers from `fragment_home.xml`.
 */
@Composable
fun ProviderChips(
    providers: List<ProviderOption>,
    selectedProvider: String,
    onProviderSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(providers, key = { it.value }) { provider ->
            YtdlChip(
                label = provider.name,
                selected = provider.value == selectedProvider,
                onClick = { onProviderSelected(provider.value) },
            )
        }
    }
}
