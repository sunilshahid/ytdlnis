package com.deniscerri.ytdl.ui.compose.settings

import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.deniscerri.ytdl.ui.compose.components.YtdlSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.components.ConfirmDialog
import com.deniscerri.ytdl.ui.compose.components.InputDialog
import com.deniscerri.ytdl.ui.compose.components.PreferenceCard
import com.deniscerri.ytdl.ui.compose.components.SectionHeader
import com.deniscerri.ytdl.ui.compose.components.SingleChoiceDialog
import kotlin.math.roundToInt

/**
 * Standard scrollable column container for all Settings screens.
 */
@Composable
fun SettingsScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        content = content
    )
}

/**
 * Material 3 Settings Category header.
 */
@Composable
fun SettingsCategory(
    title: String,
    modifier: Modifier = Modifier,
) {
    SectionHeader(
        text = title,
        modifier = modifier,
    )
}

/**
 * Material 3 standard Clickable Preference card.
 */
@Composable
fun SettingsPreference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    badgeColor: Color? = null,
    iconTint: Color? = null,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    PreferenceCard(
        title = title,
        modifier = if (enabled) modifier else modifier.alpha(0.5f),
        summary = summary,
        iconRes = iconRes,
        badgeColor = badgeColor,
        iconTint = iconTint,
        showChevron = showChevron,
        onClick = if (enabled) onClick else null,
        trailing = trailing,
    )
}

/**
 * Material 3 Switch Preference.
 *
 * If [key] is supplied and [checked] is null, state is automatically wired to [SharedPreferences].
 */
@Composable
fun SettingsSwitchPreference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    key: String? = null,
    defaultValue: Boolean = false,
    checked: Boolean? = null,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var internalChecked by remember(key) {
        mutableStateOf(if (key != null) prefs.getBoolean(key, defaultValue) else defaultValue)
    }

    if (key != null) {
        DisposableEffect(key, prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    internalChecked = prefs.getBoolean(key, defaultValue)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    val isChecked = checked ?: internalChecked

    val toggle = {
        if (enabled) {
            val newVal = !isChecked
            if (key != null) {
                prefs.edit().putBoolean(key, newVal).apply()
                internalChecked = newVal
            }
            onCheckedChange?.invoke(newVal)
        }
    }

    PreferenceCard(
        title = title,
        modifier = if (enabled) modifier else modifier.alpha(0.5f),
        summary = summary,
        iconRes = iconRes,
        onClick = if (enabled) toggle else null,
        trailing = {
            YtdlSwitch(
                checked = isChecked,
                onCheckedChange = if (enabled) { _ -> toggle() } else null,
                enabled = enabled,
            )
        }
    )
}

/**
 * Material 3 Single-Choice List Preference.
 *
 * If [key] is supplied and [value] is null, state is automatically wired to [SharedPreferences].
 */
@Composable
fun SettingsListPreference(
    title: String,
    dialogTitle: String = title,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    key: String? = null,
    defaultValue: String = "",
    entries: List<String>,
    entryValues: List<String>,
    value: String? = null,
    enabled: Boolean = true,
    onValueChange: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var internalValue by remember(key) {
        mutableStateOf(if (key != null) prefs.getString(key, defaultValue) ?: defaultValue else defaultValue)
    }

    if (key != null) {
        DisposableEffect(key, prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    internalValue = prefs.getString(key, defaultValue) ?: defaultValue
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    val currentValue = value ?: internalValue
    var showDialog by remember { mutableStateOf(false) }

    val selectedIndex = entryValues.indexOf(currentValue).let { if (it >= 0) it else 0 }
    val displaySummary = summary ?: entries.getOrNull(entryValues.indexOf(currentValue)) ?: currentValue

    PreferenceCard(
        title = title,
        modifier = if (enabled) modifier else modifier.alpha(0.5f),
        summary = displaySummary,
        iconRes = iconRes,
        showChevron = true,
        onClick = if (enabled) { { showDialog = true } } else null,
    )

    if (showDialog) {
        SingleChoiceDialog(
            title = dialogTitle,
            options = entries,
            selectedIndex = selectedIndex,
            onSelect = { idx ->
                val newValue = entryValues.getOrElse(idx) { "" }
                if (key != null) {
                    prefs.edit().putString(key, newValue).apply()
                    internalValue = newValue
                }
                onValueChange?.invoke(newValue)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Material 3 Slider Preference.
 *
 * If [key] is supplied and [value] is null, state is automatically wired to [SharedPreferences].
 */
@Suppress("DEPRECATION")
@Composable
fun SettingsSliderPreference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    key: String? = null,
    defaultValue: Int = 0,
    min: Int = 0,
    max: Int = 100,
    value: Int? = null,
    enabled: Boolean = true,
    onValueChange: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var internalValue by remember(key) {
        mutableIntStateOf(if (key != null) prefs.getInt(key, defaultValue) else defaultValue)
    }

    if (key != null) {
        DisposableEffect(key, prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    internalValue = prefs.getInt(key, defaultValue)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    val currentValue = value ?: internalValue
    var sliderValue by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }

    val shape = RoundedCornerShape(20.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.5.dp)
            .then(if (enabled) Modifier else Modifier.alpha(0.5f)),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = border,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 11.dp, end = 16.dp, bottom = 11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconRes != null) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (!summary.isNullOrBlank()) {
                        Text(
                            text = summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }
                Text(
                    text = sliderValue.roundToInt().toString(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (enabled) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        val intVal = sliderValue.roundToInt()
                        if (key != null) {
                            prefs.edit().putInt(key, intVal).apply()
                            internalValue = intVal
                        }
                        onValueChange?.invoke(intVal)
                    },
                    valueRange = min.toFloat()..max.toFloat(),
                    steps = if (max - min > 1) (max - min - 1) else 0,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Material 3 Multi-Select List Preference.
 *
 * If [key] is supplied and [values] is null, state is automatically wired to [SharedPreferences].
 */
@Composable
fun SettingsMultiSelectListPreference(
    title: String,
    dialogTitle: String = title,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    key: String? = null,
    defaultValue: Set<String> = emptySet(),
    entries: List<String>,
    entryValues: List<String>,
    values: Set<String>? = null,
    enabled: Boolean = true,
    onValuesChange: ((Set<String>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var internalValues by remember(key) {
        mutableStateOf(if (key != null) prefs.getStringSet(key, defaultValue) ?: defaultValue else defaultValue)
    }

    if (key != null) {
        DisposableEffect(key, prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    internalValues = prefs.getStringSet(key, defaultValue) ?: defaultValue
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    val currentValues = values ?: internalValues
    var showDialog by remember { mutableStateOf(false) }

    val displaySummary = summary ?: if (currentValues.isEmpty()) {
        null
    } else {
        currentValues.mapNotNull { v ->
            val idx = entryValues.indexOf(v)
            if (idx >= 0) entries.getOrNull(idx) else null
        }.joinToString(", ")
    }

    PreferenceCard(
        title = title,
        modifier = if (enabled) modifier else modifier.alpha(0.5f),
        summary = displaySummary,
        iconRes = iconRes,
        showChevron = true,
        onClick = if (enabled) { { showDialog = true } } else null,
    )

    if (showDialog) {
        var selectedValues by remember { mutableStateOf(currentValues.toSet()) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(dialogTitle) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    entries.forEachIndexed { index, option ->
                        val entryVal = entryValues.getOrElse(index) { "" }
                        val isChecked = selectedValues.contains(entryVal)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedValues = if (isChecked) {
                                        selectedValues - entryVal
                                    } else {
                                        selectedValues + entryVal
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedValues = if (checked) {
                                        selectedValues + entryVal
                                    } else {
                                        selectedValues - entryVal
                                    }
                                }
                            )
                            Text(
                                text = option,
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (key != null) {
                            prefs.edit().putStringSet(key, selectedValues).apply()
                            internalValues = selectedValues
                        }
                        onValuesChange?.invoke(selectedValues)
                        showDialog = false
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Material 3 EditText Preference.
 *
 * If [key] is supplied and [value] is null, state is automatically wired to [SharedPreferences].
 */
@Composable
fun SettingsEditTextPreference(
    title: String,
    dialogTitle: String = title,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    key: String? = null,
    defaultValue: String = "",
    value: String? = null,
    enabled: Boolean = true,
    hint: String? = null,
    onValueChange: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var internalValue by remember(key) {
        mutableStateOf(if (key != null) prefs.getString(key, defaultValue) ?: defaultValue else defaultValue)
    }

    if (key != null) {
        DisposableEffect(key, prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    internalValue = prefs.getString(key, defaultValue) ?: defaultValue
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    val currentValue = value ?: internalValue
    var showDialog by remember { mutableStateOf(false) }

    val displaySummary = summary ?: currentValue.ifBlank { null }

    PreferenceCard(
        title = title,
        modifier = if (enabled) modifier else modifier.alpha(0.5f),
        summary = displaySummary,
        iconRes = iconRes,
        showChevron = true,
        onClick = if (enabled) { { showDialog = true } } else null,
    )

    if (showDialog) {
        InputDialog(
            title = dialogTitle,
            initialValue = currentValue,
            hint = hint,
            onConfirm = { newVal ->
                if (key != null) {
                    prefs.edit().putString(key, newVal).apply()
                    internalValue = newVal
                }
                onValueChange?.invoke(newVal)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Material 3 Reset Preferences preference button with confirm dialog.
 */
@Composable
fun ResetPreferencesPreference(
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.reset),
    summary: String = stringResource(R.string.reset_preferences_in_screen),
    @DrawableRes iconRes: Int = R.drawable.baseline_reset_tv_24,
) {
    var showConfirm by remember { mutableStateOf(false) }

    SettingsPreference(
        title = title,
        summary = summary,
        iconRes = iconRes,
        modifier = modifier,
        onClick = { showConfirm = true }
    )

    if (showConfirm) {
        ConfirmDialog(
            title = title,
            message = summary,
            confirmText = stringResource(R.string.reset),
            onConfirm = {
                showConfirm = false
                onReset()
            },
            onDismiss = { showConfirm = false }
        )
    }
}

/**
 * Full-width danger button composable replacing DangerButtonPreference.
 */
@Composable
fun SettingsDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        androidx.compose.material3.Button(
            onClick = onClick,
            enabled = enabled,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * Temporary files summary card composable replacing TemporaryFilesSummaryPreference.
 */
@Composable
fun TemporaryFilesSummaryCard(
    totalSize: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
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
}

/**
 * Size preference row composable replacing SizePreference.
 */
@Composable
fun SettingsSizePreference(
    title: String,
    sizeText: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    SettingsPreference(
        title = title,
        summary = if (sizeText.isNotBlank()) "$sizeText · ${summary.orEmpty()}".trim().removeSuffix("·").trim() else summary,
        iconRes = iconRes,
        modifier = modifier,
        onClick = onClick,
        trailing = {
            if (sizeText.isNotBlank()) {
                Text(
                    text = sizeText,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    )
}

/**
 * Update channel selector composable replacing UpdateChannelPreference.
 */
@Composable
fun UpdateChannelSelector(
    channels: List<Pair<String, String>>,
    selectedChannel: String,
    onChannelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        channels.forEach { (value, label) ->
            val isSelected = value == selectedChannel
            val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            val border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onChannelSelected(value) },
                shape = RoundedCornerShape(12.dp),
                color = containerColor,
                contentColor = contentColor,
                border = border,
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

/**
 * Utility for formatting update check times.
 */
object UpdateStatusCardUtil {
    fun buildDetail(context: android.content.Context, lastCheck: Long): String {
        val now = System.currentTimeMillis()
        return when {
            lastCheck <= 0L -> context.getString(R.string.update_not_checked_yet)
            now - lastCheck < android.text.format.DateUtils.MINUTE_IN_MILLIS -> context.getString(R.string.update_checked_just_now)
            else -> context.getString(
                R.string.update_checked_ago,
                android.text.format.DateUtils.getRelativeTimeSpanString(lastCheck, now, android.text.format.DateUtils.MINUTE_IN_MILLIS)
            )
        }
    }
}

/**
 * Update status card composable replacing UpdateStatusCardPreference.
 */
@Composable
fun UpdateStatusCard(
    title: String,
    version: String?,
    subtitle: String?,
    detail: String?,
    isChecking: Boolean = false,
    showButton: Boolean = true,
    buttonText: String = stringResource(R.string.check_now),
    onButtonClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (!detail.isNullOrBlank()) {
                        Text(
                            text = detail,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                if (showButton) {
                    androidx.compose.material3.Button(
                        onClick = onButtonClick,
                        enabled = !isChecking,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isChecking) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(buttonText)
                        }
                    }
                }
            }
        }
    }
}

