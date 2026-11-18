package com.deniscerri.ytdl.ui.compose.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.ui.compose.components.PicassoImage
import com.deniscerri.ytdl.util.FileUtil

/**
 * Material 3 Active Download Card (Phase 5).
 *
 * Displays live downloading / paused item with progress indicator, thumbnail backdrop,
 * metadata pills, real-time speed/ETA output, and tactile pause/resume/cancel buttons.
 */
@Composable
fun ActiveDownloadCard(
    item: DownloadItem,
    progress: Int,
    output: String?,
    modifier: Modifier = Modifier,
    onPauseClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onOutputClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(20.dp)
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "activeCardScale",
    )

    val isPaused = item.status == DownloadRepository.Status.Paused.toString()
    val displayTitle = item.title.ifEmpty { item.playlistTitle.ifEmpty { item.url } }

    val authorDuration = buildString {
        if (item.author.isNotEmpty()) append(item.author)
        if (item.duration.isNotEmpty() && item.duration != "-1") {
            if (isNotEmpty()) append(" • ")
            append(item.duration)
        }
    }

    val formatPillText = remember(item.format, item.container, item.downloadSections) {
        val sideDetails = mutableListOf<String>()
        val note = item.format.format_note.uppercase().replace("\n", " ").trim()
        if (note.isNotEmpty()) sideDetails.add(note)

        val container = item.container.uppercase().ifEmpty { item.format.container.uppercase() }.trim()
        if (container.isNotEmpty()) sideDetails.add(container)

        val fileSize = FileUtil.convertFileSize(item.format.filesize)
        if (fileSize != "?" && item.downloadSections.isBlank()) {
            sideDetails.add(fileSize)
        }
        sideDetails.filter { it.isNotBlank() }.joinToString("  ·  ")
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
        ) {
            // Background thumbnail with subtle darkening overlay
            PicassoImage(
                url = item.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Scrim overlay for high contrast readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.78f),
                            )
                        )
                    )
            )

            // Progress bar overlay (visible across the card)
            if (!isPaused && progress > 0) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            } else if (!isPaused && progress <= 0) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }

            // Foreground Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Row: Type Badge + Format Chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val typeIconRes = when (item.type) {
                        DownloadType.audio -> R.drawable.ic_music
                        DownloadType.video -> R.drawable.ic_video
                        DownloadType.command -> R.drawable.ic_terminal
                        else -> R.drawable.ic_music
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(typeIconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp),
                        )
                    }

                    if (formatPillText.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = formatPillText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                }

                // Middle Row: Title & Author on left, Action buttons on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (authorDuration.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = authorDuration,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Action Buttons (Pause/Resume + Cancel)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Pause / Resume Button
                        IconButton(
                            onClick = if (isPaused) onResumeClick else onPauseClick,
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isPaused) R.drawable.exomedia_ic_play_arrow_white
                                    else R.drawable.exomedia_ic_pause_white
                                ),
                                contentDescription = stringResource(
                                    if (isPaused) R.string.resume else R.string.exo_download_paused
                                ),
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // Cancel Button
                        IconButton(
                            onClick = onCancelClick,
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_close_24),
                                contentDescription = stringResource(R.string.Remove),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Bottom: Monospace live output text (clickable to view log)
                val statusText = when {
                    isPaused -> stringResource(R.string.exo_download_paused)
                    !output.isNullOrBlank() -> output
                    progress > 0 -> "$progress%"
                    else -> "..."
                }

                Text(
                    text = statusText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOutputClick)
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}
