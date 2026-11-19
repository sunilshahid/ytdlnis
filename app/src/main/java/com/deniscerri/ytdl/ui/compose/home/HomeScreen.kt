package com.deniscerri.ytdl.ui.compose.home

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.ui.compose.components.YtdlChip

/**
 * Screen composable for the Home tab (Phase 4a & 4b).
 *
 * Implements M3 SearchBar, search suggestions, provider chips, paged result cards,
 * playlist filter chips, multi-selection mode, and floating action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    onSearchQueryChange: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onSearchActiveChange: (Boolean) -> Unit = {},
    onProviderSelected: (String) -> Unit = {},
    onPlaylistFilterSelected: (String) -> Unit = {},
    onSuggestionClick: (String) -> Unit = {},
    onSuggestionLongClick: (String) -> Unit = {},
    onItemClick: (ResultItem) -> Unit = {},
    onItemLongClick: (ResultItem) -> Unit = {},
    onAudioClick: (ResultItem) -> Unit = {},
    onAudioLongClick: (ResultItem) -> Unit = {},
    onVideoClick: (ResultItem) -> Unit = {},
    onVideoLongClick: (ResultItem) -> Unit = {},
    onDownloadAllClick: () -> Unit = {},
    onDownloadSelectedClick: () -> Unit = {},
    onCopiedUrlClick: (String) -> Unit = {},
    onCloseSelectionMode: () -> Unit = {},
    onSelectAllClick: () -> Unit = {},
    onInvertSelectionClick: () -> Unit = {},
    onDeleteSelectedClick: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (state.isSelectionMode) {
                // Contextual selection app bar
                TopAppBar(
                    title = {
                        Text(
                            text = "${state.selectedItemIds.size}",
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onCloseSelectionMode) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_close_24),
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onSelectAllClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_select_all),
                                contentDescription = stringResource(R.string.select_all),
                            )
                        }
                        IconButton(onClick = onInvertSelectionClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_select_between),
                                contentDescription = stringResource(R.string.invert_selected),
                            )
                        }
                        IconButton(onClick = onDeleteSelectedClick) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_delete_24),
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                // Copied URL FAB
                AnimatedVisibility(
                    visible = !state.copiedUrl.isNullOrBlank() && !state.isSearchActive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { state.copiedUrl?.let { onCopiedUrlClick(it) } },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_clipboard),
                                contentDescription = null,
                            )
                        },
                        text = { Text(stringResource(R.string.link_you_copied)) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                // Download All FAB
                AnimatedVisibility(
                    visible = state.showDownloadAllFab && !state.isSelectionMode && !state.isSearchActive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = onDownloadAllClick,
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_down),
                                contentDescription = null,
                            )
                        },
                        text = { Text(stringResource(R.string.download_all)) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                // Download Selected FAB
                AnimatedVisibility(
                    visible = state.isSelectionMode && state.selectedItemIds.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = onDownloadSelectedClick,
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_down),
                                contentDescription = null,
                            )
                        },
                        text = { Text(stringResource(R.string.download)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // M3 Search Bar
            if (!state.isSelectionMode) {
                val isUrl = Patterns.WEB_URL.matcher(state.searchQuery).matches()

                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = state.searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onSearch = { onSearch(it) },
                            expanded = state.isSearchActive,
                            onExpandedChange = onSearchActiveChange,
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(
                                            painter = painterResource(R.drawable.baseline_close_24),
                                            contentDescription = stringResource(R.string.clear),
                                        )
                                    }
                                }
                            },
                        )
                    },
                    expanded = state.isSearchActive,
                    onExpandedChange = onSearchActiveChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (state.isSearchActive) 0.dp else 16.dp, vertical = 8.dp),
                ) {
                    // Providers row (only when search is active and not a URL)
                    if (!isUrl && state.providers.isNotEmpty()) {
                        ProviderChips(
                            providers = state.providers,
                            selectedProvider = state.selectedProvider,
                            onProviderSelected = onProviderSelected,
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }

                    // Suggestions list
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(state.suggestions, key = { index, suggestion -> "${suggestion.type}_${suggestion.text}_$index" }) { _, suggestion ->
                            SearchSuggestionRow(
                                item = suggestion,
                                onClick = onSuggestionClick,
                                onLongClick = onSuggestionLongClick,
                                onAppendToSearch = onSearchQueryChange,
                            )
                        }
                    }
                }
            }

            // Loading indicator
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            // Playlist Filter Chips (if multiple playlists detected)
            if (state.playlistFilters.isNotEmpty() && !state.isSearchActive) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        YtdlChip(
                            label = stringResource(R.string.all),
                            selected = state.selectedPlaylistFilter.isEmpty(),
                            onClick = { onPlaylistFilterSelected("") },
                        )
                    }
                    itemsIndexed(state.playlistFilters, key = { index, filter -> "${filter}_$index" }) { _, filter ->
                        YtdlChip(
                            label = filter,
                            selected = state.selectedPlaylistFilter == filter,
                            onClick = { onPlaylistFilterSelected(filter) },
                        )
                    }
                }
            }

            // Results Section
            if (state.isLoading && state.results.isEmpty()) {
                // Shimmer cards while fetching
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                ) {
                    items(5) {
                        ResultCardShimmer()
                    }
                }
            } else if (state.results.isEmpty() && !state.isLoading && !state.isSearchActive) {
                EmptyState(
                    title = stringResource(R.string.no_results),
                    iconRes = R.drawable.ic_search,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (!state.isSearchActive) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                ) {
                    itemsIndexed(state.results, key = { index, item -> "${item.id}_${item.url}_$index" }) { _, item ->
                        ResultCard(
                            item = item,
                            isSelected = state.selectedItemIds.contains(item.id),
                            isSelectionMode = state.isSelectionMode,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) },
                            onAudioClick = { onAudioClick(item) },
                            onAudioLongClick = { onAudioLongClick(item) },
                            onVideoClick = { onVideoClick(item) },
                            onVideoLongClick = { onVideoLongClick(item) },
                        )
                    }
                }
            }
        }
    }
}
