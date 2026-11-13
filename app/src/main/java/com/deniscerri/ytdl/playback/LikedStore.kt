package com.deniscerri.ytdl.playback

import android.content.Context
import android.content.SharedPreferences
import com.deniscerri.ytdl.playback.model.PlaybackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Reference-only liked library.
 *
 * Stores [PlaybackItem] descriptors (metadata + artwork/source URIs) as JSON in
 * SharedPreferences. It never copies or moves the underlying audio/video file, so liking a
 * downloaded song cannot create a duplicate.
 */
object LikedStore {

    private const val PREFS_NAME = "ytdlnis_liked"
    private const val KEY_ITEMS = "items"

    private var prefs: SharedPreferences? = null
    private val _items = MutableStateFlow<List<PlaybackItem>>(emptyList())
    val items: StateFlow<List<PlaybackItem>> = _items.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _items.value = read()
    }

    fun isLiked(mediaId: String): Boolean = _items.value.any { it.mediaId == mediaId }

    /** Adds or removes [item] and returns true when it is liked afterwards. */
    fun toggle(item: PlaybackItem): Boolean {
        val current = _items.value
        val alreadyLiked = current.any { it.mediaId == item.mediaId }
        val updated = if (alreadyLiked) {
            current.filterNot { it.mediaId == item.mediaId }
        } else {
            listOf(item) + current
        }
        persist(updated)
        return !alreadyLiked
    }

    fun add(item: PlaybackItem) {
        val current = _items.value
        if (current.any { it.mediaId == item.mediaId }) return
        persist(listOf(item) + current)
    }

    fun remove(mediaId: String) {
        val updated = _items.value.filterNot { it.mediaId == mediaId }
        persist(updated)
    }

    fun all(): List<PlaybackItem> = _items.value

    private fun persist(updated: List<PlaybackItem>) {
        _items.value = updated
        val array = JSONArray()
        updated.forEach { array.put(it.toJson()) }
        prefs?.edit()?.putString(KEY_ITEMS, array.toString())?.apply()
    }

    private fun read(): List<PlaybackItem> {
        val raw = prefs?.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    PlaybackItem.fromJson(array.getJSONObject(i).toString())?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }
}
