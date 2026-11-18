package com.deniscerri.ytdl.ui.compose.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * Replaces `filter_chip.xml` and `suggestion_chip.xml`.
 *
 * Those layouts are thin wrappers over `Widget.Material3.Chip.*`, so the Compose equivalent is the
 * Material 3 `FilterChip` with the same theme-derived colours. Selection state is hoisted, so the
 * chip owns no state and behaves predictably inside a `LazyRow`.
 */
@Composable
fun YtdlChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { ChipLabel(label) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

/** Replaces `textview_chip.xml` and the non-selectable `input_chip.xml` usages. */
@Composable
fun YtdlActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    AssistChip(
        onClick = onClick,
        label = { ChipLabel(label) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
    )
}

@Composable
private fun ChipLabel(label: String) {
    Text(
        text = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
