package com.deniscerri.ytdl.ui.compose.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.deniscerri.ytdl.R
import com.google.android.material.color.MaterialColors

/**
 * Bridges the Activity's XML theme into Compose.
 *
 * The app has seven accent themes, dynamic colour (API 31+), a high-contrast `Pure` overlay and a
 * user-selected light/dark mode, all applied through `ThemeUtil.updateTheme(activity)` before the
 * Activity is created. Resolving the `color*` attributes at runtime keeps Compose in lock-step with
 * that machinery instead of duplicating eight palettes in Kotlin.
 */
internal fun Context.ytdlColorScheme(dark: Boolean): ColorScheme {
    fun color(attrRes: Int, fallback: Long): Color =
        Color(MaterialColors.getColor(this, attrRes, fallback.toInt()))

    val primary = color(R.attr.colorPrimary, if (dark) 0xFFD0BCFF else 0xFF6750A4)
    val onPrimary = color(R.attr.colorOnPrimary, if (dark) 0xFF381E72 else 0xFFFFFFFF)
    val primaryContainer = color(R.attr.colorPrimaryContainer, if (dark) 0xFF4F378B else 0xFFEADDFF)
    val onPrimaryContainer = color(R.attr.colorOnPrimaryContainer, if (dark) 0xFFEADDFF else 0xFF21005D)
    val secondary = color(R.attr.colorSecondary, if (dark) 0xFFCCC2DC else 0xFF625B71)
    val onSecondary = color(R.attr.colorOnSecondary, if (dark) 0xFF332D41 else 0xFFFFFFFF)
    val secondaryContainer = color(R.attr.colorSecondaryContainer, if (dark) 0xFF4A4458 else 0xFFE8DEF8)
    val onSecondaryContainer = color(R.attr.colorOnSecondaryContainer, if (dark) 0xFFE8DEF8 else 0xFF1D192B)
    val tertiary = color(R.attr.colorTertiary, if (dark) 0xFFEFB8C8 else 0xFF7D5260)
    val onTertiary = color(R.attr.colorOnTertiary, if (dark) 0xFF492532 else 0xFFFFFFFF)
    val tertiaryContainer = color(R.attr.colorTertiaryContainer, if (dark) 0xFF633B48 else 0xFFFFD8E4)
    val onTertiaryContainer = color(R.attr.colorOnTertiaryContainer, if (dark) 0xFFFFD8E4 else 0xFF31111D)
    val error = color(R.attr.colorError, if (dark) 0xFFF2B8B5 else 0xFFB3261E)
    val onError = color(R.attr.colorOnError, if (dark) 0xFF601410 else 0xFFFFFFFF)
    val errorContainer = color(R.attr.colorErrorContainer, if (dark) 0xFF8C1D18 else 0xFFF9DEDC)
    val onErrorContainer = color(R.attr.colorOnErrorContainer, if (dark) 0xFFF9DEDC else 0xFF410E0B)
    val background = color(android.R.attr.colorBackground, if (dark) 0xFF1C1B1F else 0xFFFFFBFE)
    val onBackground = color(R.attr.colorOnSurface, if (dark) 0xFFE6E1E5 else 0xFF1C1B1F)
    val surface = color(R.attr.colorSurface, if (dark) 0xFF1C1B1F else 0xFFFFFBFE)
    val onSurface = color(R.attr.colorOnSurface, if (dark) 0xFFE6E1E5 else 0xFF1C1B1F)
    val surfaceVariant = color(R.attr.colorSurfaceVariant, if (dark) 0xFF49454F else 0xFFE7E0EC)
    val onSurfaceVariant = color(R.attr.colorOnSurfaceVariant, if (dark) 0xFFCAC4D0 else 0xFF49454F)
    val outline = color(R.attr.colorOutline, if (dark) 0xFF938F99 else 0xFF79747E)
    val outlineVariant = color(R.attr.colorOutlineVariant, if (dark) 0xFF49454F else 0xFFCAC4D0)
    val inverseSurface = color(R.attr.colorSurfaceInverse, if (dark) 0xFFE6E1E5 else 0xFF313033)
    val inverseOnSurface = color(R.attr.colorOnSurfaceInverse, if (dark) 0xFF313033 else 0xFFF4EFF4)
    val inversePrimary = color(R.attr.colorPrimaryInverse, if (dark) 0xFF6750A4 else 0xFFD0BCFF)
    val surfaceBright = color(R.attr.colorSurfaceBright, if (dark) 0xFF3B383E else 0xFFFFFBFE)
    val surfaceDim = color(R.attr.colorSurfaceDim, if (dark) 0xFF141218 else 0xFFDED8E1)
    val surfaceContainer = color(R.attr.colorSurfaceContainer, if (dark) 0xFF211F26 else 0xFFF3EDF7)
    val surfaceContainerHigh = color(R.attr.colorSurfaceContainerHigh, if (dark) 0xFF2B2930 else 0xFFECE6F0)
    val surfaceContainerHighest = color(R.attr.colorSurfaceContainerHighest, if (dark) 0xFF36343B else 0xFFE6E0E9)
    val surfaceContainerLow = color(R.attr.colorSurfaceContainerLow, if (dark) 0xFF1D1B20 else 0xFFF7F2FA)
    val surfaceContainerLowest = color(R.attr.colorSurfaceContainerLowest, if (dark) 0xFF0F0D13 else 0xFFFFFFFF)

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceBright = surfaceBright,
            surfaceDim = surfaceDim,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceBright = surfaceBright,
            surfaceDim = surfaceDim,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest,
        )
    }
}
