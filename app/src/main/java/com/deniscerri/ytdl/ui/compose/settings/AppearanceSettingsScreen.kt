package com.deniscerri.ytdl.ui.compose.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.components.ConfirmDialog
import com.deniscerri.ytdl.ui.compose.components.SingleChoiceDialog
import com.deniscerri.ytdl.util.LocaleUtil
import com.deniscerri.ytdl.util.ThemeUtil

@Composable
fun AppearanceSettingsScreen(
    onOpenIconPicker: (() -> Unit)? = null,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var showIconPicker by remember { mutableStateOf(false) }

    val supported = remember { LocaleUtil.supportedLocales(context) }
    var currentLanguageSummary by remember {
        mutableStateOf(LocaleUtil.displayName(LocaleUtil.currentLocale(), supported))
    }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val themes = stringArrayResource(R.array.themes).toList()
    val themeValues = stringArrayResource(R.array.themes_values).toList()
    var pendingThemeValue by remember { mutableStateOf<String?>(null) }

    val accents = stringArrayResource(R.array.accents).toList()
    val accentValues = stringArrayResource(R.array.accents_values).toList()

    val hideThumbnailsEntries = stringArrayResource(R.array.hide_thumbnail).toList()
    val hideThumbnailsValues = stringArrayResource(R.array.hide_thumbnail_values).toList()

    val modifyCardEntries = stringArrayResource(R.array.modify_download_card).toList()
    val modifyCardValues = stringArrayResource(R.array.modify_download_card_values).toList()

    val currentIconValue = prefs.getString("ytdlnis_icon", "default")
    val currentIconSummary = ThemeUtil.availableIcons.firstOrNull { it.activityAlias == currentIconValue }
        ?.let { stringResource(it.nameResource) } ?: stringResource(R.string.defaultValue)

    SettingsScreenColumn(modifier = modifier) {
        SettingsCategory(title = stringResource(R.string.appearance))

        // Language
        SettingsPreference(
            title = stringResource(R.string.language),
            summary = currentLanguageSummary,
            iconRes = R.drawable.baseline_translate_24,
            showChevron = true,
            onClick = {
                if (LocaleUtil.systemPickerAvailable()) {
                    runCatching { context.startActivity(LocaleUtil.systemPickerIntent(context)) }
                } else {
                    showLanguageDialog = true
                }
            }
        )

        // Theme
        SettingsListPreference(
            title = stringResource(R.string.Theme),
            key = "ytdlnis_theme",
            defaultValue = "System",
            entries = themes,
            entryValues = themeValues,
            iconRes = R.drawable.ic_theme_circle,
            onValueChange = { newValue ->
                pendingThemeValue = newValue
            }
        )

        // App Icon
        SettingsPreference(
            title = stringResource(R.string.app_icon),
            summary = currentIconSummary,
            iconRes = R.drawable.ic_frame,
            showChevron = true,
            onClick = {
                if (onOpenIconPicker != null) onOpenIconPicker() else showIconPicker = true
            }
        )

        // Accent
        SettingsListPreference(
            title = stringResource(R.string.accents),
            key = "theme_accent",
            defaultValue = "blue",
            entries = accents,
            entryValues = accentValues,
            iconRes = R.drawable.baseline_color_lens_24,
            onValueChange = {
                ThemeUtil.updateThemes()
            }
        )

        // High contrast / Pure black
        SettingsSwitchPreference(
            title = stringResource(R.string.high_contrast),
            summary = stringResource(R.string.pure_theme_summary),
            key = "high_contrast",
            defaultValue = false,
            iconRes = R.drawable.ic_theme_circle,
            onCheckedChange = {
                ThemeUtil.updateThemes()
            }
        )

        // Hide thumbnails
        SettingsMultiSelectListPreference(
            title = stringResource(R.string.hide_thumbnails),
            dialogTitle = stringResource(R.string.hide_thumbnails),
            key = "hide_thumbnails",
            defaultValue = emptySet(),
            entries = hideThumbnailsEntries,
            entryValues = hideThumbnailsValues,
            iconRes = R.drawable.baseline_hide_image_24,
        )

        // Modify download card
        SettingsMultiSelectListPreference(
            title = stringResource(R.string.modify_download_card),
            dialogTitle = stringResource(R.string.modify_download_card),
            key = "modify_download_card",
            defaultValue = modifyCardValues.toSet(),
            entries = modifyCardEntries,
            entryValues = modifyCardValues,
            iconRes = R.drawable.ic_card,
        )

        // Reset
        ResetPreferencesPreference(
            onReset = onReset
        )
    }

    if (showLanguageDialog) {
        val locales = supported
            .map { it to LocaleUtil.displayName(it, supported) }
            .sortedBy { it.second.lowercase() }
        val names = listOf(stringResource(R.string.system)) + locales.map { it.second }
        val current = LocaleUtil.currentLocale().toLanguageTag()
        val checkedIndex = if (!LocaleUtil.isOverridden()) 0
        else locales.indexOfFirst { it.first.toLanguageTag() == current }.let { if (it >= 0) it + 1 else 0 }

        SingleChoiceDialog(
            title = stringResource(R.string.language),
            options = names,
            selectedIndex = checkedIndex,
            onSelect = { which ->
                LocaleUtil.setAppLocale(if (which == 0) null else locales[which - 1].first)
                currentLanguageSummary = LocaleUtil.displayName(LocaleUtil.currentLocale(), supported)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    pendingThemeValue?.let { themeVal ->
        ConfirmDialog(
            title = stringResource(R.string.app_icon_change),
            message = null,
            onConfirm = {
                prefs.edit().putString("ytdlnis_theme", themeVal).commit()
                ThemeUtil.updateThemes()
                pendingThemeValue = null
            },
            onDismiss = {
                pendingThemeValue = null
            }
        )
    }

    if (showIconPicker) {
        AppIconPickerDialog(
            currentIcon = currentIconValue ?: "default",
            onSelectIcon = { appIcon ->
                prefs.edit().putString("ytdlnis_icon", appIcon.activityAlias).apply()
                val theme = prefs.getString("ytdlnis_theme", "System")!!
                (context as? android.app.Activity)?.let { activity ->
                    ThemeUtil.updateAppIcon(activity, theme, appIcon.activityAlias)
                }
                ThemeUtil.recreateAllActivities()
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }
}

@Composable
fun AppIconPickerDialog(
    currentIcon: String,
    onSelectIcon: (ThemeUtil.AppIcon) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_icon)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ThemeUtil.availableIcons) { icon ->
                    val isSelected = icon.activityAlias == currentIcon
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectIcon(icon) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Image(
                                painter = painterResource(icon.iconResource),
                                contentDescription = stringResource(icon.nameResource),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(icon.nameResource),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
