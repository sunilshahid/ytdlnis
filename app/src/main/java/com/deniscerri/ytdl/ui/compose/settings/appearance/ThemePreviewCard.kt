package com.deniscerri.ytdl.ui.compose.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.theme.ThemeMode

/**
 * 56 × 88 dp mini phone preview for System, Light, Dusk, Dark, OLED.
 */
@Composable
fun ThemePreviewCard(
    title: String,
    mode: ThemeMode,
    previewScheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 88.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(previewScheme.surface)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    } else {
                        Modifier.border(1.dp, previewScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    }
                )
                .padding(6.dp)
        ) {
            // Inside the mini preview
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Top app bar / pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(previewScheme.primary)
                    )
                }

                Spacer(Modifier.height(2.dp))

                // 2 small squares/cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(previewScheme.primaryContainer)
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(previewScheme.tertiaryContainer)
                    )
                }

                // 2 lines
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(previewScheme.surfaceContainerHighest)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(previewScheme.surfaceContainerHigh)
                )

                Spacer(Modifier.weight(1f))

                // Bottom bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(previewScheme.surfaceContainer)
                )
            }

            // Selected check badge in top-end corner
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
