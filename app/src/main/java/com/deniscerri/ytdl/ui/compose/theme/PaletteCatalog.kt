package com.deniscerri.ytdl.ui.compose.theme

import androidx.annotation.StringRes
import com.deniscerri.ytdl.R

data class PaletteEntry(
    val id: String,
    @param:StringRes val nameRes: Int,
    val seed: Int,
    val variant: PaletteVariant = PaletteVariant.TONAL_SPOT,
)

object PaletteCatalog {
    val entries: List<PaletteEntry> = listOf(
        PaletteEntry("blue", R.string.palette_orion, 0xFF0058CB.toInt(), PaletteVariant.TONAL_SPOT),
        PaletteEntry("mint", R.string.palette_mint, 0xFF3DDC97.toInt(), PaletteVariant.TONAL_SPOT),
        PaletteEntry("ember", R.string.palette_ember, 0xFFFF8A80.toInt(), PaletteVariant.CONTENT),
        PaletteEntry("amber", R.string.palette_amber, 0xFFFFB74D.toInt(), PaletteVariant.TONAL_SPOT),
        PaletteEntry("cyber", R.string.palette_cyber, 0xFF4DD0E1.toInt(), PaletteVariant.VIBRANT),
        PaletteEntry("midnight", R.string.palette_midnight, 0xFF3949AB.toInt(), PaletteVariant.FIDELITY),
        PaletteEntry("lime", R.string.palette_lime, 0xFFC6FF00.toInt(), PaletteVariant.VIBRANT),
        PaletteEntry("cocoa", R.string.palette_cocoa, 0xFF8D6E63.toInt(), PaletteVariant.CONTENT),
        PaletteEntry("red", R.string.red, 0xFFB3261E.toInt(), PaletteVariant.TONAL_SPOT),
        PaletteEntry("green", R.string.green, 0xFF2E7D32.toInt(), PaletteVariant.TONAL_SPOT),
        PaletteEntry("purple", R.string.purple, 0xFF6750A4.toInt(), PaletteVariant.TONAL_SPOT),
        PaletteEntry("yellow", R.string.yellow, 0xFFF9A825.toInt(), PaletteVariant.TONAL_SPOT),
        PaletteEntry("orange", R.string.orange, 0xFFE65100.toInt(), PaletteVariant.TONAL_SPOT),
        PaletteEntry("monochrome", R.string.monochrome, 0xFF5F5F5F.toInt(), PaletteVariant.NEUTRAL),
    )

    fun byId(id: String): PaletteEntry =
        entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: entries.first()
}
