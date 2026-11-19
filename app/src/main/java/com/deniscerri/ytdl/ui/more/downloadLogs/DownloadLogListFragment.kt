package com.deniscerri.ytdl.ui.more.downloadLogs

import android.content.DialogInterface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.LogItem
import com.deniscerri.ytdl.database.viewmodel.LogViewModel
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.logs.DownloadLogListScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Download-log list (Phase 3).
 *
 * The list, swipe-to-delete, empty state and overflow menu are Compose
 * ([DownloadLogListScreen]); multi-select deliberately keeps the AppCompat `ActionMode` used by the
 * other download lists so the contextual bar looks and behaves identically across the app.
 *
 * Replaces `fragment_download_log_list.xml`, `log_card.xml` and `DownloadLogsAdapter`.
 */
class DownloadLogListFragment : ComposeFragment() {

    private lateinit var mainActivity: MainActivity
    private lateinit var logViewModel: LogViewModel

    private var selectedIds by mutableStateOf<Set<Long>>(emptySet())
    private var actionMode: ActionMode? = null
    private var currentItems: List<LogItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logViewModel = ViewModelProvider(this)[LogViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity = activity as MainActivity
        mainActivity.hideBottomNavigation()
    }

    @Composable
    override fun Content() {
        // `asFlow()` creates a new Flow each call; remembering it keeps `collectAsState` from
        // resubscribing (and recomposing) on every frame.
        val flow = remember { logViewModel.items.asFlow() }
        val logs by flow.collectAsState(initial = emptyList())
        currentItems = logs

        DownloadLogListScreen(
            logs = logs,
            selectedIds = selectedIds,
            swipeGestures = remember {
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getBoolean("swipe_gestures", true)
            },
            onBack = { mainActivity.onBackPressedDispatcher.onBackPressed() },
            onOpen = { item ->
                findNavController().navigate(
                    R.id.downloadLogFragment,
                    bundleOf("logID" to item.id),
                )
            },
            onToggleSelection = ::toggleSelection,
            onDeleteAll = ::confirmDeleteAll,
            onDelete = { logViewModel.delete(it) },
            onUndoDelete = { item ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    logViewModel.insert(item)
                }
            },
        )
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete_history)
            .setMessage(R.string.confirm_delete_logs_desc)
            .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.cancel() }
            .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int -> logViewModel.deleteAll() }
            .show()
    }

    private fun toggleSelection(item: LogItem) {
        val next = selectedIds.toMutableSet()
        if (!next.add(item.id)) next.remove(item.id)
        selectedIds = next

        if (next.isEmpty()) {
            actionMode?.finish()
            actionMode = null
            return
        }

        if (actionMode == null) {
            actionMode = mainActivity.startSupportActionMode(contextualActionBar)
        } else {
            actionMode?.title = "${next.size} ${getString(R.string.selected)}"
        }
    }

    private val contextualActionBar = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode!!.menuInflater.inflate(R.menu.logs_menu_context, menu)
            mode.title = "${selectedIds.size} ${getString(R.string.selected)}"
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item!!.itemId) {
                R.id.delete_results -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.you_are_going_to_delete_multiple_items)
                        .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.cancel() }
                        .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                            val toDelete = currentItems.filter { it.id in selectedIds }
                            toDelete.forEach { logViewModel.delete(it) }
                            selectedIds = emptySet()
                            actionMode?.finish()
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
                    actionMode?.title = "${selectedIds.size} ${getString(R.string.selected)}"
                    if (selectedIds.isEmpty()) actionMode?.finish()
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
