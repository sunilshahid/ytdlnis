package com.deniscerri.ytdl.ui.compose.downloadcard

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.util.FileUtil

/**
 * Format details sheet replacing format_details_sheet.xml (A31).
 */
@Composable
fun FormatDetailsSheet(
    format: Format,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    fun copy(text: String, label: String) {
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

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
            text = stringResource(R.string.format_details),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (format.format_id.isNotBlank() && format.format_id != "None") {
            FormatDetailItem(label = "Format ID", value = format.format_id) { copy(format.format_id, "Format ID") }
        }
        if (format.container.isNotBlank() && format.container != "None") {
            FormatDetailItem(label = "Container", value = format.container) { copy(format.container, "Container") }
        }
        val codec = when {
            format.encoding.isNotBlank() -> format.encoding.uppercase()
            format.vcodec.isNotBlank() && format.vcodec != "none" -> format.vcodec.uppercase()
            format.acodec.isNotBlank() && format.acodec != "none" -> format.acodec.uppercase()
            else -> ""
        }
        if (codec.isNotBlank()) {
            FormatDetailItem(label = "Codec", value = codec) { copy(codec, "Codec") }
        }
        if (format.filesize > 0L) {
            val sizeStr = FileUtil.convertFileSize(format.filesize)
            FormatDetailItem(label = "File Size", value = sizeStr) { copy(sizeStr, "File Size") }
        }
        if (format.format_note.isNotBlank() && format.format_note != "None") {
            FormatDetailItem(label = "Format Note", value = format.format_note) { copy(format.format_note, "Format Note") }
        }
        val fps = format.fps.orEmpty()
        if (fps.isNotBlank() && fps != "0" && fps != "None") {
            FormatDetailItem(label = "FPS", value = fps) { copy(fps, "FPS") }
        }
        val asr = format.asr.orEmpty()
        if (asr.isNotBlank() && asr != "None") {
            FormatDetailItem(label = "Sample Rate (ASR)", value = asr) { copy(asr, "ASR") }
        }
        val tbr = format.tbr.orEmpty()
        if (tbr.isNotBlank() && tbr != "None") {
            FormatDetailItem(label = "Bitrate (TBR)", value = tbr) { copy(tbr, "Bitrate") }
        }
        if ((format.width ?: 0) > 0 || (format.height ?: 0) > 0) {
            val res = "${format.width ?: "?"} x ${format.height ?: "?"}"
            FormatDetailItem(label = "Resolution", value = res) { copy(res, "Resolution") }
        }
        val streamUrl = format.url.orEmpty()
        if (streamUrl.isNotBlank() && streamUrl != "None") {
            FormatDetailItem(label = "Stream URL", value = streamUrl) { copy(streamUrl, "URL") }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FormatDetailItem(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = "Copy",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
