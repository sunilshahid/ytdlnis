package com.deniscerri.ytdl.ui.compose.welcome

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R

/**
 * Permission item model for the welcome permissions page.
 */
data class WelcomePermissionItem(
    val id: String,
    @param:DrawableRes val iconRes: Int,
    val title: String,
    val summary: String,
    val required: Boolean,
    val isGranted: Boolean,
    val onRequest: () -> Unit,
)

/**
 * Onboarding / Welcome screen (Phase 3).
 * Replaces `activity_welcome.xml`, `fragment_welcome_intro.xml`, `fragment_welcome_guide.xml`,
 * `fragment_welcome_permissions.xml`, `fragment_welcome_updates.xml`, and `item_welcome_permission.xml`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    currentPage: Int,
    totalPages: Int = 4,
    isRestoring: Boolean,
    onGetStarted: () -> Unit,
    onRestoreBackup: () -> Unit,
    onOpenGuide: () -> Unit,
    permissionItems: List<WelcomePermissionItem>,
    folderPermissionItems: List<WelcomePermissionItem>,
    isNextEnabled: Boolean,
    updateApp: Boolean,
    onUpdateAppChanged: (Boolean) -> Unit,
    autoUpdateYtdlp: Boolean,
    onAutoUpdateYtdlpChanged: (Boolean) -> Unit,
    selectedChannel: String,
    channelTitles: List<String>,
    channelValues: List<String>,
    onChannelSelected: (value: String, label: String) -> Unit,
    customChannelSummary: String,
    onCustomChannelClick: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            WelcomeBottomBar(
                currentPage = currentPage,
                totalPages = totalPages,
                isNextEnabled = isNextEnabled,
                onBack = onBack,
                onNext = onNext,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "WelcomePageTransition",
            ) { page ->
                when (page) {
                    0 -> WelcomeIntroPage(
                        isRestoring = isRestoring,
                        onGetStarted = onGetStarted,
                        onRestoreBackup = onRestoreBackup,
                    )
                    1 -> WelcomeGuidePage(
                        onOpenGuide = onOpenGuide,
                    )
                    2 -> WelcomePermissionsPage(
                        permissionItems = permissionItems,
                        folderPermissionItems = folderPermissionItems,
                    )
                    3 -> WelcomeUpdatesPage(
                        updateApp = updateApp,
                        onUpdateAppChanged = onUpdateAppChanged,
                        autoUpdateYtdlp = autoUpdateYtdlp,
                        onAutoUpdateYtdlpChanged = onAutoUpdateYtdlpChanged,
                        selectedChannel = selectedChannel,
                        channelTitles = channelTitles,
                        channelValues = channelValues,
                        onChannelSelected = onChannelSelected,
                        customChannelSummary = customChannelSummary,
                        onCustomChannelClick = onCustomChannelClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeBottomBar(
    currentPage: Int,
    totalPages: Int,
    isNextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (currentPage > 0) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
        } else {
            Spacer(Modifier.width(64.dp))
        }

        LinearProgressIndicator(
            progress = { (currentPage + 1f) / totalPages },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        if (currentPage > 0) {
            Button(
                onClick = onNext,
                enabled = isNextEnabled,
            ) {
                Text(
                    text = if (currentPage == totalPages - 1) {
                        stringResource(R.string.welcome_finish)
                    } else {
                        stringResource(R.string.welcome_next)
                    },
                )
            }
        } else {
            Spacer(Modifier.width(64.dp))
        }
    }
}

/** Page 0: App Introduction & Backup Restore */
@Composable
private fun WelcomeIntroPage(
    isRestoring: Boolean,
    onGetStarted: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_app_icon),
            contentDescription = null,
            modifier = Modifier.size(180.dp),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        if (isRestoring) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            )
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = onGetStarted,
            enabled = !isRestoring,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.welcome_get_started))
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onRestoreBackup,
            enabled = !isRestoring,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_restore),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.welcome_restore_backup))
        }
    }
}

/** Page 1: User Guide */
@Composable
private fun WelcomeGuidePage(
    onOpenGuide: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_help_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.welcome_guide_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.welcome_guide_summary),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        FilledTonalButton(onClick = onOpenGuide) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_outward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.welcome_open_guide))
        }
    }
}

/** Page 2: Permissions */
@Composable
private fun WelcomePermissionsPage(
    permissionItems: List<WelcomePermissionItem>,
    folderPermissionItems: List<WelcomePermissionItem>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.welcome_permissions_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.welcome_permissions_summary),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        permissionItems.forEach { item ->
            PermissionRow(item = item)
            Spacer(Modifier.height(8.dp))
        }

        if (folderPermissionItems.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.welcome_perm_folders_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.welcome_perm_folders_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            folderPermissionItems.forEach { item ->
                PermissionRow(item = item)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PermissionRow(item: WelcomePermissionItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                if (item.required && !item.isGranted) {
                    Text(
                        text = stringResource(R.string.welcome_required),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (item.summary.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            FilledTonalButton(
                onClick = item.onRequest,
                enabled = !item.isGranted,
            ) {
                Text(
                    text = if (item.isGranted) {
                        stringResource(R.string.welcome_allowed)
                    } else {
                        stringResource(R.string.welcome_allow)
                    },
                )
            }
        }
    }
}

/** Page 3: Update preferences & yt-dlp source */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeUpdatesPage(
    updateApp: Boolean,
    onUpdateAppChanged: (Boolean) -> Unit,
    autoUpdateYtdlp: Boolean,
    onAutoUpdateYtdlpChanged: (Boolean) -> Unit,
    selectedChannel: String,
    channelTitles: List<String>,
    channelValues: List<String>,
    onChannelSelected: (value: String, label: String) -> Unit,
    customChannelSummary: String,
    onCustomChannelClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.welcome_updates_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.welcome_updates_summary),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        // App update switch (GitHub flavor only)
        if (BuildConfig.FLAVOR == "github") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUpdateAppChanged(!updateApp) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_update_app),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_app),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.update_app_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Switch(
                        checked = updateApp,
                        onCheckedChange = onUpdateAppChanged,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // yt-dlp auto-update switch
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAutoUpdateYtdlpChanged(!autoUpdateYtdlp) },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_update),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_update_ytdlp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.auto_update_ytdlp_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Switch(
                    checked = autoUpdateYtdlp,
                    onCheckedChange = onAutoUpdateYtdlpChanged,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.update_channel),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(10.dp))

        // Channel Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            channelValues.forEachIndexed { index, value ->
                val label = channelTitles.getOrElse(index) { value }
                val isSelected = selectedChannel == value

                FilterChip(
                    selected = isSelected,
                    onClick = { onChannelSelected(value, label) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Custom channel row
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCustomChannelClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_source_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.custom_channels),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = customChannelSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
