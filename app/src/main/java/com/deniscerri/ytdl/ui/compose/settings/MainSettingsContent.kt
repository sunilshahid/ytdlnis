package com.deniscerri.ytdl.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.deniscerri.ytdl.ui.compose.components.YtdlSwitch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.components.PreferenceCard
import com.deniscerri.ytdl.ui.compose.components.SectionHeader

/**
 * Stateless content for the root Settings screen.
 *
 * Renders Material You preference cards matching the More screen design.
 */
@Composable
fun MainSettingsContent(
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        PreferenceCard(
            title = stringResource(R.string.appearance),
            summary = state.appearanceSummary,
            iconRes = R.drawable.baseline_color_lens_24,
            showChevron = true,
            onClick = onAppearanceClick,
        )

        PreferenceCard(
            title = stringResource(R.string.general),
            summary = state.generalSummary,
            iconRes = R.drawable.ic_settings,
            showChevron = true,
            onClick = onGeneralClick,
        )

        PreferenceCard(
            title = stringResource(R.string.player),
            summary = state.playerSummary,
            iconRes = R.drawable.ic_music,
            showChevron = true,
            onClick = onPlayerClick,
        )

        PreferenceCard(
            title = stringResource(R.string.directories),
            summary = state.foldersSummary,
            iconRes = R.drawable.baseline_folder_24,
            showChevron = true,
            onClick = onFoldersClick,
        )

        PreferenceCard(
            title = stringResource(R.string.downloads),
            summary = state.downloadingSummary,
            iconRes = R.drawable.ic_downloads,
            showChevron = true,
            onClick = onDownloadingClick,
        )

        PreferenceCard(
            title = stringResource(R.string.processing),
            summary = state.processingSummary,
            iconRes = R.drawable.baseline_star_half_24,
            showChevron = true,
            onClick = onProcessingClick,
        )

        PreferenceCard(
            title = stringResource(R.string.updating),
            summary = state.updatingSummary,
            iconRes = R.drawable.ic_update,
            showChevron = true,
            onClick = onUpdatingClick,
        )

        PreferenceCard(
            title = stringResource(R.string.advanced),
            summary = state.advancedSummary,
            iconRes = R.drawable.baseline_settings_suggest_24,
            showChevron = true,
            onClick = onAdvancedClick,
        )

        // Backup & Restore
        SectionHeader(text = stringResource(R.string.backup_restore))

        PreferenceCard(
            title = stringResource(R.string.backup),
            iconRes = R.drawable.baseline_save_alt_24,
            showChevron = true,
            onClick = onBackupClick,
        )

        PreferenceCard(
            title = stringResource(R.string.restore),
            iconRes = R.drawable.baseline_restore_page_24,
            showChevron = true,
            onClick = onRestoreClick,
        )

        PreferenceCard(
            title = stringResource(R.string.automatic_backup),
            summary = stringResource(R.string.automatic_backup_summary),
            iconRes = R.drawable.ic_clock,
            trailing = {
                YtdlSwitch(
                    checked = state.automaticBackup,
                    onCheckedChange = null,
                )
            },
            onClick = { onAutomaticBackupToggle(!state.automaticBackup) },
        )

        PreferenceCard(
            title = stringResource(R.string.backup_path),
            summary = state.backupPath.ifBlank { null },
            iconRes = R.drawable.baseline_folder_24,
            showChevron = true,
            onClick = onBackupPathClick,
        )

        // About
        SectionHeader(text = stringResource(R.string.about))

        PreferenceCard(
            title = stringResource(R.string.package_name),
            summary = state.packageName,
            iconRes = R.drawable.baseline_exit_to_app_24,
            onClick = onPackageNameClick,
        )

        PreferenceCard(
            title = "Discord",
            summary = "https://discord.gg/WW3KYWxAPm",
            iconRes = R.drawable.ic_discord,
            showChevron = true,
            onClick = onDiscordClick,
        )

        PreferenceCard(
            title = "Telegram",
            summary = "https://t.me/ytdlnis",
            iconRes = R.drawable.ic_telegram,
            showChevron = true,
            onClick = onTelegramClick,
        )

        PreferenceCard(
            title = stringResource(R.string.translators),
            summary = stringResource(R.string.translators_summary),
            iconRes = R.drawable.ic_translate,
            showChevron = true,
            onClick = onTranslatorsClick,
        )
    }
}
