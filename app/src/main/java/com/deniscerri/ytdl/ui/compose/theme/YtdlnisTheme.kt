package com.deniscerri.ytdl.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Root theme for every Compose screen.
 *
 * Driven dynamically by [ThemeController] and [PaletteEngine], providing live palette and
 * theme mode switching without activity recreation flash.
 */
@Composable
fun YtdlnisTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val config by ThemeController.config.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = when (config.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        else -> true
    }
    val colorScheme = remember(context, config, dark) { resolveScheme(context, config, dark) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YtdlTypography,
        shapes = YtdlShapes,
        content = content,
    )
}

@Composable
fun YtdlTheme(
    content: @Composable () -> Unit,
) = YtdlnisTheme(content = content)
