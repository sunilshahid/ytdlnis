package com.deniscerri.ytdl.ui.compose.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.TemporaryFilesUtil

@Composable
fun TemporaryFilesSettingsScreen(
    totalSize: String,
    categorySizes: Map<TemporaryFilesUtil.Category, String>,
    onClearCategory: (TemporaryFilesUtil.Category) -> Unit,
    onMoveCache: () -> Unit,
    onClearAll: () -> Unit,
    hasFilesToClear: Boolean,
    modifier: Modifier = Modifier,
) {
    SettingsScreenColumn(modifier = modifier) {
        // Summary Header Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = totalSize.ifBlank { "0 B" },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.temporary_files),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        SettingsCategory(title = stringResource(R.string.temporary_files))

        // Categories
        TemporaryFilesUtil.Category.values().forEach { category ->
            val size = categorySizes[category].orEmpty()
            val summaryText = stringResource(category.description)
            val fullSummary = if (size.isNotBlank()) "$size · $summaryText" else summaryText

            val iconRes = when (category) {
                TemporaryFilesUtil.Category.UNFINISHED_DOWNLOADS -> R.drawable.baseline_downloading_24
                TemporaryFilesUtil.Category.INFO_JSONS -> R.drawable.outline_data_object_24
                TemporaryFilesUtil.Category.YTDLP_CACHE -> R.drawable.ic_terminal
                TemporaryFilesUtil.Category.TEMP_CONFIGS -> R.drawable.baseline_settings_suggest_24
                TemporaryFilesUtil.Category.DOWNLOADED_APKS -> R.drawable.baseline_install_mobile_24
            }

            SettingsPreference(
                title = stringResource(category.title),
                summary = fullSummary,
                iconRes = iconRes,
                onClick = { onClearCategory(category) },
            )
        }

        // Move Cache
        SettingsPreference(
            title = stringResource(R.string.move_temporary_files),
            summary = stringResource(R.string.move_temporary_files_summary),
            iconRes = R.drawable.baseline_drive_file_move_24,
            showChevron = true,
            onClick = onMoveCache,
        )

        // Clear All Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onClearAll,
                enabled = hasFilesToClear,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.clear_everything),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
