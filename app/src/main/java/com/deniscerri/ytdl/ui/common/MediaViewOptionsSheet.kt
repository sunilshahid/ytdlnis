package com.deniscerri.ytdl.ui.common

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import com.deniscerri.ytdl.R
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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Shared bottom sheet for configuring Layout, Sort, Order (with labels adapted to the
 * active sort field), and visible metadata fields across Device Files, Play, and Private Vault.
 */
class MediaViewOptionsSheet : BottomSheetDialogFragment() {

    enum class Mode {
        DEVICE,
        PLAY,
        VAULT,
        VAULT_PICKER
    }

    private var changed = false
    private var suppressListeners = false

    // Transient working state for Mode.VAULT & Mode.VAULT_PICKER (persisted by VaultActivity on result).
    private var vaultLayout = VaultLayoutMode.GRID
    private var vaultSort = VaultSortBy.DATE
    private var vaultDescending = true
    private var vaultFields: MutableSet<VaultField> = VaultSettings.DEFAULT_VISIBLE_FIELDS.toMutableSet()

    private var pickerLayout = VaultLayoutMode.GRID
    private var pickerSort = VaultPickerSortBy.DATE
    private var pickerDescending = true
    private var pickerFields: MutableSet<VaultPickerField> =
        VaultSettings.DEFAULT_PICKER_VISIBLE_FIELDS.toMutableSet()

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
                }.toMutableSet()
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
                }.toMutableSet()
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_media_view_options, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        val titleView = view.findViewById<TextView>(R.id.view_options_title)
        val summaryView = view.findViewById<TextView>(R.id.view_options_summary)
        val showSection = view.findViewById<View>(R.id.view_show_section)
        val showGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.view_show_mode_group)
        val layoutGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.view_layout_group)
        val sortValue = view.findViewById<AutoCompleteTextView>(R.id.view_sort_value)
        val orderGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.view_order_group)
        val orderPrimary = view.findViewById<MaterialButton>(R.id.view_order_primary)
        val orderSecondary = view.findViewById<MaterialButton>(R.id.view_order_secondary)
        val fieldsSummary = view.findViewById<TextView>(R.id.view_fields_summary)
        val fieldsGroup = view.findViewById<ChipGroup>(R.id.view_fields_group)
        val resetBtn = view.findViewById<MaterialButton>(R.id.view_options_reset)
        val doneBtn = view.findViewById<MaterialButton>(R.id.view_options_done)

        when (mode) {
            Mode.DEVICE -> {
                titleView.setText(R.string.device_view_options)
                summaryView.setText(R.string.device_view_options_summary)
                fieldsSummary.setText(R.string.device_fields_summary)
                showSection.isVisible = true
            }
            Mode.PLAY -> {
                titleView.setText(R.string.play_view_options)
                summaryView.setText(R.string.play_view_options_summary)
                fieldsSummary.setText(R.string.device_fields_summary)
                showSection.isVisible = false
            }
            Mode.VAULT -> {
                titleView.setText(R.string.vault_view_options)
                summaryView.setText(R.string.vault_view_options_summary)
                fieldsSummary.setText(R.string.vault_fields_summary)
                showSection.isVisible = false
            }
            Mode.VAULT_PICKER -> {
                titleView.setText(R.string.vault_view_options)
                summaryView.setText(R.string.vault_picker_view_options_summary)
                fieldsSummary.setText(R.string.vault_picker_fields_summary)
                showSection.isVisible = false
            }
        }

        fun currentSortCategory(): SortCategory = when (mode) {
            Mode.DEVICE -> DeviceFilesPrefs.sort(context).category
            Mode.PLAY -> PlayViewPrefs.sort(context).category
            Mode.VAULT -> vaultSort.category
            Mode.VAULT_PICKER -> pickerSort.category
        }

        fun currentDescending(): Boolean = when (mode) {
            Mode.DEVICE -> DeviceFilesPrefs.newestFirst(context)
            Mode.PLAY -> PlayViewPrefs.descending(context)
            Mode.VAULT -> vaultDescending
            Mode.VAULT_PICKER -> pickerDescending
        }

        fun setDescending(descending: Boolean) {
            when (mode) {
                Mode.DEVICE -> DeviceFilesPrefs.setNewestFirst(context, descending)
                Mode.PLAY -> PlayViewPrefs.setDescending(context, descending)
                Mode.VAULT -> vaultDescending = descending
                Mode.VAULT_PICKER -> pickerDescending = descending
            }
        }

        fun renderOrder() {
            val category = currentSortCategory()
            val (primaryRes, secondaryRes) = SortOrderLabels.orderLabelRes(category)
            orderPrimary.setText(primaryRes)
            orderSecondary.setText(secondaryRes)
            val isPrimary = SortOrderLabels.isPrimarySelected(category, currentDescending())
            suppressListeners = true
            orderGroup.check(if (isPrimary) R.id.view_order_primary else R.id.view_order_secondary)
            suppressListeners = false
        }

        fun addFieldChip(labelRes: Int, checked: Boolean, onToggle: (Boolean) -> Unit) {
            val chip = Chip(requireContext()).apply {
                text = getString(labelRes)
                isCheckable = true
                isCheckedIconVisible = true
                isChecked = checked
                setOnCheckedChangeListener { _, isChecked ->
                    if (!suppressListeners) {
                        onToggle(isChecked)
                        changed = true
                    }
                }
            }
            fieldsGroup.addView(chip)
        }

        fun renderFields() {
            fieldsGroup.removeAllViews()
            when (mode) {
                Mode.DEVICE -> {
                    val selected = DeviceFilesPrefs.fields(context)
                    DeviceField.entries.forEach { field ->
                        addFieldChip(field.labelRes, field in selected) { checked ->
                            val current = DeviceFilesPrefs.fields(context).toMutableSet()
                            if (checked) current.add(field) else current.remove(field)
                            DeviceFilesPrefs.setFields(context, current)
                        }
                    }
                }
                Mode.PLAY -> {
                    val selected = PlayViewPrefs.fields(context)
                    PlayField.entries.forEach { field ->
                        addFieldChip(field.labelRes, field in selected) { checked ->
                            val current = PlayViewPrefs.fields(context).toMutableSet()
                            if (checked) current.add(field) else current.remove(field)
                            PlayViewPrefs.setFields(context, current)
                        }
                    }
                }
                Mode.VAULT -> {
                    VaultField.entries.forEach { field ->
                        addFieldChip(field.labelRes, field in vaultFields) { checked ->
                            if (checked) vaultFields.add(field) else vaultFields.remove(field)
                        }
                    }
                }
                Mode.VAULT_PICKER -> {
                    VaultPickerField.entries.forEach { field ->
                        addFieldChip(field.labelRes, field in pickerFields) { checked ->
                            if (checked) pickerFields.add(field) else pickerFields.remove(field)
                        }
                    }
                }
            }
        }

        fun renderAll() {
            suppressListeners = true
            when (mode) {
                Mode.DEVICE -> {
                    showGroup.check(
                        when (DeviceFilesPrefs.showMode(context)) {
                            DeviceShowMode.ALL_FOLDERS -> R.id.view_show_all_folders
                            DeviceShowMode.FILES -> R.id.view_show_files
                            DeviceShowMode.FOLDERS -> R.id.view_show_folders
                        }
                    )
                    layoutGroup.check(
                        when (DeviceFilesPrefs.layout(context)) {
                            DeviceLayoutMode.LIST -> R.id.view_layout_list
                            DeviceLayoutMode.GRID -> R.id.view_layout_grid
                        }
                    )
                    val sorts = DeviceSort.entries.map { getString(it.labelRes) }
                    sortValue.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sorts)
                    )
                    sortValue.setText(getString(DeviceFilesPrefs.sort(context).labelRes), false)
                }
                Mode.PLAY -> {
                    layoutGroup.check(
                        when (PlayViewPrefs.layout(context)) {
                            DeviceLayoutMode.LIST -> R.id.view_layout_list
                            DeviceLayoutMode.GRID -> R.id.view_layout_grid
                        }
                    )
                    val sorts = PlaySort.entries.map { getString(it.labelRes) }
                    sortValue.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sorts)
                    )
                    sortValue.setText(getString(PlayViewPrefs.sort(context).labelRes), false)
                }
                Mode.VAULT -> {
                    layoutGroup.check(
                        when (vaultLayout) {
                            VaultLayoutMode.LIST -> R.id.view_layout_list
                            VaultLayoutMode.GRID -> R.id.view_layout_grid
                        }
                    )
                    val sorts = VaultSortBy.entries.map { getString(it.labelRes) }
                    sortValue.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sorts)
                    )
                    sortValue.setText(getString(vaultSort.labelRes), false)
                }
                Mode.VAULT_PICKER -> {
                    layoutGroup.check(
                        when (pickerLayout) {
                            VaultLayoutMode.LIST -> R.id.view_layout_list
                            VaultLayoutMode.GRID -> R.id.view_layout_grid
                        }
                    )
                    val sorts = VaultPickerSortBy.entries.map { getString(it.labelRes) }
                    sortValue.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sorts)
                    )
                    sortValue.setText(getString(pickerSort.labelRes), false)
                }
            }
            suppressListeners = false
            renderOrder()
            renderFields()
        }

        renderAll()

        if (mode == Mode.DEVICE) {
            showGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked || suppressListeners) return@addOnButtonCheckedListener
                val selected = when (checkedId) {
                    R.id.view_show_files -> DeviceShowMode.FILES
                    R.id.view_show_folders -> DeviceShowMode.FOLDERS
                    else -> DeviceShowMode.ALL_FOLDERS
                }
                if (DeviceFilesPrefs.showMode(context) != selected) {
                    DeviceFilesPrefs.setShowMode(context, selected)
                    changed = true
                }
            }
        }

        layoutGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressListeners) return@addOnButtonCheckedListener
            val isGrid = checkedId == R.id.view_layout_grid
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
        }

        orderGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressListeners) return@addOnButtonCheckedListener
            val primarySelected = checkedId == R.id.view_order_primary
            val desc = SortOrderLabels.descendingForButton(currentSortCategory(), primarySelected)
            if (currentDescending() != desc) {
                setDescending(desc)
                changed = true
            }
        }

        sortValue.setOnItemClickListener { _, _, position, _ ->
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
            renderOrder()
        }

        resetBtn.setOnClickListener {
            when (mode) {
                Mode.DEVICE -> DeviceFilesPrefs.reset(context)
                Mode.PLAY -> PlayViewPrefs.reset(context)
                Mode.VAULT -> {
                    vaultLayout = VaultLayoutMode.GRID
                    vaultSort = VaultSortBy.DATE
                    vaultDescending = true
                    vaultFields = VaultSettings.DEFAULT_VISIBLE_FIELDS.toMutableSet()
                }
                Mode.VAULT_PICKER -> {
                    pickerLayout = VaultLayoutMode.GRID
                    pickerSort = VaultPickerSortBy.DATE
                    pickerDescending = true
                    pickerFields = VaultSettings.DEFAULT_PICKER_VISIBLE_FIELDS.toMutableSet()
                }
            }
            changed = true
            renderAll()
        }

        doneBtn.setOnClickListener { dismiss() }
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
