package com.deniscerri.ytdl.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

private const val PREF_THEME = "ytdlnis_theme"

/**
 * Root theme for every Compose screen.
 *
 * The light/dark decision mirrors [com.deniscerri.ytdl.util.ThemeUtil.applyNightMode] so a forced
 * Light/Dark preference wins over the system setting. Colours come from the Activity theme (see
 * [ytdlColorScheme]), and the Activity is recreated by `ThemeUtil` whenever the user changes the
 * accent, contrast or night mode.
 */
@Composable
fun YtdlnisTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val dark = remember(prefs, systemDark) {
        when (prefs.getString(PREF_THEME, "System")) {
            "Light" -> false
            "Dark" -> true
            else -> systemDark
        }
    }
    val colorScheme = remember(context, dark) { context.ytdlColorScheme(dark) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YtdlTypography,
        shapes = YtdlShapes,
        content = content,
    )
}
