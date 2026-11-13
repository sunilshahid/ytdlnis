package com.deniscerri.ytdl.ui.play

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import java.io.File
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.models.PlaybackHistoryItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.playback.LikedStore
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.playback.PlaybackLauncher
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackSource
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.ui.common.MediaViewOptionsSheet
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.play.device.DeviceField
import com.deniscerri.ytdl.ui.play.device.DeviceFilesAdapter
import com.deniscerri.ytdl.ui.play.device.DeviceFilesPrefs
import com.deniscerri.ytdl.ui.play.device.DeviceFolder
import com.deniscerri.ytdl.ui.play.device.DeviceLayoutMode
import com.deniscerri.ytdl.ui.play.device.DeviceMedia
import com.deniscerri.ytdl.ui.play.device.DeviceMediaRepository
import com.deniscerri.ytdl.ui.play.device.DevicePermissions
import com.deniscerri.ytdl.ui.play.device.DevicePlayStatus
import com.deniscerri.ytdl.ui.play.device.DeviceRow
import com.deniscerri.ytdl.ui.play.device.DeviceShowMode
import com.deniscerri.ytdl.ui.play.device.DeviceSort
import com.deniscerri.ytdl.ui.play.device.DeviceThumbnailLoader
import com.deniscerri.ytdl.ui.play.device.groupDeviceFolders
import com.deniscerri.ytdl.ui.play.device.label
import com.deniscerri.ytdl.ui.play.device.sortDeviceFolders
import com.deniscerri.ytdl.ui.play.device.sortDeviceMedia
import com.deniscerri.ytdl.util.Extensions.convertToTimestamp
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.vault.ingest.VaultImporter
import com.deniscerri.ytdl.vault.ui.VaultImportActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayFragment : Fragment() {

    private enum class Destination { HOME, LIKED, RECENT, DEVICE }

    /** Same as the plain tree picker, but the grant can be kept across restarts. */
    private class PersistableTree : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent =
            super.createIntent(context, input).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
    }

    private lateinit var adapter: PlayAdapter
    private lateinit var deviceAdapter: DeviceFilesAdapter
    private lateinit var deviceRepository: DeviceMediaRepository
    private lateinit var deviceThumbnails: DeviceThumbnailLoader
    private var toolbar: MaterialToolbar? = null
    private var drawerLayout: DrawerLayout? = null
    private var navigationView: NavigationView? = null
    private var searchMenuItem: MenuItem? = null
    private var playbackState = PlaybackController.state.value
    private var recentItems: List<PlaybackHistoryItem> = emptyList()

    /**
     * Absolute-path index of this session's download history ("dl:" items). Used to unify
     * identities: a file played from Device files and the same file played from Downloads
     * share one mediaId, so recents and likes never duplicate.
     */
    private var historyByPath: Map<String, HistoryItem> = emptyMap()
    private var downloadItems: List<HistoryItem> = emptyList()
    private var recentQueue: List<PlaybackItem> = emptyList()
    private var downloadQueue: List<PlaybackItem> = emptyList()
    private var likedItems: List<PlaybackItem> = emptyList()
    private var destination = Destination.HOME
    private var searchQuery = ""

    private var deviceItems: List<DeviceMedia> = emptyList()
    private var deviceShowMode = DeviceShowMode.ALL_FOLDERS
    private var deviceLayoutMode = DeviceLayoutMode.LIST
    private var deviceSort = DeviceSort.DATE
    private var deviceNewestFirst = true
    private var deviceFields: Set<DeviceField> = emptySet()
    private var playLayoutMode = DeviceLayoutMode.LIST
    private var playSort = PlaySort.DATE
    private var playDescending = true
    private var playFields: Set<PlayField> = PlayViewPrefs.defaultFields
    private var playPrefsSignature: String? = null
    private var deviceScopeFolder: String? = null
    private var deviceLoading = false
    private var deviceLoadJob: Job? = null
    private var devicePermissionRequested = false
    private var devicePrefsSignature = ""
    private val selectedDeviceIds = linkedSetOf<String>()
    private var deviceActionMode: ActionMode? = null

    private val devicePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (!isAdded) return@registerForActivityResult
        if (DevicePermissions.hasAudio(requireContext()) ||
            DevicePermissions.hasVideo(requireContext())
        ) {
            loadDeviceMedia(force = true)
        } else {
            rebuildRows()
        }
    }

    private val vaultImportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (!isAdded) return@registerForActivityResult
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            loadDeviceMedia(force = true)
            rebuildRows()
        }
    }

    private val deviceFolderLauncher = registerForActivityResult(PersistableTree()) { uri ->
        if (uri == null || !isAdded) return@registerForActivityResult
        val context = requireContext()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val path = DeviceFilesPrefs.relativePathFromTreeUri(uri)
        val name = DeviceFilesPrefs.folderNameForPath(context, path)
        DeviceFilesPrefs.setFolder(context, uri, path, name)
        deviceScopeFolder = null
        refreshDevicePreferences()
        loadDeviceMedia(force = true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_play, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        PlaybackController.init(requireContext())
        LikedStore.init(requireContext())
        refreshDevicePreferences()

        adapter = PlayAdapter(
            onContinueClick = { PlaybackLauncher.openPlayer(requireContext(), it.item) },
            onContinueButton = { row ->
                val current = playbackState.currentItem
                if (row.item.mediaId == current?.mediaId && playbackState.connected) {
                    PlaybackController.togglePlayPause()
                } else if (row.item.mediaId == current?.mediaId) {
                    PlaybackController.resume(autoPlay = true)
                } else {
                    PlaybackController.playNow(listOf(row.item), autoPlay = true)
                }
            },
            onShuffle = { shuffleLibrary() },
            onPlayDownloads = { playDownloads() },
            onTrackClick = { row ->
                // Tapping the item that is already active must not restart it from zero.
                if (row.isCurrent) {
                    PlaybackLauncher.openPlayer(requireContext(), row.item)
                } else if (row.currentQueue) {
                    PlaybackController.playAt(row.index)
                    PlaybackLauncher.openPlayer(requireContext(), row.item)
                } else {
                    PlaybackLauncher.playAll(requireContext(), row.queue, row.index)
                }
            },
            onTrackMenu = { anchor, row -> showTrackMenu(anchor, row) }
        )

        deviceRepository = DeviceMediaRepository(requireContext())
        deviceThumbnails = DeviceThumbnailLoader(requireContext())
        deviceAdapter = DeviceFilesAdapter(
            thumbnails = deviceThumbnails,
            onFileClick = { media, queue ->
                if (deviceActionMode != null) {
                    toggleDeviceSelection(media)
                } else {
                    playDeviceMedia(media, queue)
                }
            },
            onFileMenu = { anchor, media -> showDeviceFileMenu(anchor, media) },
            onFolderClick = { folder ->
                deviceScopeFolder = folder.path
                toolbar?.subtitle = deviceScopeSubtitle()
                rebuildRows()
            },
            onFileLongClick = { media -> enterDeviceSelection(media) }
        )

        view.findViewById<RecyclerView>(R.id.play_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PlayFragment.adapter
            setHasFixedSize(false)
            itemAnimator = null
        }

        drawerLayout = view.findViewById(R.id.play_drawer)
        navigationView = view.findViewById(R.id.play_navigation)
        toolbar = view.findViewById(R.id.play_toolbar)
        navigationView?.menu?.findItem(R.id.nav_device)?.isVisible =
            DeviceFilesPrefs.isEnabled(requireContext())

        parentFragmentManager.setFragmentResultListener(
            MediaViewOptionsSheet.REQUEST_DEVICE,
            viewLifecycleOwner
        ) { _, _ ->
            refreshDevicePreferences()
            if (destination == Destination.DEVICE) {
                loadDeviceMedia(force = true)
            } else {
                rebuildRows()
            }
        }

        parentFragmentManager.setFragmentResultListener(
            MediaViewOptionsSheet.REQUEST_PLAY,
            viewLifecycleOwner
        ) { _, _ ->
            refreshPlayPreferences()
            if (destination != Destination.DEVICE) {
                rebuildRows()
            }
        }

        toolbar?.setNavigationOnClickListener {
            drawerLayout?.openDrawer(GravityCompat.START)
        }
        toolbar?.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(requireContext(), SettingsActivity::class.java))
                    true
                }
                R.id.action_now_playing -> {
                    PlaybackLauncher.openPlayer(requireContext())
                    true
                }
                R.id.action_clear_queue -> {
                    PlaybackController.clearQueue()
                    true
                }
                R.id.action_device_view -> {
                    if (destination == Destination.DEVICE) {
                        openDeviceViewOptions()
                    } else {
                        openPlayViewOptions()
                    }
                    true
                }
                R.id.action_device_folder -> {
                    deviceFolderLauncher.launch(null)
                    true
                }
                R.id.action_device_scope_back -> {
                    deviceScopeFolder = null
                    rebuildRows()
                    true
                }
                R.id.action_device_clear_folder -> {
                    clearConfiguredDeviceFolder()
                    true
                }
                else -> false
            }
        }

        // Re-sync the drawer check with the retained destination (the fragment survives on
        // the back stack; only its view is recreated, so always defaulting to Home desynced
        // the highlight from the screen actually being shown).
        navigationView?.setCheckedItem(
            when (destination) {
                Destination.LIKED -> R.id.nav_liked
                Destination.RECENT -> R.id.nav_recent
                Destination.DEVICE -> R.id.nav_device
                Destination.HOME -> R.id.nav_home
            }
        )
        navigationView?.setNavigationItemSelectedListener { item ->
            val selected = when (item.itemId) {
                R.id.nav_liked -> Destination.LIKED
                R.id.nav_recent -> Destination.RECENT
                R.id.nav_device -> Destination.DEVICE
                else -> Destination.HOME
            }
            selectDestination(selected)
            true
        }

        setUpSearch()

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val drawer = drawerLayout
                    if (deviceActionMode != null) {
                        deviceActionMode?.finish()
                    } else if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
                        drawer.closeDrawers()
                    } else if (destination == Destination.DEVICE && deviceScopeFolder != null) {
                        deviceScopeFolder = null
                        rebuildRows()
                    } else if (searchMenuItem?.isActionViewExpanded == true) {
                        searchMenuItem?.collapseActionView()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        observeData()
    }

    override fun onResume() {
        super.onResume()
        if (!isAdded) return
        val enabled = DeviceFilesPrefs.isEnabled(requireContext())
        navigationView?.menu?.findItem(R.id.nav_device)?.isVisible = enabled
        val devicePrefsChanged = refreshDevicePreferences()
        refreshPlayPreferences()
        when {
            !enabled && destination == Destination.DEVICE -> selectDestination(Destination.HOME)
            destination == Destination.DEVICE &&
                    (devicePrefsChanged || deviceItems.isEmpty()) -> loadDeviceMedia(force = true)
            else -> rebuildRows()
        }
    }

    private fun setUpSearch() {
        searchMenuItem = toolbar?.menu?.findItem(R.id.action_search)
        val searchView = searchMenuItem?.actionView as? SearchView ?: return
        searchView.queryHint = getString(R.string.search_play_hint)
        searchView.setQuery(searchQuery, false)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query.orEmpty()
                rebuildRows()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty()
                rebuildRows()
                return true
            }
        })
        searchMenuItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchQuery = ""
                rebuildRows()
                return true
            }
        })
    }

    private fun selectDestination(selected: Destination) {
        if (selected != Destination.DEVICE) {
            deviceActionMode?.finish()
        }
        if (searchMenuItem?.isActionViewExpanded == true) {
            searchMenuItem?.collapseActionView()
        }
        destination = selected
        toolbar?.title = when (selected) {
            Destination.HOME -> getString(R.string.play_tab)
            Destination.LIKED -> getString(R.string.liked)
            Destination.RECENT -> getString(R.string.recently_played)
            Destination.DEVICE -> getString(R.string.device_files_title)
        }
        (searchMenuItem?.actionView as? SearchView)?.queryHint = getString(
            if (selected == Destination.DEVICE) R.string.device_files_search_hint
            else R.string.search_play_hint
        )
        navigationView?.setCheckedItem(
            when (selected) {
                Destination.HOME -> R.id.nav_home
                Destination.LIKED -> R.id.nav_liked
                Destination.RECENT -> R.id.nav_recent
                Destination.DEVICE -> R.id.nav_device
            }
        )
        drawerLayout?.closeDrawers()
        if (selected == Destination.DEVICE) {
            loadDeviceMedia(force = true)
        } else {
            rebuildRows()
        }
    }

    private fun refreshDevicePreferences(): Boolean {
        if (!isAdded) return false
        val context = requireContext()
        val showMode = DeviceFilesPrefs.showMode(context)
        val layout = DeviceFilesPrefs.layout(context)
        val sort = DeviceFilesPrefs.sort(context)
        val newestFirst = DeviceFilesPrefs.newestFirst(context)
        val fields = DeviceFilesPrefs.fields(context)
        val folder = DeviceFilesPrefs.folderUri(context)?.toString().orEmpty()
        val signature = buildString {
            append(showMode).append('|')
            append(layout).append('|')
            append(sort).append('|')
            append(newestFirst).append('|')
            append(fields.map { it.name }.sorted().joinToString(",")).append('|')
            append(folder)
        }
        val changed = signature != devicePrefsSignature
        devicePrefsSignature = signature
        deviceShowMode = showMode
        deviceLayoutMode = layout
        deviceSort = sort
        deviceNewestFirst = newestFirst
        deviceFields = fields
        return changed
    }

    private fun refreshPlayPreferences(): Boolean {
        if (!isAdded) return false
        val context = requireContext()
        val layout = PlayViewPrefs.layout(context)
        val sort = PlayViewPrefs.sort(context)
        val descending = PlayViewPrefs.descending(context)
        val fields = PlayViewPrefs.fields(context)
        val signature = buildString {
            append(layout).append('|')
            append(sort).append('|')
            append(descending).append('|')
            append(fields.map { it.name }.sorted().joinToString(","))
        }
        val changed = signature != playPrefsSignature
        playPrefsSignature = signature
        playLayoutMode = layout
        playSort = sort
        playDescending = descending
        playFields = fields
        return changed
    }

    private fun observeData() {
        val db = DBManager.getInstance(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    PlaybackController.state.collect {
                        playbackState = it
                        rebuildRows()
                    }
                }
                launch {
                    LikedStore.items.collect {
                        likedItems = it
                        rebuildRows()
                    }
                }
                launch {
                    db.playbackHistoryDao.getRecent(100).collect {
                        recentItems = it
                        recentQueue = it.map { row -> PlaybackItem.fromPlaybackHistory(row) }
                        rebuildRows()
                    }
                }
                launch {
                    db.historyDao.getAllHistory().collect { history ->
                        downloadItems = history
                            .filter { it.type == DownloadType.audio || it.type == DownloadType.video }
                            .filter { it.downloadPath.isNotEmpty() }
                            .distinctBy { it.url }
                            .take(200)
                        downloadQueue = downloadItems.map { item -> PlaybackItem.fromHistory(item) }
                        rebuildRows()
                    }
                }
            }
        }
    }

    private fun rebuildRows() {
        if (!isAdded || !::adapter.isInitialized) return
        val current = playbackState.currentItem

        toolbar?.menu?.findItem(R.id.action_now_playing)?.isVisible = current != null
        toolbar?.menu?.findItem(R.id.action_clear_queue)?.isVisible =
            playbackState.queue.isNotEmpty()

        val device = destination == Destination.DEVICE
        toolbar?.subtitle = if (device) deviceScopeSubtitle() else null
        toolbar?.menu?.findItem(R.id.action_device_view)?.isVisible = true
        toolbar?.menu?.findItem(R.id.action_device_folder)?.isVisible = device
        toolbar?.menu?.findItem(R.id.action_device_scope_back)?.isVisible =
            device && deviceScopeFolder != null
        toolbar?.menu?.findItem(R.id.action_device_clear_folder)?.isVisible =
            device && DeviceFilesPrefs.folderUri(requireContext()) != null

        if (device) {
            ensureListAdapter(useDevice = true)
            val rows = mutableListOf<DeviceRow>()
            buildDeviceRows(rows)
            deviceAdapter.submitList(rows)
            return
        }

        ensureListAdapter(useDevice = false)
        if (searchQuery.isNotBlank()) {
            rebuildSearchRows(searchQuery.trim())
            return
        }

        val rows = mutableListOf<PlayRow>()
        when (destination) {
            Destination.HOME -> buildHomeRows(rows, current)
            Destination.LIKED -> buildLikedRows(rows)
            Destination.RECENT -> buildRecentRows(rows)
            Destination.DEVICE -> Unit
        }
        adapter.submitList(rows)
    }

    private fun ensureListAdapter(useDevice: Boolean) {
        val list = view?.findViewById<RecyclerView>(R.id.play_list) ?: return
        if (useDevice) {
            deviceAdapter.setGrid(deviceLayoutMode == DeviceLayoutMode.GRID)
            if (list.adapter !== deviceAdapter) list.adapter = deviceAdapter
            val grid = deviceLayoutMode == DeviceLayoutMode.GRID
            val manager = list.layoutManager
            val needsNewManager = if (grid) {
                manager !is GridLayoutManager || manager.spanCount != 2
            } else {
                manager !is LinearLayoutManager || manager is GridLayoutManager
            }
            if (needsNewManager) {
                list.layoutManager = if (grid) {
                    GridLayoutManager(requireContext(), 2).apply {
                        spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                            override fun getSpanSize(position: Int): Int =
                                deviceAdapter.spanSize(position)
                        }
                    }
                } else {
                    LinearLayoutManager(requireContext())
                }
            }
        } else {
            val grid = playLayoutMode == DeviceLayoutMode.GRID
            adapter.setGrid(grid)
            if (list.adapter !== adapter) list.adapter = adapter
            val manager = list.layoutManager
            val needsNewManager = if (grid) {
                manager !is GridLayoutManager || manager.spanCount != 2
            } else {
                manager !is LinearLayoutManager || manager is GridLayoutManager
            }
            if (needsNewManager) {
                list.layoutManager = if (grid) {
                    GridLayoutManager(requireContext(), 2).apply {
                        spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                            override fun getSpanSize(position: Int): Int =
                                adapter.spanSize(position)
                        }
                    }
                } else {
                    LinearLayoutManager(requireContext())
                }
            }
        }
    }

    private fun buildDeviceRows(rows: MutableList<DeviceRow>) {
        val context = requireContext()
        val hasAudio = DevicePermissions.hasAudio(context)
        val hasVideo = DevicePermissions.hasVideo(context)
        if (!hasAudio && !hasVideo) {
            val openSettings = devicePermissionRequested && !canRequestDevicePermissionAgain()
            rows += DeviceRow.Message(
                title = getString(R.string.device_files_permission_title),
                subtitle = getString(R.string.device_files_permission_hint),
                icon = R.drawable.baseline_folder_24,
                actionLabel = getString(
                    if (openSettings) R.string.device_files_open_settings
                    else R.string.device_files_permission_grant
                ),
                action = {
                    if (openSettings) openAppSettings() else requestDevicePermission()
                }
            )
            return
        }

        if (deviceLoading) {
            rows += DeviceRow.Message(
                title = getString(R.string.device_files_loading),
                icon = R.drawable.ic_refresh
            )
            return
        }

        val query = searchQuery.trim()
        val scope = deviceScopeFolder
        val scopedItems = if (scope != null) {
            // Folder drill-down: only the files inside the tapped folder.
            deviceItems.filter { it.folderPath == scope }
        } else {
            deviceItems
        }
        val filtered = if (query.isBlank()) {
            scopedItems
        } else {
            scopedItems.filter { media ->
                media.title.contains(query, ignoreCase = true) ||
                        media.artist?.contains(query, ignoreCase = true) == true ||
                        media.album?.contains(query, ignoreCase = true) == true ||
                        media.folderPath.contains(query, ignoreCase = true) ||
                        media.displayName.contains(query, ignoreCase = true)
            }
        }

        if (deviceItems.isEmpty()) {
            val hasFolderFilter = DeviceFilesPrefs.folderUri(context) != null
            rows += DeviceRow.Message(
                title = getString(R.string.device_files_empty_title),
                subtitle = getString(R.string.device_files_empty_hint),
                icon = R.drawable.baseline_folder_24,
                actionLabel = if (hasFolderFilter) {
                    getString(R.string.device_files_scope_show_all)
                } else {
                    null
                },
                action = if (hasFolderFilter) {
                    { clearConfiguredDeviceFolder() }
                } else {
                    null
                }
            )
            return
        }

        if (filtered.isEmpty() && scope == null) {
            rows += DeviceRow.Message(
                title = getString(R.string.device_files_empty_search_title),
                subtitle = getString(R.string.device_files_empty_search_hint),
                icon = R.drawable.ic_search
            )
            return
        }

        val playedAt = recentItems.associate { it.mediaId to it.lastPlayedAt }
        val statusFor: (DeviceMedia) -> DevicePlayStatus = { deviceStatus(it) }
        if (scope != null) {
            if (filtered.isNotEmpty()) {
                val sorted = sortDeviceMedia(
                    filtered,
                    deviceSort,
                    deviceNewestFirst,
                    playedAt,
                    statusFor
                )
                rows += DeviceRow.Header(deviceFolderTitle(scope), sorted.size)
                sorted.forEach { rows += deviceFileRow(it, playedAt, statusFor) }
            } else {
                // No files directly in this folder: surface its immediate subfolders so
                // containers like Download/YTDLnis stay navigable.
                val prefix = "$scope/"
                val children = deviceItems
                    .filter { it.folderPath.startsWith(prefix) }
                    .groupBy { it.folderPath.removePrefix(prefix).substringBefore('/') }
                if (children.isEmpty()) {
                    rows += DeviceRow.Message(
                        title = getString(R.string.device_files_empty_title),
                        subtitle = getString(R.string.device_files_empty_hint),
                        icon = R.drawable.baseline_folder_24
                    )
                } else {
                    val folders = children.map { (name, members) ->
                        DeviceFolder(path = prefix + name, items = members)
                    }
                    val sorted = sortDeviceFolders(
                        folders,
                        deviceSort,
                        deviceNewestFirst,
                        playedAt,
                        statusFor
                    )
                    rows += DeviceRow.Header(deviceFolderTitle(scope), folders.size)
                    sorted.forEach { rows += DeviceRow.Folder(it) }
                }
            }
            return
        }

        when (deviceShowMode) {
            DeviceShowMode.FILES -> {
                val sorted = sortDeviceMedia(
                    filtered,
                    deviceSort,
                    deviceNewestFirst,
                    playedAt,
                    statusFor
                )
                rows += DeviceRow.Header(getString(R.string.device_files_all_media), sorted.size)
                sorted.forEach { rows += deviceFileRow(it, playedAt, statusFor) }
            }
            DeviceShowMode.FOLDERS -> {
                val folders = sortDeviceFolders(
                    groupDeviceFolders(filtered),
                    deviceSort,
                    deviceNewestFirst,
                    playedAt,
                    statusFor
                )
                folders.forEach { rows += DeviceRow.Folder(it) }
            }
            DeviceShowMode.ALL_FOLDERS -> {
                val folders = sortDeviceFolders(
                    groupDeviceFolders(filtered),
                    deviceSort,
                    deviceNewestFirst,
                    playedAt,
                    statusFor
                )
                folders.forEach { folder ->
                    rows += DeviceRow.Header(folder.label(context), folder.items.size)
                    sortDeviceMedia(
                        folder.items,
                        deviceSort,
                        deviceNewestFirst,
                        playedAt,
                        statusFor
                    ).forEach { rows += deviceFileRow(it, playedAt, statusFor) }
                }
            }
        }
    }

    private fun deviceFileRow(
        media: DeviceMedia,
        playedAt: Map<String, Long>,
        statusFor: (DeviceMedia) -> DevicePlayStatus
    ): DeviceRow.File {
        val current = playbackState.currentItem
        // The playing marker must match both the raw device identity and the canonical
        // "dl:" identity used when the same file is played from Downloads.
        val canonicalId = runCatching { playbackItemFor(media).mediaId }.getOrNull()
        return DeviceRow.File(
            media = media,
            status = statusFor(media),
            isCurrent = current != null &&
                    (current.mediaId == media.mediaId || current.mediaId == canonicalId),
            fields = deviceFields,
            lastPlayedAt = playedAt[media.mediaId] ?: 0L
        )
    }

    private fun deviceStatus(media: DeviceMedia): DevicePlayStatus {
        val state = playbackState
        if (state.currentItem?.mediaId == media.mediaId) return DevicePlayStatus.PLAYING
        if (state.queue.any { it.mediaId == media.mediaId }) return DevicePlayStatus.QUEUED
        val canonicalId = runCatching { playbackItemFor(media).mediaId }.getOrNull()
        if (state.currentItem?.mediaId == canonicalId) return DevicePlayStatus.PLAYING
        if (canonicalId != null && state.queue.any { it.mediaId == canonicalId }) {
            return DevicePlayStatus.QUEUED
        }
        if (recentItems.any {
                it.mediaId == media.mediaId || (canonicalId != null && it.mediaId == canonicalId)
            }
        ) {
            return DevicePlayStatus.PLAYED
        }
        return DevicePlayStatus.NEW
    }

    private fun deviceScopeSubtitle(): String? {
        val scope = deviceScopeFolder
        if (scope != null) return deviceFolderTitle(scope)
        return DeviceFilesPrefs.folderName(requireContext())
    }

    private fun deviceFolderTitle(path: String): String =
        path.trim('/').substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: getString(R.string.device_files_internal_storage)

    private fun loadDeviceMedia(force: Boolean = false) {
        if (!isAdded) return
        val context = requireContext()
        val hasAudio = DevicePermissions.hasAudio(context)
        val hasVideo = DevicePermissions.hasVideo(context)
        if (!hasAudio && !hasVideo) {
            deviceLoadJob?.cancel()
            deviceItems = emptyList()
            deviceLoading = false
            rebuildRows()
            return
        }
        if (deviceLoading && !force) return

        deviceLoading = true
        deviceLoadJob?.cancel()
        rebuildRows()

        val folder = DeviceFilesPrefs.folderPath(context)
        val needsFrameRate = DeviceField.FRAME_RATE in deviceFields ||
                deviceSort == DeviceSort.FRAME_RATE
        val generation = ++deviceLoadGeneration
        deviceLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val loaded = deviceRepository.load(folder, hasAudio, hasVideo)
            if (!isAdded) return@launch
            deviceItems = loaded
            deviceLoading = false
            rebuildRows()

            // Path index of the Downloads history so device files played from here adopt the
            // same "dl:" identity as the same file played from the Downloads tab.
            historyByPath = loadHistoryPathIndex()

            // Warm the artwork disk cache for the whole library in the background, so the
            // second open (and later scrolls) show every thumbnail instantly.
            launch(Dispatchers.IO) {
                runCatching { deviceThumbnails.warmUp(loaded) }
            }

            // Everything is visible at this point; expensive metadata reads continue off the
            // critical path and refresh the list once (duration, resolution, frame rate).
            val needsMeta = loaded.any { media ->
                media.filePath != null &&
                        (media.durationMs == 0L ||
                                (media.type == PlaybackType.VIDEO && media.width == 0))
            }
            val needsFrames = needsFrameRate && loaded.any { it.type == PlaybackType.VIDEO }
            if (!needsMeta && !needsFrames) return@launch
            val enriched = withContext(Dispatchers.IO) {
                var current = loaded
                if (needsMeta) current = deviceRepository.enrichMetadata(current)
                if (needsFrames) current = deviceRepository.enrichFrameRates(current)
                current
            }
            if (!isAdded || generation != deviceLoadGeneration) return@launch
            deviceItems = enriched
            rebuildRows()
        }
    }

    private var deviceLoadGeneration = 0

    /** Builds a lowercase-file-path index of the Downloads history for identity unification. */
    private suspend fun loadHistoryPathIndex(): Map<String, HistoryItem> {
        val context = context ?: return emptyMap()
        return withContext(Dispatchers.IO) {
            runCatching {
                val history = DBManager.getInstance(context.applicationContext)
                    .historyDao.getAllHistoryList()
                val index = mutableMapOf<String, HistoryItem>()
                for (item in history) {
                    for (path in item.downloadPath) {
                        if (path.isBlank() || path.startsWith("content://")) continue
                        val normalized = File(path).absolutePath.lowercase()
                        if (!index.containsKey(normalized)) index[normalized] = item
                    }
                }
                index
            }.getOrDefault(emptyMap())
        }
    }

    /**
     * The canonical playback item for a device file: if the same physical file exists in the
     * Downloads history, its "dl:" identity is reused so recents and likes stay in sync with
     * playing it from the Downloads tab.
     */
    private fun playbackItemFor(media: DeviceMedia): PlaybackItem {
        val base = media.toPlaybackItem()
        val localPath = base.localUri?.takeIf { it.startsWith("file://") }?.removePrefix("file://")
        val path: String = media.filePath ?: localPath ?: return base
        val absolute = File(path).absolutePath
        val history = historyByPath[absolute.lowercase()] ?: return base
        if (!media.typeMatches(history)) return base
        return PlaybackItem.fromHistory(history).copy(
            durationMs = base.durationMs ?: history.durationMsOf(),
            localUri = base.localUri,
            extras = base.extras
        )
    }

    private infix fun DeviceMedia.typeMatches(history: HistoryItem): Boolean =
        try {
            when (type) {
                PlaybackType.AUDIO -> history.type == DownloadType.audio
                else -> history.type != DownloadType.audio
            }
        } catch (_: Exception) {
            true
        }

    private fun HistoryItem.durationMsOf(): Long? {
        val parsed = runCatching {
            duration.convertToTimestamp()
        }.getOrDefault(0L)
        return parsed.takeIf { it > 0 }
    }

    private fun requestDevicePermission() {
        devicePermissionRequested = true
        devicePermissionLauncher.launch(DevicePermissions.required())
    }

    private fun canRequestDevicePermissionAgain(): Boolean {
        val host = activity ?: return false
        return DevicePermissions.required().any(host::shouldShowRequestPermissionRationale)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }

    private fun openDeviceViewOptions() {
        if (parentFragmentManager.findFragmentByTag(MediaViewOptionsSheet.TAG) != null) return
        MediaViewOptionsSheet.newInstance(MediaViewOptionsSheet.Mode.DEVICE)
            .show(parentFragmentManager, MediaViewOptionsSheet.TAG)
    }

    private fun openPlayViewOptions() {
        if (parentFragmentManager.findFragmentByTag(MediaViewOptionsSheet.TAG) != null) return
        MediaViewOptionsSheet.newInstance(MediaViewOptionsSheet.Mode.PLAY)
            .show(parentFragmentManager, MediaViewOptionsSheet.TAG)
    }

    private fun clearConfiguredDeviceFolder() {
        DeviceFilesPrefs.clearFolder(requireContext())
        deviceScopeFolder = null
        refreshDevicePreferences()
        loadDeviceMedia(force = true)
    }

    private fun playDeviceMedia(media: DeviceMedia, queue: List<DeviceMedia>) {
        val item = playbackItemFor(media)
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

    private fun showDeviceFileMenu(anchor: View, media: DeviceMedia) {
        val item = playbackItemFor(media)
        val popup = PopupMenu(requireContext(), anchor)
        val liked = LikedStore.isLiked(item.mediaId)
        popup.menu.add(
            0,
            MENU_TOGGLE_LIKE,
            0,
            getString(if (liked) R.string.remove_from_liked else R.string.add_to_liked)
        )
        popup.menu.add(0, MENU_PLAY_NEXT, 1, R.string.play_next)
        popup.menu.add(0, MENU_ADD_TO_QUEUE, 2, R.string.add_to_queue)
        val queueIndex = playbackState.queue.indexOfFirst { it.mediaId == item.mediaId }
        if (queueIndex >= 0) {
            popup.menu.add(0, MENU_REMOVE_FROM_QUEUE, 3, R.string.Remove)
        }
        if (media.type == PlaybackType.VIDEO) {
            popup.menu.add(0, MENU_MOVE_TO_VAULT, 4, R.string.move_to_vault)
        }
        popup.menu.add(0, MENU_DELETE_FILE, 5, R.string.delete)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                MENU_TOGGLE_LIKE -> LikedStore.toggle(item)
                MENU_PLAY_NEXT -> PlaybackController.playNext(item)
                MENU_ADD_TO_QUEUE -> PlaybackController.addToQueue(item)
                MENU_REMOVE_FROM_QUEUE -> PlaybackController.removeFromQueue(queueIndex)
                MENU_MOVE_TO_VAULT -> launchMoveDeviceMediaToVault(media, item.mediaId)
                MENU_DELETE_FILE -> confirmDeleteDeviceFile(media)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private fun launchMoveDeviceMediaToVault(media: DeviceMedia, mediaId: String) {
        val intent = com.deniscerri.ytdl.vault.ui.VaultImportActivity.createIntent(
            context = requireContext(),
            title = media.title,
            filePath = media.filePath,
            contentUri = media.contentUri,
            originalFileName = media.filePath?.let { File(it).name },
            mimeType = media.mimeType,
            durationMs = media.durationMs,
            sizeBytes = media.sizeBytes,
            width = media.width,
            height = media.height,
            playbackMediaId = mediaId,
            historyId = media.filePath?.let { historyByPath[it]?.id }
        )
        vaultImportLauncher.launch(intent)
    }

    private fun confirmDeleteDeviceFile(media: DeviceMedia) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_device_file_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> deleteDeviceFileConfirmed(media) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun enterDeviceSelection(media: DeviceMedia) {
        selectedDeviceIds.add(media.mediaId)
        if (deviceActionMode == null) {
            deviceActionMode = (activity as? AppCompatActivity)?.startSupportActionMode(deviceActionModeCallback)
        }
        syncDeviceSelectionUi()
    }

    private fun toggleDeviceSelection(media: DeviceMedia) {
        if (!selectedDeviceIds.add(media.mediaId)) {
            selectedDeviceIds.remove(media.mediaId)
        }
        if (selectedDeviceIds.isEmpty()) {
            deviceActionMode?.finish()
        } else {
            syncDeviceSelectionUi()
        }
    }

    private fun syncDeviceSelectionUi() {
        val active = deviceActionMode != null && selectedDeviceIds.isNotEmpty()
        deviceActionMode?.title = "${selectedDeviceIds.size} ${getString(R.string.selected)}"
        deviceAdapter.updateSelection(selectedDeviceIds.toSet(), active)
    }

    private val deviceActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode?.menuInflater?.inflate(R.menu.device_files_menu_context, menu)
            (activity as? MainActivity)?.disableBottomNavigation()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.device_delete -> {
                    confirmDeleteSelectedDeviceFiles()
                    true
                }
                R.id.device_move_to_vault -> {
                    moveSelectedDeviceVideosToVault()
                    true
                }
                R.id.device_select_all -> {
                    val visibleIds = deviceAdapter.visibleFiles().map { it.mediaId }
                    if (visibleIds.isNotEmpty()) {
                        if (visibleIds.all { it in selectedDeviceIds }) {
                            selectedDeviceIds.removeAll(visibleIds.toSet())
                            if (selectedDeviceIds.isEmpty()) {
                                mode?.finish()
                                return true
                            }
                        } else {
                            selectedDeviceIds.addAll(visibleIds)
                        }
                        syncDeviceSelectionUi()
                    }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            deviceActionMode = null
            selectedDeviceIds.clear()
            deviceAdapter.updateSelection(emptySet(), false)
            (activity as? MainActivity)?.enableBottomNavigation()
        }
    }

    private fun confirmDeleteSelectedDeviceFiles() {
        val targets = deviceItems.filter { it.mediaId in selectedDeviceIds }
        if (targets.isEmpty()) {
            deviceActionMode?.finish()
            return
        }
        val msg = if (targets.size == 1) {
            getString(R.string.delete_device_file_confirm)
        } else {
            getString(R.string.delete_device_files_confirm_multi, targets.size)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                deviceActionMode?.finish()
                deleteSelectedDeviceFilesConfirmed(targets)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteSelectedDeviceFilesConfirmed(targets: List<DeviceMedia>) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var deletedCount = 0
            for (media in targets) {
                val deleted = runCatching {
                    val path = media.filePath
                    if (!path.isNullOrBlank()) {
                        FileUtil.deleteFile(path)
                        !File(path).exists()
                    } else {
                        requireContext().contentResolver.delete(media.contentUri, null, null) > 0
                    }
                }.getOrDefault(false)
                if (deleted) {
                    deletedCount++
                    LikedStore.remove(playbackItemFor(media).mediaId)
                }
            }
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                val msg = when {
                    deletedCount == 0 -> getString(R.string.delete_device_file_failed)
                    targets.size == 1 -> getString(R.string.delete_device_file_done)
                    deletedCount == targets.size ->
                        getString(R.string.delete_device_files_done_multi, deletedCount)
                    else ->
                        getString(R.string.delete_device_files_partial, deletedCount, targets.size)
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                if (deletedCount > 0) loadDeviceMedia(force = true)
            }
        }
    }

    private fun moveSelectedDeviceVideosToVault() {
        val selected = deviceItems.filter { it.mediaId in selectedDeviceIds }
        val videos = selected.filter { it.type == PlaybackType.VIDEO }
        if (videos.isEmpty()) {
            Toast.makeText(
                requireContext(),
                R.string.device_files_no_videos_selected,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val requests = videos.map { media ->
            val mediaId = playbackItemFor(media).mediaId
            VaultImporter.MoveSourceRequest(
                title = media.title,
                filePath = media.filePath,
                contentUri = media.contentUri,
                originalFileName = media.filePath?.let { File(it).name }
                    ?: media.displayName.takeIf { it.isNotBlank() },
                mimeType = media.mimeType,
                durationMs = media.durationMs,
                sizeBytes = media.sizeBytes,
                width = media.width,
                height = media.height,
                playbackMediaId = mediaId,
                historyId = media.filePath?.let { path ->
                    historyByPath[File(path).absolutePath.lowercase()]?.id
                }
            )
        }
        deviceActionMode?.finish()
        vaultImportLauncher.launch(VaultImportActivity.createBatchIntent(requireContext(), requests))
    }

    private fun deleteDeviceFileConfirmed(media: DeviceMedia) {
        deleteSelectedDeviceFilesConfirmed(listOf(media))
    }

    private fun buildHomeRows(rows: MutableList<PlayRow>, current: PlaybackItem?) {
        if (current != null) {
            rows.add(
                PlayRow.Continue(
                    item = current,
                    playing = playbackState.playing,
                    positionMs = playbackState.positionMs,
                    durationMs = playbackState.durationMs.takeIf { it > 0 }
                        ?: current.durationMs ?: 0L
                )
            )
            // Quick actions only make sense while something is loaded in the player.
            val library = libraryQueue()
            rows.add(
                PlayRow.Actions(
                    shuffleEnabled = library.isNotEmpty(),
                    downloadsEnabled = downloadQueue.isNotEmpty()
                )
            )
        } else {
            rows.add(
                PlayRow.Empty(
                    title = getString(R.string.nothing_playing),
                    subtitle = getString(R.string.play_something_hint),
                    icon = R.drawable.ic_music
                )
            )
        }

        val upNext = if (current != null &&
            playbackState.queue.size > playbackState.currentIndex + 1
        ) {
            playbackState.queue.drop(playbackState.currentIndex + 1).take(6)
        } else {
            emptyList()
        }
        if (upNext.isNotEmpty()) {
            rows.add(
                PlayRow.Header(
                    title = getString(R.string.up_next),
                    count = playbackState.queue.size - playbackState.currentIndex - 1
                )
            )
            upNext.forEachIndexed { offset, item ->
                val index = playbackState.currentIndex + 1 + offset
                rows.add(
                    trackRow(
                        item = item,
                        queue = playbackState.queue,
                        index = index,
                        currentQueue = true
                    )
                )
            }
        }

        val downloads = sortPlayList(
            downloadQueue.filter { it.mediaId != current?.mediaId }
        ).take(8)
        rows.add(PlayRow.Header(getString(R.string.recent_downloads), downloads.size))
        if (downloads.isEmpty()) {
            rows.add(
                PlayRow.Empty(
                    title = getString(R.string.no_downloads_yet_title),
                    subtitle = getString(R.string.no_downloads_yet),
                    icon = R.drawable.baseline_download_24
                )
            )
        } else {
            downloads.forEachIndexed { index, item ->
                rows.add(trackRow(item, downloads, index, currentQueue = false))
            }
        }
    }

    private fun buildLikedRows(rows: MutableList<PlayRow>) {
        val liked = sortPlayList(likedItems)
        if (liked.isEmpty()) {
            rows.add(
                PlayRow.Empty(
                    title = getString(R.string.liked_empty_title),
                    subtitle = getString(R.string.liked_empty_hint),
                    icon = R.drawable.ic_favorite_border
                )
            )
            return
        }
        liked.forEachIndexed { index, item ->
            rows.add(trackRow(item, liked, index, currentQueue = false))
        }
    }

    private fun buildRecentRows(rows: MutableList<PlayRow>) {
        val recent = sortPlayList(recentQueue)
        if (recent.isEmpty()) {
            rows.add(
                PlayRow.Empty(
                    title = getString(R.string.no_recent_plays),
                    subtitle = getString(R.string.no_recent_playback),
                    icon = R.drawable.ic_history
                )
            )
            return
        }
        recent.forEachIndexed { index, item ->
            rows.add(trackRow(item, recent, index, currentQueue = false))
        }
    }

    private fun rebuildSearchRows(query: String) {
        val library = (likedItems + playbackState.queue + recentQueue + downloadQueue)
            .distinctBy { it.mediaId }
        val matches = sortPlayList(
            library.filter { item ->
                item.title.contains(query, ignoreCase = true) ||
                    item.artist?.contains(query, ignoreCase = true) == true ||
                    item.album?.contains(query, ignoreCase = true) == true
            }
        )
        val rows = mutableListOf<PlayRow>()
        rows.add(PlayRow.Header(getString(R.string.search_results), matches.size))
        if (matches.isEmpty()) {
            rows.add(
                PlayRow.Empty(
                    title = getString(R.string.no_search_results),
                    subtitle = getString(R.string.no_search_results_hint),
                    icon = R.drawable.ic_search
                )
            )
        } else {
            matches.forEachIndexed { index, item ->
                rows.add(trackRow(item, matches, index, currentQueue = false))
            }
        }
        adapter.submitList(rows)
    }

    private fun playDateMap(): Map<String, Long> = buildMap {
        downloadItems.forEach { h ->
            val ms = normalizeEpochMs(h.time).takeIf { it > 0L } ?: h.id
            put("dl:${h.id}", ms)
        }
        recentItems.forEach { r ->
            if (!containsKey(r.mediaId) && r.lastPlayedAt > 0L) {
                put(r.mediaId, r.lastPlayedAt)
            }
        }
    }

    private fun sortPlayList(items: List<PlaybackItem>): List<PlaybackItem> {
        val dateById = playDateMap()
        val playedAtById = recentItems.associate { it.mediaId to it.lastPlayedAt }
        return sortPlayItems(
            items = items,
            sort = playSort,
            descending = playDescending,
            dateMsById = dateById,
            playedAtById = playedAtById
        )
    }

    private fun trackRow(
        item: PlaybackItem,
        queue: List<PlaybackItem>,
        index: Int,
        currentQueue: Boolean
    ): PlayRow.Track {
        val dateMs = playDateMap()[item.mediaId]
        val subtitle = item.playDetailSubtitle(requireContext(), playFields, dateMs)
        val trailing = if (PlayField.LENGTH in playFields) {
            item.durationMs?.takeIf { it > 0 }?.toStringTimeStamp()
        } else {
            null
        }
        val current = playbackState.currentItem
        return PlayRow.Track(
            item = item,
            subtitle = subtitle,
            trailing = trailing,
            queue = queue,
            index = index,
            currentQueue = currentQueue,
            isCurrent = current != null && current.mediaId == item.mediaId
        )
    }

    private fun libraryQueue(): List<PlaybackItem> =
        (recentQueue + downloadQueue).distinctBy { it.mediaId }

    private fun shuffleLibrary() {
        val queue = libraryQueue()
        if (queue.isEmpty()) return
        PlaybackLauncher.playAll(requireContext(), queue.shuffled(), 0)
    }

    private fun playDownloads() {
        if (downloadQueue.isEmpty()) return
        PlaybackLauncher.playAll(requireContext(), downloadQueue, 0)
    }

    private fun showTrackMenu(anchor: View, row: PlayRow.Track) {
        val popup = PopupMenu(requireContext(), anchor)
        val liked = LikedStore.isLiked(row.item.mediaId)
        popup.menu.add(
            0,
            MENU_TOGGLE_LIKE,
            0,
            getString(if (liked) R.string.remove_from_liked else R.string.add_to_liked)
        )
        popup.menu.add(0, MENU_PLAY_NEXT, 1, R.string.play_next)
        popup.menu.add(0, MENU_ADD_TO_QUEUE, 2, R.string.add_to_queue)
        val canDownload = row.item.source == PlaybackSource.YOUTUBE &&
            !row.item.webpageUrl.isNullOrBlank()
        if (canDownload) {
            popup.menu.add(0, MENU_DOWNLOAD, 3, R.string.download)
        }
        if (row.currentQueue) {
            popup.menu.add(0, MENU_REMOVE_FROM_QUEUE, 4, R.string.Remove)
        }
        val moveTarget = resolveLocalVideoMoveTarget(row.item)
        if (moveTarget != null) {
            popup.menu.add(0, MENU_MOVE_TO_VAULT, 5, R.string.move_to_vault)
        }
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                MENU_TOGGLE_LIKE -> LikedStore.toggle(row.item)
                MENU_PLAY_NEXT -> PlaybackController.playNext(row.item)
                MENU_ADD_TO_QUEUE -> PlaybackController.addToQueue(row.item)
                MENU_DOWNLOAD -> openDownloadCard(row.item)
                MENU_REMOVE_FROM_QUEUE -> PlaybackController.removeFromQueue(row.index)
                MENU_MOVE_TO_VAULT -> moveTarget?.let { target ->
                    val intent = com.deniscerri.ytdl.vault.ui.VaultImportActivity.createIntent(
                        context = requireContext(),
                        title = row.item.title,
                        filePath = target.first,
                        contentUri = target.second,
                        originalFileName = target.first?.let { p -> File(p).name },
                        durationMs = row.item.durationMs ?: 0L,
                        playbackMediaId = row.item.mediaId,
                        historyId = row.item.mediaId.removePrefix("dl:").toLongOrNull()
                            ?: target.first?.let { p -> historyByPath[p]?.id }
                    )
                    vaultImportLauncher.launch(intent)
                }
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private fun resolveLocalVideoMoveTarget(item: PlaybackItem): Pair<String?, Uri?>? {
        if (item.type != PlaybackType.VIDEO) return null
        val local = item.localUri
        if (!local.isNullOrBlank()) {
            val parsed = runCatching { Uri.parse(local) }.getOrNull()
            when {
                parsed?.scheme == "content" -> return null to parsed
                parsed?.scheme == "file" -> {
                    val p = parsed.path
                    if (!p.isNullOrBlank() && File(p).exists()) return p to null
                }
                !local.contains("://") && File(local).exists() -> return local to null
            }
        }
        val dlId = item.mediaId.removePrefix("dl:").toLongOrNull() ?: return null
        val path = downloadItems.firstOrNull { it.id == dlId }
            ?.downloadPath
            ?.firstOrNull { File(it).exists() }
            ?: return null
        return path to null
    }

    /**
     * Opens the regular download card for a YouTube item. The card fetches formats and applies
     * the user's download preferences; Play never builds a DownloadItem by itself.
     */
    private fun openDownloadCard(item: PlaybackItem) {
        val url = item.webpageUrl ?: return
        val downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        val downloadCardViewModel =
            ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        val result = ResultItem(
            id = 0,
            url = url,
            title = item.title,
            author = item.artist.orEmpty(),
            duration = item.durationMs?.takeIf { it > 0 }?.div(1000)?.toString().orEmpty(),
            thumb = item.artworkUri.orEmpty(),
            website = getString(R.string.youtube),
            playlistTitle = "",
            urls = "",
            chapters = null
        )
        downloadCardViewModel.setResultItem(result)
        downloadCardViewModel.setDownloadItem(null)
        val bundle = Bundle().apply {
            putSerializable("type", downloadViewModel.getDownloadType(null, url))
        }
        findNavController().navigate(R.id.action_playFragment_to_downloadBottomSheetDialog, bundle)
    }

    override fun onPause() {
        // A SearchView left expanded while another activity opens can leave the toolbar's
        // action items in a broken state when we resume, so collapse it before leaving.
        if (searchMenuItem?.isActionViewExpanded == true) {
            searchMenuItem?.collapseActionView()
        }
        super.onPause()
    }

    override fun onDestroyView() {
        deviceActionMode?.finish()
        deviceLoadJob?.cancel()
        if (::deviceThumbnails.isInitialized) {
            deviceThumbnails.close()
        }
        toolbar = null
        drawerLayout = null
        navigationView = null
        searchMenuItem = null
        super.onDestroyView()
    }

    companion object {
        private const val MENU_TOGGLE_LIKE = 1
        private const val MENU_PLAY_NEXT = 2
        private const val MENU_ADD_TO_QUEUE = 3
        private const val MENU_DOWNLOAD = 4
        private const val MENU_REMOVE_FROM_QUEUE = 5
        private const val MENU_DELETE_FILE = 6
        private const val MENU_MOVE_TO_VAULT = 7
    }
}
