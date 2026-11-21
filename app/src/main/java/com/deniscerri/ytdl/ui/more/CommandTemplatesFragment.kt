package com.deniscerri.ytdl.ui.more

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager.SORTING
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.models.CommandTemplateExport
import com.deniscerri.ytdl.database.repository.CommandTemplateRepository.CommandTemplateSortType
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.ui.compose.commands.CommandTemplatesScreen
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Command templates screen (Phase 3).
 *
 * Hosts [CommandTemplatesScreen] inside [ComposeFragment]. Multi-select keeps the AppCompat
 * [ActionMode] so the contextual bar remains consistent with the other lists.
 *
 * Replaces `fragment_command_templates.xml`, `command_templates_sort_sheet.xml` and `TemplatesAdapter`.
 */
class CommandTemplatesFragment : ComposeFragment() {

    private lateinit var mainActivity: MainActivity
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel
    private lateinit var preferences: SharedPreferences

    private var selectedIds by mutableStateOf<Set<Long>>(emptySet())
    private var actionMode: ActionMode? = null
    private var currentItems: List<CommandTemplate> = emptyList()
    private val jsonFormat = Json { prettyPrint = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        commandTemplateViewModel = ViewModelProvider(requireActivity())[CommandTemplateViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity = activity as MainActivity
    }

    @Composable
    override fun Content() {
        val templatesFlow = remember { commandTemplateViewModel.getFilteredList().asFlow() }
        val templates by templatesFlow.collectAsState(initial = emptyList())
        currentItems = templates

        val allItemsFlow = remember { commandTemplateViewModel.allItems.asFlow() }
        val allItems by allItemsFlow.collectAsState(initial = emptyList())

        val sortTypeFlow = remember { commandTemplateViewModel.sortType.asFlow() }
        val sortType by sortTypeFlow.collectAsState(initial = CommandTemplateSortType.DATE)

        val sortOrderFlow = remember { commandTemplateViewModel.sortOrder.asFlow() }
        val sortOrder by sortOrderFlow.collectAsState(initial = SORTING.DESC)

        val snackbarHostState = remember { SnackbarHostState() }

        val swipeGestures = remember {
            val defaultGestures = resources.getStringArray(R.array.swipe_gestures_values).toSet()
            val userGestures = preferences.getStringSet("swipe_gesture", defaultGestures) ?: defaultGestures
            userGestures.contains("templates")
        }

        CommandTemplatesScreen(
            templates = templates,
            isTableEmpty = allItems.isEmpty(),
            selectedIds = selectedIds,
            sortType = sortType ?: CommandTemplateSortType.DATE,
            sortOrder = sortOrder ?: SORTING.DESC,
            swipeGestures = swipeGestures,
            snackbarHostState = snackbarHostState,
            onBack = {
                mainActivity.onBackPressedDispatcher.onBackPressed()
            },
            onSearchQueryChanged = { query ->
                commandTemplateViewModel.setQueryFilter(query)
            },
            onNewTemplate = {
                UiUtil.showCommandTemplateCreationOrUpdatingSheet(
                    null,
                    mainActivity,
                    this,
                    commandTemplateViewModel,
                    {},
                    {},
                )
            },
            onOpenShortcuts = {
                UiUtil.showShortcutsSheet(mainActivity, this, commandTemplateViewModel)
            },
            onSortTypeSelected = { type ->
                commandTemplateViewModel.setSorting(type)
            },
            onOpenTemplate = { template ->
                UiUtil.showCommandTemplateCreationOrUpdatingSheet(
                    template,
                    mainActivity,
                    this,
                    commandTemplateViewModel,
                    {},
                    {},
                )
            },
            onToggleSelection = ::toggleSelection,
            onDeleteTemplate = { template ->
                UiUtil.showGenericDeleteDialog(requireContext(), template.title) {
                    commandTemplateViewModel.delete(template)
                }
            },
            onExportToClipboard = {
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        commandTemplateViewModel.exportToClipboard()
                    }
                    snackbarHostState.showSnackbar(getString(R.string.copied_to_clipboard))
                }
            },
            onImportFromClipboard = {
                viewLifecycleOwner.lifecycleScope.launch {
                    val count = withContext(Dispatchers.IO) {
                        commandTemplateViewModel.importFromClipboard()
                    }
                    snackbarHostState.showSnackbar("${getString(R.string.items_imported)} ($count)")
                }
            },
        )
    }

    private fun toggleSelection(item: CommandTemplate) {
        val next = selectedIds.toMutableSet()
        if (!next.add(item.id)) next.remove(item.id)
        selectedIds = next

        if (next.isEmpty()) {
            actionMode?.finish()
            actionMode = null
            return
        }

        if (actionMode == null) {
            actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(contextualActionBar)
        } else {
            actionMode?.title = "${next.size} ${getString(R.string.selected)}"
        }
        updateSelectBetweenVisibility()
    }

    private fun updateSelectBetweenVisibility() {
        val selectBetweenItem = actionMode?.menu?.findItem(R.id.select_between) ?: return
        selectBetweenItem.isVisible = false
        if (selectedIds.size == 2) {
            val sorted = selectedIds.sorted()
            viewLifecycleOwner.lifecycleScope.launch {
                val resultsInMiddle = withContext(Dispatchers.IO) {
                    commandTemplateViewModel.getRecordsBetweenTwoItems(sorted.first(), sorted.last())
                }
                selectBetweenItem.isVisible = resultsInMiddle.isNotEmpty()
            }
        }
    }

    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menu?.add(Menu.NONE, R.id.select_between, Menu.NONE, R.string.select_between)?.apply {
                setIcon(R.drawable.baseline_compare_arrows_24)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            menu?.add(Menu.NONE, R.id.select_all, Menu.NONE, R.string.select_all)?.apply {
                setIcon(R.drawable.ic_select_all)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            menu?.add(Menu.NONE, R.id.delete, Menu.NONE, R.string.delete)?.apply {
                setIcon(R.drawable.baseline_delete_24)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            mode?.title = "${selectedIds.size} ${getString(R.string.selected)}"
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            val selectBetweenItem = menu?.findItem(R.id.select_between)
            selectBetweenItem?.isVisible = false
            if (selectedIds.size == 2) {
                val sorted = selectedIds.sorted()
                viewLifecycleOwner.lifecycleScope.launch {
                    val resultsInMiddle = withContext(Dispatchers.IO) {
                        commandTemplateViewModel.getRecordsBetweenTwoItems(sorted.first(), sorted.last())
                    }
                    selectBetweenItem?.isVisible = resultsInMiddle.isNotEmpty()
                }
            }
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item!!.itemId) {
                R.id.select_between -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val sorted = selectedIds.sorted()
                        if (sorted.size >= 2) {
                            val resultsInMiddle = withContext(Dispatchers.IO) {
                                commandTemplateViewModel.getRecordsBetweenTwoItems(sorted.first(), sorted.last())
                            }
                            if (resultsInMiddle.isNotEmpty()) {
                                selectedIds = selectedIds + resultsInMiddle.map { it.id }.toSet()
                                mode?.title = "${selectedIds.size} ${getString(R.string.selected)}"
                            }
                        }
                        mode?.menu?.findItem(R.id.select_between)?.isVisible = false
                    }
                    true
                }

                R.id.delete_results -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
                        .setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int ->
                            dialogInterface.cancel()
                        }
                        .setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                            val toDelete = currentItems.filter { it.id in selectedIds }
                            for (obj in toDelete) {
                                commandTemplateViewModel.delete(obj)
                            }
                            selectedIds = emptySet()
                            mode?.finish()
                        }
                        .show()
                    true
                }

                R.id.select_all -> {
                    selectedIds = currentItems.map { it.id }.toSet()
                    mode?.title = "(${selectedIds.size}) ${resources.getString(R.string.all_items_selected)}"
                    true
                }

                R.id.invert_selected -> {
                    selectedIds = currentItems.filter { it.id !in selectedIds }.map { it.id }.toSet()
                    mode?.title = "${selectedIds.size} ${getString(R.string.selected)}"
                    if (selectedIds.isEmpty()) mode?.finish()
                    true
                }

                R.id.export_clipboard -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val selectedObjects = currentItems.filter { it.id in selectedIds }
                        val output = jsonFormat.encodeToString(
                            CommandTemplateExport(
                                templates = selectedObjects,
                                shortcuts = listOf()
                            )
                        )
                        val clipboard: ClipboardManager =
                            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.text = output
                        view?.let {
                            com.google.android.material.snackbar.Snackbar.make(
                                it,
                                getString(R.string.copied_to_clipboard),
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            ).show()
                        }
                        selectedIds = emptySet()
                        mode?.finish()
                    }
                    true
                }

                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            selectedIds = emptySet()
        }
    }
}
