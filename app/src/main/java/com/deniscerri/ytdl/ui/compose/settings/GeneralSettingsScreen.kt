package com.deniscerri.ytdl.ui.compose.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.NavbarUtil
import com.deniscerri.ytdl.util.ThemeUtil

@Composable
fun GeneralSettingsScreen(
    onOpenNavBarOptions: (() -> Unit)? = null,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var showNavBarDialog by remember { mutableStateOf(false) }

    val historyQuickActionEntries = stringArrayResource(R.array.history_quick_action_entries).toList()
    val historyQuickActionValues = stringArrayResource(R.array.history_quick_action_values).toList()

    val countries = stringArrayResource(R.array.countries).toList()
    val countriesValues = stringArrayResource(R.array.countries_values).toList()

    val labelVisibilityEntries = stringArrayResource(R.array.label_visibility).toList()
    val labelVisibilityValues = stringArrayResource(R.array.label_visibility_values).toList()

    val searchEngines = stringArrayResource(R.array.search_engines).toList()
    val searchEnginesValues = stringArrayResource(R.array.search_engines_values).toList()

    val swipeGesturesEntries = stringArrayResource(R.array.swipe_gestures).toList()
    val swipeGesturesValues = stringArrayResource(R.array.swipe_gestures_values).toList()

    val videoRecommendationsEntries = stringArrayResource(R.array.video_recommendations).toList()
    val videoRecommendationsValues = stringArrayResource(R.array.video_recommnedations_values).toList()

    val dataFetchingExtractorEntries = stringArrayResource(R.array.data_fetching_extractor_youtube).toList()
    val dataFetchingExtractorValues = stringArrayResource(R.array.data_fetching_extractor_youtube_values).toList()

    val formatsSourceEntries = stringArrayResource(R.array.formats_source).toList()
    val formatsSourceValues = stringArrayResource(R.array.formats_source_values).toList()

    val usesSideNav = context.resources.getBoolean(R.bool.uses_side_nav)

    NavbarUtil.init(context)
    val navBarSummary = remember {
        NavbarUtil.getNavBarItems(context).filter { it.isVisible }
            .joinToString(", ") { it.title?.toString().orEmpty() }
    }

    var recommendationsHome by remember {
        mutableStateOf(prefs.getString("recommendations_home", "").orEmpty())
    }

    SettingsScreenColumn(modifier = modifier) {
        // Misc
        SettingsCategory(title = stringResource(R.string.misc))

        // History Quick Action
        SettingsListPreference(
            title = stringResource(R.string.history_quick_action),
            key = "history_quick_action",
            defaultValue = "share",
            entries = historyQuickActionEntries,
            entryValues = historyQuickActionValues,
            iconRes = R.drawable.ic_edit,
        )

        // Preferred Locale
        SettingsListPreference(
            title = stringResource(R.string.preferred_locale),
            key = "locale",
            defaultValue = "",
            entries = countries,
            entryValues = countriesValues,
            iconRes = R.drawable.ic_language,
        )

        // Navigation Bar Options
        if (!usesSideNav) {
            SettingsPreference(
                title = stringResource(R.string.preferred_home_screen),
                summary = navBarSummary,
                iconRes = R.drawable.baseline_home_filled_24,
                showChevron = true,
                onClick = {
                    if (onOpenNavBarOptions != null) onOpenNavBarOptions() else showNavBarDialog = true
                },
            )

            SettingsListPreference(
                title = stringResource(R.string.label_visibility),
                key = "label_visibility",
                defaultValue = "always",
                entries = labelVisibilityEntries,
                entryValues = labelVisibilityValues,
                iconRes = R.drawable.baseline_disabled_visible_24,
                onValueChange = {
                    ThemeUtil.recreateMain()
                }
            )
        }

        // Preferred Search Engine
        SettingsListPreference(
            title = stringResource(R.string.preferred_search_engine),
            key = "search_engine",
            defaultValue = "ytsearch",
            entries = searchEngines,
            entryValues = searchEnginesValues,
            iconRes = R.drawable.baseline_manage_search_24,
        )

        // Check clipboard
        SettingsSwitchPreference(
            title = stringResource(R.string.check_clipboard_home),
            summary = stringResource(R.string.check_clipboard_home_summary),
            key = "check_clipboard_home",
            defaultValue = true,
            iconRes = R.drawable.ic_clipboard,
        )

        // Search suggestions
        SettingsSwitchPreference(
            title = stringResource(R.string.search_suggestions),
            summary = stringResource(R.string.search_suggestions_summary),
            key = "search_suggestions",
            defaultValue = false,
            iconRes = R.drawable.ic_search_accent,
        )

        // Swipe gestures
        SettingsMultiSelectListPreference(
            title = stringResource(R.string.swipe_gestures),
            dialogTitle = stringResource(R.string.swipe_gestures),
            key = "swipe_gesture",
            defaultValue = swipeGesturesValues.toSet(),
            entries = swipeGesturesEntries,
            entryValues = swipeGesturesValues,
            iconRes = R.drawable.baseline_swipe_24,
        )

        // Swipe gestures download card
        SettingsSwitchPreference(
            title = stringResource(R.string.swipe_gestures_download_card),
            key = "swipe_gestures_download_card",
            defaultValue = true,
            iconRes = R.drawable.baseline_swipe_24,
        )

        // Show terminal in share menu
        SettingsSwitchPreference(
            title = stringResource(R.string.show_terminal),
            key = "show_terminal",
            defaultValue = false,
            iconRes = R.drawable.baseline_share2_24,
            onCheckedChange = { checked ->
                val packageManager = context.packageManager
                val alias = ComponentName(context, "com.deniscerri.ytdl.terminalShareAlias")
                val state = if (checked) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                packageManager.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP)
            }
        )

        // Quick download share menu
        SettingsSwitchPreference(
            title = stringResource(R.string.show_quick_download_share_menu),
            key = "show_quick_download_share",
            defaultValue = false,
            iconRes = R.drawable.baseline_share2_24,
            onCheckedChange = { checked ->
                val packageManager = context.packageManager
                val alias = ComponentName(context, "com.deniscerri.ytdl.quickDownloadShareAlias")
                val state = if (checked) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                packageManager.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP)
            }
        )

        // Show download count
        SettingsSwitchPreference(
            title = stringResource(R.string.show_download_count),
            key = "show_count_downloads",
            defaultValue = true,
            iconRes = R.drawable.baseline_numbers_24,
        )

        // Write yt-dlp terminal
        SettingsSwitchPreference(
            title = stringResource(R.string.write_ytdlp_terminal),
            key = "write_ytdlp_terminal",
            defaultValue = true,
            iconRes = R.drawable.baseline_numbers_24,
        )

        // Ignore battery optimizations
        SettingsPreference(
            title = stringResource(R.string.ignore_battery_optimization),
            iconRes = R.drawable.ic_battery,
            showChevron = true,
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:" + context.packageName)
                }
                runCatching { context.startActivity(intent) }
            }
        )

        // Reset
        ResetPreferencesPreference(
            onReset = onReset
        )

        // YouTube
        SettingsCategory(title = "YouTube")

        // Video Recommendations
        SettingsListPreference(
            title = stringResource(R.string.video_recommendations),
            key = "recommendations_home",
            defaultValue = "",
            entries = videoRecommendationsEntries,
            entryValues = videoRecommendationsValues,
            iconRes = R.drawable.baseline_recommend_24,
            onValueChange = { newValue ->
                recommendationsHome = newValue
            }
        )

        // Custom Recommendation URL
        if (recommendationsHome == "custom") {
            SettingsEditTextPreference(
                title = "[${stringResource(R.string.video_recommendations)}] ${stringResource(R.string.custom)}",
                key = "custom_home_recommendation_url",
                defaultValue = "",
                iconRes = R.drawable.baseline_recommend_24,
            )
        }

        // API Key
        if (recommendationsHome == "yt_api") {
            SettingsEditTextPreference(
                title = stringResource(R.string.api_key),
                summary = stringResource(R.string.api_key_summary),
                key = "api_key",
                defaultValue = "",
                iconRes = R.drawable.ic_key,
            )
        }

        // Data fetching extractor
        SettingsListPreference(
            title = stringResource(R.string.data_fetching_extractor_youtube),
            key = "youtube_data_fetching_extractor",
            defaultValue = "YT_DLP",
            entries = dataFetchingExtractorEntries,
            entryValues = dataFetchingExtractorValues,
            iconRes = R.drawable.baseline_manage_search_24,
        )

        // Formats source
        SettingsListPreference(
            title = stringResource(R.string.format_source),
            key = "formats_source",
            defaultValue = "yt-dlp",
            entries = formatsSourceEntries,
            entryValues = formatsSourceValues,
            iconRes = R.drawable.baseline_manage_search_24,
        )

        // Use item url instead of playlist url
        SettingsSwitchPreference(
            title = stringResource(R.string.use_item_url_not_playlist),
            summary = stringResource(R.string.use_item_url_not_playlist_summary),
            key = "use_itemurl_instead_playlisturl",
            defaultValue = false,
            iconRes = R.drawable.baseline_format_quote_24,
        )
    }

    if (showNavBarDialog) {
        NavBarOptionsDialog(onDismiss = { showNavBarDialog = false })
    }
}

@Composable
fun NavBarOptionsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val navItems = remember { NavbarUtil.getNavBarItems(context).toMutableList() }
    var itemsList by remember { mutableStateOf(navItems.toList()) }
    var selectedHomeId by remember { mutableIntStateOf(NavbarUtil.getStartFragmentId(context)) }

    val essential = remember { listOf(R.id.homeFragment, R.id.moreFragment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.navigation_bar)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                itemsList.forEachIndexed { index, item ->
                    val isChecked = item.isVisible || essential.contains(item.itemId)
                    val isEssential = essential.contains(item.itemId)
                    val isHome = item.itemId == selectedHomeId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            enabled = !isEssential,
                            onCheckedChange = { checked ->
                                if (checked && itemsList.count { it.isVisible } >= 5) {
                                    android.widget.Toast.makeText(
                                        context,
                                        R.string.max_nav_items_reached,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    return@Checkbox
                                }
                                item.isVisible = checked
                                itemsList = itemsList.toList()
                            }
                        )

                        Text(
                            text = item.title?.toString().orEmpty(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        IconButton(
                            onClick = {
                                if (isChecked) {
                                    selectedHomeId = item.itemId
                                }
                            },
                            enabled = isChecked
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isHome) R.drawable.baseline_home_filled_24 else R.drawable.ic_home_outlined
                                ),
                                contentDescription = "Home",
                                tint = if (isHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val list = itemsList.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index - 1]
                                    list[index - 1] = temp
                                    itemsList = list
                                }
                            },
                            enabled = index > 0
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_expand_less_24),
                                contentDescription = "Move Up"
                            )
                        }

                        IconButton(
                            onClick = {
                                if (index < itemsList.size - 1) {
                                    val list = itemsList.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index + 1]
                                    list[index + 1] = temp
                                    itemsList = list
                                }
                            },
                            enabled = index < itemsList.size - 1
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_expand_more_24),
                                contentDescription = "Move Down"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    NavbarUtil.setNavBarItems(itemsList, context)
                    NavbarUtil.setStartFragment(selectedHomeId)
                    ThemeUtil.recreateMain()
                    (context as? android.app.Activity)?.recreate()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
