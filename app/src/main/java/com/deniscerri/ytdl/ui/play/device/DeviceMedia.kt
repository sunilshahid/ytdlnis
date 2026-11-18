package com.deniscerri.ytdl.ui.play.device

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackSource
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.deniscerri.ytdl.util.SortCategory
import com.deniscerri.ytdl.util.SortOrderLabels
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class DeviceShowMode(@StringRes val labelRes: Int) {
    ALL_FOLDERS(R.string.device_show_all_folders),
    FILES(R.string.device_show_files),
    FOLDERS(R.string.device_show_folders)
}

enum class DeviceLayoutMode(@StringRes val labelRes: Int) {
    LIST(R.string.list),
    GRID(R.string.grid)
}

enum class DeviceSort(@StringRes val labelRes: Int, val category: SortCategory) {
    DATE(R.string.device_sort_date, SortCategory.DATE_TIME),
    TITLE(R.string.device_sort_title, SortCategory.ALPHABETICAL),
    PLAYED(R.string.device_sort_played, SortCategory.DATE_TIME),
    STATUS(R.string.device_sort_status, SortCategory.STATUS),
    LENGTH(R.string.device_sort_length, SortCategory.DURATION),
    SIZE(R.string.device_sort_size, SortCategory.SIZE),
    RESOLUTION(R.string.device_sort_resolution, SortCategory.MAGNITUDE),
    PATH(R.string.device_sort_path, SortCategory.ALPHABETICAL),
    FRAME_RATE(R.string.device_sort_frame_rate, SortCategory.MAGNITUDE),
    TYPE(R.string.device_sort_type, SortCategory.TYPE)
}

enum class DeviceField(@StringRes val labelRes: Int) {
    ARTIST(R.string.device_field_artist),
    DATE(R.string.device_sort_date),
    PLAYED(R.string.device_sort_played),
    STATUS(R.string.device_sort_status),
    LENGTH(R.string.device_sort_length),
    SIZE(R.string.device_sort_size),
    RESOLUTION(R.string.device_sort_resolution),
    PATH(R.string.device_sort_path),
    FRAME_RATE(R.string.device_sort_frame_rate),
    TYPE(R.string.device_sort_type)
}

enum class DevicePlayStatus(val rank: Int) {
    NEW(1),
    PLAYED(2),
    QUEUED(3),
    PLAYING(4)
}

data class DeviceMedia(
    val storeId: Long,
    val type: PlaybackType,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedMs: Long,
    val folderPath: String,
    val displayName: String,
    val mimeType: String?,
    val width: Int,
    val height: Int,
    val albumId: Long?,
    val contentUri: Uri,
    val albumArtUri: Uri?,
    val frameRate: Float? = null,
    /** Absolute path for files that MediaStore did not index (for example .opus downloads). */
    val filePath: String? = null
) {
    val mediaId: String
        get() {
            val kind = type.name.lowercase(Locale.ROOT)
            val path = filePath?.takeIf { it.isNotBlank() }
            return if (path != null) {
                "device:$kind:file:${path.lowercase(Locale.ROOT)}"
            } else {
                "device:$kind:$storeId"
            }
        }

    val folderName: String
        get() = folderPath.trim('/').substringAfterLast('/').takeIf { it.isNotBlank() }.orEmpty()

    fun toPlaybackItem(): PlaybackItem = PlaybackItem(
        mediaId = mediaId,
        type = type,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs.takeIf { it > 0L },
        artworkUri = albumArtUri?.toString(),
        source = PlaybackSource.LOCAL,
        localUri = contentUri.toString(),
        extras = mapOf(KEY_FOLDER_PATH to folderPath)
    )

    companion object {
        const val KEY_FOLDER_PATH = "device.folder.path"
    }
}

data class DeviceFolder(
    val path: String,
    val items: List<DeviceMedia>
) {
    val sizeBytes: Long get() = items.sumOf { it.sizeBytes }
}

enum class DeviceRowType {
    HEADER,
    FILE_LIST,
    FILE_GRID,
    FOLDER_LIST,
    FOLDER_GRID,
    MESSAGE
}

sealed interface DeviceRow {
    val stableId: String

    data class Header(val title: String, val count: Int) : DeviceRow {
        override val stableId: String get() = "device_header:$title"
    }

    data class File(
        val media: DeviceMedia,
        val status: DevicePlayStatus,
        val isCurrent: Boolean,
        val fields: Set<DeviceField>,
        val lastPlayedAt: Long
    ) : DeviceRow {
        override val stableId: String get() = "device_file:${media.mediaId}"
    }

    data class Folder(val folder: DeviceFolder) : DeviceRow {
        override val stableId: String get() = "device_folder:${folder.path}"
    }

    data class FolderCarousel(
        val folders: List<DeviceFolder>,
        val selectedPath: String?
    ) : DeviceRow {
        override val stableId: String get() = "device_folder_carousel"
    }

    data class Message(
        val title: String,
        val subtitle: String? = null,
        val icon: Int = R.drawable.baseline_folder_24,
        val actionLabel: String? = null,
        val action: (() -> Unit)? = null
    ) : DeviceRow {
        override val stableId: String get() = "device_message:$title"
    }
}

object DeviceFilesPrefs {
    const val KEY_ENABLED = "show_device_files"
    const val KEY_VIEW_MODE = "device_files_view_mode"
    const val KEY_LAYOUT = "device_files_layout"
    const val KEY_SORT = "device_files_sort"
    const val KEY_NEWEST_FIRST = "device_files_newest_first"
    const val KEY_FIELDS = "device_files_fields"
    const val KEY_SHOW_CAROUSEL = "device_files_show_carousel"
    const val KEY_FOLDER_URI = "device_files_folder_uri"
    const val KEY_FOLDER_PATH = "device_files_folder_path"
    const val KEY_FOLDER_NAME = "device_files_folder_name"

    private val defaultFields = setOf(
        DeviceField.ARTIST,
        DeviceField.LENGTH,
        DeviceField.SIZE,
        DeviceField.TYPE
    )

    fun isEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_ENABLED, true)

    fun showCarousel(context: Context): Boolean =
        preferences(context).getBoolean(KEY_SHOW_CAROUSEL, false)

    fun setShowCarousel(context: Context, show: Boolean) {
        preferences(context).edit { putBoolean(KEY_SHOW_CAROUSEL, show) }
    }

    fun showMode(context: Context): DeviceShowMode {
        val value = preferences(context).getString(KEY_VIEW_MODE, null)
        return runCatching { DeviceShowMode.valueOf(value.orEmpty()) }
            .getOrDefault(DeviceShowMode.FOLDERS)
    }

    fun setShowMode(context: Context, mode: DeviceShowMode) {
        preferences(context).edit { putString(KEY_VIEW_MODE, mode.name) }
    }

    fun layout(context: Context): DeviceLayoutMode {
        val value = preferences(context).getString(KEY_LAYOUT, null)
        return runCatching { DeviceLayoutMode.valueOf(value.orEmpty()) }
            .getOrDefault(DeviceLayoutMode.LIST)
    }

    fun setLayout(context: Context, layout: DeviceLayoutMode) {
        preferences(context).edit { putString(KEY_LAYOUT, layout.name) }
    }

    fun sort(context: Context): DeviceSort {
        val value = preferences(context).getString(KEY_SORT, null)
        return runCatching { DeviceSort.valueOf(value.orEmpty()) }
            .getOrDefault(DeviceSort.DATE)
    }

    fun setSort(context: Context, sort: DeviceSort) {
        preferences(context).edit { putString(KEY_SORT, sort.name) }
    }

    fun newestFirst(context: Context): Boolean =
        preferences(context).getBoolean(KEY_NEWEST_FIRST, true)

    fun setNewestFirst(context: Context, newestFirst: Boolean) {
        preferences(context).edit { putBoolean(KEY_NEWEST_FIRST, newestFirst) }
    }

    fun fields(context: Context): Set<DeviceField> {
        val saved = preferences(context).getStringSet(KEY_FIELDS, null)
        if (saved == null) return defaultFields
        return saved.mapNotNull { runCatching { DeviceField.valueOf(it) }.getOrNull() }.toSet()
    }

    fun setFields(context: Context, fields: Set<DeviceField>) {
        preferences(context).edit {
            putStringSet(KEY_FIELDS, fields.map { it.name }.toSet())
        }
    }

    fun folderPath(context: Context): String? =
        preferences(context).getString(KEY_FOLDER_PATH, null)
            ?.trim('/')
            ?.takeIf { it.isNotBlank() }

    fun folderUri(context: Context): Uri? =
        preferences(context).getString(KEY_FOLDER_URI, null)
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)

    fun folderName(context: Context): String? =
        preferences(context).getString(KEY_FOLDER_NAME, null)
            ?.takeIf { it.isNotBlank() }

    fun folderSummary(context: Context): String =
        folderName(context) ?: context.getString(R.string.device_files_all_folders)

    fun setFolder(context: Context, uri: Uri, path: String?, name: String) {
        preferences(context).edit {
            putString(KEY_FOLDER_URI, uri.toString())
            putString(KEY_FOLDER_PATH, path.orEmpty())
            putString(KEY_FOLDER_NAME, name)
        }
    }

    fun clearFolder(context: Context) {
        preferences(context).edit {
            remove(KEY_FOLDER_URI)
            remove(KEY_FOLDER_PATH)
            remove(KEY_FOLDER_NAME)
        }
    }

    fun reset(context: Context) {
        preferences(context).edit {
            remove(KEY_VIEW_MODE)
            remove(KEY_LAYOUT)
            remove(KEY_SORT)
            remove(KEY_NEWEST_FIRST)
            remove(KEY_FIELDS)
            remove(KEY_SHOW_CAROUSEL)
        }
    }

    fun relativePathFromTreeUri(uri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: uri.lastPathSegment
            ?: return null
        return documentId.substringAfter(':', documentId).trim('/').takeIf { it.isNotBlank() }
    }

    fun folderNameForPath(context: Context, path: String?): String {
        val last = path?.trim('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return last ?: context.getString(R.string.device_files_internal_storage)
    }

    private fun preferences(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context)
}

fun DevicePlayStatus.labelRes(): Int = when (this) {
    DevicePlayStatus.PLAYING -> R.string.device_status_playing
    DevicePlayStatus.QUEUED -> R.string.device_status_queued
    DevicePlayStatus.PLAYED -> R.string.device_status_played
    DevicePlayStatus.NEW -> R.string.device_status_new
}

fun sortDeviceMedia(
    items: List<DeviceMedia>,
    sort: DeviceSort,
    newestFirst: Boolean,
    playedAt: Map<String, Long>,
    statusFor: (DeviceMedia) -> DevicePlayStatus
): List<DeviceMedia> {
    val comparator = Comparator<DeviceMedia> { first, second ->
        val primary = when (sort) {
            DeviceSort.TITLE -> SortOrderLabels.compareStrings(first.title, second.title)
            DeviceSort.DATE -> first.dateAddedMs.compareTo(second.dateAddedMs)
            DeviceSort.PLAYED ->
                (playedAt[first.mediaId] ?: 0L).compareTo(playedAt[second.mediaId] ?: 0L)
            DeviceSort.STATUS ->
                statusFor(first).rank.compareTo(statusFor(second).rank)
            DeviceSort.LENGTH -> first.durationMs.compareTo(second.durationMs)
            DeviceSort.SIZE -> first.sizeBytes.compareTo(second.sizeBytes)
            DeviceSort.RESOLUTION -> resolutionArea(first).compareTo(resolutionArea(second))
            DeviceSort.PATH -> SortOrderLabels.compareStrings(pathKey(first), pathKey(second))
            DeviceSort.FRAME_RATE ->
                (first.frameRate ?: -1f).compareTo(second.frameRate ?: -1f)
            DeviceSort.TYPE -> first.type.ordinal.compareTo(second.type.ordinal)
        }
        SortOrderLabels.applyDirection(primary, newestFirst) {
            if (sort.category == SortCategory.ALPHABETICAL) {
                second.dateAddedMs.compareTo(first.dateAddedMs)
            } else {
                SortOrderLabels.compareStrings(first.title, second.title)
            }
        }
    }
    return items.sortedWith(comparator)
}

fun groupDeviceFolders(items: List<DeviceMedia>): List<DeviceFolder> =
    items.groupBy { it.folderPath }.map { (path, folderItems) ->
        DeviceFolder(path = path, items = folderItems)
    }

fun sortDeviceFolders(
    folders: List<DeviceFolder>,
    sort: DeviceSort,
    newestFirst: Boolean,
    playedAt: Map<String, Long>,
    statusFor: (DeviceMedia) -> DevicePlayStatus
): List<DeviceFolder> {
    val comparator = Comparator<DeviceFolder> { first, second ->
        val primary = when (sort) {
            DeviceSort.TITLE -> SortOrderLabels.compareStrings(folderLabel(first), folderLabel(second))
            DeviceSort.DATE -> folderDate(first).compareTo(folderDate(second))
            DeviceSort.PLAYED -> folderPlayed(first, playedAt).compareTo(folderPlayed(second, playedAt))
            DeviceSort.STATUS -> folderStatus(first, statusFor).rank
                .compareTo(folderStatus(second, statusFor).rank)
            DeviceSort.LENGTH -> folderDuration(first).compareTo(folderDuration(second))
            DeviceSort.SIZE -> first.sizeBytes.compareTo(second.sizeBytes)
            DeviceSort.RESOLUTION -> first.items.maxOfOrNull(::resolutionArea)
                .orZero().compareTo(second.items.maxOfOrNull(::resolutionArea).orZero())
            DeviceSort.PATH -> SortOrderLabels.compareStrings(first.path, second.path)
            DeviceSort.FRAME_RATE -> first.items.mapNotNull { it.frameRate }.maxOrNull()
                .orZero().compareTo(second.items.mapNotNull { it.frameRate }.maxOrNull().orZero())
            DeviceSort.TYPE -> folderType(first).compareTo(folderType(second))
        }
        SortOrderLabels.applyDirection(primary, newestFirst) {
            if (sort.category == SortCategory.ALPHABETICAL) {
                folderDate(second).compareTo(folderDate(first))
            } else {
                SortOrderLabels.compareStrings(folderLabel(first), folderLabel(second))
            }
        }
    }
    return folders.sortedWith(comparator)
}

fun DeviceMedia.detailParts(
    context: Context,
    fields: Set<DeviceField>,
    status: DevicePlayStatus,
    lastPlayedAt: Long
): List<String> {
    val parts = mutableListOf<String>()
    if (DeviceField.ARTIST in fields) {
        artist?.takeIf { it.isNotBlank() }?.let(parts::add)
    }
    if (DeviceField.DATE in fields && dateAddedMs > 0L) {
        parts += DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dateAddedMs))
    }
    if (DeviceField.PLAYED in fields && lastPlayedAt > 0L) {
        parts += DateUtils.getRelativeTimeSpanString(
            lastPlayedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }
    if (DeviceField.STATUS in fields) {
        parts += context.getString(status.labelRes())
    }
    if (DeviceField.LENGTH in fields && durationMs > 0L) {
        parts += durationMs.toStringTimeStamp()
    }
    if (DeviceField.SIZE in fields && sizeBytes > 0L) {
        parts += Formatter.formatShortFileSize(context, sizeBytes)
    }
    if (DeviceField.RESOLUTION in fields && width > 0 && height > 0) {
        parts += "${width}×$height"
    }
    if (DeviceField.PATH in fields) {
        parts += folderPath.ifBlank { context.getString(R.string.device_files_internal_storage) }
    }
    if (DeviceField.FRAME_RATE in fields) {
        frameRate?.let { parts += String.format(Locale.US, "%.2f fps", it) }
    }
    if (DeviceField.TYPE in fields) {
        parts += context.getString(if (type == PlaybackType.VIDEO) R.string.video else R.string.audio)
    }
    return parts
}

fun DeviceFolder.label(context: Context): String =
    path.trim('/').substringAfterLast('/').takeIf { it.isNotBlank() }
        ?: context.getString(R.string.device_files_internal_storage)

fun DeviceFolder.detail(context: Context): String {
    val count = context.resources.getQuantityString(
        R.plurals.device_files_count,
        items.size,
        items.size
    )
    val size = Formatter.formatShortFileSize(context, sizeBytes)
    return "$count · $size"
}

private fun folderLabel(folder: DeviceFolder): String =
    folder.path.trim('/').substringAfterLast('/').ifBlank { folder.path }

private fun folderDate(folder: DeviceFolder): Long = folder.items.maxOfOrNull { it.dateAddedMs } ?: 0L

private fun folderPlayed(folder: DeviceFolder, playedAt: Map<String, Long>): Long =
    folder.items.maxOfOrNull { playedAt[it.mediaId] ?: 0L } ?: 0L

private fun folderStatus(
    folder: DeviceFolder,
    statusFor: (DeviceMedia) -> DevicePlayStatus
): DevicePlayStatus = folder.items.maxByOrNull { statusFor(it).rank }?.let(statusFor)
    ?: DevicePlayStatus.NEW

private fun folderDuration(folder: DeviceFolder): Long = folder.items.sumOf { it.durationMs }

private fun folderType(folder: DeviceFolder): Int = folder.items.maxOfOrNull { it.type.ordinal } ?: 0

private fun resolutionArea(item: DeviceMedia): Int = item.width * item.height

private fun pathKey(item: DeviceMedia): String =
    "${item.folderPath}/${item.displayName}"

private fun Float?.orZero(): Float = this ?: 0f
private fun Int?.orZero(): Int = this ?: 0
