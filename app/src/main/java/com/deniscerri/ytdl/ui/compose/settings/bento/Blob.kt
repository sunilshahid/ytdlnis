package com.deniscerri.ytdl.ui.compose.settings.bento

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun scallopedPath(size: Size, lobes: Int, amplitude: Float): Path {
    val path = Path()
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outer = min(cx, cy)
    val inner = outer * (1f - amplitude)

    fun point(radius: Float, angle: Float) =
        Offset(cx + radius * cos(angle), cy + radius * sin(angle))

    val step = (2.0 * PI / lobes).toFloat()
    val first = point(outer, 0f)
    path.moveTo(first.x, first.y)

    for (i in 0 until lobes) {
        val control = point(inner, step * (i + 0.5f))
        val end = point(outer, step * (i + 1))
        path.quadraticTo(control.x, control.y, end.x, end.y)
    }
    path.close()
    return path
}

/** Scalloped pill for the card corner (the mock-up's count bubble). */
@Composable
fun CountPill(value: Int, colors: BentoColors) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = 0.22f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
        )
    }
}

/** 52 dp scalloped disc behind the card's leading icon. */
@Composable
fun ScallopedIconBadge(@DrawableRes iconRes: Int, colors: BentoColors) {
    Box(
        Modifier
            .size(52.dp)
            .drawBehind {
                drawPath(
                    path = scallopedPath(size, lobes = 9, amplitude = 0.18f),
                    color = colors.accent.copy(alpha = 0.20f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** The big soft blob anchored to the card's bottom-end corner, clipped by the card shape. */
@Composable
fun BlobDecoration(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val cx = size.width - r * 0.55f
        val cy = size.height - r * 0.45f
        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy),
        )
    }
}
