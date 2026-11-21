package com.deniscerri.ytdl.ui.compose.dialogs

import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.host.ComposeDialogFragment

/**
 * Terminate App confirmation dialog composable (Phase 3).
 *
 * Replaces `dialog_terminate_app.xml`.
 */
@Composable
fun TerminateAppDialog(
    onConfirm: (doNotShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.kill_app),
    initialDoNotShowAgain: Boolean = false,
) {
    var doNotShowAgain by remember { mutableStateOf(initialDoNotShowAgain) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.confirm_terminate),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { doNotShowAgain = !doNotShowAgain }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = doNotShowAgain,
                        onCheckedChange = { doNotShowAgain = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.dont_ask_again),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(doNotShowAgain) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * DialogFragment hosting [TerminateAppDialog].
 */
class TerminateAppDialogFragment : ComposeDialogFragment() {

    var onConfirmCallback: ((doNotShowAgain: Boolean) -> Unit)? = null
    var onDismissCallback: (() -> Unit)? = null

    @Composable
    override fun Content() {
        val title = arguments?.getString(ARG_TITLE) ?: stringResource(R.string.kill_app)
        val initialDoNotShowAgain = arguments?.getBoolean(ARG_DO_NOT_SHOW_AGAIN, false) ?: false

        TerminateAppDialog(
            title = title,
            initialDoNotShowAgain = initialDoNotShowAgain,
            onConfirm = { doNotShowAgain ->
                dismiss()
                onConfirmCallback?.invoke(doNotShowAgain)
            },
            onDismiss = {
                dismiss()
                onDismissCallback?.invoke()
            },
        )
    }

    companion object {
        const val TAG = "TerminateAppDialogFragment"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_DO_NOT_SHOW_AGAIN = "arg_do_not_show_again"

        fun newInstance(
            title: String,
            initialDoNotShowAgain: Boolean = false,
            onConfirm: (doNotShowAgain: Boolean) -> Unit,
            onDismiss: () -> Unit = {},
        ): TerminateAppDialogFragment {
            return TerminateAppDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putBoolean(ARG_DO_NOT_SHOW_AGAIN, initialDoNotShowAgain)
                }
                onConfirmCallback = onConfirm
                onDismissCallback = onDismiss
            }
        }
    }
}
