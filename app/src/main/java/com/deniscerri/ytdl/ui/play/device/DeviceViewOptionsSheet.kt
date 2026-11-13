package com.deniscerri.ytdl.ui.play.device

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.deniscerri.ytdl.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView

/**
 * Bottom sheet that controls how the device library is presented. Every interaction is written
 * straight to [DeviceFilesPrefs]; the Play tab reloads when the sheet reports a change.
 */
class DeviceViewOptionsSheet : BottomSheetDialogFragment() {

    private var changed = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_device_view_options, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        val showGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.device_show_mode_group)
        val layoutGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.device_layout_group)
        val orderGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.device_order_group)
        val sortValue = view.findViewById<MaterialAutoCompleteTextView>(R.id.device_sort_value)
        val fieldsGroup = view.findViewById<ChipGroup>(R.id.device_fields_group)
        val done = view.findViewById<TextView>(R.id.device_view_done)
        val reset = view.findViewById<TextView>(R.id.device_view_reset)

        fun checkedShow(mode: DeviceShowMode) = when (mode) {
            DeviceShowMode.ALL_FOLDERS -> R.id.device_show_all
            DeviceShowMode.FILES -> R.id.device_show_files
            DeviceShowMode.FOLDERS -> R.id.device_show_folders
        }

        fun checkedLayout(layout: DeviceLayoutMode) = when (layout) {
            DeviceLayoutMode.LIST -> R.id.device_layout_list
            DeviceLayoutMode.GRID -> R.id.device_layout_grid
        }

        fun renderFields() {
            val selected = DeviceFilesPrefs.fields(context)
            fieldsGroup.removeAllViews()
            DeviceField.entries.forEach { field ->
                val chip = layoutInflater.inflate(R.layout.device_field_chip, fieldsGroup, false) as Chip
                chip.id = View.generateViewId()
                chip.text = getString(field.labelRes)
                chip.isChecked = field in selected
                chip.setOnCheckedChangeListener { _, isChecked ->
                    val current = DeviceFilesPrefs.fields(context).toMutableSet()
                    if (isChecked) current.add(field) else current.remove(field)
                    DeviceFilesPrefs.setFields(context, current)
                    changed = true
                }
                fieldsGroup.addView(chip)
            }
        }

        fun renderAll() {
            showGroup.check(checkedShow(DeviceFilesPrefs.showMode(context)))
            layoutGroup.check(checkedLayout(DeviceFilesPrefs.layout(context)))
            orderGroup.check(
                if (DeviceFilesPrefs.newestFirst(context)) R.id.device_order_newest
                else R.id.device_order_oldest
            )
            val sorts = DeviceSort.entries.map { getString(it.labelRes) }
            sortValue.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sorts)
            )
            sortValue.setText(getString(DeviceFilesPrefs.sort(context).labelRes), false)
            renderFields()
        }

        showGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.device_show_files -> DeviceShowMode.FILES
                R.id.device_show_folders -> DeviceShowMode.FOLDERS
                else -> DeviceShowMode.ALL_FOLDERS
            }
            if (DeviceFilesPrefs.showMode(context) != mode) {
                DeviceFilesPrefs.setShowMode(context, mode)
                changed = true
            }
        }

        layoutGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val layout = if (checkedId == R.id.device_layout_grid) DeviceLayoutMode.GRID
            else DeviceLayoutMode.LIST
            if (DeviceFilesPrefs.layout(context) != layout) {
                DeviceFilesPrefs.setLayout(context, layout)
                changed = true
            }
        }

        orderGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newest = checkedId == R.id.device_order_newest
            if (DeviceFilesPrefs.newestFirst(context) != newest) {
                DeviceFilesPrefs.setNewestFirst(context, newest)
                changed = true
            }
        }

        sortValue.setOnItemClickListener { _, _, position, _ ->
            DeviceSort.entries.getOrNull(position)?.let { sort ->
                if (DeviceFilesPrefs.sort(context) != sort) {
                    DeviceFilesPrefs.setSort(context, sort)
                    changed = true
                }
            }
        }

        reset.setOnClickListener {
            DeviceFilesPrefs.reset(context)
            changed = true
            renderAll()
        }

        done.setOnClickListener { dismiss() }

        renderAll()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (changed) {
            parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf())
        }
    }

    companion object {
        const val REQUEST_KEY = "device_view_options_changed"
        const val TAG = "device_view_options"
    }
}
