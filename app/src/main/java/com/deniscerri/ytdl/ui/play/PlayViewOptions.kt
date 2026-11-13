package com.deniscerri.ytdl.ui.play

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackSource
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.ui.play.device.DeviceLayoutMode
import com.deniscerri.ytdl.util.SortCategory
import com.deniscerri.ytdl.util.SortOrderLabels
import java.text.DateFormat
import java.util.Date

enum class PlaySort(@StringRes val labelRes: Int, val category: SortCategory) {
    DATE(R.string.device_sort_date, SortCategory.DATE_TIME),
    TITLE(R.string.device_sort_title, SortCategory.ALPHABETICAL),
    ARTIST(R.string.device_field_artist, SortCategory.ALPHABETICAL),
    PLAYED(R.string.device_sort_played, SortCategory.DATE_TIME),
    LENGTH(R.string.device_sort_length, SortCategory.DURATION),
    TYPE(R.string.device_sort_type, SortCategory.TYPE)
}

enum class PlayField(@StringRes val labelRes: Int) {
    ARTIST(R.string.device_field_artist),
    SOURCE(R.string.source),
    TYPE(R.string.device_sort_type),
    DATE(R.string.device_sort_date),
    LENGTH(R.string.device_sort_length)
}

object PlayViewPrefs {
    private const val KEY_LAYOUT = "play_view_layout"
    private const val KEY_SORT = "play_view_sort"
    private const val KEY_DESCENDING = "play_view_sort_descending"
    private const val KEY_FIELDS = "play_view_fields"

    val defaultFields: Set<PlayField> = setOf(
        PlayField.ARTIST,
        PlayField.SOURCE,
        PlayField.TYPE,
        PlayField.LENGTH
    )

    fun layout(context: Context): DeviceLayoutMode {
        val raw = preferences(context).getString(KEY_LAYOUT, null)
        return runCatching { DeviceLayoutMode.valueOf(raw.orEmpty()) }
            .getOrDefault(DeviceLayoutMode.LIST)
    }

    fun setLayout(context: Context, layout: DeviceLayoutMode) {
        preferences(context).edit { putString(KEY_LAYOUT, layout.name) }
    }

    fun sort(context: Context): PlaySort {
        val raw = preferences(context).getString(KEY_SORT, null)
        return runCatching { PlaySort.valueOf(raw.orEmpty()) }
            .getOrDefault(PlaySort.DATE)
    }

    fun setSort(context: Context, sort: PlaySort) {
        preferences(context).edit { putString(KEY_SORT, sort.name) }
    }

    fun descending(context: Context): Boolean =
        preferences(context).getBoolean(KEY_DESCENDING, true)

    fun setDescending(context: Context, descending: Boolean) {
        preferences(context).edit { putBoolean(KEY_DESCENDING, descending) }
    }

    fun fields(context: Context): Set<PlayField> {
        val saved = preferences(context).getStringSet(KEY_FIELDS, null) ?: return defaultFields
        return saved.mapNotNull { runCatching { PlayField.valueOf(it) }.getOrNull() }.toSet()
    }

    fun setFields(context: Context, fields: Set<PlayField>) {
        preferences(context).edit {
            putStringSet(KEY_FIELDS, fields.map { it.name }.toSet())
        }
    }

    fun reset(context: Context) {
        preferences(context).edit {
            remove(KEY_LAYOUT)
            remove(KEY_SORT)
            remove(KEY_DESCENDING)
            remove(KEY_FIELDS)
        }
    }

    private fun preferences(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context)
}

/** Normalizes Unix timestamps stored in either seconds (`HistoryItem.time`) or milliseconds. */
fun normalizeEpochMs(raw: Long): Long =
    if (raw in 1L..9_999_999_999L) raw * 1000L else raw

/**
 * Sorts [items] using [sort] and [descending].
 * When [sort] is [PlaySort.DATE] and an item lacks an entry in [dateMsById], its original list
 * order (`items.size - index`, so index 0 is newest) acts as the chronological tie-in.
 */
fun sortPlayItems(
    items: List<PlaybackItem>,
    sort: PlaySort,
    descending: Boolean,
    dateMsById: Map<String, Long>,
    playedAtById: Map<String, Long>
): List<PlaybackItem> {
    if (items.size <= 1) return items
    val indexed = items.withIndex().toList()
    val comparator = Comparator<IndexedValue<PlaybackItem>> { firstEntry, secondEntry ->
        val first = firstEntry.value
        val second = secondEntry.value
        val firstFallbackDate = (items.size - firstEntry.index).toLong()
        val secondFallbackDate = (items.size - secondEntry.index).toLong()
        val firstDate = dateMsById[first.mediaId] ?: playedAtById[first.mediaId] ?: firstFallbackDate
        val secondDate = dateMsById[second.mediaId] ?: playedAtById[second.mediaId] ?: secondFallbackDate

        val primary = when (sort) {
            PlaySort.DATE -> firstDate.compareTo(secondDate)
            PlaySort.TITLE -> SortOrderLabels.compareStrings(first.title, second.title)
            PlaySort.ARTIST -> SortOrderLabels.compareStrings(
                first.artist.orEmpty(),
                second.artist.orEmpty()
            )
            PlaySort.PLAYED ->
                (playedAtById[first.mediaId] ?: 0L).compareTo(playedAtById[second.mediaId] ?: 0L)
            PlaySort.LENGTH -> (first.durationMs ?: 0L).compareTo(second.durationMs ?: 0L)
            PlaySort.TYPE -> first.type.ordinal.compareTo(second.type.ordinal)
        }
        SortOrderLabels.applyDirection(primary, descending) {
            if (sort.category == SortCategory.ALPHABETICAL) {
                secondDate.compareTo(firstDate)
            } else {
                SortOrderLabels.compareStrings(first.title, second.title)
            }
        }
    }
    return indexed.sortedWith(comparator).map { it.value }
}

fun PlaybackItem.playDetailSubtitle(
    context: Context,
    fields: Set<PlayField>,
    dateMs: Long?
): String? {
    val parts = mutableListOf<String>()
    if (PlayField.ARTIST in fields) {
        artist?.takeIf { it.isNotBlank() }?.let(parts::add)
    }
    if (PlayField.SOURCE in fields) {
        when (source) {
            PlaybackSource.YOUTUBE -> parts.add(context.getString(R.string.youtube))
            PlaybackSource.DOWNLOAD -> parts.add(context.getString(R.string.downloads))
            PlaybackSource.LOCAL -> Unit
        }
    }
    if (PlayField.TYPE in fields) {
        parts.add(
            context.getString(if (type == PlaybackType.VIDEO) R.string.video else R.string.audio)
        )
    }
    if (PlayField.DATE in fields && dateMs != null && dateMs > 0L) {
        parts.add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dateMs)))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
