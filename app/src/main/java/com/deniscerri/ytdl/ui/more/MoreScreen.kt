package com.deniscerri.ytdl.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Screen composable for the More tab.
 *
 * Delegates state and event handlers down to [MoreContent].
 */
@Composable
fun MoreScreen(
    state: MoreUiState,
    modifier: Modifier = Modifier,
    onTerminalClick: () -> Unit = {},
    onLogsClick: () -> Unit = {},
    onCommandTemplatesClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onDownloadQueueClick: () -> Unit = {},
    onCookiesClick: () -> Unit = {},
    onObserveSourcesClick: () -> Unit = {},
    onPrivateVaultClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onTerminateClick: () -> Unit = {},
    onTerminateLongClick: () -> Unit = {},
) {
    MoreContent(
        state = state,
        modifier = modifier,
        onTerminalClick = onTerminalClick,
        onLogsClick = onLogsClick,
        onCommandTemplatesClick = onCommandTemplatesClick,
        onDownloadsClick = onDownloadsClick,
        onDownloadQueueClick = onDownloadQueueClick,
        onCookiesClick = onCookiesClick,
        onObserveSourcesClick = onObserveSourcesClick,
        onPrivateVaultClick = onPrivateVaultClick,
        onSettingsClick = onSettingsClick,
        onTerminateClick = onTerminateClick,
        onTerminateLongClick = onTerminateLongClick,
    )
}
