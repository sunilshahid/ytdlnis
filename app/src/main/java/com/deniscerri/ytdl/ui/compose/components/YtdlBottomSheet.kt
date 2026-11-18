package com.deniscerri.ytdl.ui.compose.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compose replacement for the app's `BottomSheetDialogFragment` boilerplate.
 *
 * The sheet still lives in its own window, so `YtdlnisTheme` must be above it in the composition
 * tree (the `ComposeSheetFragment` base does that). Colours come from the same theme bridge as the
 * rest of the app, which keeps the 7 accents and the `Pure` high-contrast overlay working.
 *
 * Prefer this over `ModalBottomSheet` directly so the container colours and drag handle stay
 * consistent across the ~26 migrated sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtdlBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        content = content,
    )
}
