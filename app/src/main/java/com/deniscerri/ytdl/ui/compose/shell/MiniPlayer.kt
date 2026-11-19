package com.deniscerri.ytdl.ui.compose.shell

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.playback.PlaybackLauncher
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackSource
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.ui.compose.components.PicassoImage
import com.deniscerri.ytdl.util.PlaybackColors

/**
 * Phase 10: Jetpack Compose Mini-Player.
 *
 * Replaces the legacy `mini_player.xml` with an expressive Material 3 card:
 * - Dynamic artwork glow color derivation blended with surface background.
 * - Reactive state collection from [PlaybackController.state].
 * - Play/Pause, Next, and Dismiss/Close controls.
 * - Smooth vertical slide-in and slide-out transitions.
 * - Click anywhere on the card launches the full AudioPlayer via [PlaybackLauncher].
 */
@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    allowed: Boolean = true,
    onOpenPlayer: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uiState by PlaybackController.state.collectAsState()
    val item = uiState.currentItem

    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val isDismissed = prefs.getBoolean("playback_dismissed", false)
    val visible = item != null && allowed && (!isDismissed || uiState.playing)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        if (item != null) {
            MiniPlayerCard(
                item = item,
                uiState = uiState,
                context = context,
                onOpenPlayer = onOpenPlayer ?: { PlaybackLauncher.openPlayer(context) }
            )
        }
    }
}

@Composable
private fun MiniPlayerCard(
    item: PlaybackItem,
    uiState: PlaybackController.UiState,
    context: Context,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val glowColorInt by produceState<Int?>(initialValue = null, key1 = item.mediaId) {
        value = PlaybackColors.artworkGlowColor(context, item)
    }

    val cardBg = if (glowColorInt != null) {
        val blended = ColorUtils.blendARGB(glowColorInt!!, surfaceColor.toArgb(), 0.72f)
        Color(blended)
    } else {
        surfaceColor
    }

    val playBg = if (glowColorInt != null) {
        val blended = ColorUtils.blendARGB(glowColorInt!!, android.graphics.Color.WHITE, 0.45f)
        Color(blended)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val onPlayIcon = if (glowColorInt != null) {
        val playBgInt = ColorUtils.blendARGB(glowColorInt!!, android.graphics.Color.WHITE, 0.45f)
        if (ColorUtils.calculateLuminance(playBgInt) > 0.45) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    val iconTint = if (glowColorInt != null) {
        val blended = ColorUtils.blendARGB(glowColorInt!!, android.graphics.Color.WHITE, 0.75f)
        Color(blended)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val source = when (item.source) {
        PlaybackSource.YOUTUBE -> stringResource(R.string.youtube)
        PlaybackSource.DOWNLOAD -> stringResource(R.string.downloads)
        PlaybackSource.LOCAL -> null
    }
    val type = stringResource(
        if (item.type == PlaybackType.VIDEO) R.string.video else R.string.audio
    )
    val subtitle = listOfNotNull(
        item.artist?.takeIf { it.isNotBlank() },
        source,
        type
    ).joinToString(" · ")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onOpenPlayer),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Square-rounded Artwork
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    PicassoImage(
                        url = item.artworkUri,
                        contentDescription = stringResource(R.string.now_playing),
                        placeholderRes = R.drawable.ic_music,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Subtitle metadata
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Expressive Play/Pause pill button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(playBg)
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
                            if (uiState.playing) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play
                        ),
                        contentDescription = stringResource(if (uiState.playing) R.string.pause else R.string.play),
                        tint = onPlayIcon,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Next Track Button
                IconButton(
                    onClick = { PlaybackController.next() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lyrics_forward),
                        contentDescription = stringResource(R.string.next),
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Close / Dismiss Button
                IconButton(
                    onClick = { PlaybackController.stop(dismissed = true) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cancel),
                        contentDescription = stringResource(R.string.close),
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Subtle progress indicator along bottom of card
            if (uiState.durationMs > 0L) {
                val progress = (uiState.positionMs.toFloat() / uiState.durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = playBg,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
