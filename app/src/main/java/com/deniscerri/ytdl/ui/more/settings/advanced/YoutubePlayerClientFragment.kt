package com.deniscerri.ytdl.ui.more.settings.advanced

import android.content.SharedPreferences
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.YoutubePlayerClientItem
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.youtubeclients.YoutubePlayerClientSheet
import com.deniscerri.ytdl.ui.compose.youtubeclients.YoutubePlayerClientsScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.UiUtil
import com.google.gson.Gson

/**
 * Compose port of the YouTube player client list (Phase 3).
 *
 * The persisted shape (`youtube_player_clients` holding a Gson array of
 * [YoutubePlayerClientItem]) and the drag-to-reorder behaviour are unchanged.
 */
class YoutubePlayerClientFragment : ComposeFragment() {

    private lateinit var preferences: SharedPreferences

    private var items by mutableStateOf<List<YoutubePlayerClientItem>>(emptyList())
    private var showDragHandles by mutableStateOf(false)
    private var sheetVisible by mutableStateOf(false)
    private var sheetItem by mutableStateOf<YoutubePlayerClientItem?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as SettingsActivity).changeTopAppbarTitle(getString(R.string.player_client))
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        items = readItems()
        showDragHandles = false
    }

    private fun readItems(): List<YoutubePlayerClientItem> {
        val raw = preferences.getString("youtube_player_clients", "[]")!!.ifEmpty { "[]" }
        return runCatching {
            Gson().fromJson(raw, Array<YoutubePlayerClientItem>::class.java).toMutableList()
        }.getOrElse { mutableListOf() }
    }

    private fun persist() {
        preferences.edit().putString("youtube_player_clients", Gson().toJson(items)).apply()
    }

    @Composable
    override fun Content() {
        YoutubePlayerClientsScreen(
            items = items,
            showDragHandles = showDragHandles,
            onAddClick = {
                sheetItem = null
                sheetVisible = true
            },
            onToggleDragHandles = { showDragHandles = !showDragHandles },
            onItemClick = { item ->
                sheetItem = item
                sheetVisible = true
            },
            onItemLongClick = { item -> confirmDelete(item) },
            onStatusToggle = { item, enabled ->
                val index = items.indexOf(item)
                if (index >= 0) {
                    val updated = items.toMutableList()
                    updated[index] = updated[index].copy(enabled = enabled)
                    items = updated
                    persist()
                }
            },
            onMove = { from, to ->
                if (from != to && from in items.indices && to in items.indices) {
                    val updated = items.toMutableList()
                    val moved = updated.removeAt(from)
                    updated.add(to, moved)
                    items = updated
                    persist()
                }
            },
        )

        if (sheetVisible) {
            YoutubePlayerClientSheet(
                currentValue = sheetItem,
                existingConfigs = items,
                onDismiss = { sheetVisible = false },
                onConfirm = { newItem ->
                    val editing = sheetItem
                    val updated = items.toMutableList()
                    if (editing == null) {
                        updated.add(newItem)
                    } else {
                        val index = updated.indexOf(editing)
                        if (index >= 0) updated.removeAt(index)
                        updated.add(newItem)
                    }
                    items = updated
                    persist()
                    sheetVisible = false
                },
                onDelete = {
                    val editing = sheetItem
                    sheetVisible = false
                    if (editing != null) confirmDelete(editing)
                },
            )
        }
    }

    private fun confirmDelete(item: YoutubePlayerClientItem) {
        UiUtil.showGenericDeleteDialog(requireContext(), item.playerClient) {
            items = items.filterNot { it == item }
            persist()
        }
    }
}
