package com.deniscerri.ytdl.ui.downloadcard

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.ui.compose.components.YtdlBottomSheet
import com.deniscerri.ytdl.ui.compose.host.ComposeSheetFragment
import com.deniscerri.ytdl.util.Extensions.convertToTimestamp
import com.deniscerri.ytdl.util.extractors.LrcLibUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lyrics bottom sheet dialog (Phase 5).
 *
 * Replaces `lyrics_sheet.xml`, `lyrics_line_item.xml`, and `lyrics_search_result_item.xml`.
 */
class LyricsBottomSheetDialog(
    private val _item: DownloadItem? = null,
    private val urls: String? = null,
    private val onChanged: (() -> Unit)? = null
) : ComposeSheetFragment() {

    @Composable
    override fun Content() {
        val item = _item ?: run {
            dismiss()
            return
        }

        LyricsConfigSheet(
            item = item,
            onDismiss = {
                onChanged?.invoke()
                dismiss()
            },
            onChanged = {
                onChanged?.invoke()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsConfigSheet(
    item: DownloadItem,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = item.audioPreferences
    var embedLyrics by remember { mutableStateOf(prefs.embedLyrics) }
    var saveLrc by remember { mutableStateOf(prefs.saveLrc) }
    var embedTtml by remember { mutableStateOf(prefs.embedTtml) }
    var offset by remember { mutableLongStateOf(prefs.lyricsOffset) }

    var lyrics by remember { mutableStateOf<LrcLibUtil.Lyrics?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(item.title, item.author) {
        isLoading = true
        lyrics = withContext(Dispatchers.IO) {
            val chosen = item.audioPreferences.lyricsId
            if (chosen > 0) {
                LrcLibUtil.byId(chosen.toLong())
            } else {
                LrcLibUtil.find(
                    item.title,
                    item.author,
                    "",
                    (item.duration.convertToTimestamp() / 1000).toInt()
                )
            }
        }
        isLoading = false
    }

    YtdlBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lyrics),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.title.ifEmpty { item.url },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.done))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Switch: Embed Lyrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.embed_lyrics),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = embedLyrics,
                    onCheckedChange = { checked ->
                        embedLyrics = checked
                        prefs.embedLyrics = checked
                        if (!checked) {
                            embedTtml = false
                            prefs.embedTtml = false
                        }
                        onChanged()
                    }
                )
            }

            // Switch: Save LRC
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.save_lrc),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = saveLrc,
                    onCheckedChange = { checked ->
                        saveLrc = checked
                        prefs.saveLrc = checked
                        onChanged()
                    }
                )
            }

            // Switch: Embed TTML
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.embed_ttml),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = embedTtml,
                    enabled = embedLyrics,
                    onCheckedChange = { checked ->
                        embedTtml = checked
                        prefs.embedTtml = checked
                        if (checked && !embedLyrics) {
                            embedLyrics = true
                            prefs.embedLyrics = true
                        }
                        onChanged()
                    }
                )
            }

            // Offset Adjuster
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${stringResource(R.string.lyrics_offset)}: ${if (offset > 0) "+$offset" else "$offset"} ms",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        offset = (offset - 500L).coerceIn(-30_000L, 30_000L)
                        prefs.lyricsOffset = offset
                        onChanged()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("-500ms")
                }
                FilledTonalButton(
                    onClick = {
                        offset = (offset - 100L).coerceIn(-30_000L, 30_000L)
                        prefs.lyricsOffset = offset
                        onChanged()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("-100ms")
                }
                FilledTonalButton(
                    onClick = {
                        offset = (offset + 100L).coerceIn(-30_000L, 30_000L)
                        prefs.lyricsOffset = offset
                        onChanged()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+100ms")
                }
                FilledTonalButton(
                    onClick = {
                        offset = (offset + 500L).coerceIn(-30_000L, 30_000L)
                        prefs.lyricsOffset = offset
                        onChanged()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+500ms")
                }
            }

            // Lyrics Preview Section
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.preview),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                val currentLyrics = lyrics
                if (currentLyrics != null) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "${currentLyrics.artistName} - ${currentLyrics.trackName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (currentLyrics.isSynced) {
                                currentLyrics.synced.forEach { line ->
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = currentLyrics.plain,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.lyrics_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
