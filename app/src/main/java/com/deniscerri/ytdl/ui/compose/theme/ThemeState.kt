package com.deniscerri.ytdl.ui.compose.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val paletteId: String = "blue",
    val customSeed: Int? = null,
    val materialYou: Boolean = false,
    val highContrast: Boolean = false,
    val compact: Boolean = false,
    val reduceMotion: Boolean = false,
)

object ThemeController {
    private val _config = MutableStateFlow(ThemeConfig())
    val config: StateFlow<ThemeConfig> = _config.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun init(context: Context) {
        if (prefs != null) return
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        prefs = sp
        _config.value = readConfig(sp)

        listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                "ytdlnis_theme", "theme_accent", "custom_palette_seed", "material_you",
                "high_contrast", "compact_mode", "reduce_motion" -> {
                    _config.value = readConfig(sharedPreferences)
                }
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun readConfig(sp: SharedPreferences): ThemeConfig {
        val themeStr = sp.getString("ytdlnis_theme", "System") ?: "System"
        val mode = when (themeStr.lowercase()) {
            "light" -> ThemeMode.LIGHT
            "dusk" -> ThemeMode.DUSK
            "dark" -> ThemeMode.DARK
            "oled" -> ThemeMode.OLED
            else -> ThemeMode.SYSTEM
        }
        val accentStr = sp.getString("theme_accent", "blue") ?: "blue"
        val customSeed = if (sp.contains("custom_palette_seed")) sp.getInt("custom_palette_seed", 0) else null
        val materialYou = if (sp.contains("material_you")) {
            sp.getBoolean("material_you", false)
        } else {
            accentStr.equals("Default", ignoreCase = true)
        }
        val highContrast = sp.getBoolean("high_contrast", false)
        val compact = sp.getBoolean("compact_mode", false)
        val reduceMotion = sp.getBoolean("reduce_motion", false)

        return ThemeConfig(
            mode = mode,
            paletteId = if (accentStr.equals("Default", ignoreCase = true)) "blue" else accentStr,
            customSeed = customSeed,
            materialYou = materialYou,
            highContrast = highContrast,
            compact = compact,
            reduceMotion = reduceMotion,
        )
    }

    fun update(transform: (ThemeConfig) -> ThemeConfig) {
        val next = transform(_config.value)
        _config.value = next
        prefs?.edit()?.apply {
            putString("ytdlnis_theme", when (next.mode) {
                ThemeMode.LIGHT -> "Light"
                ThemeMode.DUSK -> "Dusk"
                ThemeMode.DARK -> "Dark"
                ThemeMode.OLED -> "OLED"
                ThemeMode.SYSTEM -> "System"
            })
            putString("theme_accent", next.paletteId)
            if (next.customSeed != null) {
                putInt("custom_palette_seed", next.customSeed)
            } else {
                remove("custom_palette_seed")
            }
            putBoolean("material_you", next.materialYou)
            putBoolean("high_contrast", next.highContrast)
            putBoolean("compact_mode", next.compact)
            putBoolean("reduce_motion", next.reduceMotion)
            apply()
        }
    }
}

fun resolveScheme(context: Context, config: ThemeConfig, dark: Boolean): ColorScheme {
    val dynamic = config.materialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        else -> {
            val entry = PaletteCatalog.byId(config.paletteId)
            val seed = config.customSeed ?: entry.seed
            buildColorScheme(
                seed = seed,
                dark = dark,
                variant = entry.variant,
                contrast = if (config.highContrast) 1.0 else 0.0,
            )
        }
    }
    return when (config.mode) {
        ThemeMode.DUSK -> {
            val entry = PaletteCatalog.byId(config.paletteId)
            val seed = config.customSeed ?: entry.seed
            val s = schemeFor(
                seed = seed,
                dark = true,
                variant = entry.variant,
                contrast = 0.0,
            )
            base.toDusk(s.neutralPalette.hue, s.neutralPalette.chroma)
        }
        ThemeMode.OLED -> base.toOled()
        else -> base
    }
}
