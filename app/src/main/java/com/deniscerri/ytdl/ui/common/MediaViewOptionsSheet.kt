package com.deniscerri.ytdl.ui.common

import android.content.DialogInterface
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.setFragmentResult
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.host.ComposeSheetFragment
import com.deniscerri.ytdl.ui.play.PlayField
import com.deniscerri.ytdl.ui.play.PlaySort
import com.deniscerri.ytdl.ui.play.PlayViewPrefs
import com.deniscerri.ytdl.ui.play.device.DeviceField
import com.deniscerri.ytdl.ui.play.device.DeviceFilesPrefs
import com.deniscerri.ytdl.ui.play.device.DeviceLayoutMode
import com.deniscerri.ytdl.ui.play.device.DeviceShowMode
import com.deniscerri.ytdl.ui.play.device.DeviceSort
import com.deniscerri.ytdl.util.SortCategory
import com.deniscerri.ytdl.util.SortOrderLabels
import com.deniscerri.ytdl.vault.VaultField
import com.deniscerri.ytdl.vault.VaultLayoutMode
import com.deniscerri.ytdl.vault.VaultPickerField
import com.deniscerri.ytdl.vault.VaultPickerSortBy
import com.deniscerri.ytdl.vault.VaultSettings
import com.deniscerri.ytdl.vault.VaultSortBy

/**
 * Shared bottom sheet for configuring Layout, Sort, Order (with labels adapted to the
 * active sort field), and visible metadata fields across Device Files, Play, and Private Vault.
 *
 * Migrated to Jetpack Compose (replaces dialog_media_view_options.xml).
 */
class MediaViewOptionsSheet : ComposeSheetFragment() {

    enum class Mode {
        DEVICE,
        PLAY,
        VAULT,
        VAULT_PICKER
    }

    private var changed = false

    // Transient working state for Mode.VAULT & Mode.VAULT_PICKER (persisted by VaultActivity on result).
    private var vaultLayout by mutableStateOf(VaultLayoutMode.GRID)
    private var vaultSort by mutableStateOf(VaultSortBy.DATE)
    private var vaultDescending by mutableStateOf(true)
    private var vaultFields by mutableStateOf(VaultSettings.DEFAULT_VISIBLE_FIELDS.toSet())

    private var pickerLayout by mutableStateOf(VaultLayoutMode.GRID)
    private var pickerSort by mutableStateOf(VaultPickerSortBy.DATE)
    private var pickerDescending by mutableStateOf(true)
    private var pickerFields by mutableStateOf(VaultSettings.DEFAULT_PICKER_VISIBLE_FIELDS.toSet())

    private val mode: Mode by lazy {
        val raw = arguments?.getString(ARG_MODE).orEmpty()
        runCatching { Mode.valueOf(raw) }.getOrDefault(Mode.DEVICE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (mode == Mode.VAULT) {
            val source = savedInstanceState ?: arguments
            vaultLayout = runCatching {
                VaultLayoutMode.valueOf(source?.getString(RESULT_VAULT_LAYOUT).orEmpty())
            }.getOrDefault(VaultLayoutMode.GRID)
            vaultSort = runCatching {
                VaultSortBy.valueOf(source?.getString(RESULT_VAULT_SORT).orEmpty())
            }.getOrDefault(VaultSortBy.DATE)
            vaultDescending = source?.getBoolean(RESULT_VAULT_DESCENDING, true) ?: true
            val rawFields = source?.getStringArrayList(RESULT_VAULT_FIELDS)
            if (rawFields != null) {
                vaultFields = rawFields.mapNotNull { name ->
                    runCatching { VaultField.valueOf(name) }.getOrNull()
                }.toSet()
            }
        } else if (mode == Mode.VAULT_PICKER) {
            val source = savedInstanceState ?: arguments
            pickerLayout = runCatching {
                VaultLayoutMode.valueOf(source?.getString(RESULT_PICKER_LAYOUT).orEmpty())
            }.getOrDefault(VaultLayoutMode.GRID)
            pickerSort = runCatching {
                VaultPickerSortBy.valueOf(source?.getString(RESULT_PICKER_SORT).orEmpty())
            }.getOrDefault(VaultPickerSortBy.DATE)
            pickerDescending = source?.getBoolean(RESULT_PICKER_DESCENDING, true) ?: true
            val rawFields = source?.getStringArrayList(RESULT_PICKER_FIELDS)
            if (rawFields != null) {
                pickerFields = rawFields.mapNotNull { name ->
                    runCatching { VaultPickerField.valueOf(name) }.getOrNull()
                }.toSet()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        when (mode) {
            Mode.VAULT -> fillVaultResultBundle(outState)
            Mode.VAULT_PICKER -> fillPickerResultBundle(outState)
            else -> Unit
        }
    }

    @Composable
    override fun Content() {
        val context = requireContext()
        MediaViewOptionsContent(
            mode = mode,
            vaultLayout = vaultLayout,
            vaultSort = vaultSort,
            vaultDescending = vaultDescending,
            vaultFields = vaultFields,
            pickerLayout = pickerLayout,
            pickerSort = pickerSort,
            pickerDescending = pickerDescending,
            pickerFields = pickerFields,
            onDeviceShowModeChanged = { newMode ->
                if (DeviceFilesPrefs.showMode(context) != newMode) {
                    DeviceFilesPrefs.setShowMode(context, newMode)
                    changed = true
                }
            },
            onDeviceCarouselChanged = { isChecked ->
                DeviceFilesPrefs.setShowCarousel(context, isChecked)
                changed = true
            },
            onLayoutChanged = { isGrid ->
                when (mode) {
                    Mode.DEVICE -> {
                        val target = if (isGrid) DeviceLayoutMode.GRID else DeviceLayoutMode.LIST
                        if (DeviceFilesPrefs.layout(context) != target) {
                            DeviceFilesPrefs.setLayout(context, target)
                            changed = true
                        }
                    }
                    Mode.PLAY -> {
                        val target = if (isGrid) DeviceLayoutMode.GRID else DeviceLayoutMode.LIST
                        if (PlayViewPrefs.layout(context) != target) {
                            PlayViewPrefs.setLayout(context, target)
                            changed = true
                        }
                    }
                    Mode.VAULT -> {
                        val target = if (isGrid) VaultLayoutMode.GRID else VaultLayoutMode.LIST
                        if (vaultLayout != target) {
                            vaultLayout = target
                            changed = true
                        }
                    }
                    Mode.VAULT_PICKER -> {
                        val target = if (isGrid) VaultLayoutMode.GRID else VaultLayoutMode.LIST
                        if (pickerLayout != target) {
                            pickerLayout = target
                            changed = true
                        }
                    }
                }
            },
            onSortChanged = { position ->
                when (mode) {
                    Mode.DEVICE -> {
                        DeviceSort.entries.getOrNull(position)?.let { sort ->
                            if (DeviceFilesPrefs.sort(context) != sort) {
                                DeviceFilesPrefs.setSort(context, sort)
                                DeviceFilesPrefs.setNewestFirst(
                                    context,
                                    SortOrderLabels.defaultDescending(sort.category)
                                )
                                changed = true
                            }
                        }
                    }
                    Mode.PLAY -> {
                        PlaySort.entries.getOrNull(position)?.let { sort ->
                            if (PlayViewPrefs.sort(context) != sort) {
                                PlayViewPrefs.setSort(context, sort)
                                PlayViewPrefs.setDescending(
                                    context,
                                    SortOrderLabels.defaultDescending(sort.category)
                                )
                                changed = true
                            }
                        }
                    }
                    Mode.VAULT -> {
                        VaultSortBy.entries.getOrNull(position)?.let { sort ->
                            if (vaultSort != sort) {
                                vaultSort = sort
                                vaultDescending = SortOrderLabels.defaultDescending(sort.category)
                                changed = true
                            }
                        }
                    }
                    Mode.VAULT_PICKER -> {
                        VaultPickerSortBy.entries.getOrNull(position)?.let { sort ->
                            if (pickerSort != sort) {
                                pickerSort = sort
                                pickerDescending = SortOrderLabels.defaultDescending(sort.category)
                                changed = true
                            }
                        }
                    }
                }
            },
            onDescendingChanged = { descending ->
                when (mode) {
                    Mode.DEVICE -> {
                        if (DeviceFilesPrefs.newestFirst(context) != descending) {
                            DeviceFilesPrefs.setNewestFirst(context, descending)
                            changed = true
                        }
                    }
                    Mode.PLAY -> {
                        if (PlayViewPrefs.descending(context) != descending) {
                            PlayViewPrefs.setDescending(context, descending)
                            changed = true
                        }
                    }
                    Mode.VAULT -> {
                        if (vaultDescending != descending) {
                            vaultDescending = descending
                            changed = true
                        }
                    }
                    Mode.VAULT_PICKER -> {
                        if (pickerDescending != descending) {
                            pickerDescending = descending
                            changed = true
                        }
                    }
                }
            },
            onFieldToggled = { fieldName, isChecked ->
                changed = true
                when (mode) {
                    Mode.DEVICE -> {
                        val field = runCatching { DeviceField.valueOf(fieldName) }.getOrNull() ?: return@MediaViewOptionsContent
                        val current = DeviceFilesPrefs.fields(context).toMutableSet()
                        if (isChecked) current.add(field) else current.remove(field)
                        DeviceFilesPrefs.setFields(context, current)
                    }
                    Mode.PLAY -> {
                        val field = runCatching { PlayField.valueOf(fieldName) }.getOrNull() ?: return@MediaViewOptionsContent
                        val current = PlayViewPrefs.fields(context).toMutableSet()
                        if (isChecked) current.add(field) else current.remove(field)
                        PlayViewPrefs.setFields(context, current)
                    }
                    Mode.VAULT -> {
                        val field = runCatching { VaultField.valueOf(fieldName) }.getOrNull() ?: return@MediaViewOptionsContent
                        val current = vaultFields.toMutableSet()
                        if (isChecked) current.add(field) else current.remove(field)
                        vaultFields = current
                    }
                    Mode.VAULT_PICKER -> {
                        val field = runCatching { VaultPickerField.valueOf(fieldName) }.getOrNull() ?: return@MediaViewOptionsContent
                        val current = pickerFields.toMutableSet()
                        if (isChecked) current.add(field) else current.remove(field)
                        pickerFields = current
                    }
                }
            },
            onReset = {
                when (mode) {
                    Mode.DEVICE -> DeviceFilesPrefs.reset(context)
                    Mode.PLAY -> PlayViewPrefs.reset(context)
                    Mode.VAULT -> {
                        vaultLayout = VaultLayoutMode.GRID
                        vaultSort = VaultSortBy.DATE
                        vaultDescending = true
                        vaultFields = VaultSettings.DEFAULT_VISIBLE_FIELDS.toSet()
                    }
                    Mode.VAULT_PICKER -> {
                        pickerLayout = VaultLayoutMode.GRID
                        pickerSort = VaultPickerSortBy.DATE
                        pickerDescending = true
                        pickerFields = VaultSettings.DEFAULT_PICKER_VISIBLE_FIELDS.toSet()
                    }
                }
                changed = true
            },
            onDone = {
                dismiss()
            }
        )
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (changed) {
            val requestKey = when (mode) {
                Mode.DEVICE -> REQUEST_DEVICE
                Mode.PLAY -> REQUEST_PLAY
                Mode.VAULT -> REQUEST_VAULT
                Mode.VAULT_PICKER -> REQUEST_VAULT_PICKER
            }
            val result = Bundle().apply {
                when (mode) {
                    Mode.VAULT -> fillVaultResultBundle(this)
                    Mode.VAULT_PICKER -> fillPickerResultBundle(this)
                    else -> Unit
                }
            }
            setFragmentResult(requestKey, result)
        }
        super.onDismiss(dialog)
    }

    private fun fillVaultResultBundle(bundle: Bundle) {
        bundle.putString(RESULT_VAULT_LAYOUT, vaultLayout.name)
        bundle.putString(RESULT_VAULT_SORT, vaultSort.name)
        bundle.putBoolean(RESULT_VAULT_DESCENDING, vaultDescending)
        bundle.putStringArrayList(
            RESULT_VAULT_FIELDS,
            ArrayList(vaultFields.map { it.name })
        )
    }

    private fun fillPickerResultBundle(bundle: Bundle) {
        bundle.putString(RESULT_PICKER_LAYOUT, pickerLayout.name)
        bundle.putString(RESULT_PICKER_SORT, pickerSort.name)
        bundle.putBoolean(RESULT_PICKER_DESCENDING, pickerDescending)
        bundle.putStringArrayList(
            RESULT_PICKER_FIELDS,
            ArrayList(pickerFields.map { it.name })
        )
    }

    companion object {
        const val TAG = "MediaViewOptionsSheet"
        const val REQUEST_DEVICE = "device_view_options_changed"
        const val REQUEST_PLAY = "play_view_options_changed"
        const val REQUEST_VAULT = "vault_view_options_changed"
        const val REQUEST_VAULT_PICKER = "vault_picker_view_options_changed"

        const val RESULT_VAULT_LAYOUT = "vault_layout"
        const val RESULT_VAULT_SORT = "vault_sort"
        const val RESULT_VAULT_DESCENDING = "vault_descending"
        const val RESULT_VAULT_FIELDS = "vault_fields"

        const val RESULT_PICKER_LAYOUT = "picker_layout"
        const val RESULT_PICKER_SORT = "picker_sort"
        const val RESULT_PICKER_DESCENDING = "picker_descending"
        const val RESULT_PICKER_FIELDS = "picker_fields"

        private const val ARG_MODE = "mode"

        fun newInstance(mode: Mode): MediaViewOptionsSheet = MediaViewOptionsSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_MODE, mode.name)
            }
        }

        fun newVaultInstance(settings: VaultSettings): MediaViewOptionsSheet =
            MediaViewOptionsSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, Mode.VAULT.name)
                    putString(RESULT_VAULT_LAYOUT, settings.layoutMode.name)
                    putString(RESULT_VAULT_SORT, settings.sortBy.name)
                    putBoolean(RESULT_VAULT_DESCENDING, settings.sortDescending)
                    putStringArrayList(
                        RESULT_VAULT_FIELDS,
                        ArrayList(settings.visibleFields.map { it.name })
                    )
                }
            }

        fun newVaultPickerInstance(settings: VaultSettings): MediaViewOptionsSheet =
            MediaViewOptionsSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, Mode.VAULT_PICKER.name)
                    putString(RESULT_PICKER_LAYOUT, settings.pickerLayoutMode.name)
                    putString(RESULT_PICKER_SORT, settings.pickerSortBy.name)
                    putBoolean(RESULT_PICKER_DESCENDING, settings.pickerSortDescending)
                    putStringArrayList(
                        RESULT_PICKER_FIELDS,
                        ArrayList(settings.pickerVisibleFields.map { it.name })
                    )
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaViewOptionsContent(
    mode: MediaViewOptionsSheet.Mode,
    vaultLayout: VaultLayoutMode,
    vaultSort: VaultSortBy,
    vaultDescending: Boolean,
    vaultFields: Set<VaultField>,
    pickerLayout: VaultLayoutMode,
    pickerSort: VaultPickerSortBy,
    pickerDescending: Boolean,
    pickerFields: Set<VaultPickerField>,
    onDeviceShowModeChanged: (DeviceShowMode) -> Unit,
    onDeviceCarouselChanged: (Boolean) -> Unit,
    onLayoutChanged: (isGrid: Boolean) -> Unit,
    onSortChanged: (position: Int) -> Unit,
    onDescendingChanged: (Boolean) -> Unit,
    onFieldToggled: (fieldName: String, isChecked: Boolean) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val titleRes = when (mode) {
        MediaViewOptionsSheet.Mode.DEVICE -> R.string.device_view_options
        MediaViewOptionsSheet.Mode.PLAY -> R.string.play_view_options
        MediaViewOptionsSheet.Mode.VAULT, MediaViewOptionsSheet.Mode.VAULT_PICKER -> R.string.vault_view_options
    }

    val summaryRes = when (mode) {
        MediaViewOptionsSheet.Mode.DEVICE -> R.string.device_view_options_summary
        MediaViewOptionsSheet.Mode.PLAY -> R.string.play_view_options_summary
        MediaViewOptionsSheet.Mode.VAULT -> R.string.vault_view_options_summary
        MediaViewOptionsSheet.Mode.VAULT_PICKER -> R.string.vault_picker_view_options_summary
    }

    val fieldsSummaryRes = when (mode) {
        MediaViewOptionsSheet.Mode.VAULT -> R.string.vault_fields_summary
        MediaViewOptionsSheet.Mode.VAULT_PICKER -> R.string.vault_picker_fields_summary
        else -> R.string.device_fields_summary
    }

    val isGrid = when (mode) {
        MediaViewOptionsSheet.Mode.DEVICE -> DeviceFilesPrefs.layout(context) == DeviceLayoutMode.GRID
        MediaViewOptionsSheet.Mode.PLAY -> PlayViewPrefs.layout(context) == DeviceLayoutMode.GRID
        MediaViewOptionsSheet.Mode.VAULT -> vaultLayout == VaultLayoutMode.GRID
        MediaViewOptionsSheet.Mode.VAULT_PICKER -> pickerLayout == VaultLayoutMode.GRID
    }

    val sortCategory: SortCategory = when (mode) {
        MediaViewOptionsSheet.Mode.DEVICE -> DeviceFilesPrefs.sort(context).category
        MediaViewOptionsSheet.Mode.PLAY -> PlayViewPrefs.sort(context).category
        MediaViewOptionsSheet.Mode.VAULT -> vaultSort.category
        MediaViewOptionsSheet.Mode.VAULT_PICKER -> pickerSort.category
    }

    val isDescending = when (mode) {
        MediaViewOptionsSheet.Mode.DEVICE -> DeviceFilesPrefs.newestFirst(context)
        MediaViewOptionsSheet.Mode.PLAY -> PlayViewPrefs.descending(context)
        MediaViewOptionsSheet.Mode.VAULT -> vaultDescending
        MediaViewOptionsSheet.Mode.VAULT_PICKER -> pickerDescending
    }

    val sortLabels = when (mode) {
        MediaViewOptionsSheet.Mode.DEVICE -> DeviceSort.entries.map { stringResource(it.labelRes) }
        MediaViewOptionsSheet.Mode.PLAY -> PlaySort.entries.map { stringResource(it.labelRes) }
        MediaViewOptionsSheet.Mode.VAULT -> VaultSortBy.entries.map { stringResource(it.labelRes) }
        MediaViewOptionsSheet.Mode.VAULT_PICKER -> VaultPickerSortBy.entries.map { stringResource(it.labelRes) }
    }

    val currentSortIndex = when (mode) {
        MediaViewOptionsSheet.Mode.DEVICE -> DeviceSort.entries.indexOf(DeviceFilesPrefs.sort(context)).coerceAtLeast(0)
        MediaViewOptionsSheet.Mode.PLAY -> PlaySort.entries.indexOf(PlayViewPrefs.sort(context)).coerceAtLeast(0)
        MediaViewOptionsSheet.Mode.VAULT -> VaultSortBy.entries.indexOf(vaultSort).coerceAtLeast(0)
        MediaViewOptionsSheet.Mode.VAULT_PICKER -> VaultPickerSortBy.entries.indexOf(pickerSort).coerceAtLeast(0)
    }

    val (orderPrimaryRes, orderSecondaryRes) = SortOrderLabels.orderLabelRes(sortCategory)
    val isPrimary = SortOrderLabels.isPrimarySelected(sortCategory, isDescending)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Title & Summary
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(summaryRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )

        // Show Section (Mode.DEVICE only)
        if (mode == MediaViewOptionsSheet.Mode.DEVICE) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.device_show_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))

            val currentShowMode = DeviceFilesPrefs.showMode(context)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentShowMode == DeviceShowMode.ALL_FOLDERS,
                    onClick = { onDeviceShowModeChanged(DeviceShowMode.ALL_FOLDERS) },
                    label = { Text(stringResource(R.string.device_show_all_folders), fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = currentShowMode == DeviceShowMode.FILES,
                    onClick = { onDeviceShowModeChanged(DeviceShowMode.FILES) },
                    label = { Text(stringResource(R.string.device_show_files), fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = currentShowMode == DeviceShowMode.FOLDERS,
                    onClick = { onDeviceShowModeChanged(DeviceShowMode.FOLDERS) },
                    label = { Text(stringResource(R.string.device_show_folders), fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.device_show_carousel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = DeviceFilesPrefs.showCarousel(context),
                    onCheckedChange = onDeviceCarouselChanged
                )
            }
        }

        // Layout Section
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.device_layout_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !isGrid,
                onClick = { onLayoutChanged(false) },
                label = { Text(stringResource(R.string.list)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = isGrid,
                onClick = { onLayoutChanged(true) },
                label = { Text(stringResource(R.string.grid)) },
                modifier = Modifier.weight(1f)
            )
        }

        // Sort Section
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.device_sort_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))

        var sortExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = sortExpanded,
            onExpandedChange = { sortExpanded = !sortExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = sortLabels.getOrElse(currentSortIndex) { "" },
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false }
            ) {
                sortLabels.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            sortExpanded = false
                            onSortChanged(index)
                        }
                    )
                }
            }
        }

        // Order Section
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.device_order_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = isPrimary,
                onClick = {
                    val desc = SortOrderLabels.descendingForButton(sortCategory, true)
                    onDescendingChanged(desc)
                },
                label = { Text(stringResource(orderPrimaryRes)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = !isPrimary,
                onClick = {
                    val desc = SortOrderLabels.descendingForButton(sortCategory, false)
                    onDescendingChanged(desc)
                },
                label = { Text(stringResource(orderSecondaryRes)) },
                modifier = Modifier.weight(1f)
            )
        }

        // Fields Section
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.device_fields_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(fieldsSummaryRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            when (mode) {
                MediaViewOptionsSheet.Mode.DEVICE -> {
                    val selected = DeviceFilesPrefs.fields(context)
                    DeviceField.entries.forEach { field ->
                        FilterChip(
                            selected = field in selected,
                            onClick = { onFieldToggled(field.name, field !in selected) },
                            label = { Text(stringResource(field.labelRes)) }
                        )
                    }
                }
                MediaViewOptionsSheet.Mode.PLAY -> {
                    val selected = PlayViewPrefs.fields(context)
                    PlayField.entries.forEach { field ->
                        FilterChip(
                            selected = field in selected,
                            onClick = { onFieldToggled(field.name, field !in selected) },
                            label = { Text(stringResource(field.labelRes)) }
                        )
                    }
                }
                MediaViewOptionsSheet.Mode.VAULT -> {
                    VaultField.entries.forEach { field ->
                        FilterChip(
                            selected = field in vaultFields,
                            onClick = { onFieldToggled(field.name, field !in vaultFields) },
                            label = { Text(stringResource(field.labelRes)) }
                        )
                    }
                }
                MediaViewOptionsSheet.Mode.VAULT_PICKER -> {
                    VaultPickerField.entries.forEach { field ->
                        FilterChip(
                            selected = field in pickerFields,
                            onClick = { onFieldToggled(field.name, field !in pickerFields) },
                            label = { Text(stringResource(field.labelRes)) }
                        )
                    }
                }
            }
        }

        // Action Buttons: Reset & Done
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.reset))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onDone) {
                Text(stringResource(R.string.done))
            }
        }
    }
}
