package com.deniscerri.ytdl.ui.more

/**
 * UI State for the More screen.
 */
data class MoreUiState(
    val showTerminal: Boolean = true,
    val showDownloads: Boolean = true,
    val showDownloadQueue: Boolean = true,
    val showPrivateVault: Boolean = true,
    val appIconTintPrimary: Boolean = false,
)
