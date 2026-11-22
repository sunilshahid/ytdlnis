package com.deniscerri.ytdl.ui.compose.home

import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.SearchSuggestionItem
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel

/**
 * State for the Home screen (Phase 4a & 4b).
 */
data class HomeUiState(
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = false,
    val results: List<ResultItem> = emptyList(),
    val suggestions: List<SearchSuggestionItem> = emptyList(),
    val providers: List<ProviderOption> = emptyList(),
    val selectedProvider: String = "ytsearch",
    val playlistFilters: List<String> = emptyList(),
    val selectedPlaylistFilter: String = "",
    val selectedItemIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val copiedUrl: String? = null,
    val showDownloadAllFab: Boolean = false,
    val totalCount: Int = 0,
    val failedQueries: List<ResultViewModel.FailedQuery> = emptyList(),
    val availableUpdates: List<ResultViewModel.AvailableUpdate> = emptyList(),
)
