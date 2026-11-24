package com.deniscerri.ytdl.ui.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFidelity
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant

/** How the seed is expanded into a tonal palette. Mirrors material-color-utilities. */
enum class PaletteVariant { TONAL_SPOT, VIBRANT, EXPRESSIVE, CONTENT, FIDELITY, NEUTRAL }

/** User-facing theme mode. DUSK and OLED are both dark, but with different surface tones. */
enum class ThemeMode { SYSTEM, LIGHT, DUSK, DARK, OLED }

private val roles = MaterialDynamicColors()

private fun DynamicColor.of(scheme: DynamicScheme): Color = Color(getArgb(scheme))

internal fun schemeFor(seed: Int, dark: Boolean, variant: PaletteVariant, contrast: Double): DynamicScheme {
    val hct = Hct.fromInt(seed)
    return when (variant) {
        PaletteVariant.TONAL_SPOT -> SchemeTonalSpot(hct, dark, contrast)
        PaletteVariant.VIBRANT -> SchemeVibrant(hct, dark, contrast)
        PaletteVariant.EXPRESSIVE -> SchemeExpressive(hct, dark, contrast)
        PaletteVariant.CONTENT -> SchemeContent(hct, dark, contrast)
        PaletteVariant.FIDELITY -> SchemeFidelity(hct, dark, contrast)
        PaletteVariant.NEUTRAL -> SchemeTonalSpot(Hct.from(hct.hue, 0.0, hct.tone), dark, contrast)
    }
}

private fun tone(hue: Double, chroma: Double, tone: Double): Color =
    Color(Hct.from(hue, chroma, tone).toInt())

/**
 * Dusk surface ladder. Stock dark uses 6 / 10 / 12 / 17 / 22; Dusk uses 14 / 18 / 20 / 24 / 26 so
 * cards read as "lifted" instead of merging into the background.
 */
fun ColorScheme.withSurfaceLadder(
    hue: Double,
    chroma: Double,
    surface: Double,
    dim: Double,
    bright: Double,
    lowest: Double,
    low: Double,
    container: Double,
    high: Double,
    highest: Double,
): ColorScheme = copy(
    surface = tone(hue, chroma, surface),
    background = tone(hue, chroma, surface),
    surfaceDim = tone(hue, chroma, dim),
    surfaceBright = tone(hue, chroma, bright),
    surfaceContainerLowest = tone(hue, chroma, lowest),
    surfaceContainerLow = tone(hue, chroma, low),
    surfaceContainer = tone(hue, chroma, container),
    surfaceContainerHigh = tone(hue, chroma, high),
    surfaceContainerHighest = tone(hue, chroma, highest),
)

fun ColorScheme.toDusk(neutralHue: Double, neutralChroma: Double): ColorScheme =
    withSurfaceLadder(neutralHue, neutralChroma, 14.0, 10.0, 30.0, 11.0, 14.0, 18.0, 22.0, 26.0)

fun ColorScheme.toOled(): ColorScheme = copy(
    surface = Color.Black,
    background = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF07070A),
    surfaceContainer = Color(0xFF0E0E12),
    surfaceContainerHigh = Color(0xFF15151A),
    surfaceContainerHighest = Color(0xFF1C1C22),
)

/**
 * Full Material 3 colour scheme from one seed.
 *
 * Every role is taken from material-color-utilities, including the surface containers and the
 * `*Fixed` roles that `darkColorScheme()` / `lightColorScheme()` would otherwise leave at the
 * baseline purple.
 */
fun buildColorScheme(
    seed: Int,
    dark: Boolean,
    variant: PaletteVariant = PaletteVariant.TONAL_SPOT,
    contrast: Double = 0.0,
): ColorScheme {
    val s = schemeFor(seed, dark, variant, contrast)
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = roles.primary().of(s),
        onPrimary = roles.onPrimary().of(s),
        primaryContainer = roles.primaryContainer().of(s),
        onPrimaryContainer = roles.onPrimaryContainer().of(s),
        inversePrimary = roles.inversePrimary().of(s),
        primaryFixed = roles.primaryFixed().of(s),
        primaryFixedDim = roles.primaryFixedDim().of(s),
        onPrimaryFixed = roles.onPrimaryFixed().of(s),
        onPrimaryFixedVariant = roles.onPrimaryFixedVariant().of(s),
        secondary = roles.secondary().of(s),
        onSecondary = roles.onSecondary().of(s),
        secondaryContainer = roles.secondaryContainer().of(s),
        onSecondaryContainer = roles.onSecondaryContainer().of(s),
        secondaryFixed = roles.secondaryFixed().of(s),
        secondaryFixedDim = roles.secondaryFixedDim().of(s),
        onSecondaryFixed = roles.onSecondaryFixed().of(s),
        onSecondaryFixedVariant = roles.onSecondaryFixedVariant().of(s),
        tertiary = roles.tertiary().of(s),
        onTertiary = roles.onTertiary().of(s),
        tertiaryContainer = roles.tertiaryContainer().of(s),
        onTertiaryContainer = roles.onTertiaryContainer().of(s),
        tertiaryFixed = roles.tertiaryFixed().of(s),
        tertiaryFixedDim = roles.tertiaryFixedDim().of(s),
        onTertiaryFixed = roles.onTertiaryFixed().of(s),
        onTertiaryFixedVariant = roles.onTertiaryFixedVariant().of(s),
        error = roles.error().of(s),
        onError = roles.onError().of(s),
        errorContainer = roles.errorContainer().of(s),
        onErrorContainer = roles.onErrorContainer().of(s),
        background = roles.background().of(s),
        onBackground = roles.onBackground().of(s),
        surface = roles.surface().of(s),
        onSurface = roles.onSurface().of(s),
        surfaceVariant = roles.surfaceVariant().of(s),
        onSurfaceVariant = roles.onSurfaceVariant().of(s),
        surfaceTint = roles.surfaceTint().of(s),
        inverseSurface = roles.inverseSurface().of(s),
        inverseOnSurface = roles.inverseOnSurface().of(s),
        outline = roles.outline().of(s),
        outlineVariant = roles.outlineVariant().of(s),
        surfaceBright = roles.surfaceBright().of(s),
        surfaceDim = roles.surfaceDim().of(s),
        surfaceContainerLowest = roles.surfaceContainerLowest().of(s),
        surfaceContainerLow = roles.surfaceContainerLow().of(s),
        surfaceContainer = roles.surfaceContainer().of(s),
        surfaceContainerHigh = roles.surfaceContainerHigh().of(s),
        surfaceContainerHighest = roles.surfaceContainerHighest().of(s),
        scrim = Color.Black,
    )
}
