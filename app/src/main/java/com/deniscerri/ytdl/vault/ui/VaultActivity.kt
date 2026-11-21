package com.deniscerri.ytdl.vault.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.common.MediaViewOptionsSheet
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.ui.compose.vault.VaultPickerState
import com.deniscerri.ytdl.ui.compose.vault.VaultScreen
import com.deniscerri.ytdl.ui.compose.vault.VaultSettingsData
import com.deniscerri.ytdl.ui.compose.vault.VaultStage
import com.deniscerri.ytdl.ui.compose.vault.VaultUiState
import com.deniscerri.ytdl.ui.play.device.DeviceMedia
import com.deniscerri.ytdl.ui.play.device.DeviceMediaRepository
import com.deniscerri.ytdl.ui.play.device.DevicePermissions
import com.deniscerri.ytdl.ui.play.device.DevicePlayStatus
import com.deniscerri.ytdl.ui.play.device.DeviceThumbnailLoader
import com.deniscerri.ytdl.ui.play.device.sortDeviceMedia
import com.deniscerri.ytdl.vault.VaultBiometrics
import com.deniscerri.ytdl.vault.VaultEnvelopeEngine
import com.deniscerri.ytdl.vault.VaultField
import com.deniscerri.ytdl.vault.VaultLayoutMode
import com.deniscerri.ytdl.vault.VaultPickerField
import com.deniscerri.ytdl.vault.VaultRepository
import com.deniscerri.ytdl.vault.VaultSessionManager
import com.deniscerri.ytdl.vault.VaultSettings
import com.deniscerri.ytdl.vault.VaultSortBy
import com.deniscerri.ytdl.vault.VaultStorage
import com.deniscerri.ytdl.vault.VaultUiItem
import com.deniscerri.ytdl.vault.ingest.VaultImporter
import com.deniscerri.ytdl.vault.sortVaultItems
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Private Vault main container handling Setup, Unlock (Password + Hardware Biometric CryptoObject
 * + Decoy + Exponential Backoff + Auto-Wipe), Gallery (Grid/List + in-RAM search + Play + Rename +
 * Export + Delete + SAF Cut & Move), and Vault Settings — composed cleanly with Material 3.
 */
class VaultActivity : AppCompatActivity() {

    private var uiState by mutableStateOf(VaultUiState())

    private lateinit var pickerRepo: DeviceMediaRepository
    private lateinit var pickerThumbnails: DeviceThumbnailLoader
    private var allPickerVideos: List<DeviceMedia> = emptyList()
    private var visiblePickerVideos: List<DeviceMedia> = emptyList()
    private val selectedPickerIds = linkedSetOf<String>()

    private var allItems: List<VaultUiItem> = emptyList()
    private var visibleItems: List<VaultUiItem> = emptyList()
    private val selectedIds = linkedSetOf<String>()
    private var isSelectionMode = false
    private var vaultViewSettings = VaultSettings()

    private var setupOnlyMode = false
    private var wasUnlockedInSession = false
    private var internalChildInFlight = false
    private var externalOverlayInFlight = false
    private var smoothExitInFlight = false
    private var panicExited = false

    private data class PendingOsDelete(
        val title: String,
        val cleanupToken: VaultImporter.PendingTraceCleanup,
        val intentSender: android.content.IntentSender
    )

    private val pendingOsDeletes = ArrayDeque<PendingOsDelete>()
    private var activeOsDelete: PendingOsDelete? = null
    private var batchMovedAndDeleted = 0
    private var batchNeedsManualDelete = 0
    private var batchFailed = 0
    private var batchTotal = 0
    private var batchLastTitle = ""
    private var batchLastError: String? = null
    private var batchClosePickerOnComplete = false

    private val handler = Handler(Looper.getMainLooper())
    private var lockoutRunnable: Runnable? = null

    private val autoWipeOptions = intArrayOf(0, 5, 10, 20)

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                panicLockAndFinish()
            }
        }
    }

    private val pickerPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        externalOverlayInFlight = false
        if (VaultSessionManager.isUnlocked.value && uiState.stage == VaultStage.PICKER) {
            loadPickerVideos()
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        externalOverlayInFlight = false
        if (uris.isNotEmpty() && VaultSessionManager.isUnlocked.value) {
            handlePickedExternalUris(uris)
        }
    }

    private val pickVideoFallbackLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        externalOverlayInFlight = false
        if (uri != null && VaultSessionManager.isUnlocked.value) {
            handlePickedExternalUris(listOf(uri))
        }
    }

    private val osDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        externalOverlayInFlight = false
        val current = activeOsDelete
        activeOsDelete = null
        lifecycleScope.launch {
            if (current != null) {
                if (res.resultCode == RESULT_OK) {
                    VaultImporter.cleanupTraces(this@VaultActivity, current.cleanupToken)
                    batchMovedAndDeleted++
                } else {
                    batchNeedsManualDelete++
                }
            }
            if (!launchNextPickerOsDelete()) {
                finishBatchImportInVault()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        super.onCreate(savedInstanceState)
        externalOverlayInFlight = savedInstanceState?.getBoolean(KEY_EXTERNAL_OVERLAY_IN_FLIGHT) == true

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        setupOnlyMode = intent.getBooleanExtra(EXTRA_SETUP_ONLY, false)

        pickerRepo = DeviceMediaRepository(this)
        pickerThumbnails = DeviceThumbnailLoader(this)

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    VaultScreen(
                        state = uiState,
                        pickerThumbnailLoader = pickerThumbnails,
                        onBackClick = { handleBackAction() },
                        onCreateVault = { pw, confirm -> handleCreateVault(pw, confirm) },
                        onUnlockPassword = { pw -> handlePasswordUnlock(pw) },
                        onUnlockBiometric = { triggerBiometricUnlock() },
                        onItemClick = { item ->
                            if (uiState.isSelectionMode) {
                                toggleItemSelection(item)
                            } else {
                                openVaultPlayer(item)
                            }
                        },
                        onItemLongClick = { item -> enterSelectionModeWith(item) },
                        onItemRename = { item -> showRenameDialog(item) },
                        onItemExport = { item -> runRestore(listOf(item.meta.id), deleteFromVaultAfter = false) },
                        onItemDelete = { item -> showDeleteConfirmDialog(listOf(item)) },
                        onToggleSelectAll = { toggleSelectAll() },
                        onExitSelectionMode = { exitSelectionMode() },
                        onDeleteSelected = { confirmDeleteSelected() },
                        onExportSelected = { runRestore(selectedIds.toList(), deleteFromVaultAfter = false) },
                        onSearchQueryChange = { q ->
                            uiState = uiState.copy(searchQuery = q)
                            filterGallery(q)
                        },
                        onToggleSearch = {
                            val active = !uiState.isSearchActive
                            uiState = uiState.copy(isSearchActive = active)
                            if (!active) {
                                uiState = uiState.copy(searchQuery = "")
                                filterGallery("")
                            }
                        },
                        onOpenOptionsSheet = { openVaultViewOptions() },
                        onOpenSettings = { openVaultSettings() },
                        onLockVault = { smoothLockAndFinish() },
                        onOpenPicker = { openInAppVideoPicker() },
                        onCloseSettings = { closeVaultSettings() },
                        onToggleBiometric = { enable -> handleToggleBiometric(enable) },
                        onTogglePasswordManager = { enable -> handleTogglePasswordManager(enable) },
                        onToggleStealth = { enable -> handleToggleStealth(enable) },
                        onSaveStealthPin = { pin -> handleSaveStealthPin(pin) },
                        onSetupDecoy = { showDecoyPasswordDialog() },
                        onClearDecoy = { handleClearDecoy() },
                        onChangePassword = { showChangePasswordDialog() },
                        onSelectAutoWipe = { promptSelectAutoWipe() },
                        onToggleKeepOpen = { enable -> handleToggleKeepOpen(enable) },
                        onWipeVault = { confirmWipeVault() },
                        onClosePicker = { closeInAppVideoPicker() },
                        onPickerToggleItem = { media -> togglePickerSelection(media) },
                        onPickerToggleSelectAll = { togglePickerSelectAll() },
                        onPickerImportSelected = { importSelectedPickerVideos() },
                        onPickerGrantPermission = {
                            externalOverlayInFlight = true
                            pickerPermissionLauncher.launch(DevicePermissions.required())
                        },
                        onPickerSearchChange = { q ->
                            uiState = uiState.copy(
                                pickerState = uiState.pickerState.copy(searchQuery = q)
                            )
                            filterPickerVideos(q)
                        },
                        onPickerToggleLayout = {
                            val next = !uiState.pickerState.isGridMode
                            uiState = uiState.copy(
                                pickerState = uiState.pickerState.copy(isGridMode = next)
                            )
                        },
                        onPickerOpenOptions = { openVaultPickerViewOptions() },
                        onPickerBrowseSystem = { launchExternalDocumentPicker() }
                    )
                }
            }
        }
        setContentView(composeView)

        supportFragmentManager.setFragmentResultListener(
            MediaViewOptionsSheet.REQUEST_VAULT,
            this
        ) { _, bundle ->
            onVaultViewOptionsResult(bundle)
        }
        supportFragmentManager.setFragmentResultListener(
            MediaViewOptionsSheet.REQUEST_VAULT_PICKER,
            this
        ) { _, bundle ->
            onVaultPickerViewOptionsResult(bundle)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackAction()
                }
            }
        )

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        renderCurrentState(autoTriggerBiometrics = true)
    }

    private fun isPasswordManagerEnabled(): Boolean =
        VaultRepository.loadEnvelopeOrNull(this)?.passwordManagerEnabled == true

    private fun renderCurrentState(autoTriggerBiometrics: Boolean = false) {
        stopLockoutTicker()
        when {
            !VaultRepository.isInitialized(this) -> {
                uiState = uiState.copy(
                    stage = VaultStage.SETUP,
                    isInitialized = false,
                    isUnlocked = false,
                    setupError = null
                )
            }
            !VaultSessionManager.isUnlocked.value -> {
                configureUnlockScreen(autoTriggerBiometrics)
            }
            else -> {
                wasUnlockedInSession = true
                if (setupOnlyMode) {
                    setResult(RESULT_OK)
                    VaultSessionManager.lockAndZeroize()
                    smoothExitOnly()
                    return
                }
                uiState = uiState.copy(
                    stage = VaultStage.GALLERY,
                    isInitialized = true,
                    isUnlocked = true,
                    isDecoy = VaultSessionManager.isDecoySession
                )
                loadGallery()
            }
        }
    }

    private fun handleCreateVault(password: String, confirm: String) {
        val pw = password.toCharArray()
        val conf = confirm.toCharArray()
        if (pw.size < 4) {
            pw.fill('\u0000')
            conf.fill('\u0000')
            uiState = uiState.copy(setupError = getString(R.string.vault_password_too_short))
            return
        }
        if (!pw.contentEquals(conf)) {
            pw.fill('\u0000')
            conf.fill('\u0000')
            uiState = uiState.copy(setupError = getString(R.string.vault_password_mismatch))
            return
        }
        conf.fill('\u0000')
        uiState = uiState.copy(setupError = null)
        showBusy(getString(R.string.vault_creating_vault))
        lifecycleScope.launch {
            runCatching {
                VaultRepository.createVault(this@VaultActivity, pw)
            }.onSuccess {
                hideBusy()
                if (setupOnlyMode) {
                    setResult(RESULT_OK)
                    VaultSessionManager.lockAndZeroize()
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                } else {
                    renderCurrentState()
                }
            }.onFailure { err ->
                hideBusy()
                uiState = uiState.copy(setupError = err.message ?: "Error")
            }
        }
    }

    private fun configureUnlockScreen(autoTriggerBiometrics: Boolean) {
        var env = VaultRepository.loadEnvelopeOrNull(this) ?: return
        var invalidatedNow = env.biometricInvalidated
        if (env.biometricEnabled) {
            val keyValid = !env.biometricWrappedDekIvB64.isNullOrBlank() &&
                !env.biometricWrappedDekCtB64.isNullOrBlank() &&
                VaultBiometrics.checkEnrolledKeyStillValid(this, env.biometricWrappedDekIvB64)
            if (!keyValid) {
                VaultRepository.clearBiometricEnrollment(this, dueToChange = true)
                env = VaultRepository.loadEnvelopeOrNull(this) ?: env
                invalidatedNow = true
            }
        }

        val now = System.currentTimeMillis()
        val isLockedOut = env.lockoutUntilMs > now
        if (isLockedOut) {
            startLockoutTicker(env.lockoutUntilMs)
        }

        val canBio = !invalidatedNow &&
            env.biometricEnabled &&
            !env.biometricWrappedDekIvB64.isNullOrBlank() &&
            !env.biometricWrappedDekCtB64.isNullOrBlank() &&
            VaultBiometrics.isSupported(this)

        uiState = uiState.copy(
            stage = VaultStage.UNLOCK,
            isInitialized = true,
            isUnlocked = false,
            unlockError = if (invalidatedNow) getString(R.string.vault_biometric_invalidated) else null,
            canBiometricUnlock = canBio
        )

        if (canBio && autoTriggerBiometrics && !isLockedOut) {
            triggerBiometricUnlock()
        }
    }

    private fun triggerBiometricUnlock() {
        val env = VaultRepository.loadEnvelopeOrNull(this) ?: return
        val iv = env.biometricWrappedDekIvB64 ?: return
        val ct = env.biometricWrappedDekCtB64 ?: return
        externalOverlayInFlight = true
        VaultBiometrics.unlockWithBiometrics(
            context = this,
            title = getString(R.string.vault_biometric_title),
            subtitle = getString(R.string.vault_biometric_subtitle),
            cancelText = getString(R.string.cancel),
            ivB64 = iv,
            ctB64 = ct,
            onSuccess = { dek ->
                externalOverlayInFlight = false
                lifecycleScope.launch {
                    showBusy(getString(R.string.vault_unlock_button))
                    VaultRepository.finishBiometricUnlock(this@VaultActivity, dek)
                    hideBusy()
                    renderCurrentState()
                }
            },
            onInvalidated = {
                externalOverlayInFlight = false
                VaultRepository.clearBiometricEnrollment(this, dueToChange = true)
                uiState = uiState.copy(
                    canBiometricUnlock = false,
                    unlockError = getString(R.string.vault_biometric_invalidated)
                )
            },
            onError = { msg ->
                externalOverlayInFlight = false
                if (msg.contains("invalidat", ignoreCase = true) ||
                    msg.contains("permanently", ignoreCase = true)
                ) {
                    VaultRepository.clearBiometricEnrollment(this, dueToChange = true)
                    uiState = uiState.copy(
                        canBiometricUnlock = false,
                        unlockError = getString(R.string.vault_biometric_invalidated)
                    )
                }
            }
        )
    }

    private fun handlePasswordUnlock(password: String) {
        val chars = password.toCharArray()
        if (chars.isEmpty()) return
        uiState = uiState.copy(unlockError = null)
        showBusy(getString(R.string.vault_unlock_button))
        lifecycleScope.launch {
            val outcome = runCatching {
                VaultRepository.unlockWithPassword(this@VaultActivity, chars)
            }
            hideBusy()
            outcome.onSuccess { res ->
                when (res) {
                    is VaultEnvelopeEngine.UnlockOutcome.Success -> renderCurrentState()
                    is VaultEnvelopeEngine.UnlockOutcome.LockedOut -> {
                        startLockoutTicker(System.currentTimeMillis() + res.remainingMs)
                    }
                    is VaultEnvelopeEngine.UnlockOutcome.WrongPassword -> {
                        if (res.autoWiped) {
                            Toast.makeText(
                                this@VaultActivity,
                                R.string.vault_auto_wiped,
                                Toast.LENGTH_LONG
                            ).show()
                            renderCurrentState()
                        } else {
                            val errText = res.remainingBeforeWipe?.let {
                                getString(R.string.vault_wrong_password_remaining, it)
                            } ?: getString(R.string.vault_wrong_password)
                            uiState = uiState.copy(unlockError = errText)
                            if (res.backoffMs > 0L) {
                                startLockoutTicker(res.updatedEnvelope.lockoutUntilMs)
                            }
                        }
                    }
                }
            }.onFailure { err ->
                uiState = uiState.copy(unlockError = err.message ?: getString(R.string.vault_wrong_password))
            }
        }
    }

    private fun startLockoutTicker(untilMs: Long) {
        stopLockoutTicker()
        val runnable = object : Runnable {
            override fun run() {
                val remMs = untilMs - System.currentTimeMillis()
                if (remMs <= 0L) {
                    uiState = uiState.copy(lockoutRemainingSeconds = null, unlockError = null)
                    lockoutRunnable = null
                } else {
                    val secs = ((remMs + 999L) / 1000L).toInt()
                    uiState = uiState.copy(lockoutRemainingSeconds = secs)
                    handler.postDelayed(this, 400L)
                }
            }
        }
        lockoutRunnable = runnable
        handler.post(runnable)
    }

    private fun stopLockoutTicker() {
        lockoutRunnable?.let { handler.removeCallbacks(it) }
        lockoutRunnable = null
    }

    private fun openInAppVideoPicker() {
        if (!VaultSessionManager.isUnlocked.value) return
        if (isSelectionMode) exitSelectionMode()
        selectedPickerIds.clear()
        uiState = uiState.copy(
            stage = VaultStage.PICKER,
            pickerState = uiState.pickerState.copy(
                selectedIds = emptySet(),
                searchQuery = "",
                hasPermission = DevicePermissions.hasVideo(this),
                isGridMode = vaultViewSettings.pickerLayoutMode == VaultLayoutMode.GRID,
                visibleFields = vaultViewSettings.pickerVisibleFields
            )
        )
        loadPickerVideos()
    }

    private fun closeInAppVideoPicker() {
        selectedPickerIds.clear()
        uiState = uiState.copy(stage = VaultStage.GALLERY)
    }

    private fun loadPickerVideos() {
        if (!VaultSessionManager.isUnlocked.value) return
        if (!DevicePermissions.hasVideo(this)) {
            allPickerVideos = emptyList()
            visiblePickerVideos = emptyList()
            uiState = uiState.copy(
                pickerState = uiState.pickerState.copy(
                    hasPermission = false,
                    isLoading = false,
                    allVideos = emptyList(),
                    visibleVideos = emptyList(),
                    selectedIds = emptySet()
                )
            )
            return
        }
        uiState = uiState.copy(
            pickerState = uiState.pickerState.copy(
                hasPermission = true,
                isLoading = true
            )
        )
        lifecycleScope.launch {
            val loaded = runCatching {
                pickerRepo.load(folderPath = null, includeAudio = false, includeVideo = true)
            }.getOrDefault(emptyList())
            if (!VaultSessionManager.isUnlocked.value) return@launch
            allPickerVideos = loaded
            val validIds = loaded.mapTo(hashSetOf()) { it.mediaId }
            selectedPickerIds.retainAll(validIds)
            filterPickerVideos(uiState.pickerState.searchQuery)
            if (loaded.isNotEmpty()) {
                launch(Dispatchers.IO) { runCatching { pickerThumbnails.warmUp(loaded) } }
            }
        }
    }

    private fun filterPickerVideos(query: String) {
        val sorted = sortDeviceMedia(
            items = allPickerVideos,
            sort = vaultViewSettings.pickerSortBy.toDeviceSort(),
            newestFirst = vaultViewSettings.pickerSortDescending,
            playedAt = emptyMap(),
            statusFor = { DevicePlayStatus.NEW }
        )
        val q = query.trim()
        val filtered = if (q.isEmpty()) {
            sorted
        } else {
            sorted.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.folderName.contains(q, ignoreCase = true)
            }
        }
        visiblePickerVideos = filtered
        uiState = uiState.copy(
            pickerState = uiState.pickerState.copy(
                hasPermission = DevicePermissions.hasVideo(this),
                isLoading = false,
                allVideos = allPickerVideos,
                visibleVideos = filtered,
                selectedIds = selectedPickerIds.toSet()
            )
        )
    }

    private fun togglePickerSelection(media: DeviceMedia) {
        if (!selectedPickerIds.add(media.mediaId)) {
            selectedPickerIds.remove(media.mediaId)
        }
        uiState = uiState.copy(
            pickerState = uiState.pickerState.copy(
                selectedIds = selectedPickerIds.toSet()
            )
        )
    }

    private fun togglePickerSelectAll() {
        val visibleIds = visiblePickerVideos.map { it.mediaId }
        if (visibleIds.isEmpty()) return
        if (visibleIds.all { it in selectedPickerIds }) {
            selectedPickerIds.removeAll(visibleIds.toSet())
        } else {
            selectedPickerIds.addAll(visibleIds)
        }
        uiState = uiState.copy(
            pickerState = uiState.pickerState.copy(
                selectedIds = selectedPickerIds.toSet()
            )
        )
    }

    private fun importSelectedPickerVideos() {
        if (!VaultSessionManager.isUnlocked.value) return
        val selected = allPickerVideos.filter { it.mediaId in selectedPickerIds }
        if (selected.isEmpty()) return
        val requests = selected.map { media ->
            VaultImporter.MoveSourceRequest(
                contentUri = media.contentUri,
                title = media.title,
                originalFileName = media.title
            )
        }
        showBatchImportDisclosureAndMove(requests, closePickerOnComplete = true)
    }

    private fun launchExternalDocumentPicker() {
        if (!VaultSessionManager.isUnlocked.value) return
        externalOverlayInFlight = true
        try {
            pickVideoLauncher.launch(arrayOf("video/*"))
        } catch (_: Exception) {
            try {
                pickVideoFallbackLauncher.launch("video/*")
            } catch (e: Exception) {
                externalOverlayInFlight = false
                Toast.makeText(this, e.message ?: "Failed to open picker", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePickedExternalUris(uris: List<Uri>) {
        if (!VaultSessionManager.isUnlocked.value || uris.isEmpty()) return
        val requests = uris.map { uri ->
            VaultImporter.MoveSourceRequest(
                contentUri = uri,
                title = uri.lastPathSegment.orEmpty(),
                originalFileName = uri.lastPathSegment
            )
        }
        showBatchImportDisclosureAndMove(requests, closePickerOnComplete = false)
    }

    private fun enterSelectionModeWith(item: VaultUiItem) {
        if (!isSelectionMode) {
            isSelectionMode = true
        }
        selectedIds.add(item.meta.id)
        syncSelectionUi()
    }

    private fun toggleItemSelection(item: VaultUiItem) {
        if (!selectedIds.add(item.meta.id)) {
            selectedIds.remove(item.meta.id)
        }
        if (selectedIds.isEmpty()) {
            exitSelectionMode()
        } else {
            syncSelectionUi()
        }
    }

    private fun toggleSelectAll() {
        val visibleIds = visibleItems.map { it.meta.id }
        if (visibleIds.isEmpty()) return
        if (visibleIds.all { it in selectedIds }) {
            exitSelectionMode()
        } else {
            selectedIds.addAll(visibleIds)
            syncSelectionUi()
        }
    }

    private fun exitSelectionMode() {
        if (!isSelectionMode && selectedIds.isEmpty()) return
        isSelectionMode = false
        selectedIds.clear()
        syncSelectionUi()
    }

    private fun syncSelectionUi() {
        val active = isSelectionMode && selectedIds.isNotEmpty()
        isSelectionMode = active
        uiState = uiState.copy(
            isSelectionMode = active,
            selectedIds = selectedIds.toSet()
        )
    }

    private fun loadGallery() {
        if (!VaultSessionManager.isUnlocked.value) return
        lifecycleScope.launch {
            val loadedSettings = if (VaultSessionManager.isDecoySession) {
                vaultViewSettings
            } else {
                withContext(Dispatchers.IO) { VaultRepository.loadSettings(this@VaultActivity) }
            }
            val loaded = VaultRepository.listItems(this@VaultActivity)
            if (!VaultSessionManager.isUnlocked.value) return@launch
            vaultViewSettings = loadedSettings
            allItems = loaded
            val validIds = loaded.mapTo(hashSetOf()) { it.meta.id }
            selectedIds.retainAll(validIds)
            if (isSelectionMode && selectedIds.isEmpty()) {
                isSelectionMode = false
            }
            filterGallery(uiState.searchQuery)
            syncSelectionUi()
        }
    }

    private fun filterGallery(query: String) {
        val sorted = sortVaultItems(allItems, vaultViewSettings.sortBy, vaultViewSettings.sortDescending)
        val q = query.trim()
        val filtered = if (q.isEmpty()) {
            sorted
        } else {
            sorted.filter {
                it.meta.title.contains(q, ignoreCase = true) ||
                    it.meta.author.contains(q, ignoreCase = true) ||
                    it.meta.website.contains(q, ignoreCase = true)
            }
        }
        visibleItems = filtered
        uiState = uiState.copy(
            allItems = allItems,
            visibleItems = filtered,
            layoutMode = vaultViewSettings.layoutMode,
            visibleFields = vaultViewSettings.visibleFields,
            sortBy = vaultViewSettings.sortBy,
            sortDescending = vaultViewSettings.sortDescending,
            selectedIds = selectedIds.toSet(),
            isSelectionMode = isSelectionMode
        )
    }

    private fun openVaultViewOptions() {
        if (!VaultSessionManager.isUnlocked.value) return
        if (supportFragmentManager.findFragmentByTag(MediaViewOptionsSheet.TAG) != null) return
        val latest = if (VaultSessionManager.isDecoySession) {
            vaultViewSettings
        } else {
            VaultRepository.loadSettings(this).also { vaultViewSettings = it }
        }
        MediaViewOptionsSheet.newVaultInstance(latest)
            .show(supportFragmentManager, MediaViewOptionsSheet.TAG)
    }

    private fun openVaultPickerViewOptions() {
        if (!VaultSessionManager.isUnlocked.value) return
        if (supportFragmentManager.findFragmentByTag(MediaViewOptionsSheet.TAG) != null) return
        val latest = if (VaultSessionManager.isDecoySession) {
            vaultViewSettings
        } else {
            VaultRepository.loadSettings(this).also { vaultViewSettings = it }
        }
        MediaViewOptionsSheet.newVaultPickerInstance(latest)
            .show(supportFragmentManager, MediaViewOptionsSheet.TAG)
    }

    private fun onVaultViewOptionsResult(bundle: Bundle) {
        val layout = runCatching {
            VaultLayoutMode.valueOf(bundle.getString(MediaViewOptionsSheet.RESULT_VAULT_LAYOUT).orEmpty())
        }.getOrDefault(VaultLayoutMode.GRID)
        val sort = runCatching {
            VaultSortBy.valueOf(bundle.getString(MediaViewOptionsSheet.RESULT_VAULT_SORT).orEmpty())
        }.getOrDefault(VaultSortBy.DATE)
        val descending = bundle.getBoolean(MediaViewOptionsSheet.RESULT_VAULT_DESCENDING, true)
        val fields = bundle.getStringArrayList(MediaViewOptionsSheet.RESULT_VAULT_FIELDS)
            ?.mapNotNull { runCatching { VaultField.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?: VaultSettings.DEFAULT_VISIBLE_FIELDS

        val base = if (VaultSessionManager.isDecoySession) {
            vaultViewSettings
        } else {
            VaultRepository.loadSettings(this)
        }
        val updated = base.copy(
            layoutMode = layout,
            sortBy = sort,
            sortDescending = descending,
            visibleFields = fields
        )
        vaultViewSettings = updated
        uiState = uiState.copy(
            layoutMode = layout,
            sortBy = sort,
            sortDescending = descending,
            visibleFields = fields
        )
        filterGallery(uiState.searchQuery)

        if (!VaultSessionManager.isDecoySession) {
            lifecycleScope.launch(Dispatchers.IO) {
                VaultRepository.updateViewSettings(this@VaultActivity, updated)
            }
        }
    }

    private fun onVaultPickerViewOptionsResult(bundle: Bundle) {
        val layout = runCatching {
            VaultLayoutMode.valueOf(bundle.getString(MediaViewOptionsSheet.RESULT_PICKER_LAYOUT).orEmpty())
        }.getOrDefault(VaultLayoutMode.GRID)
        val sort = runCatching {
            com.deniscerri.ytdl.vault.VaultPickerSortBy.valueOf(
                bundle.getString(MediaViewOptionsSheet.RESULT_PICKER_SORT).orEmpty()
            )
        }.getOrDefault(com.deniscerri.ytdl.vault.VaultPickerSortBy.DATE)
        val descending = bundle.getBoolean(MediaViewOptionsSheet.RESULT_PICKER_DESCENDING, true)
        val fields = bundle.getStringArrayList(MediaViewOptionsSheet.RESULT_PICKER_FIELDS)
            ?.mapNotNull { runCatching { VaultPickerField.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?: VaultSettings.DEFAULT_PICKER_VISIBLE_FIELDS

        val base = if (VaultSessionManager.isDecoySession) {
            vaultViewSettings
        } else {
            VaultRepository.loadSettings(this)
        }
        val updated = base.copy(
            pickerLayoutMode = layout,
            pickerSortBy = sort,
            pickerSortDescending = descending,
            pickerVisibleFields = fields
        )
        vaultViewSettings = updated
        uiState = uiState.copy(
            pickerState = uiState.pickerState.copy(
                isGridMode = layout == VaultLayoutMode.GRID,
                visibleFields = fields
            )
        )
        filterPickerVideos(uiState.pickerState.searchQuery)

        if (!VaultSessionManager.isDecoySession) {
            lifecycleScope.launch(Dispatchers.IO) {
                VaultRepository.updateViewSettings(this@VaultActivity, updated)
            }
        }
    }

    private fun openVaultPlayer(item: VaultUiItem) {
        if (!VaultSessionManager.isUnlocked.value) return
        internalChildInFlight = true
        startActivity(VaultVideoPlayerActivity.createIntent(this, item.meta.id, item.meta.title))
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun confirmDeleteSelected() {
        val targets = allItems.filter { it.meta.id in selectedIds }
        if (targets.isEmpty()) return
        showDeleteConfirmDialog(targets)
    }

    private fun showRenameDialog(item: VaultUiItem) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        val input = TextInputEditText(this).apply {
            setText(item.meta.title)
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        container.addView(input)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_rename)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newTitle = input.text?.toString()?.trim().orEmpty()
                if (newTitle.isNotEmpty()) {
                    lifecycleScope.launch {
                        VaultRepository.renameItem(this@VaultActivity, item.meta.id, newTitle)
                        loadGallery()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    private fun runRestore(uuids: List<String>, deleteFromVaultAfter: Boolean) {
        if (uuids.isEmpty()) return
        showBusy(getString(R.string.vault_exporting_progress))
        lifecycleScope.launch {
            runCatching {
                VaultRepository.restoreVaultItems(
                    context = this@VaultActivity,
                    uuids = uuids,
                    deleteFromVaultAfter = deleteFromVaultAfter,
                    onProgress = { idx, count, written, total ->
                        if (total > 0L) {
                            val pct = ((written * 100L) / total).toInt().coerceIn(0, 100)
                            val prefix = if (count > 1) "${idx + 1}/$count · " else ""
                            uiState = uiState.copy(
                                busyLabel = "${getString(R.string.vault_exporting_progress)} ($prefix$pct%)"
                            )
                        }
                    }
                )
            }.onSuccess { restored ->
                hideBusy()
                exitSelectionMode()
                if (deleteFromVaultAfter) {
                    loadGallery()
                }
                val msg = if (restored.size == 1) {
                    val item = restored.first()
                    val folderName = java.io.File(item.destinationLabel).name.ifBlank { item.destinationLabel }
                    getString(
                        if (deleteFromVaultAfter) R.string.vault_decrypted_single else R.string.vault_decrypted_copy_single,
                        item.title,
                        folderName
                    )
                } else {
                    getString(
                        if (deleteFromVaultAfter) R.string.vault_decrypted_multi else R.string.vault_decrypted_copy_multi,
                        restored.size
                    )
                }
                Toast.makeText(this@VaultActivity, msg, Toast.LENGTH_LONG).show()
            }.onFailure { err ->
                hideBusy()
                Toast.makeText(this@VaultActivity, err.message ?: "Decrypt failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDeleteConfirmDialog(items: List<VaultUiItem>) {
        if (items.isEmpty()) return
        val message = if (items.size == 1) {
            getString(R.string.vault_delete_confirm, items.first().meta.title)
        } else {
            getString(R.string.vault_delete_confirm_multi, items.size)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(message)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    VaultRepository.deleteItems(this@VaultActivity, items.map { it.meta.id })
                    exitSelectionMode()
                    Toast.makeText(this@VaultActivity, R.string.vault_deleted, Toast.LENGTH_SHORT).show()
                    loadGallery()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    private fun showBatchImportDisclosureAndMove(
        requests: List<VaultImporter.MoveSourceRequest>,
        closePickerOnComplete: Boolean
    ) {
        if (isFinishing || isDestroyed || !VaultSessionManager.isUnlocked.value || requests.isEmpty()) return
        val message = if (requests.size == 1) {
            val req = requests.first()
            val label = req.title.ifBlank { req.originalFileName ?: "Selected Video" }
            getString(R.string.vault_import_disclosure_msg, label)
        } else {
            getString(R.string.vault_import_disclosure_multi_msg, requests.size)
        }
        val dialog = MaterialAlertDialogBuilder(this@VaultActivity)
            .setTitle(R.string.vault_import_title)
            .setMessage(message)
            .setPositiveButton(R.string.vault_import_continue) { _, _ ->
                executeBatchMoveInVault(requests, closePickerOnComplete)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    private fun executeBatchMoveInVault(
        requests: List<VaultImporter.MoveSourceRequest>,
        closePickerOnComplete: Boolean
    ) {
        batchTotal = requests.size
        batchMovedAndDeleted = 0
        batchNeedsManualDelete = 0
        batchFailed = 0
        batchLastTitle = requests.firstOrNull()?.let {
            it.title.ifBlank { it.originalFileName ?: "Private Video" }
        }.orEmpty()
        batchLastError = null
        batchClosePickerOnComplete = closePickerOnComplete
        pendingOsDeletes.clear()
        activeOsDelete = null

        val baseLabel = getString(R.string.vault_moving_progress)
        showBusy(if (batchTotal > 1) "$baseLabel (1/$batchTotal)" else baseLabel)

        lifecycleScope.launch {
            for ((index, req) in requests.withIndex()) {
                if (isFinishing || isDestroyed || !VaultSessionManager.isUnlocked.value) break
                val prefix = if (batchTotal > 1) "${index + 1}/$batchTotal · " else ""
                uiState = uiState.copy(busyLabel = "$baseLabel (${prefix}0%)")
                val result = runCatching {
                    VaultImporter.moveVideoToVault(
                        context = this@VaultActivity,
                        request = req,
                        onProgress = { written, total ->
                            if (total > 0L) {
                                val pct = ((written * 100L) / total).toInt().coerceIn(0, 100)
                                uiState = uiState.copy(busyLabel = "$baseLabel ($prefix$pct%)")
                            }
                        }
                    )
                }
                result.onSuccess { outcome ->
                    when (outcome) {
                        is VaultImporter.MoveOutcome.MovedAndDeleted -> {
                            batchMovedAndDeleted++
                            batchLastTitle = outcome.title
                        }
                        is VaultImporter.MoveOutcome.RequiresOsDeletePermission -> {
                            batchLastTitle = outcome.title
                            pendingOsDeletes.addLast(
                                PendingOsDelete(
                                    title = outcome.title,
                                    cleanupToken = outcome.cleanupToken,
                                    intentSender = outcome.intentSender
                                )
                            )
                        }
                        is VaultImporter.MoveOutcome.ImportedNeedsManualDelete -> {
                            batchNeedsManualDelete++
                            batchLastTitle = outcome.title
                        }
                    }
                }.onFailure { err ->
                    batchFailed++
                    batchLastError = err.message ?: "Error"
                }
            }

            if (!launchNextPickerOsDelete()) {
                finishBatchImportInVault()
            }
        }
    }

    private fun launchNextPickerOsDelete(): Boolean {
        val next = pendingOsDeletes.removeFirstOrNull() ?: return false
        activeOsDelete = next
        externalOverlayInFlight = true
        osDeleteLauncher.launch(IntentSenderRequest.Builder(next.intentSender).build())
        return true
    }

    private fun finishBatchImportInVault() {
        hideBusy()
        val totalImported = batchMovedAndDeleted + batchNeedsManualDelete
        if (batchTotal <= 1) {
            when {
                batchMovedAndDeleted == 1 -> Toast.makeText(
                    this,
                    getString(R.string.vault_import_success_deleted, batchLastTitle),
                    Toast.LENGTH_SHORT
                ).show()
                batchNeedsManualDelete == 1 -> Toast.makeText(
                    this,
                    getString(R.string.vault_import_success_needs_delete, batchLastTitle),
                    Toast.LENGTH_LONG
                ).show()
                else -> Toast.makeText(
                    this,
                    getString(R.string.vault_import_failed, batchLastError ?: "Error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            val msg = when {
                totalImported == 0 ->
                    getString(R.string.vault_import_failed, batchLastError ?: "Error")
                batchFailed == 0 && batchNeedsManualDelete == 0 ->
                    getString(R.string.vault_import_batch_success, totalImported)
                batchFailed == 0 ->
                    getString(R.string.vault_import_batch_needs_delete, batchMovedAndDeleted, batchNeedsManualDelete)
                else ->
                    getString(R.string.vault_import_batch_partial, totalImported, batchFailed)
            }
            Toast.makeText(
                this,
                msg,
                if (batchFailed > 0 || batchNeedsManualDelete > 0 || totalImported == 0) {
                    Toast.LENGTH_LONG
                } else {
                    Toast.LENGTH_SHORT
                }
            ).show()
        }
        if (totalImported > 0) {
            if (batchClosePickerOnComplete) {
                closeInAppVideoPicker()
            }
            loadGallery()
        }
    }

    private fun persistQuickSettings(
        autoWipeThreshold: Int? = null,
        stealthMode: Boolean? = null,
        keepOpenOnPlayerBack: Boolean? = null,
        biometricsEnabled: Boolean? = null,
        passwordManagerEnabled: Boolean? = null
    ) {
        if (!VaultSessionManager.isUnlocked.value || VaultSessionManager.isDecoySession) return
        val cur = VaultRepository.loadSettings(this)
        val env = VaultRepository.loadEnvelopeOrNull(this)
        val updated = cur.copy(
            autoWipeThreshold = autoWipeThreshold ?: cur.autoWipeThreshold,
            stealthMode = stealthMode ?: (env?.stealthEnabled ?: cur.stealthMode),
            keepOpenOnPlayerBack = keepOpenOnPlayerBack ?: cur.keepOpenOnPlayerBack,
            biometricsEnabled = biometricsEnabled ?: (env?.biometricEnabled ?: cur.biometricsEnabled),
            passwordManagerEnabled = passwordManagerEnabled ?: cur.passwordManagerEnabled
        )
        if (stealthMode == true && env?.hasStealthPin() == true && !env.stealthEnabled) {
            VaultRepository.updateSettings(this, updated.copy(stealthMode = true))
        } else {
            VaultRepository.updateSettings(this, updated)
        }
    }

    private fun openVaultSettings() {
        if (!VaultSessionManager.isUnlocked.value) return
        if (VaultSessionManager.isDecoySession) {
            Toast.makeText(this, R.string.vault_decoy_restricted, Toast.LENGTH_SHORT).show()
            return
        }
        refreshSettingsState()
        uiState = uiState.copy(stage = VaultStage.SETTINGS)
    }

    private fun closeVaultSettings() {
        uiState = uiState.copy(stage = VaultStage.GALLERY)
        loadGallery()
    }

    private fun refreshSettingsState() {
        val currentSettings = VaultRepository.loadSettings(this)
        val env = VaultRepository.loadEnvelopeOrNull(this)
        uiState = uiState.copy(
            settingsData = VaultSettingsData(
                biometricEnabled = env?.biometricEnabled == true,
                biometricAvailable = VaultBiometrics.isSupported(this),
                biometricInvalidated = env?.biometricInvalidated == true,
                passwordManagerEnabled = currentSettings.passwordManagerEnabled || env?.passwordManagerEnabled == true,
                stealthEnabled = env?.stealthEnabled == true,
                stealthPin = if (env?.hasStealthPin() == true) "••••" else "",
                decoyConfigured = env?.hasDecoy() == true,
                autoWipeThreshold = currentSettings.autoWipeThreshold,
                keepOpenInSession = currentSettings.keepOpenOnPlayerBack
            )
        )
    }

    private fun handleToggleBiometric(enable: Boolean) {
        if (!enable) {
            VaultRepository.clearBiometricEnrollment(this)
            refreshSettingsState()
        } else {
            val dek = VaultSessionManager.getKeyCopyOrNull()
            if (dek != null) {
                externalOverlayInFlight = true
                VaultBiometrics.enrollBiometricDek(
                    context = this,
                    title = getString(R.string.vault_setting_biometrics),
                    subtitle = getString(R.string.vault_biometric_subtitle),
                    cancelText = getString(R.string.cancel),
                    dek = dek,
                    onResult = { res ->
                        externalOverlayInFlight = false
                        dek.fill(0)
                        res.onSuccess { (iv, ct) ->
                            VaultRepository.finishBiometricEnrollment(this, iv, ct)
                            refreshSettingsState()
                        }.onFailure {
                            refreshSettingsState()
                        }
                    }
                )
            }
        }
    }

    private fun handleTogglePasswordManager(enable: Boolean) {
        persistQuickSettings(passwordManagerEnabled = enable)
        refreshSettingsState()
    }

    private fun handleToggleKeepOpen(enable: Boolean) {
        persistQuickSettings(keepOpenOnPlayerBack = enable)
        refreshSettingsState()
    }

    private fun promptSelectAutoWipe() {
        val cur = VaultRepository.loadSettings(this)
        val idx = autoWipeOptions.indexOf(cur.autoWipeThreshold).let { if (it < 0) 2 else it }
        val next = autoWipeOptions[(idx + 1) % autoWipeOptions.size]
        persistQuickSettings(autoWipeThreshold = next)
        refreshSettingsState()
    }

    private fun handleToggleStealth(enable: Boolean) {
        val env = VaultRepository.loadEnvelopeOrNull(this)
        if (!enable) {
            persistQuickSettings(stealthMode = false)
        } else if (env?.hasStealthPin() == true) {
            persistQuickSettings(stealthMode = true)
        }
        refreshSettingsState()
    }

    private fun handleSaveStealthPin(pin: String) {
        val pinChars = pin.toCharArray()
        if (pinChars.size < 4) {
            pinChars.fill('\u0000')
            Toast.makeText(this, R.string.vault_password_too_short, Toast.LENGTH_SHORT).show()
            return
        }
        showBusy(getString(R.string.vault_saving_stealth))
        lifecycleScope.launch {
            val cur = VaultRepository.loadSettings(this@VaultActivity)
            VaultRepository.updateSettings(
                context = this@VaultActivity,
                newSettings = cur.copy(stealthMode = true),
                newStealthPinChars = pinChars
            )
            hideBusy()
            refreshSettingsState()
            Toast.makeText(this@VaultActivity, R.string.vault_stealth_pin_active, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleClearDecoy() {
        showBusy(getString(R.string.vault_removing_decoy))
        lifecycleScope.launch {
            VaultRepository.clearDecoyPassword(this@VaultActivity)
            hideBusy()
            refreshSettingsState()
            Toast.makeText(this@VaultActivity, R.string.vault_setting_decoy_not_set, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showChangePasswordDialog() {
        val pmEnabled = isPasswordManagerEnabled()
        val density = resources.displayMetrics.density
        val pad = (18 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        fun field(hintRes: Int, autofillHint: String): Pair<TextInputLayout, TextInputEditText> {
            val l = TextInputLayout(this).apply {
                hint = getString(hintRes)
                endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            }
            val e = TextInputEditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    importantForAutofill = if (pmEnabled) {
                        android.view.View.IMPORTANT_FOR_AUTOFILL_YES
                    } else {
                        android.view.View.IMPORTANT_FOR_AUTOFILL_NO
                    }
                    if (pmEnabled) {
                        setAutofillHints(autofillHint)
                    }
                }
            }
            l.addView(e)
            container.addView(l)
            return l to e
        }

        val curHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) android.view.View.AUTOFILL_HINT_PASSWORD else "password"
        val newHint = "newPassword"
        val (curLayout, curInput) = field(R.string.vault_current_password_hint, curHint)
        val (newLayout, newInput) = field(R.string.vault_new_password_hint, newHint)
        val (confLayout, confInput) = field(R.string.vault_confirm_password_hint, newHint)

        val d = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_setting_change_password)
            .setView(container)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        d.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        d.show()

        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val cur = curInput.text?.toString()?.toCharArray() ?: CharArray(0)
            val nw = newInput.text?.toString()?.toCharArray() ?: CharArray(0)
            val cf = confInput.text?.toString()?.toCharArray() ?: CharArray(0)
            curInput.text?.clear()
            newInput.text?.clear()
            confInput.text?.clear()
            if (nw.size < 4) {
                cur.fill('\u0000'); nw.fill('\u0000'); cf.fill('\u0000')
                newLayout.error = getString(R.string.vault_password_too_short)
                return@setOnClickListener
            }
            if (!nw.contentEquals(cf)) {
                cur.fill('\u0000'); nw.fill('\u0000'); cf.fill('\u0000')
                confLayout.error = getString(R.string.vault_password_mismatch)
                return@setOnClickListener
            }
            cf.fill('\u0000')
            lifecycleScope.launch {
                val ok = VaultRepository.changeMasterPassword(this@VaultActivity, cur, nw)
                if (ok) {
                    Toast.makeText(this@VaultActivity, R.string.vault_password_changed, Toast.LENGTH_SHORT).show()
                    d.dismiss()
                } else {
                    curLayout.error = getString(R.string.vault_wrong_password)
                }
            }
        }
    }

    private fun showDecoyPasswordDialog() {
        val density = resources.displayMetrics.density
        val pad = (18 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val layout = TextInputLayout(this).apply {
            hint = getString(R.string.vault_setting_decoy_password)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val input = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        layout.addView(input)
        container.addView(layout)

        val d = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_setting_decoy_password)
            .setMessage(R.string.vault_setting_decoy_summary)
            .setView(container)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        d.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        d.show()

        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val chars = input.text?.toString()?.toCharArray() ?: CharArray(0)
            input.text?.clear()
            if (chars.size < 4) {
                chars.fill('\u0000')
                layout.error = getString(R.string.vault_password_too_short)
                return@setOnClickListener
            }
            d.dismiss()
            showBusy(getString(R.string.vault_creating_decoy))
            lifecycleScope.launch {
                VaultRepository.setDecoyPassword(this@VaultActivity, chars)
                hideBusy()
                refreshSettingsState()
                Toast.makeText(this@VaultActivity, R.string.vault_setting_decoy_active, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmWipeVault() {
        val d = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_setting_wipe_all)
            .setMessage(R.string.vault_wipe_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                VaultStorage.wipeEntireVault(this)
                smoothLockAndFinish()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        d.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        d.show()
    }

    private fun showBusy(text: String) {
        uiState = uiState.copy(isBusy = true, busyLabel = text)
    }

    private fun hideBusy() {
        uiState = uiState.copy(isBusy = false, busyLabel = "")
    }

    private fun applyCloseTransition(enterAnim: Int, exitAnim: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, enterAnim, exitAnim)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(enterAnim, exitAnim)
        }
    }

    private fun handleBackAction() {
        when (uiState.stage) {
            VaultStage.PICKER -> closeInAppVideoPicker()
            VaultStage.SETTINGS -> closeVaultSettings()
            VaultStage.GALLERY -> {
                if (uiState.isSelectionMode) {
                    exitSelectionMode()
                } else {
                    smoothLockAndFinish()
                }
            }
            else -> smoothLockAndFinish()
        }
    }

    private fun smoothExitOnly() {
        if (panicExited) return
        smoothExitInFlight = true
        panicExited = true
        stopLockoutTicker()
        finish()
        applyCloseTransition(0, 0)
    }

    private fun smoothLockAndFinish() {
        if (panicExited) return
        smoothExitInFlight = true
        panicExited = true
        stopLockoutTicker()
        allItems = emptyList()
        VaultSessionManager.lockAndZeroize()
        if (setupOnlyMode) {
            finish()
        } else {
            finishAndRemoveTask()
        }
        applyCloseTransition(0, 0)
    }

    private fun panicLockAndFinish() {
        if (panicExited) return
        panicExited = true
        stopLockoutTicker()
        uiState = uiState.copy(isBlackout = true)
        allItems = emptyList()
        VaultSessionManager.lockAndZeroize()
        finishAndRemoveTask()
        applyCloseTransition(0, 0)
    }

    private fun shouldAllowPasswordManagerPause(): Boolean =
        !VaultSessionManager.isUnlocked.value && isPasswordManagerEnabled()

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!internalChildInFlight &&
            !externalOverlayInFlight &&
            !smoothExitInFlight &&
            !shouldAllowPasswordManagerPause()
        ) {
            panicLockAndFinish()
        }
    }

    override fun onPause() {
        val allowPmPause = shouldAllowPasswordManagerPause()
        if (!smoothExitInFlight && !allowPmPause) {
            uiState = uiState.copy(isBlackout = true)
        }
        super.onPause()
        if (!internalChildInFlight &&
            !externalOverlayInFlight &&
            !smoothExitInFlight &&
            !allowPmPause &&
            !panicExited
        ) {
            panicLockAndFinish()
        }
    }

    override fun onResume() {
        super.onResume()
        val returningFromChild = internalChildInFlight
        internalChildInFlight = false
        if (panicExited) return

        if ((returningFromChild || wasUnlockedInSession) && !VaultSessionManager.isUnlocked.value) {
            allItems = emptyList()
            finishAndRemoveTask()
            applyCloseTransition(0, 0)
            return
        }

        uiState = uiState.copy(isBlackout = false)
        if (VaultSessionManager.isUnlocked.value) {
            loadGallery()
        }
    }

    override fun onDestroy() {
        stopLockoutTicker()
        runCatching { unregisterReceiver(screenOffReceiver) }
        allItems = emptyList()
        if (!internalChildInFlight) {
            VaultSessionManager.lockAndZeroize()
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_EXTERNAL_OVERLAY_IN_FLIGHT, externalOverlayInFlight)
    }

    companion object {
        private const val EXTRA_SETUP_ONLY = "vault_setup_only"
        private const val KEY_EXTERNAL_OVERLAY_IN_FLIGHT = "vault_external_overlay_in_flight"

        fun createIntent(context: Context): Intent =
            Intent(context, VaultActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        fun createSetupIntent(context: Context): Intent =
            Intent(context, VaultActivity::class.java).apply {
                putExtra(EXTRA_SETUP_ONLY, true)
            }
    }
}
