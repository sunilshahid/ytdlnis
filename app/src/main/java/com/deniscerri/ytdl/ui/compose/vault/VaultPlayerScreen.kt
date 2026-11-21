package com.deniscerri.ytdl.ui.compose.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.host.PlayerViewHost

data class VaultPlayerUiState(
    val isLoading: Boolean = true,
    val isBlackout: Boolean = false,
    val isLocked: Boolean = false,
    val levelVisible: Boolean = false,
    val levelIconRes: Int? = R.drawable.ic_volume_up,
    val levelProgress: Int? = 50,
    val levelMaxProgress: Int = 100,
    val levelText: String = "100",
    val speedBoostVisible: Boolean = false,
    val showBoostTicks: Boolean = true,
    val currentBoostIndex: Int = 4, // 2.0x
    val speedBoostLabel: String = "2.0x Speed Playing",
    val boostSpeeds: List<Float> = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
)

/**
 * Compose screen for Vault Video Player (eleXML §5 D4).
 * Replaces activity_vault_player.xml while preserving 1:1 overlays and blackout veil.
 */
@Composable
fun VaultPlayerScreen(
    state: VaultPlayerUiState,
    player: Player? = null,
    modifier: Modifier = Modifier,
    onPlayerReady: (PlayerView) -> Unit = {},
    onPlayerRelease: (PlayerView) -> Unit = {},
    onUnlockClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Player Surface
        PlayerViewHost(
            player = player,
            modifier = Modifier.fillMaxSize(),
            onReady = onPlayerReady,
            onRelease = onPlayerRelease,
        )

        // Preparing video loading spinner
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
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.preparing_video),
                    color = Color.White,
                    fontSize = 15.sp,
                )
            }
        }

        // Level Indicator Bar (Volume, Brightness, Seek, Feedback)
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
                state.levelIconRes?.let { iconRes ->
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White,
                    )
                }
                state.levelProgress?.let { progress ->
                    Spacer(Modifier.width(14.dp))
                    LinearProgressIndicator(
                        progress = {
                            (progress.toFloat() / state.levelMaxProgress.coerceAtLeast(1))
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
                } ?: Spacer(Modifier.width(10.dp))
                Text(
                    text = state.levelText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.defaultMinSize(minWidth = 34.dp),
                )
            }
        }

        // Tap-and-Hold Speed Boost Bar
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
                        state.boostSpeeds.forEachIndexed { index, spd ->
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

        // Floating Unlock Button
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

        // Top-most pure black blackout veil for instant panic blanking
        if (state.isBlackout) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            )
        }
    }
}
