package com.deniscerri.ytdl.util

import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackSource
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.ui.play.PlaySort
import com.deniscerri.ytdl.ui.play.device.DeviceSort
import com.deniscerri.ytdl.ui.play.normalizeEpochMs
import com.deniscerri.ytdl.ui.play.sortPlayItems
import com.deniscerri.ytdl.vault.VaultField
import com.deniscerri.ytdl.vault.VaultItemMeta
import com.deniscerri.ytdl.vault.VaultLayoutMode
import com.deniscerri.ytdl.vault.VaultPickerField
import com.deniscerri.ytdl.vault.VaultPickerSortBy
import com.deniscerri.ytdl.vault.VaultSettings
import com.deniscerri.ytdl.vault.VaultSortBy
import com.deniscerri.ytdl.vault.VaultUiItem
import com.deniscerri.ytdl.vault.sortVaultItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SortingAndViewOptionsTest {

    @Test
    fun sortOrderLabels_matchEachCategoryAndDefaultDirection() {
        assertEquals(
            R.string.device_order_newest to R.string.device_order_oldest,
            SortOrderLabels.orderLabelRes(SortCategory.DATE_TIME)
        )
        assertTrue(SortOrderLabels.defaultDescending(SortCategory.DATE_TIME))

        assertEquals(
            R.string.order_alpha_asc to R.string.order_alpha_desc,
            SortOrderLabels.orderLabelRes(SortCategory.ALPHABETICAL)
        )
        assertFalse(SortOrderLabels.defaultDescending(SortCategory.ALPHABETICAL))

        assertEquals(
            R.string.order_largest_first to R.string.order_smallest_first,
            SortOrderLabels.orderLabelRes(SortCategory.SIZE)
        )
        assertTrue(SortOrderLabels.defaultDescending(SortCategory.SIZE))

        assertEquals(
            R.string.order_longest_first to R.string.order_shortest_first,
            SortOrderLabels.orderLabelRes(SortCategory.DURATION)
        )
        assertTrue(SortOrderLabels.defaultDescending(SortCategory.DURATION))

        assertEquals(
            R.string.order_highest_first to R.string.order_lowest_first,
            SortOrderLabels.orderLabelRes(SortCategory.MAGNITUDE)
        )
        assertTrue(SortOrderLabels.defaultDescending(SortCategory.MAGNITUDE))

        assertEquals(
            R.string.order_active_first to R.string.order_new_first,
            SortOrderLabels.orderLabelRes(SortCategory.STATUS)
        )
        assertEquals(
            R.string.order_videos_first to R.string.order_audio_first,
            SortOrderLabels.orderLabelRes(SortCategory.TYPE)
        )
    }

    @Test
    fun sortOrderLabels_buttonRoundTripWorksForAlphaAndTime() {
        assertTrue(SortOrderLabels.isPrimarySelected(SortCategory.ALPHABETICAL, descending = false))
        assertFalse(SortOrderLabels.isPrimarySelected(SortCategory.ALPHABETICAL, descending = true))
        assertEquals(false, SortOrderLabels.descendingForButton(SortCategory.ALPHABETICAL, primarySelected = true))
        assertEquals(true, SortOrderLabels.descendingForButton(SortCategory.ALPHABETICAL, primarySelected = false))
        assertEquals(R.string.order_alpha_asc, SortOrderLabels.activeLabelRes(SortCategory.ALPHABETICAL, descending = false))
        assertEquals(R.string.order_alpha_desc, SortOrderLabels.activeLabelRes(SortCategory.ALPHABETICAL, descending = true))

        assertTrue(SortOrderLabels.isPrimarySelected(SortCategory.DATE_TIME, descending = true))
        assertFalse(SortOrderLabels.isPrimarySelected(SortCategory.DATE_TIME, descending = false))
        assertEquals(true, SortOrderLabels.descendingForButton(SortCategory.DATE_TIME, primarySelected = true))
        assertEquals(false, SortOrderLabels.descendingForButton(SortCategory.DATE_TIME, primarySelected = false))
        assertEquals(R.string.device_order_newest, SortOrderLabels.activeLabelRes(SortCategory.DATE_TIME, descending = true))
        assertEquals(R.string.device_order_oldest, SortOrderLabels.activeLabelRes(SortCategory.DATE_TIME, descending = false))
    }

    @Test
    fun defaultSortEntriesStartWithDateAcrossSurfaces() {
        assertEquals(DeviceSort.DATE, DeviceSort.entries.first())
        assertEquals(PlaySort.DATE, PlaySort.entries.first())
        assertEquals(VaultSortBy.DATE, VaultSortBy.entries.first())
        assertEquals(VaultPickerSortBy.DATE, VaultPickerSortBy.entries.first())
        assertEquals(DeviceSort.DATE, VaultPickerSortBy.DATE.toDeviceSort())
    }

    @Test
    fun sortPlayItems_ordersByDateTitleLengthAndType() {
        val alpha = PlaybackItem(
            mediaId = "1",
            type = PlaybackType.AUDIO,
            title = "Alpha",
            artist = "Zed",
            durationMs = 120_000L,
            source = PlaybackSource.DOWNLOAD
        )
        val beta = PlaybackItem(
            mediaId = "2",
            type = PlaybackType.VIDEO,
            title = "Beta",
            artist = "Amy",
            durationMs = 300_000L,
            source = PlaybackSource.DOWNLOAD
        )
        val gamma = PlaybackItem(
            mediaId = "3",
            type = PlaybackType.AUDIO,
            title = "gamma",
            artist = "Ben",
            durationMs = 60_000L,
            source = PlaybackSource.YOUTUBE
        )
        val dates = mapOf(
            "1" to normalizeEpochMs(1_700_000_100L),
            "2" to normalizeEpochMs(1_700_000_300L),
            "3" to normalizeEpochMs(1_700_000_200L)
        )
        val played = mapOf("1" to 500L, "2" to 100L, "3" to 900L)

        // Default Date + Newest first (descending = true)
        assertEquals(
            listOf("2", "3", "1"),
            sortPlayItems(listOf(alpha, beta, gamma), PlaySort.DATE, true, dates, played).map { it.mediaId }
        )
        // Oldest first
        assertEquals(
            listOf("1", "3", "2"),
            sortPlayItems(listOf(alpha, beta, gamma), PlaySort.DATE, false, dates, played).map { it.mediaId }
        )
        // Title A -> Z (descending = false) and Z -> A (descending = true)
        assertEquals(
            listOf("1", "2", "3"),
            sortPlayItems(listOf(gamma, beta, alpha), PlaySort.TITLE, false, dates, played).map { it.mediaId }
        )
        assertEquals(
            listOf("3", "2", "1"),
            sortPlayItems(listOf(alpha, beta, gamma), PlaySort.TITLE, true, dates, played).map { it.mediaId }
        )
        // Artist A -> Z
        assertEquals(
            listOf("2", "3", "1"),
            sortPlayItems(listOf(alpha, beta, gamma), PlaySort.ARTIST, false, dates, played).map { it.mediaId }
        )
        // Length Longest first
        assertEquals(
            listOf("2", "1", "3"),
            sortPlayItems(listOf(alpha, beta, gamma), PlaySort.LENGTH, true, dates, played).map { it.mediaId }
        )
        // Type Videos first
        assertEquals(
            "2",
            sortPlayItems(listOf(alpha, beta, gamma), PlaySort.TYPE, true, dates, played).first().mediaId
        )
    }

    @Test
    fun vaultSettings_defaultsAndJsonRoundTrip() {
        // Legacy JSON missing new view fields defaults cleanly to Date + Newest first + Grid.
        val legacy = VaultSettings.fromJsonString("""{"autoWipeThreshold":7,"stealthMode":true}""")
        assertEquals(VaultLayoutMode.GRID, legacy.layoutMode)
        assertEquals(VaultSortBy.DATE, legacy.sortBy)
        assertTrue(legacy.sortDescending)
        assertEquals(VaultSettings.DEFAULT_VISIBLE_FIELDS, legacy.visibleFields)
        assertEquals(VaultLayoutMode.GRID, legacy.pickerLayoutMode)
        assertEquals(VaultPickerSortBy.DATE, legacy.pickerSortBy)
        assertTrue(legacy.pickerSortDescending)
        assertEquals(VaultSettings.DEFAULT_PICKER_VISIBLE_FIELDS, legacy.pickerVisibleFields)
        assertEquals(7, legacy.autoWipeThreshold)
        assertTrue(legacy.stealthMode)

        val custom = VaultSettings(
            layoutMode = VaultLayoutMode.LIST,
            sortBy = VaultSortBy.TITLE,
            sortDescending = false,
            visibleFields = setOf(VaultField.DATE, VaultField.AUTHOR),
            pickerLayoutMode = VaultLayoutMode.LIST,
            pickerSortBy = VaultPickerSortBy.SIZE,
            pickerSortDescending = false,
            pickerVisibleFields = setOf(VaultPickerField.DATE, VaultPickerField.RESOLUTION)
        )
        val restored = VaultSettings.fromJsonString(custom.toJsonString())
        assertEquals(custom, restored)

        val reset = restored.withResetViewOptions()
        assertEquals(VaultLayoutMode.GRID, reset.layoutMode)
        assertEquals(VaultSortBy.DATE, reset.sortBy)
        assertTrue(reset.sortDescending)
        assertEquals(VaultSettings.DEFAULT_VISIBLE_FIELDS, reset.visibleFields)
    }

    @Test
    fun sortVaultItems_supportsAllSortFieldsAndTieBreakers() {
        fun item(
            id: String,
            title: String,
            dateAddedMs: Long,
            durationMs: Long,
            sizeBytes: Long,
            w: Int,
            h: Int,
            author: String,
            website: String
        ) = VaultUiItem(
            meta = VaultItemMeta(
                id = id,
                title = title,
                originalFileName = "$title.mp4",
                durationMs = durationMs,
                filesizeBytes = sizeBytes,
                width = w,
                height = h,
                mimeType = "video/mp4",
                dateAddedMs = dateAddedMs,
                author = author,
                website = website
            ),
            thumbnail = null
        )

        val a = item("a", "Zebra", 100L, 40_000L, 2_000L, 1280, 720, "Charlie", "vimeo")
        val b = item("b", "Apple", 300L, 10_000L, 5_000L, 3840, 2160, "Alice", "youtube")
        val c = item("c", "mango", 200L, 90_000L, 1_000L, 1920, 1080, "Bob", "archive")

        assertEquals(listOf("b", "c", "a"), sortVaultItems(listOf(a, b, c), VaultSortBy.DATE, true).map { it.meta.id })
        assertEquals(listOf("b", "c", "a"), sortVaultItems(listOf(a, b, c), VaultSortBy.TITLE, false).map { it.meta.id })
        assertEquals(listOf("a", "c", "b"), sortVaultItems(listOf(a, b, c), VaultSortBy.TITLE, true).map { it.meta.id })
        assertEquals(listOf("c", "a", "b"), sortVaultItems(listOf(a, b, c), VaultSortBy.LENGTH, true).map { it.meta.id })
        assertEquals(listOf("b", "a", "c"), sortVaultItems(listOf(a, b, c), VaultSortBy.SIZE, true).map { it.meta.id })
        assertEquals(listOf("b", "c", "a"), sortVaultItems(listOf(a, b, c), VaultSortBy.RESOLUTION, true).map { it.meta.id })
        assertEquals(listOf("b", "c", "a"), sortVaultItems(listOf(a, b, c), VaultSortBy.AUTHOR, false).map { it.meta.id })
        assertEquals(listOf("c", "a", "b"), sortVaultItems(listOf(a, b, c), VaultSortBy.WEBSITE, false).map { it.meta.id })
    }
}
