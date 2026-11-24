package com.deniscerri.ytdl.ui.compose.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.components.SingleChoiceDialog
import com.deniscerri.ytdl.ui.compose.settings.appearance.CustomColorPickerDialog
import com.deniscerri.ytdl.ui.compose.settings.appearance.PaletteSwatch
import com.deniscerri.ytdl.ui.compose.settings.appearance.ScallopedButton
import com.deniscerri.ytdl.ui.compose.settings.appearance.ThemePreviewCard
import com.deniscerri.ytdl.ui.compose.theme.PaletteCatalog
import com.deniscerri.ytdl.ui.compose.theme.ThemeController
import com.deniscerri.ytdl.ui.compose.theme.ThemeMode
import com.deniscerri.ytdl.ui.compose.theme.buildColorScheme
import com.deniscerri.ytdl.ui.compose.theme.resolveScheme
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
    val config by ThemeController.config.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()

    var showIconPicker by remember { mutableStateOf(false) }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    val supported = remember { LocaleUtil.supportedLocales(context) }
    var currentLanguageSummary by remember {
        mutableStateOf(LocaleUtil.displayName(LocaleUtil.currentLocale(), supported))
    }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val currentFont = prefs.getString("app_font", "system") ?: "system"

    val hideThumbnailsEntries = stringArrayResource(R.array.hide_thumbnail).toList()
    val hideThumbnailsValues = stringArrayResource(R.array.hide_thumbnail_values).toList()

    val modifyCardEntries = stringArrayResource(R.array.modify_download_card).toList()
    val modifyCardValues = stringArrayResource(R.array.modify_download_card_values).toList()

    val currentIconValue = prefs.getString("ytdlnis_icon", "default")
    val currentIconSummary = ThemeUtil.availableIcons.firstOrNull { it.activityAlias == currentIconValue }
        ?.let { stringResource(it.nameResource) } ?: stringResource(R.string.defaultValue)

    SettingsScreenColumn(modifier = modifier) {
        // Section: Theme
        SettingsCategory(title = stringResource(R.string.Theme))

        val themeModes = listOf(
            ThemeMode.SYSTEM to stringResource(R.string.system),
            ThemeMode.LIGHT to stringResource(R.string.light),
            ThemeMode.DUSK to stringResource(R.string.theme_dusk),
            ThemeMode.DARK to stringResource(R.string.dark),
            ThemeMode.OLED to stringResource(R.string.theme_oled),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            items(themeModes) { (mode, label) ->
                val previewDark = when (mode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemDark
                    else -> true
                }
                val previewScheme = resolveScheme(context, config.copy(mode = mode), previewDark)
                ThemePreviewCard(
                    title = label,
                    mode = mode,
                    previewScheme = previewScheme,
                    selected = config.mode == mode,
                    onClick = {
                        ThemeController.update { it.copy(mode = mode) }
                        (context as? android.app.Activity)?.let { _ ->
                            ThemeUtil.applyNightMode(context)
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Section: Palette
        SettingsCategory(title = stringResource(R.string.palette))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            items(PaletteCatalog.entries) { entry ->
                val scheme = buildColorScheme(
                    seed = entry.seed,
                    dark = when (config.mode) {
                        ThemeMode.LIGHT -> false
                        ThemeMode.SYSTEM -> isSystemDark
                        else -> true
                    },
                    variant = entry.variant,
                    contrast = if (config.highContrast) 1.0 else 0.0,
                )
                val swatchColors = listOf(
                    scheme.primaryContainer,
                    scheme.tertiaryContainer,
                    scheme.primary,
                    scheme.surfaceContainerHighest,
                )
                val isSelected = !config.materialYou && config.paletteId == entry.id
                PaletteSwatch(
                    colors = swatchColors,
                    selected = isSelected,
                    label = stringResource(entry.nameRes),
                    onClick = {
                        ThemeController.update {
                            it.copy(paletteId = entry.id, customSeed = null, materialYou = false)
                        }
                    }
                )
            }

            // Custom palette swatch if selected
            if (config.paletteId == "custom" && config.customSeed != null) {
                item {
                    val customScheme = buildColorScheme(
                        seed = config.customSeed!!,
                        dark = when (config.mode) {
                            ThemeMode.LIGHT -> false
                            ThemeMode.SYSTEM -> isSystemDark
                            else -> true
                        },
                        contrast = if (config.highContrast) 1.0 else 0.0,
                    )
                    val customSwatchColors = listOf(
                        customScheme.primaryContainer,
                        customScheme.tertiaryContainer,
                        customScheme.primary,
                        customScheme.surfaceContainerHighest,
                    )
                    PaletteSwatch(
                        colors = customSwatchColors,
                        selected = !config.materialYou,
                        label = stringResource(R.string.palette_custom),
                        onClick = {
                            showCustomColorDialog = true
                        }
                    )
                }
            }

            // Scalloped Button for "+ Make" custom palette
            item {
                ScallopedButton(
                    label = stringResource(R.string.palette_make),
                    onClick = {
                        showCustomColorDialog = true
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Section: Color & type
        SettingsCategory(title = stringResource(R.string.color_and_type))

        // Material You switch (API 31+)
        SettingsSwitchPreference(
            title = stringResource(R.string.material_you),
            summary = stringResource(R.string.material_you_summary),
            key = "material_you",
            defaultValue = false,
            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            iconRes = R.drawable.baseline_color_lens_24,
            onCheckedChange = { isChecked ->
                ThemeController.update { it.copy(materialYou = isChecked) }
                (context as? android.app.Activity)?.let { activity ->
                    ThemeUtil.updateTheme(activity)
                }
            }
        )

        // Font
        SettingsPreference(
            title = stringResource(R.string.font),
            summary = if (currentFont == "space_grotesk") stringResource(R.string.font_space_grotesk) else stringResource(R.string.font_system),
            iconRes = R.drawable.baseline_format_size_24,
            showChevron = true,
            onClick = { showFontDialog = true }
        )

        // High contrast
        SettingsSwitchPreference(
            title = stringResource(R.string.high_contrast),
            summary = stringResource(R.string.pure_theme_summary),
            key = "high_contrast",
            defaultValue = false,
            iconRes = R.drawable.ic_theme_circle,
            onCheckedChange = { isChecked ->
                ThemeController.update { it.copy(highContrast = isChecked) }
                ThemeUtil.updateThemes()
            }
        )

        Spacer(Modifier.height(8.dp))

        // Section: Motion & feel
        SettingsCategory(title = stringResource(R.string.motion_and_feel))

        SettingsSwitchPreference(
            title = stringResource(R.string.haptics_enabled),
            summary = stringResource(R.string.haptics_summary),
            key = "haptics_enabled",
            defaultValue = true,
            iconRes = R.drawable.ic_clock,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.smooth_motion),
            summary = stringResource(R.string.smooth_motion_summary),
            key = "smooth_motion",
            defaultValue = true,
            iconRes = R.drawable.ic_speed,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.reduce_motion),
            summary = stringResource(R.string.reduce_motion_summary),
            key = "reduce_motion",
            defaultValue = false,
            iconRes = R.drawable.baseline_loop_24,
            onCheckedChange = { isChecked ->
                ThemeController.update { it.copy(reduceMotion = isChecked) }
            }
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.glass_dock),
            summary = stringResource(R.string.glass_dock_summary),
            key = "glass_dock",
            defaultValue = false,
            iconRes = R.drawable.ic_lines,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.fullscreen_mode),
            summary = stringResource(R.string.fullscreen_mode_summary),
            key = "fullscreen_mode",
            defaultValue = false,
            iconRes = R.drawable.ic_aspect_ratio,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.compact_mode),
            summary = stringResource(R.string.compact_mode_summary),
            key = "compact_mode",
            defaultValue = false,
            iconRes = R.drawable.ic_card,
            onCheckedChange = { isChecked ->
                ThemeController.update { it.copy(compact = isChecked) }
            }
        )

        Spacer(Modifier.height(8.dp))

        // Section: App icon
        SettingsCategory(title = stringResource(R.string.app_icon))

        SettingsPreference(
            title = stringResource(R.string.app_icon),
            summary = currentIconSummary,
            iconRes = R.drawable.ic_frame,
            showChevron = true,
            onClick = {
                if (onOpenIconPicker != null) onOpenIconPicker() else showIconPicker = true
            }
        )

        Spacer(Modifier.height(8.dp))

        // Section: Layout
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

    if (showFontDialog) {
        val fontOptions = listOf(
            stringResource(R.string.font_system),
            stringResource(R.string.font_space_grotesk)
        )
        val selectedIdx = if (currentFont == "space_grotesk") 1 else 0
        SingleChoiceDialog(
            title = stringResource(R.string.font),
            options = fontOptions,
            selectedIndex = selectedIdx,
            onSelect = { which ->
                val chosen = if (which == 1) "space_grotesk" else "system"
                prefs.edit().putString("app_font", chosen).apply()
                showFontDialog = false
            },
            onDismiss = { showFontDialog = false }
        )
    }

    if (showCustomColorDialog) {
        CustomColorPickerDialog(
            initialColor = config.customSeed,
            onSelectColor = { seedColor ->
                ThemeController.update {
                    it.copy(paletteId = "custom", customSeed = seedColor, materialYou = false)
                }
            },
            onDismiss = { showCustomColorDialog = false }
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
