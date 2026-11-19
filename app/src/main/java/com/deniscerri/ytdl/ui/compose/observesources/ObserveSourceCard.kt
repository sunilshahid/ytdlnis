package com.deniscerri.ytdl.ui.compose.observesources

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.util.Extensions
import com.deniscerri.ytdl.util.Extensions.calculateNextTimeForObserving
import com.deniscerri.ytdl.util.Extensions.displayStatus
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Grid tile for one observe source. Replaces `observe_sources_item.xml` and
 * `ObserveSourcesAdapter`.
 *
 * Metrics are 1:1 with the layout: the cell keeps 10 dp margins, the card is sized to the
 * `layout_constraintDimensionRatio="H,2.7:1"` box (width / 2.7) and is then inset by another
 * 10 dp, so the visible gap between tiles is 20 dp. 20 dp corners, `colorSurfaceContainer` fill,
 * a 17 sp bold two-line title, a 12 sp bold URL and the status chip pinned to the bottom.
 */
@Composable
fun ObserveSourceCard(
    item: ObserveSourcesItem,
    isRunning: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(20.dp)

    // `Extensions.popup()` scaled the card 0.75 -> 1 on every bind.
    val scale = remember { Animatable(0.75f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    val titleText = if (item.name.length > 100) item.name.substring(0, 40) + "..." else item.name
    val status = item.displayStatus()

    val chipText: String
    @DrawableRes val chipIcon: Int
    val chipContainer: Color
    val chipContent: Color
    val titleAlpha: Float
    val urlAlpha: Float

    when (status) {
        Extensions.ObserveSourceDisplayStatus.ACTIVE -> {
            chipText = nextRunLabel(context, item)
            chipIcon = R.drawable.baseline_loop_24
            chipContainer = MaterialTheme.colorScheme.tertiaryContainer
            chipContent = MaterialTheme.colorScheme.onTertiaryContainer
            titleAlpha = 1f
            urlAlpha = 0.7f
        }

        Extensions.ObserveSourceDisplayStatus.PAUSED -> {
            chipText = stringResource(R.string.paused)
            chipIcon = R.drawable.exomedia_ic_pause_white
            chipContainer = MaterialTheme.colorScheme.surfaceVariant
            chipContent = MaterialTheme.colorScheme.onSurfaceVariant
            titleAlpha = 0.5f
            urlAlpha = 0.4f
        }

        Extensions.ObserveSourceDisplayStatus.FINISHED -> {
            chipText = context.resources.getQuantityString(
                R.plurals.finished_runs,
                item.runCount,
                item.runCount,
            )
            chipIcon = R.drawable.ic_check
            chipContainer = MaterialTheme.colorScheme.surfaceVariant
            chipContent = MaterialTheme.colorScheme.onSurfaceVariant
            titleAlpha = 0.5f
            urlAlpha = 0.4f
        }
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 8.dp),
            ) {
                Text(
                    text = titleText,
                    modifier = Modifier.alpha(titleAlpha),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.url,
                    modifier = Modifier.alpha(urlAlpha),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.weight(1f))

                ObserveChip(
                    text = chipText,
                    iconRes = chipIcon,
                    container = chipContainer,
                    content = chipContent,
                )
            }

            // `download_progress`: indeterminate, 0.7 alpha, covering the card while the worker
            // for this source is running.
            if (isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.7f),
                    trackColor = Color.Transparent,
                )
            }
        }
    }
}

/**
 * Non-interactive status chip shared by the grid tile and the details sheet. Mirrors
 * `Widget.Material3.Chip.Assist` metrics: 8 dp corners, 18 dp icon, 14 sp label.
 */
@Composable
internal fun ObserveChip(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(container)
            .padding(
                start = if (iconRes != null) 8.dp else 12.dp,
                top = 6.dp,
                end = 12.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = content,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = content,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** `"Wed, 19 Nov 2026 - 14:30"`, exactly the format the ViewHolder used. */
internal fun nextRunLabel(context: Context, item: ObserveSourcesItem): String {
    val c = Calendar.getInstance().apply { timeInMillis = item.calculateNextTimeForObserving() }
    val weekdays = DateFormatSymbols(Locale.getDefault()).shortWeekdays
    return context.getString(
        R.string.next_run,
        "${weekdays[c.get(Calendar.DAY_OF_WEEK)]}, ${nextRunDate(item)}",
    )
}

/** The date half of [nextRunLabel]; the details sheet shows it after the schedule summary. */
internal fun nextRunDate(item: ObserveSourcesItem): String {
    val c = Calendar.getInstance().apply { timeInMillis = item.calculateNextTimeForObserving() }
    return SimpleDateFormat(
        DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"),
        Locale.getDefault(),
    ).format(c.timeInMillis)
}
