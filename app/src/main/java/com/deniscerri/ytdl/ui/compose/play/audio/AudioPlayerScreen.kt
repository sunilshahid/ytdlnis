package com.deniscerri.ytdl.ui.compose.play.audio

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.LikedStore
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.ui.compose.components.PicassoImage
import com.deniscerri.ytdl.ui.compose.play.PlayTrackRow
import com.deniscerri.ytdl.ui.play.GlowView
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.deniscerri.ytdl.util.PlaybackColors
import kotlin.math.abs

/**
 * Jetpack Compose Audio Player Chrome (Phase 6b).
 *
 * Implements:
 * - Material 3 Expressive playback screen
 * - Artwork Glow interop with GlowView
 * - Swipe-to-skip gestures on album art
 * - Wavy / Seek Slider with remaining vs total time toggle
 * - Shuffle, Previous, Play/Pause, Next, Repeat
 * - Speed picker, Lyrics button, Queue Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    onBackClick: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by PlaybackController.state.collectAsState()
    val item = state.currentItem

    val context = LocalContext.current
    var isLiked by remember(item?.mediaId) {
        mutableStateOf(item?.let { LikedStore.isLiked(it.mediaId) } ?: false)
    }

    var showRemainingTime by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var dominantColor by remember { mutableStateOf<Int?>(null) }

    // Swipe gesture state
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "artDragOffset"
    )

    // Scrub state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }

    val duration = state.durationMs.coerceAtLeast(0L)
    val position = if (isScrubbing) {
        (scrubProgress * duration).toLong()
    } else {
        state.positionMs.coerceAtLeast(0L)
    }

    val progressFraction = if (duration > 0) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chapter_list),
                        contentDescription = stringResource(R.string.queue),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Center Artwork with GlowView behind it
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                // Glow View interop
                AndroidView(
                    factory = { ctx ->
                        GlowView(ctx).apply {
                            setPulsing(state.playing)
                            setBeatRate(if (state.playing) 1.8f else 0f)
                            setColor(dominantColor)
                        }
                    },
                    update = { view ->
                        view.setPulsing(state.playing)
                        view.setBeatRate(if (state.playing) 1.8f else 0f)
                        view.setColor(dominantColor)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Artwork Card with Swipe-to-skip gestures
                Card(
                    modifier = Modifier
                        .fillMaxSize(0.85f)
                        .graphicsLayer {
                            translationX = animatedOffsetX
                            rotationZ = (animatedOffsetX / 300f) * 8f
                            alpha = (1f - (abs(animatedOffsetX) / 600f)).coerceIn(0.5f, 1f)
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragOffsetX > 140f) {
                                        PlaybackController.skipToPreviousAudio()
                                    } else if (dragOffsetX < -140f) {
                                        PlaybackController.skipToNextAudio()
                                    }
                                    dragOffsetX = 0f
                                },
                                onDragCancel = { dragOffsetX = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragOffsetX += dragAmount * 0.8f
                                }
                            )
                        },
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (item?.artworkUri != null) {
                            PicassoImage(
                                url = item.artworkUri,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                placeholderRes = R.drawable.ic_music_downloaded
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_music_downloaded),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(72.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Metadata Row: Title + Artist + Like Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item?.title.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item?.artist ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        item?.let {
                            LikedStore.toggle(it)
                            isLiked = LikedStore.isLiked(it.mediaId)
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                        contentDescription = stringResource(R.string.liked),
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Seekbar / Slider Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (isScrubbing) scrubProgress else progressFraction,
                    onValueChange = { fraction ->
                        isScrubbing = true
                        scrubProgress = fraction
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        val targetMs = (scrubProgress * duration).toLong()
                        PlaybackController.seekTo(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = position.toStringTimeStamp(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = if (showRemainingTime && duration > position) {
                            "-${(duration - position).toStringTimeStamp()}"
                        } else {
                            duration.toStringTimeStamp()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Main Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = { PlaybackController.toggleShuffle() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shuffle),
                        contentDescription = stringResource(R.string.shuffle),
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Previous
                IconButton(
                    onClick = { PlaybackController.previous() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lyrics_rewind),
                        contentDescription = stringResource(R.string.previous),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play / Pause FAB
                FilledIconButton(
                    onClick = {
                        val player = PlaybackController.player()
                        if (player == null || player.mediaItemCount == 0) {
                            PlaybackController.resume()
                        } else {
                            PlaybackController.togglePlayPause()
                        }
                    },
                    modifier = Modifier.size(68.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        painter = painterResource(if (state.playing) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play),
                        contentDescription = stringResource(if (state.playing) R.string.pause else R.string.play),
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = { PlaybackController.next() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lyrics_forward),
                        contentDescription = stringResource(R.string.next),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Repeat
                IconButton(onClick = { PlaybackController.cycleRepeatMode() }) {
                    Icon(
                        painter = painterResource(
                            when (state.repeatMode) {
                                androidx.media3.common.Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                                androidx.media3.common.Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
                                else -> R.drawable.ic_repeat
                            }
                        ),
                        contentDescription = stringResource(R.string.repeat),
                        tint = if (state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Bottom row: Speed, Lyrics, Queue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                        val nextIdx = ((speeds.indexOf(state.playbackSpeed) + 1) % speeds.size).coerceAtLeast(0)
                        PlaybackController.setPlaybackSpeed(speeds[nextIdx])
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("${state.playbackSpeed}x", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onOpenLyrics,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(painter = painterResource(R.drawable.ic_lyrics), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.lyrics))
                }

                FilledTonalButton(
                    onClick = { showQueueSheet = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(painter = painterResource(R.drawable.ic_chapter_list), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${state.queue.size}")
                }
            }
        }

        // Queue Modal Bottom Sheet
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.queue)} (${state.queue.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = stringResource(R.string.clear_queue),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable {
                                PlaybackController.clearQueue()
                                showQueueSheet = false
                            }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        itemsIndexed(state.queue) { index, queueItem ->
                            val isCurrent = index == state.currentIndex
                            PlayTrackRow(
                                title = queueItem.title,
                                subtitle = queueItem.artist,
                                artworkUri = queueItem.artworkUri,
                                durationText = queueItem.durationMs?.toStringTimeStamp(),
                                isPlaying = isCurrent && state.playing,
                                onClick = {
                                    PlaybackController.playAt(index)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
