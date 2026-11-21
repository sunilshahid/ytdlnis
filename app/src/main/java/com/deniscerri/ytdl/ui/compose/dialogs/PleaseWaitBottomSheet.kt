package com.deniscerri.ytdl.ui.compose.dialogs

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.components.YtdlBottomSheet
import com.deniscerri.ytdl.ui.compose.host.ComposeSheetFragment

/**
 * "Please wait" bottom sheet composable (Phase 3).
 *
 * Replaces `please_wait_bottom_sheet.xml`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PleaseWaitBottomSheet(
    onCancel: () -> Unit,
    onDismiss: () -> Unit = onCancel,
    modifier: Modifier = Modifier,
) {
    YtdlBottomSheet(onDismissRequest = onDismiss) {
        PleaseWaitContent(
            onCancel = onCancel,
            modifier = modifier,
        )
    }
}

@Composable
fun PleaseWaitContent(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.please_wait),
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(R.string.cancel),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * BottomSheetDialogFragment hosting the "Please wait" bottom sheet.
 */
class PleaseWaitBottomSheetFragment : ComposeSheetFragment() {

    var onCancelCallback: (() -> Unit)? = null

    @Composable
    override fun Content() {
        PleaseWaitContent(
            onCancel = {
                dismiss()
                onCancelCallback?.invoke()
            },
        )
    }

    companion object {
        const val TAG = "PleaseWaitBottomSheetFragment"

        fun newInstance(onCancel: () -> Unit = {}): PleaseWaitBottomSheetFragment {
            return PleaseWaitBottomSheetFragment().apply {
                onCancelCallback = onCancel
            }
        }
    }
}
