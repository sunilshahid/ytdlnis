package com.deniscerri.ytdl.ui.compose.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.lyrics.LyricsDocument
import com.deniscerri.ytdl.lyrics.LyricsLine
import com.deniscerri.ytdl.lyrics.LyricsProviderRegistry
import com.deniscerri.ytdl.lyrics.LyricsSource
import com.deniscerri.ytdl.lyrics.model.LyricsCandidate
import com.deniscerri.ytdl.lyrics.romanize.Romanizer
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.ui.play.lyrics.KaraokeLyricsView
import com.deniscerri.ytdl.ui.play.lyrics.LyricsDisplayMode
import com.deniscerri.ytdl.ui.play.lyrics.LyricsLoadStatus
import com.deniscerri.ytdl.ui.play.lyrics.LyricsUiState
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheetContent(
    uiState: LyricsUiState,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    playbackSpeed: Float,
    durationMs: Long,
    baseColor: Int?,
    accentColor: Int?,
    approximateWordTimingsEnabled: Boolean,
    romanizeEnabled: Boolean,
    scrollAnchor: Float,
    fontScale: Float,
    onBackClick: () -> Unit,
    onLineClick: (LyricsLine) -> Unit,
    onSelectMode: (LyricsDisplayMode) -> Unit,
    onToggleRomanization: () -> Unit,
    onToggleApproximateTimings: () -> Unit,
    onChangeOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onSearchClick: () -> Unit,
    onImportClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLyricsViewCreated: (KaraokeLyricsView) -> Unit,
    modifier: Modifier = Modifier
) {
    var showOffsetPill by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isHeaderVisible by remember { mutableStateOf(true) }

    val document = uiState.document
    val item = uiState.item
    val offsetMs = document?.offsetMs ?: 0L

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            LyricsTopBar(
                title = item?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.lyrics),
                artist = item?.artist?.takeIf { it.isNotBlank() }.orEmpty(),
                canRomanize = document != null && Romanizer.canRomanizeDocument(document),
                romanizeEnabled = romanizeEnabled,
                offsetMs = offsetMs,
                showMoreMenu = showMoreMenu,
                onShowMoreMenuChange = { showMoreMenu = it },
                onBackClick = onBackClick,
                onToggleRomanization = onToggleRomanization,
                onToggleOffsetPill = { showOffsetPill = !showOffsetPill },
                onSearchClick = onSearchClick,
                onImportClick = onImportClick,
                onSettingsClick = onSettingsClick
            )

            // Collapsible header controls
            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                LyricsHeaderControls(
                    document = document,
                    approximateWordTimingsEnabled = approximateWordTimingsEnabled,
                    onToggleApproximateTimings = onToggleApproximateTimings,
                    onSelectMode = onSelectMode
                )
            }

            // Main Lyrics View or Empty/Loading Prompt
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // KaraokeLyricsView
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        KaraokeLyricsView(context).apply {
                            setFocusFraction(scrollAnchor)
                            setTextScale(fontScale)
                            setApproximateWordTimings(approximateWordTimingsEnabled)
                            setShowRomanization(romanizeEnabled)
                            onLineClickListener = onLineClick
                            onScrollDirectionListener = { isScrollingUp ->
                                isHeaderVisible = isScrollingUp
                            }
                            onLyricsViewCreated(this)
                        }
                    },
                    update = { view ->
                        if (baseColor != null && accentColor != null) {
                            view.setColors(baseColor, accentColor)
                        }
                        view.setPlaybackState(isPlaying, playbackSpeed)
                        view.setPosition(playbackPositionMs)
                    }
                )

                // Prompt overlay when needed
                val showPrompt = when (uiState.status) {
                    LyricsLoadStatus.IDLE -> true
                    LyricsLoadStatus.LOADING_LOCAL -> document == null
                    LyricsLoadStatus.LOADING_NETWORK -> document == null
                    LyricsLoadStatus.NOT_FOUND -> document == null
                    LyricsLoadStatus.READY -> false
                }

                if (showPrompt) {
                    LyricsPromptOverlay(
                        status = uiState.status,
                        onSearchClick = onSearchClick,
                        onImportClick = onImportClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Floating Offset Pill
        AnimatedVisibility(
            visible = showOffsetPill,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            LyricsOffsetPill(
                offsetMs = offsetMs,
                onChangeOffset = onChangeOffset,
                onResetOffset = onResetOffset,
                onClose = { showOffsetPill = false }
            )
        }
    }
}

@Composable
private fun LyricsTopBar(
    title: String,
    artist: String,
    canRomanize: Boolean,
    romanizeEnabled: Boolean,
    offsetMs: Long,
    showMoreMenu: Boolean,
    onShowMoreMenuChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onToggleRomanization: () -> Unit,
    onToggleOffsetPill: () -> Unit,
    onSearchClick: () -> Unit,
    onImportClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (artist.isNotBlank()) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (canRomanize) {
            IconButton(onClick = onToggleRomanization) {
                Icon(
                    painter = painterResource(R.drawable.ic_translate),
                    contentDescription = stringResource(R.string.lyrics_romanize_toggle_desc),
                    tint = if (romanizeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box {
            IconButton(onClick = { onShowMoreMenuChange(true) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { onShowMoreMenuChange(false) }
            ) {
                val offsetTitle = if (offsetMs == 0L) {
                    stringResource(R.string.lyrics_offset)
                } else {
                    val sign = if (offsetMs > 0) "+" else ""
                    val sec = String.format(Locale.US, "%.1fs", offsetMs / 1000.0)
                    stringResource(R.string.lyrics_offset_value, "$sign$sec")
                }
                DropdownMenuItem(
                    text = { Text(offsetTitle) },
                    onClick = {
                        onShowMoreMenuChange(false)
                        onToggleOffsetPill()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.lyrics_search_online)) },
                    onClick = {
                        onShowMoreMenuChange(false)
                        onSearchClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.lyrics_import_file)) },
                    onClick = {
                        onShowMoreMenuChange(false)
                        onImportClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.lyrics_settings)) },
                    onClick = {
                        onShowMoreMenuChange(false)
                        onSettingsClick()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsHeaderControls(
    document: LyricsDocument?,
    approximateWordTimingsEnabled: Boolean,
    onToggleApproximateTimings: () -> Unit,
    onSelectMode: (LyricsDisplayMode) -> Unit
) {
    if (document == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Source and Badge row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sourceText = remember(document) {
                val providerDisplay = document.providerName
                    ?: document.providerId?.let { pid -> LyricsProviderRegistry.getProvider(pid)?.displayName ?: pid }
                when {
                    providerDisplay != null -> "From $providerDisplay"
                    document.source == LyricsSource.ONLINE -> "Online"
                    document.source == LyricsSource.EMBEDDED -> "Embedded"
                    document.source == LyricsSource.SIDECAR -> "Sidecar"
                    document.source == LyricsSource.CACHE -> "Cache"
                    document.source == LyricsSource.LRCLIB -> "LRCLIB"
                    document.source == LyricsSource.USER -> "User"
                    else -> ""
                }
            }

            Text(
                text = sourceText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Timing badge
            val badgeText = when {
                !document.isSynced -> stringResource(R.string.lyrics_badge_not_synced)
                document.hasWordTimings -> stringResource(R.string.lyrics_badge_word_synced)
                approximateWordTimingsEnabled -> stringResource(R.string.lyrics_badge_approx_on)
                else -> stringResource(R.string.lyrics_badge_line_synced)
            }
            val canToggleApprox = document.isSynced && !document.hasWordTimings

            FilterChip(
                selected = document.hasWordTimings || approximateWordTimingsEnabled,
                onClick = { if (canToggleApprox) onToggleApproximateTimings() },
                label = { Text(badgeText, fontSize = 11.sp) },
                enabled = canToggleApprox,
                modifier = Modifier.height(28.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

        // Mode selectors
        if (document.isSynced) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LyricsModeChip("Focus", LyricsDisplayMode.SIMPLE, onSelectMode, Modifier.weight(1f))
                LyricsModeChip("Synced", LyricsDisplayMode.SYNCED, onSelectMode, Modifier.weight(1f))
                LyricsModeChip("Word", LyricsDisplayMode.WORD_BY_WORD, onSelectMode, Modifier.weight(1f))
                LyricsModeChip("Karaoke", LyricsDisplayMode.KARAOKE, onSelectMode, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LyricsModeChip(
    label: String,
    mode: LyricsDisplayMode,
    onSelectMode: (LyricsDisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(32.dp)
            .clickable { onSelectMode(mode) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LyricsPromptOverlay(
    status: LyricsLoadStatus,
    onSearchClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (status) {
                LyricsLoadStatus.LOADING_LOCAL, LyricsLoadStatus.LOADING_NETWORK -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (status == LyricsLoadStatus.LOADING_LOCAL) {
                            stringResource(R.string.lyrics_loading)
                        } else {
                            stringResource(R.string.lyrics_searching_online)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (status == LyricsLoadStatus.IDLE) {
                            stringResource(R.string.lyrics_sheet_nothing_playing)
                        } else {
                            stringResource(R.string.lyrics_no_lyrics_title)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.lyrics_no_lyrics_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onSearchClick) {
                            Text(stringResource(R.string.lyrics_search_online))
                        }
                        OutlinedButton(onClick = onImportClick) {
                            Text(stringResource(R.string.lyrics_import_file))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsOffsetPill(
    offsetMs: Long,
    onChangeOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onChangeOffset(-500L) }, modifier = Modifier.size(36.dp)) {
                Text("-0.5s", style = MaterialTheme.typography.labelSmall)
            }

            val sign = if (offsetMs > 0) "+" else ""
            val sec = String.format(Locale.US, "%.1fs", offsetMs / 1000.0)
            val displayText = if (offsetMs == 0L) stringResource(R.string.lyrics_offset) else "$sign$sec"

            TextButton(onClick = onResetOffset) {
                Text(displayText, style = MaterialTheme.typography.labelMedium)
            }

            IconButton(onClick = { onChangeOffset(500L) }, modifier = Modifier.size(36.dp)) {
                Text("+0.5s", style = MaterialTheme.typography.labelSmall)
            }

            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun LyricsSearchDialog(
    initialTitle: String,
    initialArtist: String,
    onDismiss: () -> Unit,
    onSearch: (title: String, artist: String?) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_search)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.lyrics_search_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(stringResource(R.string.lyrics_search_artist_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onSearch(title.trim(), artist.trim().takeIf { it.isNotBlank() })
                    }
                }
            ) {
                Text(stringResource(R.string.search))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun LyricsCandidatesDialog(
    candidates: List<LyricsCandidate>,
    currentDurationSec: Int,
    onDismiss: () -> Unit,
    onSelect: (LyricsCandidate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(candidates) { candidate ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(candidate) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = candidate.trackName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val subtitle = listOfNotNull(
                            candidate.artistName?.takeIf { it.isNotBlank() },
                            candidate.albumName?.takeIf { it.isNotBlank() }
                        ).joinToString(" • ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val info = mutableListOf<String>()
                        info.add(candidate.providerName)
                        info.add(
                            when {
                                candidate.hasWordTimings -> stringResource(R.string.lyrics_badge_word_synced)
                                candidate.isSynced -> stringResource(R.string.lyrics_synced)
                                else -> stringResource(R.string.lyrics_plain)
                            }
                        )
                        if (candidate.durationSec > 0) {
                            var runtime = (candidate.durationSec * 1000L).toStringTimeStamp()
                            if (currentDurationSec > 0) {
                                val diff = candidate.durationSec - currentDurationSec
                                runtime += if (diff == 0) " (=)" else " (${if (diff > 0) "+" else ""}${diff}s)"
                            }
                            info.add(runtime)
                        }
                        Text(
                            text = info.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (candidate.preview.isNotBlank()) {
                            Text(
                                text = candidate.preview,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
