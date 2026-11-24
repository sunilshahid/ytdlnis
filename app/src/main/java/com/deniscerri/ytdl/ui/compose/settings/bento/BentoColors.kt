package com.deniscerri.ytdl.ui.compose.settings.bento

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.android.material.color.utilities.Hct

data class BentoColors(
    val container: Color,
    val onContainer: Color,
    val accent: Color,
)

@Composable
fun bentoColors(hue: Float): BentoColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(hue, dark) {
        val chroma = if (dark) 36.0 else 28.0
        val container = Hct.from(hue.toDouble(), chroma, if (dark) 22.0 else 92.0)
        val text = Hct.from(hue.toDouble(), chroma, if (dark) 90.0 else 30.0)
        val accent = Hct.from(hue.toDouble(), chroma, if (dark) 78.0 else 40.0)
        BentoColors(
            container = Color(container.toInt()),
            onContainer = Color(text.toInt()),
            accent = Color(accent.toInt()),
        )
    }
}
