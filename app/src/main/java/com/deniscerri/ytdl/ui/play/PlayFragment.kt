package com.deniscerri.ytdl.ui.play

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.playback.LikedStore
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.playback.PlaybackLauncher
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.play.PlayDestination
import com.deniscerri.ytdl.ui.compose.play.PlayScreen
import com.deniscerri.ytdl.ui.play.device.DeviceFolder
import com.deniscerri.ytdl.ui.play.device.DeviceMedia
import com.deniscerri.ytdl.ui.play.device.DeviceMediaRepository
import com.deniscerri.ytdl.ui.play.device.DevicePermissions
import com.deniscerri.ytdl.ui.play.device.groupDeviceFolders
import com.deniscerri.ytdl.util.Extensions.convertToTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Play / Media Library Fragment (Phase 6a).
 * Migrated to Jetpack Compose from fragment_play.xml.
 */
class PlayFragment : ComposeFragment() {

    private class PersistableTree : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent =
            super.createIntent(context, input).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
    }

    private lateinit var deviceRepository: DeviceMediaRepository
    private var historyByPath: Map<String, HistoryItem> = emptyMap()

    private var destination by mutableStateOf(PlayDestination.HOME)
    private var searchQuery by mutableStateOf("")
    private var isGrid by mutableStateOf(false)
    private var selectedFolder by mutableStateOf<String?>(null)
    private var deviceItems by mutableStateOf<List<DeviceMedia>>(emptyList())
    private var hasDevicePermission by mutableStateOf(false)

    private val devicePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (!isAdded) return@registerForActivityResult
        checkDevicePermissions()
        if (hasDevicePermission) {
            loadDeviceMedia()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaybackController.init(requireContext())
        LikedStore.init(requireContext())
        deviceRepository = DeviceMediaRepository(requireContext())
    }

    override fun onResume() {
        super.onResume()
        if (!isAdded) return
        (activity as? MainActivity)?.showBottomNavigation()
        checkDevicePermissions()
        if (hasDevicePermission && deviceItems.isEmpty()) {
            loadDeviceMedia()
        }
    }

    private fun checkDevicePermissions() {
        if (!isAdded) return
        hasDevicePermission = DevicePermissions.hasAudio(requireContext()) ||
                DevicePermissions.hasVideo(requireContext())
    }

    private fun loadDeviceMedia() {
        lifecycleScope.launch(Dispatchers.IO) {
            val media = runCatching { deviceRepository.load(null) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    deviceItems = media
                }
            }
        }
    }

    private fun playbackItemFor(media: DeviceMedia): PlaybackItem {
        val base = media.toPlaybackItem()
        val localPath = base.localUri?.takeIf { it.startsWith("file://") }?.removePrefix("file://")
        val path: String = media.filePath ?: localPath ?: return base
        val absolute = File(path).absolutePath
        val history = historyByPath[absolute.lowercase()] ?: return base
        return PlaybackItem.fromHistory(history).copy(
            durationMs = base.durationMs ?: history.durationMsOf(),
            localUri = base.localUri,
            extras = base.extras
        )
    }

    private fun HistoryItem.durationMsOf(): Long? {
        val parsed = runCatching { duration.convertToTimestamp() }.getOrDefault(0L)
        return parsed.takeIf { it > 0 }
    }

    private fun playDeviceMedia(media: DeviceMedia, queue: List<DeviceMedia>) {
        val item = playbackItemFor(media)
        val playbackState = PlaybackController.state.value
        if (playbackState.currentItem?.mediaId == item.mediaId) {
            PlaybackLauncher.openPlayer(requireContext(), item)
            return
        }
        val existingIndex = playbackState.queue.indexOfFirst { it.mediaId == item.mediaId }
        if (existingIndex >= 0) {
            PlaybackController.playAt(existingIndex)
            PlaybackLauncher.openPlayer(requireContext(), item)
            return
        }
        val items = queue.map { playbackItemFor(it) }
        val index = queue.indexOfFirst { it.mediaId == media.mediaId }.coerceAtLeast(0)
        PlaybackLauncher.playAll(requireContext(), items, index)
    }

    @Composable
    override fun Content() {
        val context = requireContext()
        val db = remember { DBManager.getInstance(context) }
        val playbackState by PlaybackController.state.collectAsState()
        val likedItems by LikedStore.items.collectAsState()

        val recentHistory by remember { db.playbackHistoryDao.getRecent(100) }
            .collectAsState(initial = emptyList())
        val downloadHistory by remember { db.historyDao.getAllHistory() }
            .collectAsState(initial = emptyList())

        val downloadQueue = remember(downloadHistory) {
            val filtered = downloadHistory
                .filter { it.type == DownloadType.audio || it.type == DownloadType.video }
                .filter { it.downloadPath.isNotEmpty() }
                .distinctBy { it.url }
                .take(200)
            historyByPath = filtered.associateBy { item ->
                val p = item.downloadPath.firstOrNull().orEmpty()
                if (p.isNotEmpty()) File(p).absolutePath.lowercase() else ""
            }.filterKeys { it.isNotEmpty() }
            filtered.map { PlaybackItem.fromHistory(it) }
        }

        val recentQueue = remember(recentHistory) {
            recentHistory.map { PlaybackItem.fromPlaybackHistory(it) }
        }

        val currentList = remember(destination, downloadQueue, likedItems, recentQueue, searchQuery) {
            val base = when (destination) {
                PlayDestination.HOME -> downloadQueue
                PlayDestination.LIKED -> likedItems
                PlayDestination.RECENT -> recentQueue
                PlayDestination.DEVICE -> emptyList()
            }
            if (searchQuery.isBlank()) {
                base
            } else {
                val q = searchQuery.trim().lowercase()
                base.filter {
                    it.title.lowercase().contains(q) ||
                            (it.artist?.lowercase()?.contains(q) == true)
                }
            }
        }

        val filteredDeviceMedia = remember(deviceItems, selectedFolder, searchQuery) {
            val base = if (selectedFolder != null) {
                deviceItems.filter { it.folderPath == selectedFolder }
            } else {
                deviceItems
            }
            if (searchQuery.isBlank()) {
                base
            } else {
                val q = searchQuery.trim().lowercase()
                base.filter {
                    it.title.lowercase().contains(q) ||
                            (it.artist?.lowercase()?.contains(q) == true) ||
                            (it.album?.lowercase()?.contains(q) == true)
                }
            }
        }

        val deviceFolders = remember(deviceItems) {
            groupDeviceFolders(deviceItems)
        }

        PlayScreen(
            destination = destination,
            searchQuery = searchQuery,
            isGrid = isGrid,
            items = currentList,
            recentItems = recentQueue.take(15),
            deviceFolders = deviceFolders,
            deviceMedia = filteredDeviceMedia,
            selectedFolder = selectedFolder,
            hasDevicePermission = hasDevicePermission,
            nowPlayingItem = playbackState.currentItem,
            isPlaying = playbackState.playing,
            onSelectDestination = { destination = it },
            onSearchQueryChange = { searchQuery = it },
            onToggleLayoutMode = { isGrid = !isGrid },
            onTrackClick = { clicked ->
                val index = currentList.indexOfFirst { it.mediaId == clicked.mediaId }.coerceAtLeast(0)
                PlaybackLauncher.playAll(context, currentList, index)
                PlaybackLauncher.openPlayer(context, clicked)
            },
            onTrackMenuClick = { item ->
                // Quick menu: toggle like / open player
                LikedStore.toggle(item)
            },
            onShuffle = {
                if (currentList.isNotEmpty()) {
                    PlaybackLauncher.playAll(context, currentList.shuffled(), 0)
                    PlaybackLauncher.openPlayer(context)
                }
            },
            onPlayAll = {
                if (currentList.isNotEmpty()) {
                    PlaybackLauncher.playAll(context, currentList, 0)
                    PlaybackLauncher.openPlayer(context)
                }
            },
            onDeviceFolderSelect = { folder -> selectedFolder = folder },
            onDeviceMediaClick = { media -> playDeviceMedia(media, filteredDeviceMedia) },
            onDeviceMediaMenuClick = { media ->
                val item = playbackItemFor(media)
                LikedStore.toggle(item)
            },
            onRequestDevicePermission = {
                devicePermissionLauncher.launch(
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        arrayOf(
                            android.Manifest.permission.READ_MEDIA_AUDIO,
                            android.Manifest.permission.READ_MEDIA_VIDEO
                        )
                    } else {
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                )
            },
            onOpenPlayer = { PlaybackLauncher.openPlayer(context) }
        )
    }
}
