@file:Suppress("DEPRECATION")

package com.deniscerri.ytdl.ui.compose.play.audio

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.media.audiofx.AudioEffect
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.LikedStore
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.ui.compose.components.PicassoImage
import com.deniscerri.ytdl.ui.compose.play.PlayTrackRow
import com.deniscerri.ytdl.ui.play.GlowView
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.deniscerri.ytdl.util.PlaybackColors
import kotlin.math.abs

/**
 * Material 3 Expressive Audio Player Screen.
 *
 * Implements:
 * - Header: Collapse down-chevron pill [ v ] + Track Title & Artist + Queue button
 * - Center: Rounded album artwork with ambient glow & swipe-to-skip gesture
 * - Scrubber: Elapsed time pill [ 0:03 ] + thick M3 wavy seek bar + Total time pill [ 4:12 ]
 * - Actions: Expressive pill chips [ ♡ Like ] [ Audio ] [ ≡♫ Lyrics ]
 * - Controls: Expressive squircle containers [ ⤨ ] [ ◀◀ ] [ || (Hero tall pill) ] [ ▶▶ ] [ 🔁 ]
 * - Speed indicator: [ 1x ] clickable to cycle speeds
 * - Interactive Audio Details & Queue modal bottom sheets
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    var showAudioInfoSheet by remember { mutableStateOf(false) }
    var dominantColor by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(item?.mediaId) {
        dominantColor = PlaybackColors.artworkGlowColor(context, item)
    }

    // Material 3 Expressive Accent Container Color (Pastel Lavender from screenshot or dynamic artwork tint)
    val accentContainerColor = remember(dominantColor) {
        if (dominantColor != null) {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(dominantColor!!, hsv)
            hsv[1] = (hsv[1] * 0.45f).coerceIn(0.20f, 0.45f)
            hsv[2] = (hsv[2] * 1.15f).coerceIn(0.70f, 0.85f)
            Color(AndroidColor.HSVToColor(hsv))
        } else {
            Color(0xFFA597C3) // Expressive pastel lavender from screenshot
        }
    }

    val controlButtonBg = Color(0xFF292533) // Muted container for secondary buttons & action pills
    val heroContentColor = Color(0xFF1E1A29) // High-contrast dark charcoal for play/pause icon
    val screenBg = Color(0xFF141218) // Expressive dark background

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

    // Thick rounded stroke shared by the wavy track and its unfilled guide line.
    val density = LocalDensity.current
    val waveStroke = remember(density) {
        Stroke(width = with(density) { 8.dp.toPx() }, cap = StrokeCap.Round)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        // Atmospheric ambient vignette glow at top of screen
        val ambientColor = remember(dominantColor) {
            dominantColor?.let { Color(it).copy(alpha = 0.35f) } ?: Color(0xFF382E50).copy(alpha = 0.45f)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ambientColor, Color.Transparent),
                        startY = 0f,
                        endY = 1000f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar: Collapse Button [ v ] + Track Title & Artist + Queue Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Collapse button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(controlButtonBg)
                        .clickable(onClick = onBackClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_expand_more_24),
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                // Track Title and Artist
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item?.title.orEmpty().ifEmpty { stringResource(R.string.now_playing) },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item?.artist ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        color = Color(0xFFCAC4D0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. Center Artwork with GlowView behind it
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .aspectRatio(1f)
                    .padding(vertical = 12.dp),
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
                        .fillMaxSize(0.92f)
                        .graphicsLayer {
                            translationX = animatedOffsetX
                            rotationZ = (animatedOffsetX / 300f) * 8f
                            alpha = (1f - (abs(animatedOffsetX) / 600f)).coerceIn(0.5f, 1f)
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragOffsetX > 140f) {
                                        // Swipe left-to-right: forward / next
                                        PlaybackController.next()
                                    } else if (dragOffsetX < -140f) {
                                        // Swipe right-to-left: previous
                                        PlaybackController.previous()
                                    }
                                    dragOffsetX = 0f
                                },
                                onDragCancel = { dragOffsetX = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragOffsetX += dragAmount * 0.8f
                                }
                            )
                        },
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val artUrl = item?.artworkUri?.takeIf { it.isNotBlank() } ?: item?.localUri
                        if (artUrl != null) {
                            PicassoImage(
                                url = artUrl,
                                contentDescription = item?.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                placeholderRes = R.drawable.ic_music_downloaded,
                                fullQuality = true
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF23202C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_music_downloaded),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(72.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 3. Scrubber / Progress Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Elapsed time pill [ 0:03 ]
                Surface(
                    shape = CircleShape,
                    color = accentContainerColor,
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = position.toStringTimeStamp(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                // Wavy seek bar: LinearWavyProgressIndicator renders the Material 3 Expressive wavy
                // track (flattened when paused, flowing when playing) while the Slider handles scrubbing.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LinearWavyProgressIndicator(
                        progress = { if (isScrubbing) scrubProgress else progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        color = accentContainerColor,
                        trackColor = Color(0xFF383445),
                        stroke = waveStroke,
                        trackStroke = waveStroke,
                        amplitude = { _ -> if (state.playing) 1f else 0f },
                    )

                    Slider(
                        value = if (isScrubbing) scrubProgress else progressFraction,
                        onValueChange = { fraction: Float ->
                            isScrubbing = true
                            scrubProgress = fraction
                        },
                        onValueChangeFinished = {
                            isScrubbing = false
                            val targetMs = (scrubProgress * duration).toLong()
                            PlaybackController.seekTo(targetMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = accentContainerColor,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Total or Remaining time pill [ 4:12 ]
                Surface(
                    shape = CircleShape,
                    color = accentContainerColor,
                    shadowElevation = 2.dp,
                    modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                ) {
                    Text(
                        text = if (showRemainingTime && duration > position) {
                            "-${(duration - position).toStringTimeStamp()}"
                        } else {
                            duration.toStringTimeStamp()
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 4. Action Row: [ Like ] [ Audio ] [ Lyrics ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Pill
                Surface(
                    shape = CircleShape,
                    color = controlButtonBg,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            item?.let {
                                LikedStore.toggle(it)
                                isLiked = LikedStore.isLiked(it.mediaId)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                            contentDescription = stringResource(R.string.liked),
                            tint = if (isLiked) Color(0xFFFF5252) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(if (isLiked) R.string.liked else R.string.like),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                // Audio Pill
                Surface(
                    shape = CircleShape,
                    color = controlButtonBg,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showAudioInfoSheet = true }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.audio),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                // Lyrics Pill
                Surface(
                    shape = CircleShape,
                    color = controlButtonBg,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenLyrics)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lyrics),
                            contentDescription = stringResource(R.string.lyrics),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.lyrics),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 5. Main Playback Controls: [ Shuffle ] [ Prev ] [ Play/Pause Hero ] [ Next ] [ Repeat ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(controlButtonBg)
                        .clickable { PlaybackController.toggleShuffle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shuffle),
                        contentDescription = stringResource(R.string.shuffle),
                        tint = if (state.shuffle) Color(0xFFD0BCFF) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(controlButtonBg)
                        .clickable { PlaybackController.previous() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lyrics_rewind),
                        contentDescription = stringResource(R.string.previous),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Hero Play / Pause
                Box(
                    modifier = Modifier
                        .size(width = 78.dp, height = 88.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(accentContainerColor)
                        .clickable {
                            val player = PlaybackController.player()
                            if (player == null || player.mediaItemCount == 0) {
                                PlaybackController.resume()
                            } else {
                                PlaybackController.togglePlayPause()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.playing) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play
                        ),
                        contentDescription = stringResource(if (state.playing) R.string.pause else R.string.play),
                        tint = heroContentColor,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Next
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(controlButtonBg)
                        .clickable { PlaybackController.next() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lyrics_forward),
                        contentDescription = stringResource(R.string.next),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Repeat
                val repeatMode = state.repeatMode
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(controlButtonBg)
                        .clickable { PlaybackController.cycleRepeatMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                                Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
                                else -> R.drawable.ic_repeat
                            }
                        ),
                        contentDescription = stringResource(R.string.repeat),
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) Color(0xFFD0BCFF) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 6. Speed Selector [ 1x ]
            val currentSpeed = state.playbackSpeed
            val speedDisplay = if (currentSpeed % 1.0f == 0f) "${currentSpeed.toInt()}x" else "${currentSpeed}x"
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                        val nextIdx = ((speeds.indexOf(state.playbackSpeed) + 1) % speeds.size).coerceAtLeast(0)
                        PlaybackController.setPlaybackSpeed(speeds[nextIdx])
                    }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = speedDisplay,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = Color(0xFFCAC4D0)
                )
            }
        }

        // 7. Audio Details Bottom Sheet
        if (showAudioInfoSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAudioInfoSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.audio),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = item?.title.orEmpty().ifEmpty { stringResource(R.string.now_playing) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item?.artist ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    item?.durationMs?.let {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Duration",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                it.toStringTimeStamp(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    item?.album?.takeIf { it.isNotBlank() }?.let {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Album",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                    putExtra(
                                        AudioEffect.EXTRA_AUDIO_SESSION,
                                        PlaybackController.player()?.audioSessionId ?: 0
                                    )
                                }
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                }
                                showAudioInfoSheet = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Equalizer")
                        }

                        FilledTonalButton(
                            onClick = {
                                showAudioInfoSheet = false
                                showQueueSheet = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(R.string.queue))
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // 8. Queue Modal Bottom Sheet
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
