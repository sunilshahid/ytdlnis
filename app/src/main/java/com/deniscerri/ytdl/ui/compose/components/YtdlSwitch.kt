package com.deniscerri.ytdl.ui.compose.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.deniscerri.ytdl.R

/**
 * Material 3 Expressive switch with a tick (on) / cross (off) thumb.
 *
 * The 1.5.0 `Switch` already draws the Expressive 52 x 32 track and animates the handle between
 * 16 dp (off) and 28 dp (on); the icon is the only missing piece.
 */
@Composable
fun YtdlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(
        checkedIconColor = MaterialTheme.colorScheme.onPrimary,
        uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
    ),
    interactionSource: MutableInteractionSource? = null,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = thumbContent ?: {
            Icon(
                painter = painterResource(
                    if (checked) R.drawable.ic_check else R.drawable.baseline_close_24
                ),
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}
