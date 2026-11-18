package com.deniscerri.ytdl.ui.compose.play.video

import androidx.annotation.DrawableRes
import com.deniscerri.ytdl.R

/**
 * UI state for the video player screen overlays (poster, loading, volume/brightness HUD,
 * speed boost selector, locked state, gesture seek preview, and playback error).
 */
data class VideoPlayerUiState(
    val artworkUri: String? = null,
    val showPoster: Boolean = false,
    val isLoading: Boolean = false,
    val showLoadingText: Boolean = false,
    val levelVisible: Boolean = false,
    @DrawableRes val levelIconRes: Int? = R.drawable.ic_volume_high,
    val levelText: String = "100",
    val levelProgress: Int? = 50,
    val levelMaxProgress: Int = 100,
    val speedBoostVisible: Boolean = false,
    val showBoostTicks: Boolean = true,
    val currentBoostIndex: Int = 4, // 2.0x default
    val speedBoostLabel: String = "2.0x Speed Playing",
    val isLocked: Boolean = false,
    val seekPreviewText: String? = null,
    val errorMessage: String? = null,
)
