package com.deniscerri.ytdl

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import com.anggrayudi.storage.file.getAbsolutePath
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.CookieViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.playback.PlaybackLauncher
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.compose.shell.MainShellScreen
import com.deniscerri.ytdl.ui.compose.shell.ShellNavTab
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.ui.downloads.DownloadQueueMainFragment
import com.deniscerri.ytdl.ui.downloads.HistoryFragment
import com.deniscerri.ytdl.ui.home.HomeFragment
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.more.terminal.TerminalActivity
import com.deniscerri.ytdl.ui.welcome.WelcomeActivity
import com.deniscerri.ytdl.util.ApkInstallUtil
import com.deniscerri.ytdl.util.BgUtilsPoTokenGeneratorUtil
import com.deniscerri.ytdl.util.CrashListener
import com.deniscerri.ytdl.util.IncognitoSessionManager
import com.deniscerri.ytdl.util.NavbarUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class MainActivity : BaseActivity() {

    lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var cookieViewModel: CookieViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var navController: NavController

    private var miniPlayerAllowed by mutableStateOf(true)
    var showBottomBar by mutableStateOf(true)
    private var isIncognito by mutableStateOf(false)
    private var currentDestinationId by mutableStateOf(0)
    private var activeDownloadCount = 0
    private var shellNavTabs by mutableStateOf<List<ShellNavTab>>(emptyList())

    lateinit var installLauncher: ActivityResultLauncher<Intent>
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // first run: the welcome flow asks for permissions and update preferences, then reopens this
        if (WelcomeActivity.isNeeded(PreferenceManager.getDefaultSharedPreferences(this))) {
            startActivity(Intent(this, WelcomeActivity::class.java).putExtra(WelcomeActivity.EXTRA_NEXT_INTENT, intent))
            finish()
            return
        }
        CrashListener(this).registerExceptionHandler()
        ThemeUtil.updateTheme(this)
        window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)
        context = baseContext
        PlaybackController.init(this)

        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        cookieViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(this)[DownloadCardViewModel::class.java]
        preferences = PreferenceManager.getDefaultSharedPreferences(context)

        isIncognito = preferences.getBoolean("incognito", false)

        lifecycleScope.launch(Dispatchers.IO) {
            if (preferences.getBoolean("incognito", false)) {
                resultViewModel.deleteAll()
            } else {
                IncognitoSessionManager.purgeIncognitoSession(this@MainActivity)
            }
        }

        // new users already went through the permissions page, so only the required ones are re-checked
        askPermissions(includeOptional = !preferences.getBoolean(WelcomeActivity.PREF_COMPLETED, false))

        NavbarUtil.init(this)
        updateShellTabs()

        lifecycleScope.launch {
            downloadViewModel.activePausedDownloadsCount.collectLatest { count ->
                activeDownloadCount = count
                updateShellTabs()
            }
        }

        setContent {
            YtdlnisTheme {
                MainShellScreen(
                    tabs = shellNavTabs,
                    selectedTabId = currentDestinationId,
                    onSelectTab = { id -> onSelectTab(id) },
                    onReselectTab = { id -> onReselectTab(id) },
                    isIncognito = isIncognito,
                    showBottomBar = showBottomBar,
                    miniPlayerAllowed = miniPlayerAllowed,
                    onIncognitoDisabled = {
                        isIncognito = false
                        Snackbar.make(window.decorView, R.string.incognito_session_cleared, Snackbar.LENGTH_SHORT).show()
                    },
                    onOpenPlayer = { PlaybackLauncher.openPlayer(this@MainActivity) }
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            FragmentContainerView(ctx).apply {
                                id = R.id.frame_layout
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                val existing = supportFragmentManager.findFragmentById(R.id.frame_layout) as? NavHostFragment
                                val host = existing ?: NavHostFragment.create(0).also { fragment ->
                                    supportFragmentManager.beginTransaction()
                                        .replace(R.id.frame_layout, fragment)
                                        .setPrimaryNavigationFragment(fragment)
                                        .commitNow()
                                }
                                navHostFragment = host
                                navController = host.navController
                                initNavController(savedInstanceState)
                            }
                        }
                    )
                }
            }
        }

        cookieViewModel.updateCookiesFile()
        installLauncher = ApkInstallUtil.registerInstallLauncher(this)

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val useBgUtilPoTokenServer = preferences.getBoolean("use_bgutils_potoken_generator", false)
                val bgUtilsMethod = preferences.getString("bgutils_potoken_method", "server")
                val requiresServer = useBgUtilPoTokenServer && bgUtilsMethod == "server"
                if (requiresServer) {
                    BgUtilsPoTokenGeneratorUtil.acquireServer(this@MainActivity)
                }
            }
        }

        handleIntents(intent)
        callAutoUpdates(firstRun = intent.getBooleanExtra(WelcomeActivity.EXTRA_FROM_WELCOME, false))
    }

    private fun initNavController(savedInstanceState: Bundle?) {
        if (runCatching { navController.graph }.getOrNull() == null) {
            val graph = navController.navInflater.inflate(R.navigation.nav_graph)
            graph.setStartDestination(NavbarUtil.getStartFragmentId(this@MainActivity))
            navController.graph = graph
        }
        currentDestinationId = navController.currentDestination?.id ?: NavbarUtil.getStartFragmentId(this@MainActivity)

        val showingNavbarItems = NavbarUtil.getNavBarItems(this@MainActivity).filter { it.isVisible }.map { it.itemId }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Handler(Looper.getMainLooper()).post {
                val isMainDestination = showingNavbarItems.contains(destination.id)
                miniPlayerAllowed = isMainDestination
                if (isMainDestination) {
                    showBottomNavigation()
                } else {
                    hideBottomNavigation()
                }
                currentDestinationId = destination.id
            }
        }
    }

    private fun updateShellTabs() {
        val baseTabs = NavbarUtil.getShellNavTabs(this)
        val showingDownloadQueue = baseTabs.any { it.id == R.id.downloadQueueMainFragment }
        val badgeTargetId = if (showingDownloadQueue) R.id.downloadQueueMainFragment else R.id.historyFragment
        shellNavTabs = baseTabs.map { tab ->
            if (tab.id == badgeTargetId) {
                tab.copy(badgeCount = activeDownloadCount)
            } else {
                tab
            }
        }
    }

    private fun onSelectTab(tabId: Int) {
        if (!::navController.isInitialized) return
        when (tabId) {
            R.id.terminalActivity -> {
                startActivity(Intent(this, TerminalActivity::class.java))
            }
            R.id.moreFragment -> {
                navController.navigate(R.id.moreFragment)
            }
            else -> {
                if (navController.currentDestination?.id != tabId) {
                    val navOptions = androidx.navigation.NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setRestoreState(true)
                        .setPopUpTo(navController.graph.startDestinationId, false, saveState = true)
                        .build()
                    navController.navigate(tabId, null, navOptions)
                }
            }
        }
    }

    private fun onReselectTab(tabId: Int) {
        if (!::navHostFragment.isInitialized || !::navController.isInitialized) return
        when (tabId) {
            R.id.homeFragment -> {
                kotlin.runCatching {
                    (navHostFragment.childFragmentManager.primaryNavigationFragment as? HomeFragment)?.scrollToTop()
                }
            }
            R.id.historyFragment -> {
                val showingDownloadQueue = shellNavTabs.any { it.id == R.id.downloadQueueMainFragment }
                if (!showingDownloadQueue) {
                    navController.navigate(R.id.downloadQueueMainFragment)
                } else {
                    kotlin.runCatching {
                        (navHostFragment.childFragmentManager.primaryNavigationFragment as? HistoryFragment)?.openSearchView()
                    }
                }
            }
            R.id.downloadQueueMainFragment -> {
                kotlin.runCatching {
                    (navHostFragment.childFragmentManager.primaryNavigationFragment as? DownloadQueueMainFragment)?.scrollToActive()
                }
            }
            R.id.moreFragment -> {
                val intent = Intent(context, SettingsActivity::class.java)
                startActivity(intent)
            }
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        if (::navController.isInitialized) {
            savedInstanceState.putBundle("nav_state", navController.saveState())
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (::navController.isInitialized) {
            navController.restoreState(savedInstanceState.getBundle("nav_state"))
        }
    }

    fun hideBottomNavigation() {
        showBottomBar = false
    }

    fun showBottomNavigation() {
        showBottomBar = true
    }

    fun disableBottomNavigation() {}

    fun enableBottomNavigation() {}

    override fun onResume() {
        super.onResume()
        isIncognito = preferences.getBoolean("incognito", false)
        if (::navController.isInitialized) {
            val showingNavbarItems = NavbarUtil.getNavBarItems(this).filter { it.isVisible }.map { it.itemId }
            if (showingNavbarItems.contains(navController.currentDestination?.id)) {
                showBottomNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntents(intent)
    }

    private fun handleIntents(intent: Intent) {
        if (!::navController.isInitialized) return
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_SEND == action && type != null) {
            Log.e(TAG, action)
            try {
                val uri = if (Build.VERSION.SDK_INT >= 33){
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                }else{
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }

                var downloadType = DownloadType.valueOf(preferences.getString("preferred_download_type", "video")!!)
                if (preferences.getBoolean("quick_download", false) || downloadType == DownloadType.command) {
                    val docFile = DocumentFile.fromSingleUri(this, uri!!)
                    if (docFile?.exists() == true){
                        val bundle = Bundle()
                        val path = docFile.getAbsolutePath(this)
                        if (downloadType == DownloadType.auto) {
                            downloadType = downloadViewModel.getDownloadType(null, path)
                        }

                        downloadCardViewModel.setResultItem(downloadViewModel.createEmptyResultItem(path))
                        downloadCardViewModel.setDownloadItem(null)
                        bundle.putSerializable("type", downloadType)
                        navController.navigate(R.id.downloadBottomSheetDialog, bundle)
                        return
                    }
                }

                val `is` = contentResolver.openInputStream(uri!!)
                val textBuilder = StringBuilder()
                val reader: Reader = BufferedReader(
                    InputStreamReader(
                        `is`, Charset.forName(
                            StandardCharsets.UTF_8.name()
                        )
                    )
                )
                var c: Int
                while (reader.read().also { c = it } != -1) {
                    textBuilder.append(c.toChar())
                }
                val bundle = Bundle()
                bundle.putString("url", textBuilder.toString())
                navController.popBackStack(R.id.homeFragment, true)
                navController.navigate(
                    R.id.homeFragment,
                    bundle
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't read file", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        } else if (action == Intent.ACTION_VIEW) {
            val navbarItems = NavbarUtil.getNavBarItems(this)
            when (intent.getStringExtra("destination")) {
                "Downloads" -> {
                    if (navbarItems.any { n -> n.itemId == R.id.historyFragment && n.isVisible }) {
                        navController.popBackStack(navController.graph.startDestinationId, true)
                    }
                    navController.navigate(R.id.historyFragment)
                }
                "Queue" -> {
                    if (navbarItems.any { n -> n.itemId == R.id.downloadQueueMainFragment && n.isVisible }) {
                        navController.popBackStack(navController.graph.startDestinationId, true)
                    }

                    val bundle = Bundle()
                    intent.getStringExtra("tab")?.apply {
                        bundle.putString("tab", this)
                    }
                    intent.getLongExtra("reconfigure", 0L).apply {
                        if (this != 0L) {
                            bundle.putLong("reconfigure", this)
                        }
                    }
                    navController.navigate(R.id.downloadQueueMainFragment, bundle)
                }
                "Search" -> {
                    val bundle = Bundle()
                    bundle.putBoolean("search", true)
                    navController.popBackStack(R.id.homeFragment, true)
                    navController.navigate(
                        R.id.homeFragment,
                        bundle
                    )
                }
            }
        }
    }

    private fun callAutoUpdates(firstRun: Boolean = false) {
        resultViewModel.checkForUpdates { //on app update found
            if (preferences.getBoolean("automatic_backup", false)) {
                settingsViewModel.backup()
            }
        }
        if (preferences.getBoolean("auto_update_ytdlp", false)) {
            CoroutineScope(SupervisorJob()).launch {
                try {
                    val hasActiveQueuedDownloads = DBManager.getInstance(this@MainActivity).downloadDao.getDownloadsCountByStatus(listOf("Active", "Queued")) > 0
                    if (hasActiveQueuedDownloads) return@launch

                    if (firstRun) {
                        Snackbar.make(window.decorView, context.getString(R.string.ytdl_updating_started), Snackbar.LENGTH_LONG).show()
                    }

                    val updateRes = withContext(Dispatchers.IO) {
                        UpdateUtil(this@MainActivity).updateYTDL()
                    }

                    if (updateRes.status == UpdateUtil.YTDLPUpdateStatus.DONE) {
                        val version = RuntimeManager.getInstance().version(context)
                        val message = this@MainActivity.getString(R.string.ytld_update_success) + " [${version}]"
                        Snackbar.make(window.decorView, message, Snackbar.LENGTH_LONG).show()
                    }
                } catch (err: Exception) {}
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
