package com.deniscerri.ytdl.ui.compose.play.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.components.PicassoImage
import com.deniscerri.ytdl.ui.compose.host.PlayerViewHost

private val BOOST_SPEEDS = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)

/**
 * Screen composable for fullscreen video playback.
 *
 * Hosts [PlayerViewHost] (which embeds media3 PlayerView with exo_player_control_view.xml)
 * and renders Material 3 overlays for poster, loading, volume/brightness HUD, speed boost,
 * gesture seek hints, locked mode, and playback errors.
 */
@Composable
fun VideoPlayerScreen(
    state: VideoPlayerUiState,
    player: Player? = null,
    modifier: Modifier = Modifier,
    onPlayerReady: (PlayerView) -> Unit = {},
    onPlayerRelease: (PlayerView) -> Unit = {},
    onRetryClick: () -> Unit = {},
    onUnlockClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Player surface + media3 controller (owns scrubber, chapter ticks, haptics)
        PlayerViewHost(
            player = player,
            modifier = Modifier.fillMaxSize(),
            onReady = onPlayerReady,
            onRelease = onPlayerRelease,
        )

        // Poster overlay (sits above surface until first decoded frame)
        if (state.showPoster && !state.artworkUri.isNullOrBlank()) {
            PicassoImage(
                url = state.artworkUri,
                contentDescription = stringResource(R.string.now_playing),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.38f),
            )
        }

        // Loading indicator overlay
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .wrapContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    trackColor = Color(0x33FFFFFF),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp),
                )
                if (state.showLoadingText) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.preparing_video),
                        color = Color.White,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        // Volume & Brightness HUD at top-center
        if (state.levelVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xCC1A1A1A))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.levelIconRes != null) {
                    Icon(
                        painter = painterResource(state.levelIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White,
                    )
                }
                if (state.levelProgress != null) {
                    Spacer(Modifier.width(14.dp))
                    LinearProgressIndicator(
                        progress = {
                            (state.levelProgress.toFloat() / state.levelMaxProgress.coerceAtLeast(1))
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .width(130.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF2196F3),
                        trackColor = Color(0x4DFFFFFF),
                    )
                    Spacer(Modifier.width(14.dp))
                } else {
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = state.levelText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.defaultMinSize(minWidth = 34.dp),
                )
            }
        }

        // Tap-and-hold speed boost overlay at top-center
        if (state.speedBoostVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xD91E1E1E))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.showBoostTicks) {
                    Row(
                        modifier = Modifier.padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BOOST_SPEEDS.forEachIndexed { index, spd ->
                            val isSelected = index == state.currentBoostIndex
                            Text(
                                text = "${spd}x",
                                color = if (isSelected) Color(0xFF2196F3) else Color(0xB3FFFFFF),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_speed_play),
                        contentDescription = stringResource(R.string.speed),
                        modifier = Modifier.size(16.dp),
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = state.speedBoostLabel,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Floating unlock button when screen is locked
        if (state.isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 20.dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC1A1A1A))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .clickable { onUnlockClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = stringResource(R.string.unlock_controls),
                    modifier = Modifier.size(24.dp),
                    tint = Color.White,
                )
            }
        }

        // Gesture seek preview overlay
        if (state.seekPreviewText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xD91E1E1E))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = state.seekPreviewText,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Playback Error Card
        if (state.errorMessage != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF0202020),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_error_24),
                        contentDescription = stringResource(R.string.playback_error),
                        modifier = Modifier.size(38.dp),
                        tint = Color(0xFFFFB4AB),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.playback_error),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.errorMessage,
                        color = Color(0xB3FFFFFF),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onRetryClick,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.defaultMinSize(minWidth = 132.dp),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}
