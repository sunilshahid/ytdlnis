package com.deniscerri.ytdl.ui.compose.settings

import android.content.Context
import android.content.pm.PackageManager
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
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.ApkInstallUtil
import java.io.File

@Composable
fun UpdateSettingsScreen(
    onNavigateYtdlpUpdate: () -> Unit,
    onNavigateAppUpdate: () -> Unit,
    onNavigatePackages: () -> Unit,
    onSelectExternalInstaller: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val ytdlpVersion = prefs.getString("ytdl-version", "").orEmpty()
    val ytdlpSource = prefs.getString("ytdlp_source", "stable").orEmpty()
    val ytdlpChannel = when (ytdlpSource) {
        "stable" -> stringResource(R.string.channel_stable)
        "nightly" -> stringResource(R.string.channel_nightly)
        "master" -> stringResource(R.string.channel_master)
        else -> prefs.getString("ytdlp_source_label", "").orEmpty().ifBlank { ytdlpSource }
    }
    val ytdlpSummary = if (ytdlpVersion.isNotBlank()) "$ytdlpVersion ($ytdlpChannel)" else ytdlpChannel

    val nativeArch = File(context.applicationInfo.nativeLibraryDir).name
    val appSummary = "${BuildConfig.VERSION_NAME} ($nativeArch) (${BuildConfig.FLAVOR})"

    val installMethods = stringArrayResource(R.array.apk_install_methods).toList()
    val installMethodValues = stringArrayResource(R.array.apk_install_methods_values).toList()

    val formatOrders = stringArrayResource(R.array.format_ordering).toList()
    val formatOrderValues = stringArrayResource(R.array.format_ordering_values).toList()

    val formatFilters = stringArrayResource(R.array.format_filtering).toList()
    val formatFilterValues = stringArrayResource(R.array.format_filtering_values).toList()

    var installMethod by remember {
        mutableStateOf(prefs.getString("apk_install_method", "system").orEmpty())
    }

    val externalApkId = prefs.getString("apk_install_external_apk_id", "").orEmpty()
    val externalInstallerSummary = remember(externalApkId) {
        if (externalApkId.isBlank()) {
            context.getString(R.string.notset)
        } else {
            runCatching {
                val appInfo = context.packageManager.getApplicationInfo(externalApkId, PackageManager.GET_META_DATA)
                context.packageManager.getApplicationLabel(appInfo).toString()
            }.getOrDefault(context.getString(R.string.notset))
        }
    }

    SettingsScreenColumn(modifier = modifier) {
        SettingsCategory(title = stringResource(R.string.updating))

        // yt-dlp
        SettingsPreference(
            title = stringResource(R.string.ytdlp),
            summary = ytdlpSummary,
            iconRes = R.drawable.ic_update,
            showChevron = true,
            onClick = onNavigateYtdlpUpdate,
        )

        // App
        SettingsPreference(
            title = stringResource(R.string.app),
            summary = appSummary,
            iconRes = R.drawable.ic_update_app,
            showChevron = true,
            onClick = onNavigateAppUpdate,
        )

        // Packages
        SettingsPreference(
            title = stringResource(R.string.packages),
            summary = "Python, FFmpeg, Aria2c, NodeJS, Deno",
            iconRes = R.drawable.ic_code,
            showChevron = true,
            onClick = onNavigatePackages,
        )

        // Installation Method
        SettingsListPreference(
            title = stringResource(R.string.installation_method),
            key = "apk_install_method",
            defaultValue = "system",
            entries = installMethods,
            entryValues = installMethodValues,
            iconRes = R.drawable.baseline_install_mobile_24,
            onValueChange = { selected ->
                installMethod = selected
                if (selected == "shizuku") {
                    ApkInstallUtil.requestShizukuPermission { _, _ -> }
                }
            }
        )

        // Choose Installer App
        if (installMethod == "external") {
            SettingsPreference(
                title = stringResource(R.string.choose_installer_app),
                summary = externalInstallerSummary,
                iconRes = R.drawable.baseline_pie_chart_24,
                showChevron = true,
                onClick = onSelectExternalInstaller,
            )
        }

        // Format
        SettingsCategory(title = stringResource(R.string.format))

        SettingsListPreference(
            title = stringResource(R.string.format_order),
            key = "format_order",
            defaultValue = "filesize",
            entries = formatOrders,
            entryValues = formatOrderValues,
            iconRes = R.drawable.baseline_sort_24,
        )

        SettingsListPreference(
            title = stringResource(R.string.format_filter),
            key = "format_filter",
            defaultValue = "ALL",
            entries = formatFilters,
            entryValues = formatFilterValues,
            iconRes = R.drawable.baseline_filter_alt_24,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.update_formats),
            summary = stringResource(R.string.update_formats_summary),
            key = "update_formats",
            defaultValue = true,
            iconRes = R.drawable.ic_format,
        )

        // Reset
        ResetPreferencesPreference(onReset = onReset)
    }
}
