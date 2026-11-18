package com.deniscerri.ytdl.ui.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The M3 preference row used by Settings, Lyrics settings and More.
 *
 * Mirrors `res/layout/preference.xml` exactly: 20 dp corners, `colorSurfaceContainer` fill,
 * 1 dp `colorOutlineVariant` stroke, a 42 dp circular icon badge, a 16 sp medium
 * title, a 13 sp summary, interactive tactile press bounce, and trailing slot for a switch/chevron.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreferenceCard(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    badgeColor: Color? = null,
    iconTint: Color? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val interactionSource = remember { MutableInteractionSource() }

    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        var pressJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressJob?.cancel()
                    pressJob = launch {
                        scale.animateTo(
                            targetValue = 0.94f,
                            animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing)
                        )
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val currentJob = pressJob
                    launch {
                        currentJob?.join()
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.45f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
            }
        }
    }

    val actualTrailing: (@Composable () -> Unit)? = trailing ?: if (showChevron) {
        {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .alpha(0.6f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else null

    val outer = modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 3.5.dp)
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }

    val content: @Composable () -> Unit = {
        PreferenceCardContent(
            title = title,
            summary = summary,
            iconRes = iconRes,
            badgeColor = badgeColor,
            iconTint = iconTint,
            trailing = actualTrailing,
        )
    }

    if (onLongClick != null) {
        Surface(
            modifier = outer
                .clip(shape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick
                ),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            content = content,
        )
    } else if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = outer,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            interactionSource = interactionSource,
            content = content,
        )
    } else {
        Surface(
            modifier = outer,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            content = content,
        )
    }
}

@Composable
private fun PreferenceCardContent(
    title: String,
    summary: String?,
    @DrawableRes iconRes: Int?,
    badgeColor: Color?,
    iconTint: Color?,
    trailing: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(start = 14.dp, top = 11.dp, end = 16.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            IconBadge(
                iconRes = iconRes,
                badgeColor = badgeColor ?: MaterialTheme.colorScheme.secondaryContainer,
                iconTint = iconTint ?: MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(14.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        trailing?.invoke()
    }
}

@Composable
private fun IconBadge(
    @DrawableRes iconRes: Int,
    badgeColor: Color,
    iconTint: Color
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(badgeColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = iconTint,
        )
    }
}
