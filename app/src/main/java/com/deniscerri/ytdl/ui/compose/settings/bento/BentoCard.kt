package com.deniscerri.ytdl.ui.compose.settings.bento

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BentoCard(
    spec: BentoCardSpec,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = bentoColors(spec.hue)
    val shape = RoundedCornerShape(28.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spec.minHeight.dp),
        shape = shape,
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Box(
            Modifier
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            BlobDecoration(
                modifier = Modifier.matchParentSize(),
                color = colors.accent.copy(alpha = 0.18f),
            )

            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScallopedIconBadge(spec.iconRes, colors)
                    Spacer(Modifier.weight(1f))
                    spec.badge?.let { CountPill(it, colors) }
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                spec.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onContainer.copy(alpha = 0.75f),
                    )
                }
                spec.metric?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onContainer.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}
