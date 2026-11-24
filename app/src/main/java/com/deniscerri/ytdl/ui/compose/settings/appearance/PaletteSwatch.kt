package com.deniscerri.ytdl.ui.compose.settings.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R

@Composable
fun PaletteSwatch(
    colors: List<Color>,       // exactly 4: top-left, top-right, bottom-right, bottom-left
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(56.dp)) {
                val r = size.minDimension / 2f
                val topLeft = Offset(center.x - r, center.y - r)
                val arcSize = Size(2 * r, 2 * r)
                colors.forEachIndexed { i, c ->
                    drawArc(
                        color = c,
                        startAngle = 180f + i * 90f,
                        sweepAngle = 90f,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                    )
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
fun ScallopedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_plus),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * Custom color palette picker providing a 24-hue swatch grid.
 */
@Composable
fun CustomColorPickerDialog(
    initialColor: Int?,
    onSelectColor: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val hues = remember {
        listOf(
            0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(), 0xFF5E35B1.toInt(),
            0xFF3949AB.toInt(), 0xFF1E88E5.toInt(), 0xFF039BE5.toInt(), 0xFF00ACC1.toInt(),
            0xFF00897B.toInt(), 0xFF43A047.toInt(), 0xFF7CB342.toInt(), 0xFFC0CA33.toInt(),
            0xFFFDD835.toInt(), 0xFFFFB300.toInt(), 0xFFFB8C00.toInt(), 0xFFF4511E.toInt(),
            0xFF6D4C41.toInt(), 0xFF546E7A.toInt(), 0xFF757575.toInt(), 0xFF00BCD4.toInt(),
            0xFF26A69A.toInt(), 0xFF66BB6A.toInt(), 0xFFFF7043.toInt(), 0xFFAB47BC.toInt(),
        )
    }
    var selectedColor by remember { mutableStateOf(initialColor ?: hues.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.palette_make)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(hues) { colorInt ->
                        val isSelected = colorInt == selectedColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(colorInt))
                                .clickable { selectedColor = colorInt }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelectColor(selectedColor)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
