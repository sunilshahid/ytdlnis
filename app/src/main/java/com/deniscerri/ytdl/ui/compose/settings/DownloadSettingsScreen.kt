package com.deniscerri.ytdl.ui.compose.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.IncognitoSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DownloadSettingsScreen(
    onSelectDownloadDelay: () -> Unit,
    downloadDelayText: String,
    onSelectArchivePath: () -> Unit,
    onSelectScheduleStart: () -> Unit,
    onSelectScheduleEnd: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val preventDuplicateEntries = stringArrayResource(R.array.prevent_duplicate_downloads).toList()
    val preventDuplicateValues = stringArrayResource(R.array.prevent_duplicate_downloads_values).toList()

    val downloadTypes = stringArrayResource(R.array.download_types).toList()
    val downloadTypeValues = stringArrayResource(R.array.download_types_values).toList()

    val cleanupEntries = stringArrayResource(R.array.cleanup_leftover_downloads).toList()
    val cleanupValues = stringArrayResource(R.array.cleanup_leftover_downloads_values).toList()

    var preventDuplicate by remember {
        mutableStateOf(prefs.getString("prevent_duplicate_downloads", "url_type").orEmpty())
    }
    var rememberDownloadType by remember {
        mutableStateOf(prefs.getBoolean("remember_download_type", false))
    }
    var useScheduler by remember {
        mutableStateOf(prefs.getBoolean("use_scheduler", false))
    }

    val archivePath = remember { FileUtil.getDownloadArchivePath(context) }
    val scheduleStart = prefs.getString("schedule_start", "00:00").orEmpty()
    val scheduleEnd = prefs.getString("schedule_end", "05:00").orEmpty()

    SettingsScreenColumn(modifier = modifier) {
        SettingsCategory(title = stringResource(R.string.downloading))

        // Incognito
        SettingsSwitchPreference(
            title = stringResource(R.string.incognito),
            summary = stringResource(R.string.incognito_summary),
            key = "incognito",
            defaultValue = false,
            iconRes = R.drawable.ic_nightly,
            onCheckedChange = { isIncognito ->
                if (!isIncognito) {
                    CoroutineScope(Dispatchers.IO).launch {
                        IncognitoSessionManager.purgeIncognitoSession(context)
                    }
                }
            }
        )

        // Show download card
        SettingsSwitchPreference(
            title = stringResource(R.string.show_download_card),
            summary = stringResource(R.string.download_card_summary),
            key = "download_card",
            defaultValue = true,
            iconRes = R.drawable.ic_card,
        )

        // Quick download
        SettingsSwitchPreference(
            title = stringResource(R.string.quick_download_title),
            summary = stringResource(R.string.quick_download_summary),
            key = "quick_download",
            defaultValue = false,
            iconRes = R.drawable.ic_speed,
        )

        // Prevent duplicate downloads
        SettingsListPreference(
            title = stringResource(R.string.prevent_duplicate_downloads),
            key = "prevent_duplicate_downloads",
            defaultValue = "url_type",
            entries = preventDuplicateEntries,
            entryValues = preventDuplicateValues,
            iconRes = R.drawable.baseline_archive_24,
            onValueChange = { preventDuplicate = it }
        )

        // Download delay
        SettingsPreference(
            title = stringResource(R.string.download_delay),
            summary = downloadDelayText.ifBlank { stringResource(R.string.download_delay_summary) },
            iconRes = R.drawable.baseline_timer_24,
            showChevron = true,
            onClick = onSelectDownloadDelay,
        )

        // Download archive path
        if (preventDuplicate == "download_archive") {
            SettingsPreference(
                title = stringResource(R.string.download_archive_folder),
                summary = archivePath.ifBlank { null },
                iconRes = R.drawable.baseline_archive_24,
                showChevron = true,
                onClick = onSelectArchivePath,
            )
        }

        // Metered networks
        SettingsSwitchPreference(
            title = stringResource(R.string.download_over_metered_networks),
            summary = stringResource(R.string.download_over_metered_networks_summary),
            key = "metered_networks",
            defaultValue = true,
            iconRes = R.drawable.metered_networks,
        )

        // Force IPv4
        SettingsSwitchPreference(
            title = stringResource(R.string.force_ipv4),
            summary = stringResource(R.string.force_ipv4_desc),
            key = "force_ipv4",
            defaultValue = false,
            iconRes = R.drawable.ic_ipv4,
        )

        // Use cookies
        SettingsSwitchPreference(
            title = stringResource(R.string.use_cookies),
            key = "use_cookies",
            defaultValue = false,
            iconRes = R.drawable.baseline_cookie_24,
        )

        // SOCKS5 Proxy
        SettingsEditTextPreference(
            title = stringResource(R.string.socks5_proxy),
            summary = stringResource(R.string.socks5_proxy_summary),
            key = "proxy",
            defaultValue = "",
            iconRes = R.drawable.baseline_network_locked_24,
        )

        // Preferred download type
        SettingsListPreference(
            title = stringResource(R.string.preferred_download_type),
            key = "preferred_download_type",
            defaultValue = "auto",
            entries = downloadTypes,
            entryValues = downloadTypeValues,
            enabled = !rememberDownloadType,
            iconRes = R.drawable.ic_download_type,
        )

        // Remember download type
        SettingsSwitchPreference(
            title = stringResource(R.string.remember_download_type),
            summary = stringResource(R.string.remember_download_type_summary),
            key = "remember_download_type",
            defaultValue = false,
            iconRes = R.drawable.ic_download_type,
            onCheckedChange = { rememberDownloadType = it }
        )

        // Concurrent fragments
        SettingsSliderPreference(
            title = stringResource(R.string.concurrent_fragments),
            summary = stringResource(R.string.concurrent_fragments_summary),
            key = "concurrent_fragments",
            defaultValue = 1,
            min = 1,
            max = 25,
            iconRes = R.drawable.ic_lines,
        )

        // Concurrent downloads
        SettingsSliderPreference(
            title = stringResource(R.string.concurrent_downloads),
            summary = stringResource(R.string.concurrent_downloads_summary),
            key = "concurrent_downloads",
            defaultValue = 1,
            min = 1,
            max = 10,
            iconRes = R.drawable.ic_concurrent_downloads,
        )

        // Limit rate
        SettingsEditTextPreference(
            title = stringResource(R.string.limit_rate),
            summary = stringResource(R.string.limit_rate_summary),
            key = "limit_rate",
            defaultValue = "",
            iconRes = R.drawable.ic_limitrate,
        )

        // Buffer size
        SettingsEditTextPreference(
            title = stringResource(R.string.buffer_size),
            summary = stringResource(R.string.buffer_size_summary),
            key = "buffer_size",
            defaultValue = "",
            iconRes = R.drawable.baseline_settings_input_component_24,
        )

        // Socket timeout
        SettingsEditTextPreference(
            title = stringResource(R.string.socket_timeout),
            summary = stringResource(R.string.socket_timeout_description),
            key = "socket_timeout",
            defaultValue = "",
            iconRes = R.drawable.ic_clock,
        )

        // aria2c
        SettingsSwitchPreference(
            title = "aria2c",
            summary = stringResource(R.string.aria2_summary),
            key = "aria2",
            defaultValue = false,
            iconRes = R.drawable.ic_speed,
        )

        // Log downloads
        SettingsSwitchPreference(
            title = stringResource(R.string.log_downloads),
            summary = stringResource(R.string.log_downloads_summary),
            key = "log_downloads",
            defaultValue = false,
            iconRes = R.drawable.ic_baseline_file_open_24,
        )

        // Cleanup leftover downloads
        SettingsListPreference(
            title = stringResource(R.string.cleanup_leftover_downloads),
            key = "cleanup_leftover_downloads",
            defaultValue = "",
            entries = cleanupEntries,
            entryValues = cleanupValues,
            iconRes = R.drawable.ic_folder_delete,
        )

        // Retries
        SettingsEditTextPreference(
            title = stringResource(R.string.retries),
            key = "retries",
            defaultValue = "",
            iconRes = R.drawable.ic_retries,
        )

        // Fragment retries
        SettingsEditTextPreference(
            title = stringResource(R.string.fragment_retries),
            key = "fragment_retries",
            defaultValue = "",
            iconRes = R.drawable.ic_retries,
        )

        // Scheduling
        SettingsCategory(title = stringResource(R.string.scheduling))

        // Use alarm manager
        SettingsSwitchPreference(
            title = stringResource(R.string.use_alarm_manager),
            summary = stringResource(R.string.use_alarm_manager_summary),
            key = "use_alarm_for_scheduling",
            defaultValue = false,
            iconRes = R.drawable.baseline_access_alarm_24,
        )

        // Use scheduler
        SettingsSwitchPreference(
            title = stringResource(R.string.use_scheduler),
            key = "use_scheduler",
            defaultValue = false,
            iconRes = R.drawable.ic_clock,
            onCheckedChange = { useScheduler = it }
        )

        // Start time
        SettingsPreference(
            title = stringResource(R.string.start),
            summary = scheduleStart,
            iconRes = R.drawable.ic_start,
            enabled = useScheduler,
            showChevron = true,
            onClick = onSelectScheduleStart,
        )

        // End time
        SettingsPreference(
            title = stringResource(R.string.end),
            summary = scheduleEnd,
            iconRes = R.drawable.ic_end,
            enabled = useScheduler,
            showChevron = true,
            onClick = onSelectScheduleEnd,
        )

        // Reset
        ResetPreferencesPreference(onReset = onReset)
    }
}
