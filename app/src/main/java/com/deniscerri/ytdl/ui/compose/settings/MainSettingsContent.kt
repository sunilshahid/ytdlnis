package com.deniscerri.ytdl.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.components.PreferenceCard
import com.deniscerri.ytdl.ui.compose.components.SectionHeader
import com.deniscerri.ytdl.ui.compose.components.YtdlSwitch
import com.deniscerri.ytdl.ui.compose.settings.bento.BentoCardSpec
import com.deniscerri.ytdl.ui.compose.settings.bento.BentoGrid
import com.deniscerri.ytdl.ui.compose.settings.bento.BentoReorderSheet
import com.deniscerri.ytdl.ui.compose.settings.bento.orderedCards

/**
 * Root Settings screen rendered as an interactive Material 3 Bento Grid.
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
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var showReorderSheet by remember { mutableStateOf(false) }

    val allCardSpecs = remember(state) {
        listOf(
            BentoCardSpec(
                id = "downloads",
                title = context.getString(R.string.downloads),
                subtitle = state.downloadingSummary.ifBlank { null },
                iconRes = R.drawable.ic_downloads,
                hue = 250f,
                fullWidth = true,
                minHeight = 175,
            ),
            BentoCardSpec(
                id = "look_and_feel",
                title = context.getString(R.string.look_and_feel),
                subtitle = context.getString(R.string.look_and_feel_summary),
                iconRes = R.drawable.baseline_color_lens_24,
                hue = 295f,
                fullWidth = false,
                minHeight = 210,
            ),
            BentoCardSpec(
                id = "player",
                title = context.getString(R.string.player),
                subtitle = state.playerSummary.ifBlank { null },
                iconRes = R.drawable.ic_music,
                hue = 190f,
                fullWidth = false,
                minHeight = 150,
            ),
            BentoCardSpec(
                id = "folders",
                title = context.getString(R.string.directories),
                subtitle = state.foldersSummary.ifBlank { null },
                iconRes = R.drawable.baseline_folder_24,
                hue = 55f,
                fullWidth = false,
                minHeight = 150,
            ),
            BentoCardSpec(
                id = "general",
                title = context.getString(R.string.general),
                subtitle = state.generalSummary.ifBlank { null },
                iconRes = R.drawable.ic_settings,
                hue = 145f,
                fullWidth = false,
                minHeight = 150,
            ),
            BentoCardSpec(
                id = "processing",
                title = context.getString(R.string.processing),
                subtitle = state.processingSummary.ifBlank { null },
                iconRes = R.drawable.baseline_star_half_24,
                hue = 20f,
                fullWidth = false,
                minHeight = 150,
            ),
            BentoCardSpec(
                id = "updating",
                title = context.getString(R.string.updating),
                subtitle = state.updatingSummary.ifBlank { null },
                iconRes = R.drawable.ic_update,
                hue = 320f,
                fullWidth = false,
                minHeight = 150,
            ),
            BentoCardSpec(
                id = "advanced",
                title = context.getString(R.string.advanced),
                subtitle = state.advancedSummary.ifBlank { null },
                iconRes = R.drawable.baseline_settings_suggest_24,
                hue = 220f,
                fullWidth = false,
                minHeight = 150,
            ),
        )
    }

    val savedOrder = prefs.getString("settings_bento_order", null)
    var currentCards by remember(allCardSpecs) {
        mutableStateOf(orderedCards(allCardSpecs, savedOrder))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        BentoGrid(
            cards = currentCards,
            onCardClick = { card ->
                when (card.id) {
                    "look_and_feel" -> onAppearanceClick()
                    "downloads" -> onDownloadingClick()
                    "player" -> onPlayerClick()
                    "folders" -> onFoldersClick()
                    "general" -> onGeneralClick()
                    "processing" -> onProcessingClick()
                    "updating" -> onUpdatingClick()
                    "advanced" -> onAdvancedClick()
                }
            },
            onCardLongClick = {
                showReorderSheet = true
            },
            header = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.drag_to_reorder),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            },
            footer = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Spacer(Modifier.height(16.dp))

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
        )
    }

    if (showReorderSheet) {
        BentoReorderSheet(
            cards = currentCards,
            onMove = { fromIndex, toIndex ->
                if (fromIndex in currentCards.indices && toIndex in currentCards.indices) {
                    val updated = currentCards.toMutableList().apply {
                        add(toIndex, removeAt(fromIndex))
                    }
                    currentCards = updated
                    prefs.edit().putString(
                        "settings_bento_order",
                        updated.joinToString(",") { it.id }
                    ).apply()
                }
            },
            onDismiss = { showReorderSheet = false }
        )
    }
}
