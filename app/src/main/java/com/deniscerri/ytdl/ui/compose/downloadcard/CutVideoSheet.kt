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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.deniscerri.ytdl.ui.compose.components.YtdlSwitch
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp

/**
 * Cut video sheet replacing cut_video_sheet.xml (A19).
 * Wraps ExoPlayer PlayerView in AndroidView with timestamp selection and chapter chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CutVideoSheet(
    videoUrl: String,
    initialStart: String,
    initialEnd: String,
    chapters: List<ChapterItem> = emptyList(),
    initialMergeCuts: Boolean = false,
    onConfirm: (start: String, end: String, mergeCuts: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var startTime by remember { mutableStateOf(initialStart) }
    var endTime by remember { mutableStateOf(initialEnd) }
    var mergeCuts by remember { mutableStateOf(initialMergeCuts) }

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
            text = stringResource(R.string.cut_video),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // PlayerView
        if (videoUrl.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.matchParentSize()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Chapters chips
        if (chapters.isNotEmpty()) {
            Text(
                text = stringResource(R.string.chapters),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                chapters.forEach { chapter ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            startTime = chapter.start_time.toDouble().toLong().toStringTimeStamp()
                            endTime = chapter.end_time.toDouble().toLong().toStringTimeStamp()
                        },
                        label = { Text(chapter.title) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Timestamp input fields and "Set Current Position" buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text(stringResource(R.string.start_time)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        val currentMs = exoPlayer.currentPosition
                        startTime = (currentMs / 1000).toStringTimeStamp()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("[ Current Pos")
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = { Text(stringResource(R.string.end_time)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        val currentMs = exoPlayer.currentPosition
                        endTime = (currentMs / 1000).toStringTimeStamp()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Current Pos ]")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Merge cuts toggle
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.merge_cuts),
                    style = MaterialTheme.typography.bodyMedium
                )
                YtdlSwitch(
                    checked = mergeCuts,
                    onCheckedChange = { mergeCuts = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = { onConfirm(startTime, endTime, mergeCuts) },
                modifier = Modifier.weight(1.5f)
            ) {
                Text(stringResource(R.string.ok))
            }
        }
    }
}
