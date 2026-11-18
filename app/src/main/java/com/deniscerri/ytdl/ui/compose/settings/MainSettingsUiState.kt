package com.deniscerri.ytdl.ui.compose.settings

/**
 * UI state for the root Settings screen.
 */
data class MainSettingsUiState(
    val appearanceSummary: String = "",
    val generalSummary: String = "",
    val playerSummary: String = "",
    val foldersSummary: String = "",
    val downloadingSummary: String = "",
    val processingSummary: String = "",
    val updatingSummary: String = "",
    val advancedSummary: String = "",
    val automaticBackup: Boolean = true,
    val backupPath: String = "",
    val packageName: String = "",
)
