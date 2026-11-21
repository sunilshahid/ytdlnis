@file:Suppress("DEPRECATION")

package com.deniscerri.ytdl.ui.compose.home

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.ui.compose.components.YtdlChip

/**
 * Screen composable for the Home tab (Phase 4a & 4b).
 *
 * Implements:
 * - Expressive Header: "YTD.Ex" (YTD. in red, Ex in light green)
 * - M3 SearchBar with leading search lens and trailing provider dropdown (more_vert)
 * - Search suggestions, provider chips, paged result cards,
 * - Playlist filter chips, multi-selection mode, and floating action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = state.isSearchActive || state.isSelectionMode) {
        if (state.isSelectionMode) {
            onCloseSelectionMode()
        } else if (state.isSearchActive) {
            keyboardController?.hide()
            focusManager.clearFocus()
            onSearchActiveChange(false)
        }
    }

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
            // 1. App Header / Logo: YTDLnis.Ex (YTDL in red, nis in onSurface, .Ex in light green)
            if (!state.isSelectionMode && !state.isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFFE53935),
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("YTDL")
                            }
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("nis")
                            }
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(".Ex")
                            }
                        },
                        fontSize = 32.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // 2. M3 Search Bar
            if (!state.isSelectionMode) {
                var showProviderMenu by remember { mutableStateOf(false) }
                val isUrl = Patterns.WEB_URL.matcher(state.searchQuery).matches()

                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = state.searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onSearch = { query ->
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onSearchActiveChange(false)
                                onSearch(query)
                            },
                            expanded = state.isSearchActive,
                            onExpandedChange = onSearchActiveChange,
                            placeholder = {
                                Text(
                                    stringResource(R.string.search_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 16.sp
                                )
                            },
                            leadingIcon = {
                                IconButton(
                                    onClick = {
                                        if (state.searchQuery.isNotBlank()) {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            onSearchActiveChange(false)
                                            onSearch(state.searchQuery)
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_search),
                                        contentDescription = stringResource(R.string.search),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(
                                            painter = painterResource(R.drawable.baseline_close_24),
                                            contentDescription = stringResource(R.string.clear),
                                        )
                                    }
                                } else {
                                    Box {
                                        IconButton(onClick = { showProviderMenu = true }) {
                                            Icon(
                                                painter = painterResource(R.drawable.baseline_more_vert_24),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showProviderMenu,
                                            onDismissRequest = { showProviderMenu = false }
                                        ) {
                                            state.providers.forEach { provider ->
                                                DropdownMenuItem(
                                                    text = { Text(provider.name) },
                                                    onClick = {
                                                        onProviderSelected(provider.value)
                                                        showProviderMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    },
                    expanded = state.isSearchActive,
                    onExpandedChange = onSearchActiveChange,
                    shape = if (state.isSearchActive) SearchBarDefaults.fullScreenShape else RoundedCornerShape(28.dp),
                    colors = SearchBarDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (state.isSearchActive) 0.dp else 16.dp, vertical = 6.dp),
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
                                onClick = { query ->
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onSearchActiveChange(false)
                                    onSuggestionClick(query)
                                },
                                onLongClick = onSuggestionLongClick,
                                onAppendToSearch = onSearchQueryChange,
                            )
                        }
                    }
                }
            }

            // 4. Playlist Filter Chips (if multiple playlists detected)
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

            // 5. Results Section
            if (state.isLoading && state.results.isEmpty()) {
                // Centered large Material 3 loading indicator (no ghost shimmer cards)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator(
                        modifier = Modifier.size(72.dp)
                    )
                }
            } else if (state.results.isEmpty() && !state.isLoading && !state.isSearchActive && state.searchQuery.isNotBlank()) {
                // Show EmptyState only if user searched and no results were found
                EmptyState(
                    title = stringResource(R.string.no_results),
                    iconRes = R.drawable.ic_search,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = !state.isSearchActive && state.results.isNotEmpty(),
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                ) {
                    itemsIndexed(state.results, key = { index, item -> "${item.id}_${item.url}_$index" }) { _, item ->
                        ResultCard(
                            modifier = Modifier.animateItem(),
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
