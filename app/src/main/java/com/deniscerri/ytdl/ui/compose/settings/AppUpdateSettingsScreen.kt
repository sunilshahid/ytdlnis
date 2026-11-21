package com.deniscerri.ytdl.ui.compose.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import java.io.File

@Composable
fun AppUpdateSettingsScreen(
    lastCheckText: String,
    isCheckingUpdate: Boolean,
    onCheckForUpdates: () -> Unit,
    onNavigateChangelog: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val canUpdateApp = BuildConfig.FLAVOR == "github"

    val nativeArch = File(context.applicationInfo.nativeLibraryDir).name
    var isBeta by remember { mutableStateOf(prefs.getBoolean("update_beta", false)) }
    val channelLabel = if (isBeta) stringResource(R.string.channel_beta) else stringResource(R.string.channel_stable)
    val subtitle = if (canUpdateApp) "$nativeArch · $channelLabel" else nativeArch

    val channelOptions = listOf(stringResource(R.string.channel_stable), stringResource(R.string.channel_beta))
    val channelValues = listOf("stable", "beta")

    SettingsScreenColumn(modifier = modifier) {
        // Status Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = subtitle,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        if (lastCheckText.isNotBlank()) {
                            Text(
                                text = lastCheckText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    if (canUpdateApp) {
                        Button(
                            onClick = onCheckForUpdates,
                            enabled = !isCheckingUpdate,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(2.dp)
                                )
                            } else {
                                Text(text = stringResource(R.string.update_app))
                            }
                        }
                    }
                }
            }
        }

        // Update Channel
        if (canUpdateApp) {
            SettingsCategory(title = stringResource(R.string.update_channel))

            SettingsListPreference(
                title = stringResource(R.string.update_channel),
                entries = channelOptions,
                entryValues = channelValues,
                value = if (isBeta) "beta" else "stable",
                iconRes = R.drawable.baseline_source_24,
                onValueChange = { selected ->
                    isBeta = (selected == "beta")
                    prefs.edit().putBoolean("update_beta", isBeta).apply()
                }
            )
        }

        // Settings
        SettingsCategory(title = stringResource(R.string.settings))

        if (canUpdateApp) {
            SettingsSwitchPreference(
                title = stringResource(R.string.update_app),
                summary = stringResource(R.string.update_app_summary),
                key = "update_app",
                defaultValue = true,
                iconRes = R.drawable.ic_update_app,
            )
        }

        SettingsPreference(
            title = stringResource(R.string.changelog),
            iconRes = R.drawable.ic_chapters,
            showChevron = true,
            onClick = onNavigateChangelog,
        )

        // Reset
        ResetPreferencesPreference(onReset = onReset)
    }
}
