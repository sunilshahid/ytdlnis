package com.deniscerri.ytdl.ui.home

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel.AvailableUpdate
import com.deniscerri.ytdl.ui.compose.host.ComposeDialogFragment

/**
 * Dialog listing the app and package updates found at startup. Picking one hands it to the parent
 * fragment.
 *
 * Port of `updates_dialog_title.xml`, `updates_dialog.xml` and `update_item.xml`. The list is driven
 * by [ResultViewModel.availableUpdates] (a `StateFlow`), and the dialog still closes itself once the
 * last update is dismissed, which also hides the toolbar button.
 */
class UpdatesDialog : ComposeDialogFragment() {

    interface Listener {
        fun onUpdateSelected(update: AvailableUpdate)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The Material 3 `AlertDialog` in Content() owns the visible window; this one stays a
        // transparent, full-screen host so there is no second dialog frame or dim layer.
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Translucent_NoTitleBar)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setDimAmount(0f)
    }

    @Composable
    override fun Content() {
        val resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        val updates by resultViewModel.availableUpdates.collectAsState()

        LaunchedEffect(updates) {
            if (updates.isEmpty()) dismissAllowingStateLoss()
        }

        val summary = pluralStringResource(
            R.plurals.updates_available_summary,
            updates.size,
            updates.size,
        )

        AlertDialog(
            onDismissRequest = { dismiss() },
            title = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = stringResource(R.string.update),
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = summary,
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(updates, key = { it.stableKey() }) { update ->
                        UpdateRow(
                            update = update,
                            onClick = {
                                dismiss()
                                (parentFragment as? Listener)?.onUpdateSelected(update)
                            },
                            onClose = { resultViewModel.dismissUpdate(update) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resultViewModel.dismissAllUpdates()
                        dismiss()
                    },
                ) {
                    Text(stringResource(R.string.dismiss))
                }
            },
        )
    }
}

/**
 * 1:1 with `update_item.xml`: 20 dp `colorPrimaryContainer` card, 40 dp tonal indicator, 15 sp
 * medium title, 12 sp subtitle and a close icon button.
 */
@Composable
private fun UpdateRow(
    update: AvailableUpdate,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val indicatorColor = when (update) {
        is AvailableUpdate.App -> MaterialTheme.colorScheme.primaryContainer
        is AvailableUpdate.Package -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val title = when (update) {
        is AvailableUpdate.App -> stringResource(
            R.string.version_ready_to_download,
            update.release.tag_name,
        )
        is AvailableUpdate.Package -> "${update.item.title} (${update.release.version})"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(indicatorColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_down),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                )
                Text(
                    text = stringResource(R.string.tap_to_see_changes_and_install),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .alpha(0.8f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }

            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.baseline_close_24),
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** `DiffUtil` treated every app update as the same item; packages are keyed by title. */
private fun AvailableUpdate.stableKey(): String = when (this) {
    is AvailableUpdate.App -> "app"
    is AvailableUpdate.Package -> "package:${item.title}"
}
