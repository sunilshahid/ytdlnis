package com.deniscerri.ytdl.ui.compose.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.ui.play.device.DeviceFolder
import com.deniscerri.ytdl.ui.play.device.DeviceMedia
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import kotlinx.coroutines.launch

enum class PlayDestination {
    HOME, LIKED, RECENT, DEVICE
}

/**
 * Full Compose Play & Media Library Screen (Phase 6a).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    destination: PlayDestination,
    searchQuery: String,
    isGrid: Boolean,
    items: List<PlaybackItem>,
    recentItems: List<PlaybackItem>,
    deviceFolders: List<DeviceFolder>,
    deviceMedia: List<DeviceMedia>,
    selectedFolder: String?,
    hasDevicePermission: Boolean,
    nowPlayingItem: PlaybackItem?,
    isPlaying: Boolean,
    onSelectDestination: (PlayDestination) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleLayoutMode: () -> Unit,
    onTrackClick: (PlaybackItem) -> Unit,
    onTrackMenuClick: (PlaybackItem) -> Unit,
    onShuffle: () -> Unit,
    onPlayAll: () -> Unit,
    onDeviceFolderSelect: (String?) -> Unit,
    onDeviceMediaClick: (DeviceMedia) -> Unit,
    onDeviceMediaMenuClick: (DeviceMedia) -> Unit,
    onRequestDevicePermission: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var searchActive by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_music_downloaded),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.play),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(8.dp))

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.play)) },
                    icon = { Icon(painter = painterResource(R.drawable.ic_music_downloaded), contentDescription = null) },
                    selected = destination == PlayDestination.HOME,
                    onClick = {
                        onSelectDestination(PlayDestination.HOME)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.liked)) },
                    icon = { Icon(painter = painterResource(R.drawable.ic_favorite), contentDescription = null) },
                    selected = destination == PlayDestination.LIKED,
                    onClick = {
                        onSelectDestination(PlayDestination.LIKED)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.recently_played)) },
                    icon = { Icon(painter = painterResource(R.drawable.ic_history), contentDescription = null) },
                    selected = destination == PlayDestination.RECENT,
                    onClick = {
                        onSelectDestination(PlayDestination.RECENT)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.device_files_title)) },
                    icon = { Icon(painter = painterResource(R.drawable.ic_downloads), contentDescription = null) },
                    selected = destination == PlayDestination.DEVICE,
                    onClick = {
                        onSelectDestination(PlayDestination.DEVICE)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        },
        modifier = modifier
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (searchActive) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text(stringResource(R.string.search)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(
                                                painter = painterResource(R.drawable.baseline_close_24),
                                                contentDescription = stringResource(R.string.close)
                                            )
                                        }
                                    }
                                }
                            )
                        } else {
                            Text(
                                text = when (destination) {
                                    PlayDestination.HOME -> stringResource(R.string.play)
                                    PlayDestination.LIKED -> stringResource(R.string.liked)
                                    PlayDestination.RECENT -> stringResource(R.string.recently_played)
                                    PlayDestination.DEVICE -> stringResource(R.string.device_files_title)
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        if (searchActive) {
                            IconButton(onClick = {
                                searchActive = false
                                onSearchQueryChange("")
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_back),
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_more_vert_24),
                                    contentDescription = stringResource(R.string.more)
                                )
                            }
                        }
                    },
                    actions = {
                        if (!searchActive) {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = stringResource(R.string.search)
                                )
                            }
                            IconButton(onClick = onToggleLayoutMode) {
                                Icon(
                                    painter = painterResource(if (isGrid) R.drawable.ic_chapter_list else R.drawable.ic_view_grid),
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (destination == PlayDestination.DEVICE) {
                    DeviceContent(
                        hasPermission = hasDevicePermission,
                        folders = deviceFolders,
                        media = deviceMedia,
                        selectedFolder = selectedFolder,
                        isGrid = isGrid,
                        currentPlayingId = nowPlayingItem?.mediaId,
                        onRequestPermission = onRequestDevicePermission,
                        onSelectFolder = onDeviceFolderSelect,
                        onMediaClick = onDeviceMediaClick,
                        onMediaMenuClick = onDeviceMediaMenuClick,
                    )
                } else {
                    LibraryContent(
                        destination = destination,
                        items = items,
                        recentItems = recentItems,
                        isGrid = isGrid,
                        nowPlayingItem = nowPlayingItem,
                        isPlaying = isPlaying,
                        onShuffle = onShuffle,
                        onPlayAll = onPlayAll,
                        onTrackClick = onTrackClick,
                        onTrackMenuClick = onTrackMenuClick,
                        onOpenPlayer = onOpenPlayer,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(
    destination: PlayDestination,
    items: List<PlaybackItem>,
    recentItems: List<PlaybackItem>,
    isGrid: Boolean,
    nowPlayingItem: PlaybackItem?,
    isPlaying: Boolean,
    onShuffle: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackClick: (PlaybackItem) -> Unit,
    onTrackMenuClick: (PlaybackItem) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    if (items.isEmpty() && (destination != PlayDestination.HOME || recentItems.isEmpty())) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_music_downloaded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when (destination) {
                        PlayDestination.LIKED -> stringResource(R.string.liked_empty_title)
                        PlayDestination.RECENT -> stringResource(R.string.no_recent_plays)
                        else -> stringResource(R.string.play_something_hint)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.mediaId }) { item ->
                PlayTrackGridItem(
                    title = item.title,
                    subtitle = item.artist,
                    artworkUri = item.artworkUri,
                    durationText = item.durationMs?.toStringTimeStamp(),
                    isPlaying = nowPlayingItem?.mediaId == item.mediaId && isPlaying,
                    onClick = { onTrackClick(item) },
                    onMenuClick = { onTrackMenuClick(item) }
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Quick actions
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onShuffle,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.shuffle))
                    }

                    Button(
                        onClick = onPlayAll,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_lyrics_play), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.download_all))
                    }
                }
            }

            // Recent shelf if in HOME destination
            if (destination == PlayDestination.HOME && recentItems.isNotEmpty()) {
                item {
                    PlaySectionHeader(title = stringResource(R.string.recently_played), count = recentItems.size)
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        items(recentItems, key = { "recent_${it.mediaId}" }) { item ->
                            Box(modifier = Modifier.width(130.dp)) {
                                PlayTrackGridItem(
                                    title = item.title,
                                    subtitle = item.artist,
                                    artworkUri = item.artworkUri,
                                    durationText = item.durationMs?.toStringTimeStamp(),
                                    isPlaying = nowPlayingItem?.mediaId == item.mediaId && isPlaying,
                                    onClick = { onTrackClick(item) },
                                    onMenuClick = { onTrackMenuClick(item) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                PlaySectionHeader(
                    title = when (destination) {
                        PlayDestination.HOME -> stringResource(R.string.play)
                        PlayDestination.LIKED -> stringResource(R.string.liked)
                        PlayDestination.RECENT -> stringResource(R.string.recently_played)
                        PlayDestination.DEVICE -> stringResource(R.string.device_files_title)
                    },
                    count = items.size
                )
            }

            items(items, key = { it.mediaId }) { item ->
                PlayTrackRow(
                    title = item.title,
                    subtitle = item.artist,
                    artworkUri = item.artworkUri,
                    durationText = item.durationMs?.toStringTimeStamp(),
                    isPlaying = nowPlayingItem?.mediaId == item.mediaId && isPlaying,
                    onClick = { onTrackClick(item) },
                    onMenuClick = { onTrackMenuClick(item) }
                )
            }
        }
    }
}

@Composable
private fun DeviceContent(
    hasPermission: Boolean,
    folders: List<DeviceFolder>,
    media: List<DeviceMedia>,
    selectedFolder: String?,
    isGrid: Boolean,
    currentPlayingId: String?,
    onRequestPermission: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onMediaClick: (DeviceMedia) -> Unit,
    onMediaMenuClick: (DeviceMedia) -> Unit,
) {
    if (!hasPermission) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_downloads),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.request_permission_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.device_files_permission_grant))
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (folders.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedFolder == null,
                        onClick = { onSelectFolder(null) },
                        label = { Text(stringResource(R.string.all)) }
                    )
                }
                items(folders) { folder ->
                    FilterChip(
                        selected = selectedFolder == folder.path,
                        onClick = { onSelectFolder(if (selectedFolder == folder.path) null else folder.path) },
                        label = { Text("${folder.path.substringAfterLast('/').ifEmpty { folder.path }} (${folder.items.size})") }
                    )
                }
            }
        }

        if (media.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.device_files_empty_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(media, key = { it.storeId }) { item ->
                    PlayTrackRow(
                        title = item.title,
                        subtitle = item.artist ?: item.album,
                        artworkUri = item.albumArtUri?.toString() ?: item.contentUri.toString(),
                        durationText = item.durationMs.toStringTimeStamp(),
                        isVideo = item.type == com.deniscerri.ytdl.playback.model.PlaybackType.VIDEO,
                        isPlaying = currentPlayingId == item.mediaId,
                        onClick = { onMediaClick(item) },
                        onMenuClick = { onMediaMenuClick(item) }
                    )
                }
            }
        }
    }
}
