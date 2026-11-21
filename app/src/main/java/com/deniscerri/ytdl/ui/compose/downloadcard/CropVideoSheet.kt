package com.deniscerri.ytdl.ui.compose.downloadcard

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.downloadcard.crop.CropOverlayView
import kotlin.math.roundToInt

/**
 * Crop video sheet replacing crop_video_sheet.xml (A20).
 * Wraps ExoPlayer PlayerView and CropOverlayView inside AndroidView.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CropVideoSheet(
    videoUrl: String,
    initialCropValues: String,
    videoWidth: Int = 1920,
    videoHeight: Int = 1080,
    onConfirm: (cropValues: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var cropValues by remember { mutableStateOf(initialCropValues) }
    var selectedAspect by remember { mutableStateOf("Free") }
    var overlayRef by remember { mutableStateOf<CropOverlayView?>(null) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            if (videoUrl.isNotBlank()) {
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
                prepare()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    fun computeCrop(): String {
        val overlay = overlayRef ?: return cropValues
        val vw = if (videoWidth > 0) videoWidth else 1920
        val vh = if (videoHeight > 0) videoHeight else 1080
        val viewW = overlay.width.toFloat()
        val viewH = overlay.height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return cropValues

        val scaleX = vw.toFloat() / viewW
        val scaleY = vh.toFloat() / viewH

        val x = (overlay.cropLeftValue * scaleX).roundToInt().coerceAtLeast(0)
        val y = (overlay.cropTopValue * scaleY).roundToInt().coerceAtLeast(0)
        val w = ((overlay.cropRightValue - overlay.cropLeftValue) * scaleX).roundToInt().coerceAtMost(vw - x)
        val h = ((overlay.cropBottomValue - overlay.cropTopValue) * scaleY).roundToInt().coerceAtMost(vh - y)

        return "$x:$y:$w:$h:$vw:$vh"
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(scrollState)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.crop),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Video Player + CropOverlayView stack
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            // PlayerView
            if (videoUrl.isNotBlank()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.matchParentSize()
                )
            }

            // CropOverlayView wrapped in AndroidView
            AndroidView(
                factory = { ctx ->
                    CropOverlayView(ctx, null).apply {
                        onCropChanged = {
                            cropValues = computeCrop()
                        }
                        overlayRef = this
                    }
                },
                modifier = Modifier.matchParentSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Aspect ratio options
        Text(
            text = "Aspect Ratio",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        val aspects = listOf("Free", "16:9", "4:3", "1:1", "9:16")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            aspects.forEach { aspect ->
                FilterChip(
                    selected = selectedAspect == aspect,
                    onClick = {
                        selectedAspect = aspect
                        overlayRef?.let { overlay ->
                            when (aspect) {
                                "16:9" -> overlay.aspectRatio = 16f / 9f
                                "4:3" -> overlay.aspectRatio = 4f / 3f
                                "1:1" -> overlay.aspectRatio = 1f
                                "9:16" -> overlay.aspectRatio = 9f / 16f
                                else -> overlay.aspectRatio = null
                            }
                            overlay.setCrop(0f, 0f, overlay.width.toFloat(), overlay.height.toFloat())
                        }
                    },
                    label = { Text(aspect) }
                )
            }
        }

        if (cropValues.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Crop: $cropValues",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    overlayRef?.let { overlay ->
                        overlay.setCrop(0f, 0f, overlay.width.toFloat(), overlay.height.toFloat())
                    }
                    cropValues = ""
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.reset))
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = {
                    val finalCrop = if (cropValues.isBlank()) computeCrop() else cropValues
                    onConfirm(finalCrop)
                },
                modifier = Modifier.weight(1.5f)
            ) {
                Text(stringResource(R.string.ok))
            }
        }
    }
}
