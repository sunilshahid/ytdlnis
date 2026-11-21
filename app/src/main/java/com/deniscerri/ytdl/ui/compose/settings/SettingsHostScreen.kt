package com.deniscerri.ytdl.ui.compose.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.SearchSettingsItem
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.ui.compose.components.PreferenceCard

/**
 * Single Compose shell host for the Settings screen.
 * Replaces legacy `activity_settings.xml`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHostScreen(
    title: String,
    isSearchAllowed: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    searchResults: List<SearchSettingsItem>,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (SearchSettingsItem) -> Unit,
    onSearchResultLongClick: (SearchSettingsItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    fragmentContainer: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (isSearchActive) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onSearch = { },
                            expanded = isSearchActive,
                            onExpandedChange = onSearchActiveChange,
                            placeholder = { Text(stringResource(R.string.search)) },
                            leadingIcon = {
                                IconButton(onClick = { onSearchActiveChange(false) }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_back),
                                        contentDescription = stringResource(R.string.back),
                                    )
                                }
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
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
                    expanded = isSearchActive,
                    onExpandedChange = onSearchActiveChange,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                        EmptyState(
                            title = stringResource(R.string.no_results),
                            iconRes = R.drawable.ic_search,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp),
                        ) {
                            items(searchResults) { item ->
                                val itemTitle = item.title
                                val summary = buildString {
                                    if (!item.groupTitle.isNullOrBlank()) {
                                        append(item.groupTitle)
                                        append(" · ")
                                    }
                                    append(item.summary.orEmpty())
                                }.trim().removeSuffix("·").trim()

                                PreferenceCard(
                                    title = itemTitle,
                                    summary = summary.ifBlank { null },
                                    showChevron = true,
                                    onClick = { onSearchResultClick(item) },
                                    onLongClick = { onSearchResultLongClick(item) },
                                )
                            }
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = title.ifBlank { stringResource(R.string.settings) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_back),
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        if (isSearchAllowed) {
                            IconButton(onClick = { onSearchActiveChange(true) }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = stringResource(R.string.search),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            fragmentContainer()
        }
    }
}
