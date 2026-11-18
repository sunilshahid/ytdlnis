package com.deniscerri.ytdl.ui.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.components.PreferenceCard

/**
 * Stateless content for the More screen.
 *
 * Renders the centered app logo at the top followed by the Material 3 preference action cards.
 */
@Composable
fun MoreContent(
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // App Logo (kept in its centered place)
        Box(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_app_icon),
                contentDescription = stringResource(R.string.app_name),
                colorFilter = if (state.appIconTintPrimary) {
                    ColorFilter.tint(MaterialTheme.colorScheme.primary)
                } else null,
            )
        }

        if (state.showTerminal) {
            PreferenceCard(
                title = stringResource(R.string.terminal),
                iconRes = R.drawable.ic_terminal,
                showChevron = true,
                onClick = onTerminalClick,
            )
        }

        PreferenceCard(
            title = stringResource(R.string.logs),
            iconRes = R.drawable.ic_baseline_file_open_24,
            showChevron = true,
            onClick = onLogsClick,
        )

        PreferenceCard(
            title = stringResource(R.string.command_templates),
            iconRes = R.drawable.ic_code,
            showChevron = true,
            onClick = onCommandTemplatesClick,
        )

        if (state.showDownloads) {
            PreferenceCard(
                title = stringResource(R.string.downloads),
                iconRes = R.drawable.ic_downloads,
                showChevron = true,
                onClick = onDownloadsClick,
            )
        }

        if (state.showDownloadQueue) {
            PreferenceCard(
                title = stringResource(R.string.download_queue),
                iconRes = R.drawable.baseline_downloading_24,
                showChevron = true,
                onClick = onDownloadQueueClick,
            )
        }

        PreferenceCard(
            title = stringResource(R.string.cookies),
            iconRes = R.drawable.baseline_cookie_24,
            showChevron = true,
            onClick = onCookiesClick,
        )

        PreferenceCard(
            title = stringResource(R.string.observe_sources),
            iconRes = R.drawable.baseline_calendar_month_24,
            showChevron = true,
            onClick = onObserveSourcesClick,
        )

        if (state.showPrivateVault) {
            PreferenceCard(
                title = stringResource(R.string.private_vault),
                iconRes = R.drawable.ic_lock,
                showChevron = true,
                onClick = onPrivateVaultClick,
            )
        }

        PreferenceCard(
            title = stringResource(R.string.settings),
            iconRes = R.drawable.ic_settings,
            showChevron = true,
            onClick = onSettingsClick,
        )

        PreferenceCard(
            title = stringResource(R.string.kill_app),
            iconRes = R.drawable.ic_power,
            badgeColor = MaterialTheme.colorScheme.errorContainer,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
            showChevron = false,
            onClick = onTerminateClick,
            onLongClick = onTerminateLongClick,
        )
    }
}
