package com.deniscerri.ytdl.ui.compose.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Screen composable for the root Settings screen.
 *
 * Delegates state and event handlers down to [MainSettingsContent].
 */
@Composable
fun MainSettingsScreen(
    state: MainSettingsUiState,
    modifier: Modifier = Modifier,
    onAppearanceClick: () -> Unit = {},
    onGeneralClick: () -> Unit = {},
    onPlayerClick: () -> Unit = {},
    onFoldersClick: () -> Unit = {},
    onDownloadingClick: () -> Unit = {},
    onProcessingClick: () -> Unit = {},
    onUpdatingClick: () -> Unit = {},
    onAdvancedClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onRestoreClick: () -> Unit = {},
    onAutomaticBackupToggle: (Boolean) -> Unit = {},
    onBackupPathClick: () -> Unit = {},
    onPackageNameClick: () -> Unit = {},
    onDiscordClick: () -> Unit = {},
    onTelegramClick: () -> Unit = {},
    onTranslatorsClick: () -> Unit = {},
) {
    MainSettingsContent(
        state = state,
        modifier = modifier,
        onAppearanceClick = onAppearanceClick,
        onGeneralClick = onGeneralClick,
        onPlayerClick = onPlayerClick,
        onFoldersClick = onFoldersClick,
        onDownloadingClick = onDownloadingClick,
        onProcessingClick = onProcessingClick,
        onUpdatingClick = onUpdatingClick,
        onAdvancedClick = onAdvancedClick,
        onBackupClick = onBackupClick,
        onRestoreClick = onRestoreClick,
        onAutomaticBackupToggle = onAutomaticBackupToggle,
        onBackupPathClick = onBackupPathClick,
        onPackageNameClick = onPackageNameClick,
        onDiscordClick = onDiscordClick,
        onTelegramClick = onTelegramClick,
        onTranslatorsClick = onTranslatorsClick,
    )
}
