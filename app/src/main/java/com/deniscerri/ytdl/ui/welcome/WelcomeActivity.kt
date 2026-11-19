package com.deniscerri.ytdl.ui.welcome

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.database.viewmodel.WelcomeViewModel
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.ui.compose.welcome.WelcomePermissionItem
import com.deniscerri.ytdl.ui.compose.welcome.WelcomeScreen
import com.deniscerri.ytdl.util.BackupSettingsUtil
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.background.UpdateCheckWorker
import com.google.android.material.elevation.SurfaceColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * First-run onboarding flow (Phase 3).
 * Migrated to Jetpack Compose.
 */
class WelcomeActivity : AppCompatActivity() {
    private lateinit var viewModel: WelcomeViewModel
    private val preferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val snackbarHostState = SnackbarHostState()

    private var currentPage by mutableIntStateOf(0)
    private var isRestoring by mutableStateOf(false)
    private var updateApp by mutableStateOf(true)
    private var autoUpdateYtdlp by mutableStateOf(true)
    private var selectedChannel by mutableStateOf("stable")
    private var customChannelLabel by mutableStateOf("")

    // Permission tracking trigger to re-read grants onResume or on return from activity contracts
    private var permissionsRefreshEpoch by mutableIntStateOf(0)

    private val permanentlyDenied = mutableSetOf<String>()

    private val storagePermissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) restore(uri)
        }

    private val storageLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onRuntimeResult("storage", storagePermissions)
        }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (Build.VERSION.SDK_INT >= 33) {
                onRuntimeResult("notifications", arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

    /** Same as the plain tree picker, but persistable. */
    private class PersistableTree : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent =
            super.createIntent(context, input).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
    }

    private var pendingFolderKey: String? = null

    private val folderLauncher = registerForActivityResult(PersistableTree()) { uri ->
        val key = pendingFolderKey
        pendingFolderKey = null
        if (uri != null && key != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            preferences.edit(commit = true) { putString(key, uri.toString()) }
        }
        permissionsRefreshEpoch++
    }

    private val folderPrefs = listOf(
        Triple("music_path", R.drawable.ic_music_downloaded, R.string.music_directory),
        Triple("video_path", R.drawable.ic_video_downloaded, R.string.video_directory),
        Triple("command_path", R.drawable.ic_terminal, R.string.command_directory),
        Triple("cache_path", R.drawable.baseline_folder_24, R.string.cache_directory),
        Triple("download_archive_path", R.drawable.baseline_archive_24, R.string.download_archive_folder),
        Triple("backup_path", R.drawable.baseline_folder_24, R.string.backup_path)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.updateTheme(this)
        window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[WelcomeViewModel::class.java]
        currentPage = viewModel.page
        updateApp = viewModel.updateApp
        autoUpdateYtdlp = viewModel.autoUpdateYtdlp
        selectedChannel = viewModel.ytdlpSource
        customChannelLabel = viewModel.ytdlpSourceLabel

        onBackPressedDispatcher.addCallback(this) {
            if (currentPage > 0) {
                goTo(currentPage - 1)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        setContent {
            YtdlnisTheme {
                val refresh = permissionsRefreshEpoch // triggers recomposition when refreshed

                val channelTitles = resources.getStringArray(R.array.ytdlp_source).toList()
                val channelValues = resources.getStringArray(R.array.ytdlp_source_values).toList()

                val permissionItems = buildPermissionItems()
                val folderPermissionItems = buildFolderPermissionItems()

                val allRequiredGranted = permissionItems.filter { it.required }.all { it.isGranted } &&
                        folderPermissionItems.filter { it.required }.all { it.isGranted }

                val isNextEnabled = if (currentPage == 2) allRequiredGranted else true

                val customSummary = when {
                    selectedChannel in channelValues -> getString(R.string.custom_channels_summary)
                    customChannelLabel.isBlank() -> selectedChannel
                    else -> "$customChannelLabel\n$selectedChannel"
                }

                WelcomeScreen(
                    currentPage = currentPage,
                    totalPages = 4,
                    isRestoring = isRestoring,
                    onGetStarted = { goTo(1) },
                    onRestoreBackup = { restoreLauncher.launch(arrayOf("*/*")) },
                    onOpenGuide = {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GUIDE_URL)))
                        }
                    },
                    permissionItems = permissionItems,
                    folderPermissionItems = folderPermissionItems,
                    isNextEnabled = isNextEnabled,
                    updateApp = updateApp,
                    onUpdateAppChanged = {
                        updateApp = it
                        viewModel.updateApp = it
                    },
                    autoUpdateYtdlp = autoUpdateYtdlp,
                    onAutoUpdateYtdlpChanged = {
                        autoUpdateYtdlp = it
                        viewModel.autoUpdateYtdlp = it
                    },
                    selectedChannel = selectedChannel,
                    channelTitles = channelTitles,
                    channelValues = channelValues,
                    onChannelSelected = { value, label ->
                        selectedChannel = value
                        customChannelLabel = label
                        viewModel.ytdlpSource = value
                        viewModel.ytdlpSourceLabel = label
                    },
                    customChannelSummary = customSummary,
                    onCustomChannelClick = {
                        UiUtil.showYTDLCustomSourceBottomSheet(this, preferences) { title, source ->
                            selectedChannel = source
                            customChannelLabel = title
                            viewModel.ytdlpSource = source
                            viewModel.ytdlpSourceLabel = title
                        }
                    },
                    onBack = { goTo(currentPage - 1) },
                    onNext = {
                        if (currentPage == LAST_PAGE) {
                            finishWelcome()
                        } else {
                            goTo(currentPage + 1)
                        }
                    },
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionsRefreshEpoch++
    }

    private fun goTo(page: Int) {
        if (page !in 0..LAST_PAGE) return
        viewModel.page = page
        currentPage = page
    }

    private fun buildPermissionItems(): List<WelcomePermissionItem> {
        val items = mutableListOf<WelcomePermissionItem>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            items += WelcomePermissionItem(
                id = "storage",
                iconRes = R.drawable.outline_storage_24,
                title = getString(R.string.welcome_perm_storage),
                summary = getString(R.string.welcome_perm_storage_summary),
                required = true,
                isGranted = storagePermissions.all { hasPermission(it) },
                onRequest = {
                    requestRuntime("storage") { storageLauncher.launch(storagePermissions) }
                },
            )
        }

        if (Build.VERSION.SDK_INT >= 33) {
            items += WelcomePermissionItem(
                id = "notifications",
                iconRes = R.drawable.ic_notifications,
                title = getString(R.string.welcome_perm_notifications),
                summary = getString(R.string.welcome_perm_notifications_summary),
                required = false,
                isGranted = hasPermission(Manifest.permission.POST_NOTIFICATIONS),
                onRequest = {
                    requestRuntime("notifications") {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        items += WelcomePermissionItem(
            id = "battery",
            iconRes = R.drawable.ic_battery,
            title = getString(R.string.welcome_perm_battery),
            summary = getString(R.string.welcome_perm_battery_summary),
            required = false,
            isGranted = powerManager.isIgnoringBatteryOptimizations(packageName),
            onRequest = { requestIgnoreBatteryOptimizations() },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            items += WelcomePermissionItem(
                id = "alarms",
                iconRes = R.drawable.baseline_access_alarm_24,
                title = getString(R.string.welcome_perm_alarms),
                summary = getString(R.string.welcome_perm_alarms_summary),
                required = false,
                isGranted = alarmManager.canScheduleExactAlarms(),
                onRequest = { requestExactAlarms() },
            )
        }

        return items
    }

    private fun buildFolderPermissionItems(): List<WelcomePermissionItem> {
        if (!viewModel.restoredSettings) return emptyList()

        val folderItems = mutableListOf<WelcomePermissionItem>()
        folderPrefs.forEach { (key, icon, titleRes) ->
            val path = preferences.getString(key, "").orEmpty()
            if (path.isEmpty()) return@forEach

            val formattedPath = FileUtil.formatPath(path)
            runCatching { File(formattedPath).mkdirs() }

            val granted = hasFolderAccess(path)
            if (!granted) {
                folderItems += WelcomePermissionItem(
                    id = "folder_$key",
                    iconRes = icon,
                    title = getString(titleRes),
                    summary = formattedPath,
                    required = true,
                    isGranted = false,
                    onRequest = {
                        pendingFolderKey = key
                        folderLauncher.launch(path.toUri())
                    },
                )
            }
        }
        return folderItems
    }

    private fun hasFolderAccess(path: String): Boolean {
        if (path.startsWith("content://")) {
            val uri = path.toUri()
            val granted = contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
            if (granted) return true
        }
        val dir = File(FileUtil.formatPath(path))
        if (!dir.exists()) {
            runCatching { getExternalFilesDirs(null) }
            runCatching { dir.mkdirs() }
        }
        return dir.canWrite()
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestRuntime(key: String, launch: () -> Unit) {
        if (key in permanentlyDenied) openAppSettings() else launch()
    }

    private fun onRuntimeResult(key: String, permissions: Array<String>) {
        val denied = permissions.filter { !hasPermission(it) }
        if (denied.isNotEmpty() && denied.none { shouldShowRequestPermissionRationale(it) }) {
            permanentlyDenied += key
        }
        permissionsRefreshEpoch++
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure { openAppSettings() }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
        runCatching { startActivity(intent) }
    }

    private fun restore(uri: Uri) {
        val appContext = applicationContext
        val settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        isRestoring = true

        lifecycleScope.launch {
            runCatching {
                val parsed = BackupSettingsUtil.parse(appContext, uri)
                val restored = withContext(Dispatchers.IO) {
                    settingsViewModel.restoreData(parsed.data, appContext)
                }
                check(restored) { getString(R.string.welcome_restore_failed) }
                parsed.data.settings != null
            }.onSuccess { settingsRestored ->
                isRestoring = false
                onBackupRestored(settingsRestored)
            }.onFailure { e ->
                e.printStackTrace()
                isRestoring = false
                snackbarHostState.showSnackbar(e.message ?: getString(R.string.welcome_restore_failed))
            }
        }
    }

    private fun onBackupRestored(settingsRestored: Boolean) {
        viewModel.restoredSettings = settingsRestored
        if (settingsRestored) {
            viewModel.updateApp = preferences.getBoolean("update_app", viewModel.updateApp)
            viewModel.autoUpdateYtdlp = preferences.getBoolean("auto_update_ytdlp", viewModel.autoUpdateYtdlp)
            viewModel.ytdlpSource = preferences.getString("ytdlp_source", viewModel.ytdlpSource) ?: viewModel.ytdlpSource
            viewModel.ytdlpSourceLabel = preferences.getString("ytdlp_source_label", viewModel.ytdlpSourceLabel) ?: viewModel.ytdlpSourceLabel

            updateApp = viewModel.updateApp
            autoUpdateYtdlp = viewModel.autoUpdateYtdlp
            selectedChannel = viewModel.ytdlpSource
            customChannelLabel = viewModel.ytdlpSourceLabel
        }
        goTo(1)
    }

    private fun finishWelcome() {
        val channels = resources.getStringArray(R.array.ytdlp_source)
        val channelValues = resources.getStringArray(R.array.ytdlp_source_values)
        val builtIn = channelValues.indexOf(viewModel.ytdlpSource)
        val label = if (builtIn >= 0) channels[builtIn] else viewModel.ytdlpSourceLabel

        preferences.edit(commit = true) {
            putBoolean("update_app", viewModel.updateApp)
            putBoolean("auto_update_ytdlp", viewModel.autoUpdateYtdlp)
            putString("ytdlp_source", viewModel.ytdlpSource)
            putString("ytdlp_source_label", label)
            putBoolean("asked_auto_update_preferences", true)
            putBoolean(PREF_COMPLETED, true)
        }

        scheduleUpdateCheckWorker()
        launchMain()
    }

    fun scheduleUpdateCheckWorker() {
        val checkUpdatesApp = preferences.getBoolean("update_app", true)
        val checkUpdatesYTDL = preferences.getBoolean("auto_update_ytdlp", true)

        if (checkUpdatesApp || checkUpdatesYTDL) {
            UpdateCheckWorker.schedule(applicationContext)
        }
    }

    private fun launchMain() {
        val next = IntentCompat.getParcelableExtra(intent, EXTRA_NEXT_INTENT, Intent::class.java)
            ?: Intent(this, MainActivity::class.java)
        startActivity(Intent(next).putExtra(EXTRA_FROM_WELCOME, true))
        finish()
    }

    companion object {
        const val PREF_COMPLETED = "welcome_completed"
        const val EXTRA_NEXT_INTENT = "welcome_next_intent"
        const val EXTRA_FROM_WELCOME = "from_welcome"
        private const val LAST_PAGE = 3
        private const val GUIDE_URL = "https://ytdlnis.org"

        fun isNeeded(preferences: SharedPreferences): Boolean =
            !preferences.getBoolean(PREF_COMPLETED, false) &&
                    !preferences.getBoolean("asked_auto_update_preferences", false)
    }
}
