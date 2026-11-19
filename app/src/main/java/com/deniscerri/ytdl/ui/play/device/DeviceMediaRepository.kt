package com.deniscerri.ytdl.ui.play.device

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import android.util.Size
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.util.FrameExtractor
import com.deniscerri.ytdl.util.PlaybackColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import java.util.Collections
import java.util.WeakHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

private class ScannedMeta(
    val path: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val title: String?,
    val artist: String?,
    val album: String?
)

private val AUDIO_SCAN_EXTENSIONS = setOf(
    "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "oga", "mka", "wma", "amr"
)

private val VIDEO_SCAN_EXTENSIONS = setOf(
    "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "3gp", "flv", "wmv"
)

object DevicePermissions {    fun hasAudio(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasVideo(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun required(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

class DeviceMediaRepository(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val frameRateLock = Any()
    private val frameRateCache = mutableMapOf<String, Float?>()
    private val frameRateKnown = mutableSetOf<String>()

    private var metaCacheLoaded = false
    private val metaCacheLock = Any()
    private val metaCache = LinkedHashMap<String, ScannedMeta>()

    private companion object {
        const val MAX_SCAN_FILES = 4000
        const val MAX_SCAN_DEPTH = 6
        const val META_CACHE_KEY = "device_scan_meta_v1"
        const val META_CACHE_MAX = 1500
        const val WARM_UP_PARALLELISM = 3
    }

    suspend fun load(
        folderPath: String?,
        includeAudio: Boolean = true,
        includeVideo: Boolean = true
    ): List<DeviceMedia> = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val indexedPaths = mutableSetOf<String>()
        val storeItems = buildList {
            if (includeAudio) addAll(queryAudio(folderPath, indexedPaths))
            if (includeVideo) addAll(queryVideo(folderPath, indexedPaths))
        }
        val scanned = runCatching {
            scanUnindexed(folderPath, includeAudio, includeVideo, indexedPaths)
        }.getOrDefault(emptyList())
        Log.d(
            "DeviceScan",
            "load done: store=${storeItems.size} scanned=${scanned.size} " +
                "took=${System.currentTimeMillis() - startedAt}ms folder=$folderPath"
        )
        (storeItems + scanned).sortedBy { it.title.lowercase() }
    }

    suspend fun enrichFrameRates(items: List<DeviceMedia>): List<DeviceMedia> =
        withContext(Dispatchers.IO) {
            items.map { item ->
                if (item.type != PlaybackType.VIDEO || item.frameRate != null) {
                    item
                } else {
                    item.copy(frameRate = frameRateFor(item))
                }
            }
        }

    private fun queryAudio(folderPath: String?, indexedPaths: MutableSet<String>): List<DeviceMedia> {
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ID
        )
        projection += pathColumns()
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        return query(uri, projection.toTypedArray(), PlaybackType.AUDIO, folderPath, indexedPaths)
    }

    private fun queryVideo(folderPath: String?, indexedPaths: MutableSet<String>): List<DeviceMedia> {
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.ALBUM,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        projection += pathColumns()
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        return query(uri, projection.toTypedArray(), PlaybackType.VIDEO, folderPath, indexedPaths)
    }

    private fun query(
        collection: Uri,
        projection: Array<String>,
        type: PlaybackType,
        folderPath: String?,
        indexedPaths: MutableSet<String>
    ): List<DeviceMedia> {
        val result = mutableListOf<DeviceMedia>()
        val cursor = runCatching {
            resolver.query(collection, projection, null, null, null)
        }.getOrNull() ?: return result
        cursor.use {
            val idColumn = it.getColumnIndex(MediaStore.MediaColumns._ID)
            val titleColumn = it.getColumnIndex(MediaStore.MediaColumns.TITLE)
            val artistColumn = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val durationColumn = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val sizeColumn = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val dateColumn = it.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val nameColumn = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val widthColumn = it.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightColumn = it.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val albumIdColumn = it.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val relativeColumn = it.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dataColumn = it.getColumnIndex(MediaStore.MediaColumns.DATA)

            if (idColumn < 0) return result

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val displayName = it.stringOrNull(nameColumn) ?: it.stringOrNull(titleColumn).orEmpty()
                val relative = it.stringOrNull(relativeColumn)
                val data = it.stringOrNull(dataColumn)
                val folder = normalizeFolder(relative ?: folderFromData(data))
                if (!inScope(folder, folderPath)) continue
                data?.takeIf { it.isNotBlank() }?.let { indexedPaths += it }

                val albumId = if (type == PlaybackType.AUDIO && albumIdColumn >= 0) {
                    it.getLong(albumIdColumn).takeIf { value -> value > 0L }
                } else {
                    null
                }
                val albumArt = albumId?.let {
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        it
                    )
                }
                result += DeviceMedia(
                    storeId = id,
                    type = type,
                    title = it.stringOrNull(titleColumn)
                        ?.takeIf { value -> value.isNotBlank() }
                        ?: displayName,
                    artist = it.stringOrNull(artistColumn)
                        ?.takeIf { value -> value.isNotBlank() && value != "<unknown>" },
                    album = it.stringOrNull(albumColumn)?.takeIf { value -> value.isNotBlank() },
                    durationMs = if (durationColumn >= 0) it.getLong(durationColumn) else 0L,
                    sizeBytes = if (sizeColumn >= 0) it.getLong(sizeColumn) else 0L,
                    dateAddedMs = if (dateColumn >= 0) it.getLong(dateColumn) * 1000L else 0L,
                    folderPath = folder,
                    displayName = displayName,
                    mimeType = it.stringOrNull(mimeColumn),
                    width = if (widthColumn >= 0) it.getInt(widthColumn) else 0,
                    height = if (heightColumn >= 0) it.getInt(heightColumn) else 0,
                    albumId = albumId,
                    contentUri = ContentUris.withAppendedId(collection, id),
                    albumArtUri = albumArt,
                    filePath = data?.takeIf { value -> value.isNotBlank() }
                )
            }
        }
        return result
    }

    /**
     * Walks the filesystem for media files that MediaStore did not index (some formats such as
     * `.opus` are missing from the store on many devices) and builds device rows for them.
     */
    private fun scanUnindexed(
        folderPath: String?,
        includeAudio: Boolean,
        includeVideo: Boolean,
        indexedPaths: Set<String>
    ): List<DeviceMedia> {
        if (!includeAudio && !includeVideo) return emptyList()
        val roots = scanRoots(folderPath)
        if (roots.isEmpty()) return emptyList()

        val audioExtensions = AUDIO_SCAN_EXTENSIONS
        val videoExtensions = VIDEO_SCAN_EXTENSIONS
        val skippedDirs = setOf("android", "lost.dir")
        val indexedLookup = indexedPaths.mapTo(mutableSetOf()) { it.lowercase(Locale.US) }
        val scannedPaths = mutableSetOf<String>()
        val visitedDirs = mutableSetOf<String>()

        val result = mutableListOf<DeviceMedia>()
        val stack = ArrayDeque<Pair<File, Int>>()
        roots.forEach { stack += it to 0 }

        while (stack.isNotEmpty() && result.size < MAX_SCAN_FILES) {
            val (directory, depth) = stack.removeLast()
            if (!visitedDirs.add(directory.absolutePath.lowercase(Locale.US))) continue
            val entries = runCatching { directory.listFiles() }.getOrNull() ?: continue
            for (entry in entries) {
                if (result.size >= MAX_SCAN_FILES) break
                val name = entry.name
                if (entry.isDirectory) {
                    if (name.startsWith(".") || name.lowercase(Locale.US) in skippedDirs) continue
                    if (depth < MAX_SCAN_DEPTH) stack += entry to depth + 1
                    continue
                }
                if (!entry.isFile || entry.length() <= 0L) continue
                val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
                val type = when (extension) {
                    in audioExtensions -> PlaybackType.AUDIO
                    in videoExtensions -> PlaybackType.VIDEO
                    else -> null
                } ?: continue
                if (type == PlaybackType.AUDIO && !includeAudio) continue
                if (type == PlaybackType.VIDEO && !includeVideo) continue
                val absolutePath = entry.absolutePath
                if (absolutePath.lowercase(Locale.US) in indexedLookup) continue
                if (!scannedPaths.add(absolutePath.lowercase(Locale.US))) continue
                buildScannedItem(entry, type, extension)?.let(result::add)
            }
        }
        Log.d(
            "DeviceScan",
            "scan finished: files=${result.size} roots=${roots.size} " +
                "audio=${result.count { it.type == PlaybackType.AUDIO }} " +
                "video=${result.count { it.type == PlaybackType.VIDEO }}"
        )
        return result
    }

    private fun scanRoots(folderPath: String?): List<File> {
        val storage = Environment.getExternalStorageDirectory() ?: return emptyList()
        val scope = folderPath?.trim('/')
        if (!scope.isNullOrBlank()) {
            val folder = File(storage, scope)
            return if (folder.exists() && folder.isDirectory) listOf(folder) else emptyList()
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        val roots = linkedSetOf<File>()
        listOf("music_path", "video_path").forEach { key ->
            preferences.getString(key, null)?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
                val file = File(raw)
                val candidate = if (file.isAbsolute) file else File(storage, raw.trimStart('/'))
                if (candidate.exists() && candidate.isDirectory) roots += candidate
            }
        }
        val ytdlnisFolder = File(storage, "Download/YTDLnis")
        if (ytdlnisFolder.exists() && ytdlnisFolder.isDirectory) roots += ytdlnisFolder
        return roots.toList()
    }

    /**
     * Builds rows without touching the media content: filename-based and instant. Anything
     * richer (duration, resolution, tags) is filled later by [enrichMetadata] in the background.
     */
    private fun buildScannedItem(file: File, type: PlaybackType, extension: String): DeviceMedia? {
        val displayName = file.name
        val path = file.absolutePath
        val cached = synchronized(metaCacheLock) { metaCache[path] }
        return DeviceMedia(
            storeId = 0L,
            type = type,
            title = cached?.title?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.'),
            artist = cached?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" },
            album = cached?.album?.takeIf { it.isNotBlank() },
            durationMs = cached?.durationMs ?: 0L,
            sizeBytes = file.length(),
            dateAddedMs = file.lastModified(),
            folderPath = folderFromData(path) ?: "",
            displayName = displayName,
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension),
            width = cached?.width ?: 0,
            height = cached?.height ?: 0,
            albumId = null,
            contentUri = Uri.fromFile(file),
            albumArtUri = null,
            filePath = path
        )
    }

    /**
     * Fills in real metadata (tags, duration, resolution) for scanned files that the instant scan
     * skipped. Runs in the background after the list is already visible; results are cached in
     * preferences so later loads are instant.
     */
    suspend fun enrichMetadata(items: List<DeviceMedia>): List<DeviceMedia> =
        withContext(Dispatchers.IO) {
            ensureMetaCacheLoaded()
            val missing = items
                .asSequence()
                .mapNotNull { it.filePath }
                .filter { synchronized(metaCacheLock) { metaCache[it] == null } }
                .toMutableSet()
                .toList()
            if (missing.isNotEmpty()) {
                val semaphore = Semaphore(3)
                val extracted = coroutineScope {
                    missing.map { path ->
                        async {
                            semaphore.withPermit { extractScannedMeta(path) }
                        }
                    }.awaitAll()
                }
                synchronized(metaCacheLock) {
                    extracted.forEach { meta ->
                        if (meta != null) {
                            metaCache.remove(meta.path)
                            while (metaCache.size >= META_CACHE_MAX) {
                                val iterator = metaCache.entries.iterator()
                                if (!iterator.hasNext()) break
                                iterator.next(); iterator.remove()
                            }
                            metaCache[meta.path] = meta
                        }
                    }
                    persistMetaCacheLocked()
                }
            }
            applyMetaCache(items)
        }

    private fun applyMetaCache(items: List<DeviceMedia>): List<DeviceMedia> = synchronized(metaCacheLock) {
        var changed = false
        val result = items.map { item ->
            val meta = item.filePath?.let { metaCache[it] }
            if (meta == null) {
                item
            } else {
                val updated = item.copy(
                    title = meta.title?.takeIf { it.isNotBlank() } ?: item.title,
                    artist = meta.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: item.artist,
                    album = meta.album?.takeIf { it.isNotBlank() } ?: item.album,
                    durationMs = meta.durationMs,
                    width = meta.width,
                    height = meta.height
                )
                changed = changed || updated != item
                updated
            }
        }
        if (changed) result else items
    }

    private fun extractScannedMeta(path: String): ScannedMeta? {
        val isVideo = File(path).extension.lowercase(Locale.US) in VIDEO_SCAN_EXTENSIONS
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            var width = 0
            var height = 0
            if (isVideo) {
                width = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                height = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
            }
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            ScannedMeta(path, durationMs, width, height, title, artist, album)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun ensureMetaCacheLoaded() {
        synchronized(metaCacheLock) {
            if (metaCacheLoaded) return
            metaCacheLoaded = true
            val raw = PreferenceManager.getDefaultSharedPreferences(appContext)
                .getString(META_CACHE_KEY, null) ?: return
            runCatching {
                val entries = JSONArray(raw)
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    val path = entry.optString("p")
                    if (path.isBlank()) continue
                    metaCache[path] = ScannedMeta(
                        path = path,
                        durationMs = entry.optLong("d"),
                        width = entry.optInt("w"),
                        height = entry.optInt("h"),
                        title = entry.optStringOrNull("t"),
                        artist = entry.optStringOrNull("ar"),
                        album = entry.optStringOrNull("al")
                    )
                }
            }
        }
    }

    private fun persistMetaCacheLocked() {
        val json = JSONArray()
        for (meta in metaCache.values) {
            json.put(JSONObject().apply {
                put("p", meta.path)
                put("d", meta.durationMs)
                put("w", meta.width)
                put("h", meta.height)
                meta.title?.let { put("t", it) }
                meta.artist?.let { put("ar", it) }
                meta.album?.let { put("al", it) }
            })
        }
        PreferenceManager.getDefaultSharedPreferences(appContext)
            .edit { putString(META_CACHE_KEY, json.toString()) }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return getString(key)
    }

    private fun frameRateFor(item: DeviceMedia): Float? {
        synchronized(frameRateLock) {
            if (frameRateKnown.contains(item.mediaId)) return frameRateCache[item.mediaId]
        }
        val value = readFrameRate(item)
        synchronized(frameRateLock) {
            frameRateKnown += item.mediaId
            frameRateCache[item.mediaId] = value
        }
        return value
    }

    private fun readFrameRate(item: DeviceMedia): Float? {
        val retriever = MediaMetadataRetriever()
        return try {
            val path = item.filePath
            if (path != null) {
                retriever.setDataSource(path)
            } else {
                retriever.setDataSource(appContext, item.contentUri)
            }
            val captured = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()
            if (captured != null && captured > 0f) {
                captured
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val frameCount = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                    ?.toLongOrNull()
                val duration = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                if (frameCount != null && frameCount > 0L && duration != null && duration > 0L) {
                    frameCount * 1000f / duration
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun pathColumns(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DATA)
        } else {
            listOf(MediaStore.MediaColumns.DATA)
        }

    private fun normalizeFolder(path: String?): String = path
        ?.replace('\\', '/')
        ?.trim('/')
        .orEmpty()

    private fun folderFromData(data: String?): String? {
        if (data.isNullOrBlank()) return null
        val parent = File(data).parent ?: return null
        val normalized = parent.replace('\\', '/')
        return when {
            normalized.contains("/storage/emulated/0/") ->
                normalized.substringAfter("/storage/emulated/0/")
            normalized.startsWith("/sdcard/") -> normalized.removePrefix("/sdcard/")
            else -> normalized
        }.trim('/')
    }

    private fun inScope(folder: String, scope: String?): Boolean {
        val normalizedScope = scope?.trim('/')?.takeIf { it.isNotBlank() } ?: return true
        return folder == normalizedScope || folder.startsWith("$normalizedScope/")
    }

    private fun android.database.Cursor.stringOrNull(column: Int): String? {
        if (column < 0 || isNull(column)) return null
        return getString(column)
    }
}

/**
 * Loads embedded audio artwork or a video thumbnail off the main thread with persistent disk caching
 * (cacheDir/art_thumbs/), 200x200 downsampling, and automatic cancellation for recycled views.
 */
class DeviceThumbnailLoader(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val maxCacheSize = (Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxCacheSize) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val loading = mutableSetOf<String>()
    private val viewJobs = Collections.synchronizedMap(WeakHashMap<ImageView, Job>())

    private val diskCacheDir by lazy {
        File(appContext.cacheDir, "art_thumbs").apply { mkdirs() }
    }

    private fun diskCacheFile(mediaId: String): File {
        val safe = mediaId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(diskCacheDir, "$safe.webp")
    }

    private fun loadFromDiskCache(mediaId: String): Bitmap? {
        val file = diskCacheFile(mediaId)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
    }

    private fun saveToDiskCache(mediaId: String, bitmap: Bitmap) {
        runCatching {
            val file = diskCacheFile(mediaId)
            val tmp = File(diskCacheDir, "${file.name}.tmp")
            tmp.outputStream().use { out ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
                }
            }
            tmp.renameTo(file)
        }
    }

    fun load(target: ImageView, item: DeviceMedia) {
        val key = item.mediaId
        val prevKey = target.tag as? String
        target.tag = key

        // 1. Fast path: memory cache hit (0ms)
        cache.get(key)?.let {
            viewJobs.remove(target)?.cancel()
            target.setImageBitmap(it)
            return
        }

        // 2. Cancel in-flight loading for this recycled view immediately
        viewJobs.remove(target)?.cancel()

        // 3. Flicker prevention: keep existing bitmap while loading rather than flashing placeholder
        if (target.drawable == null || prevKey == null) {
            target.setImageResource(
                if (item.type == PlaybackType.VIDEO) R.drawable.ic_video else R.drawable.ic_music
            )
        }

        // 4. Launch cancellation-aware background load
        val job = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                loadFromDiskCache(key) ?: run {
                    val extracted = loadBitmap(item)
                    if (extracted != null) {
                        saveToDiskCache(key, extracted)
                    }
                    extracted
                }
            }
            if (bitmap != null) {
                cache.put(key, bitmap)
                if (PlaybackColors.cachedColor(key) == null) {
                    PlaybackColors.extractDominant(bitmap)?.let { PlaybackColors.putCachedColor(key, it) }
                }
            }
            if (target.tag == key) {
                if (bitmap != null) {
                    target.setImageBitmap(bitmap)
                } else {
                    target.setImageResource(
                        if (item.type == PlaybackType.VIDEO) R.drawable.ic_video else R.drawable.ic_music
                    )
                }
            }
            viewJobs.remove(target)
        }
        viewJobs[target] = job
    }

    fun close() {
        viewJobs.values.forEach { it.cancel() }
        viewJobs.clear()
        scope.cancel()
    }

    suspend fun getThumbnail(item: DeviceMedia): Bitmap? {
        val key = item.mediaId
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            loadFromDiskCache(key) ?: run {
                val extracted = loadBitmap(item)
                if (extracted != null) {
                    saveToDiskCache(key, extracted)
                }
                extracted
            }
        }
        if (bitmap != null) {
            cache.put(key, bitmap)
        }
        return bitmap
    }

    /**
     * Pre-generates artwork for the whole library in the background. Called right after the
     * list is visible so every row's thumbnail disk cache is warm before the user scrolls;
     * from the second tab open onward all artwork pops in instantly.
     */
    suspend fun warmUp(items: List<DeviceMedia>) {
        val pending = items.filter { cache.get(it.mediaId) == null && !diskCacheFile(it.mediaId).exists() }
        if (pending.isEmpty()) return
        val semaphore = Semaphore(2)
        coroutineScope {
            pending.take(80).map { item ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            val bitmap = withContext(Dispatchers.IO) { loadBitmap(item) }
                            if (bitmap != null) {
                                cache.put(item.mediaId, bitmap)
                                saveToDiskCache(item.mediaId, bitmap)
                                PlaybackColors.extractDominant(bitmap)?.let {
                                    PlaybackColors.putCachedColor(item.mediaId, it)
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun thumbnailSourcePath(item: DeviceMedia): String? =
        item.filePath ?: resolveDataPath(item)

    private fun loadBitmap(item: DeviceMedia): Bitmap? =
        loadBitmap(item, thumbnailSourcePath(item))

    private fun loadBitmap(item: DeviceMedia, filePath: String?): Bitmap? {
        item.albumArtUri?.let { art ->
            decodeSampled(art)?.let { return it }
        }
        // Fast path: the ffmpeg disk cache already holds artwork for this exact file.
        if (item.type == PlaybackType.VIDEO) {
            filePath?.let { path ->
                FrameExtractor.cachedFor(
                    appContext, FrameExtractor.VIDEO_VARIANT, path, THUMBNAIL_SIZE
                )?.let { return it }
            }
        } else {
            filePath?.let { path ->
                FrameExtractor.cachedFor(
                    appContext, FrameExtractor.AUDIO_VARIANT, path, THUMBNAIL_SIZE
                )?.let { return it }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                appContext.contentResolver.loadThumbnail(
                    item.contentUri,
                    Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE),
                    null
                )
            }.getOrNull()?.let { return it }
        }
        if (item.type == PlaybackType.VIDEO && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            runCatching {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    appContext.contentResolver,
                    item.storeId,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            }.getOrNull()?.let { return it }
        }
        // Fallback for files MediaStore did not index (for example .opus): read the embedded
        // picture or a video frame straight from the file.
        if (filePath != null) {
            loadFromFile(filePath, item.type)?.let { return it }
        }
        // Last resort for undecodable encoders (e.g. AV1 webm) and audio files the retriever
        // cannot parse (e.g. ogg/opus covers): let the bundled ffmpeg extract the artwork.
        if (filePath != null) {
            return when (item.type) {
                PlaybackType.VIDEO ->
                    FrameExtractor.frameFor(appContext, filePath, THUMBNAIL_SIZE)
                PlaybackType.AUDIO ->
                    FrameExtractor.coverFor(appContext, filePath, THUMBNAIL_SIZE)
            }
        }
        return null
    }

    /** Absolute file path of a media URI, resolved through the MediaStore DATA column. */
    private fun resolveDataPath(item: DeviceMedia): String? = runCatching {
        appContext.contentResolver
            .query(item.contentUri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun loadFromFile(path: String, type: PlaybackType): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            runCatching { retriever.embeddedPicture }.getOrNull()
                ?.let { bytes -> decodeSampled(bytes) }
                ?.let { return it }
            if (type == PlaybackType.VIDEO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    runCatching {
                        retriever.getScaledFrameAtTime(
                            0L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            THUMBNAIL_SIZE,
                            THUMBNAIL_SIZE
                        )
                    }.getOrNull()
                } else {
                    null
                } ?: runCatching { retriever.getFrameAtTime(0L) }.getOrNull()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeSampled(bytes: ByteArray, targetSize: Int = THUMBNAIL_SIZE): Bitmap? = runCatching {
        if (bytes.isEmpty()) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        if (bounds.outHeight > targetSize || bounds.outWidth > targetSize) {
            val halfHeight = bounds.outHeight / 2
            val halfWidth = bounds.outWidth / 2
            while ((halfHeight / sample) >= targetSize && (halfWidth / sample) >= targetSize) {
                sample *= 2
            }
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@runCatching null
        if (decoded.width > targetSize * 1.5f || decoded.height > targetSize * 1.5f) {
            val maxDim = maxOf(decoded.width, decoded.height)
            val scale = targetSize.toFloat() / maxDim
            val scaled = Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (scaled != decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
    }.getOrNull()

    private fun decodeSampled(uri: Uri, targetSize: Int = THUMBNAIL_SIZE): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        if (bounds.outHeight > targetSize || bounds.outWidth > targetSize) {
            val halfHeight = bounds.outHeight / 2
            val halfWidth = bounds.outWidth / 2
            while ((halfHeight / sample) >= targetSize && (halfWidth / sample) >= targetSize) {
                sample *= 2
            }
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@runCatching null
        if (decoded.width > targetSize * 1.5f || decoded.height > targetSize * 1.5f) {
            val maxDim = maxOf(decoded.width, decoded.height)
            val scale = targetSize.toFloat() / maxDim
            val scaled = Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (scaled != decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
    }.getOrNull()

    private companion object {
        const val THUMBNAIL_SIZE = 256
    }
}
