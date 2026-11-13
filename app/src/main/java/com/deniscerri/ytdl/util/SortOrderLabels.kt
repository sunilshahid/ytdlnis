package com.deniscerri.ytdl.util

import androidx.annotation.StringRes
import com.deniscerri.ytdl.R

enum class SortCategory {
    DATE_TIME,
    ALPHABETICAL,
    SIZE,
    DURATION,
    MAGNITUDE,
    STATUS,
    TYPE
}

/**
 * Shared sort-direction semantics and dynamic "Order" button labels used across
 * Device Files, Play library, Private Vault, and Downloads History.
 */
object SortOrderLabels {

    /**
     * Natural primary direction (`descending` flag) when a sort field is newly selected:
     * ascending (`false`, A → Z) for alphabetical sorts, and descending (`true`, Newest /
     * Largest / Longest / Highest / Active / Videos first) for magnitude and time sorts.
     */
    fun defaultDescending(category: SortCategory): Boolean = when (category) {
        SortCategory.ALPHABETICAL -> false
        else -> true
    }

    /** String resources `(primaryButtonRes, secondaryButtonRes)` for a 2-button toggle. */
    fun orderLabelRes(category: SortCategory): Pair<Int, Int> = when (category) {
        SortCategory.DATE_TIME -> R.string.device_order_newest to R.string.device_order_oldest
        SortCategory.ALPHABETICAL -> R.string.order_alpha_asc to R.string.order_alpha_desc
        SortCategory.SIZE -> R.string.order_largest_first to R.string.order_smallest_first
        SortCategory.DURATION -> R.string.order_longest_first to R.string.order_shortest_first
        SortCategory.MAGNITUDE -> R.string.order_highest_first to R.string.order_lowest_first
        SortCategory.STATUS -> R.string.order_active_first to R.string.order_new_first
        SortCategory.TYPE -> R.string.order_videos_first to R.string.order_audio_first
    }

    /** Active human-readable direction label for `(category, descending)`. */
    @StringRes
    fun activeLabelRes(category: SortCategory, descending: Boolean): Int {
        val (primary, secondary) = orderLabelRes(category)
        return if (isPrimarySelected(category, descending)) primary else secondary
    }

    fun isPrimarySelected(category: SortCategory, descending: Boolean): Boolean =
        descending == defaultDescending(category)

    fun descendingForButton(category: SortCategory, primarySelected: Boolean): Boolean =
        if (primarySelected) defaultDescending(category) else !defaultDescending(category)

    fun compareStrings(first: String, second: String): Int {
        val ci = first.compareTo(second, ignoreCase = true)
        return if (ci != 0) ci else first.compareTo(second)
    }

    inline fun applyDirection(
        primaryCmp: Int,
        descending: Boolean,
        tieBreaker: () -> Int
    ): Int {
        if (primaryCmp != 0) {
            return if (descending) -primaryCmp else primaryCmp
        }
        return tieBreaker()
    }
}
