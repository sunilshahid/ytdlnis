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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.UpdateUtil

@Composable
fun YtdlpUpdateSettingsScreen(
    versionText: String,
    channelName: String,
    lastCheckText: String,
    isCheckingUpdate: Boolean,
    onCheckForUpdates: () -> Unit,
    onOpenCustomSource: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val defaultChannelsTitles = stringArrayResource(R.array.ytdlp_source).toList()
    val defaultChannelsValues = stringArrayResource(R.array.ytdlp_source_values).toList()

    var currentSource by remember {
        mutableStateOf(prefs.getString("ytdlp_source", "stable").orEmpty())
    }

    val customSourceSummary = prefs.getString("ytdlp_source_label", "").orEmpty().ifBlank {
        if (currentSource !in defaultChannelsValues) currentSource else stringResource(R.string.custom_channels_summary)
    }

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
                            text = "yt-dlp",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (versionText.isNotBlank()) "$versionText ($channelName)" else channelName,
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

        // Update channel
        SettingsCategory(title = stringResource(R.string.update_channel))

        SettingsListPreference(
            title = stringResource(R.string.update_channel),
            key = "ytdlp_source",
            defaultValue = "stable",
            entries = defaultChannelsTitles,
            entryValues = defaultChannelsValues,
            iconRes = R.drawable.baseline_source_24,
            onValueChange = { selected ->
                currentSource = selected
            }
        )

        SettingsPreference(
            title = stringResource(R.string.custom_channels),
            summary = customSourceSummary,
            iconRes = R.drawable.baseline_source_24,
            showChevron = true,
            onClick = onOpenCustomSource,
        )

        // Settings
        SettingsCategory(title = stringResource(R.string.settings))

        SettingsSwitchPreference(
            title = stringResource(R.string.auto_update_ytdlp),
            summary = stringResource(R.string.auto_update_ytdlp_summary),
            key = "auto_update_ytdlp",
            defaultValue = true,
            iconRes = R.drawable.ic_update,
        )

        // Reset
        ResetPreferencesPreference(onReset = onReset)
    }
}
