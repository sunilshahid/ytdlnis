package com.deniscerri.ytdl.vault.ui

import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.common.MediaViewOptionsSheet
import com.deniscerri.ytdl.ui.play.device.DeviceMedia
import com.deniscerri.ytdl.ui.play.device.DeviceMediaRepository
import com.deniscerri.ytdl.ui.play.device.DevicePermissions
import com.deniscerri.ytdl.ui.play.device.DevicePlayStatus
import com.deniscerri.ytdl.ui.play.device.DeviceSort
import com.deniscerri.ytdl.ui.play.device.DeviceThumbnailLoader
import com.deniscerri.ytdl.ui.play.device.sortDeviceMedia
import com.deniscerri.ytdl.vault.VaultBiometrics
import com.deniscerri.ytdl.vault.VaultEnvelopeEngine
import com.deniscerri.ytdl.vault.VaultField
import com.deniscerri.ytdl.vault.VaultLayoutMode
import com.deniscerri.ytdl.vault.VaultPickerField
import com.deniscerri.ytdl.vault.VaultPickerSortBy
import com.deniscerri.ytdl.vault.VaultRepository
import com.deniscerri.ytdl.vault.VaultSessionManager
import com.deniscerri.ytdl.vault.VaultSettings
import com.deniscerri.ytdl.vault.VaultSortBy
import com.deniscerri.ytdl.vault.VaultStorage
import com.deniscerri.ytdl.vault.VaultUiItem
import com.deniscerri.ytdl.vault.ingest.VaultImporter
import com.deniscerri.ytdl.vault.sortVaultItems
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Private Vault main container handling Setup, Unlock (Password + Hardware Biometric CryptoObject
 * + Decoy + Exponential Backoff + Auto-Wipe), Gallery (Grid/List + in-RAM search + Play + Rename +
 * Export + Delete + SAF Cut & Move), and Vault Settings.
 */
class VaultActivity : AppCompatActivity() {

    private lateinit var setupContainer: View
    private lateinit var unlockContainer: View
    private lateinit var galleryContainer: View
    private lateinit var settingsContainer: View
    private lateinit var pickerContainer: View
    private lateinit var progressOverlay: View
    private lateinit var progressLabel: TextView
    private lateinit var blackoutVeil: View

    private lateinit var pickerTitle: TextView
    private lateinit var pickerViewOptionsBtn: ImageButton
    private lateinit var pickerSearchInput: EditText
    private lateinit var pickerRecycler: RecyclerView
    private lateinit var pickerLoading: View
    private lateinit var pickerEmptyView: View
    private lateinit var pickerEmptyText: TextView
    private lateinit var pickerGrantBtn: MaterialButton
    private lateinit var pickerImportBtn: MaterialButton
    private lateinit var pickerAdapter: VaultPickerAdapter
    private lateinit var pickerRepo: DeviceMediaRepository
    private lateinit var pickerThumbnails: DeviceThumbnailLoader
    private var allPickerVideos: List<DeviceMedia> = emptyList()
    private var visiblePickerVideos: List<DeviceMedia> = emptyList()
    private val selectedPickerIds = linkedSetOf<String>()

    private lateinit var settingsBioSwitch: MaterialSwitch
    private lateinit var settingsPmSwitch: MaterialSwitch
    private lateinit var settingsAutoWipeBtn: MaterialButton
    private lateinit var settingsChangePwBtn: MaterialButton
    private lateinit var settingsStealthSwitch: MaterialSwitch
    private lateinit var settingsStealthPinContainer: View
    private lateinit var settingsStealthStatus: TextView
    private lateinit var settingsStealthPinLayout: TextInputLayout
    private lateinit var settingsStealthPinInput: TextInputEditText
    private lateinit var settingsStealthSaveBtn: MaterialButton
    private lateinit var settingsDecoyBtn: MaterialButton
    private lateinit var settingsClearDecoyBtn: MaterialButton
    private lateinit var settingsKeepOpenSwitch: MaterialSwitch
    private lateinit var settingsWipeBtn: MaterialButton
    private var suppressSettingsListeners = false

    private lateinit var setupPasswordInput: TextInputEditText
    private lateinit var setupConfirmInput: TextInputEditText
    private lateinit var setupErrorText: TextView

    private lateinit var unlockPasswordInput: TextInputEditText
    private lateinit var unlockErrorText: TextView
    private lateinit var unlockButton: MaterialButton
    private lateinit var unlockBioButton: MaterialButton

    private lateinit var normalHeader: View
    private lateinit var selectionHeader: View
    private lateinit var selectionCountText: TextView
    private lateinit var headerTitle: TextView
    private lateinit var viewToggleBtn: ImageButton
    private lateinit var settingsBtn: ImageButton
    private lateinit var addFab: FloatingActionButton
    private lateinit var searchInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View

    private lateinit var adapter: VaultAdapter
    private var allItems: List<VaultUiItem> = emptyList()
    private var visibleItems: List<VaultUiItem> = emptyList()
    private val selectedIds = linkedSetOf<String>()
    private var isSelectionMode = false
    private var isGridMode = true
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
        if (VaultSessionManager.isUnlocked.value &&
            ::pickerContainer.isInitialized &&
            pickerContainer.visibility == View.VISIBLE
        ) {
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
        setContentView(R.layout.activity_vault)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.vault_enter, R.anim.vault_hold)

        setupOnlyMode = intent.getBooleanExtra(EXTRA_SETUP_ONLY, false)

        setupContainer = findViewById(R.id.vault_setup_container)
        unlockContainer = findViewById(R.id.vault_unlock_container)
        galleryContainer = findViewById(R.id.vault_gallery_container)
        settingsContainer = findViewById(R.id.vault_settings_container)
        pickerContainer = findViewById(R.id.vault_picker_container)
        progressOverlay = findViewById(R.id.vault_progress_overlay)
        progressLabel = findViewById(R.id.vault_progress_label)
        blackoutVeil = findViewById(R.id.vault_blackout_veil)

        pickerTitle = findViewById(R.id.vault_picker_title)
        pickerViewOptionsBtn = findViewById(R.id.vault_picker_view_options_button)
        pickerSearchInput = findViewById(R.id.vault_picker_search_input)
        pickerRecycler = findViewById(R.id.vault_picker_recycler)
        pickerLoading = findViewById(R.id.vault_picker_loading)
        pickerEmptyView = findViewById(R.id.vault_picker_empty_view)
        pickerEmptyText = findViewById(R.id.vault_picker_empty_text)
        pickerGrantBtn = findViewById(R.id.vault_picker_grant_permission_btn)
        pickerImportBtn = findViewById(R.id.vault_picker_import_button)
        pickerRepo = DeviceMediaRepository(this)
        pickerThumbnails = DeviceThumbnailLoader(this)
        pickerAdapter = VaultPickerAdapter(pickerThumbnails) { media ->
            togglePickerSelection(media)
        }
        pickerRecycler.layoutManager = GridLayoutManager(this, 2)
        pickerRecycler.adapter = pickerAdapter

        settingsBioSwitch = findViewById(R.id.vault_setting_bio_switch)
        settingsPmSwitch = findViewById(R.id.vault_setting_pm_switch)
        settingsAutoWipeBtn = findViewById(R.id.vault_setting_auto_wipe_btn)
        settingsChangePwBtn = findViewById(R.id.vault_setting_change_pw_btn)
        settingsStealthSwitch = findViewById(R.id.vault_setting_stealth_switch)
        settingsStealthPinContainer = findViewById(R.id.vault_setting_stealth_pin_container)
        settingsStealthStatus = findViewById(R.id.vault_setting_stealth_status)
        settingsStealthPinLayout = findViewById(R.id.vault_setting_stealth_pin_layout)
        settingsStealthPinInput = findViewById(R.id.vault_setting_stealth_pin_input)
        settingsStealthSaveBtn = findViewById(R.id.vault_setting_stealth_save_btn)
        settingsDecoyBtn = findViewById(R.id.vault_setting_decoy_btn)
        settingsClearDecoyBtn = findViewById(R.id.vault_setting_clear_decoy_btn)
        settingsKeepOpenSwitch = findViewById(R.id.vault_setting_keep_open_switch)
        settingsWipeBtn = findViewById(R.id.vault_setting_wipe_btn)

        setupPasswordInput = findViewById(R.id.setup_password_input)
        setupConfirmInput = findViewById(R.id.setup_confirm_input)
        setupErrorText = findViewById(R.id.setup_error_text)

        unlockPasswordInput = findViewById(R.id.unlock_password_input)
        unlockErrorText = findViewById(R.id.unlock_error_text)
        unlockButton = findViewById(R.id.unlock_button)
        unlockBioButton = findViewById(R.id.unlock_biometric_button)

        normalHeader = findViewById(R.id.vault_normal_header)
        selectionHeader = findViewById(R.id.vault_selection_header)
        selectionCountText = findViewById(R.id.vault_sel_count)
        headerTitle = findViewById(R.id.vault_header_title)
        viewToggleBtn = findViewById(R.id.vault_view_toggle_button)
        settingsBtn = findViewById(R.id.vault_settings_button)
        addFab = findViewById(R.id.vault_add_fab)
        searchInput = findViewById(R.id.vault_search_input)
        recyclerView = findViewById(R.id.vault_recycler)
        emptyView = findViewById(R.id.vault_empty_view)

        enforceNoLearningIme(setupPasswordInput)
        enforceNoLearningIme(setupConfirmInput)
        enforceNoLearningIme(unlockPasswordInput)
        enforceNoLearningIme(searchInput)
        enforceNoLearningIme(pickerSearchInput)
        enforceNoLearningIme(settingsStealthPinInput)
        applyAutofillPolicy(isPasswordManagerEnabled())

        adapter = VaultAdapter(
            onItemClick = { item ->
                if (isSelectionMode) {
                    toggleItemSelection(item)
                } else {
                    openVaultPlayer(item)
                }
            },
            onItemLongClick = { item ->
                enterSelectionModeWith(item)
            },
            onMoreClick = { anchor, item -> showItemMenu(anchor, item) }
        )
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter

        findViewById<MaterialButton>(R.id.setup_create_button).setOnClickListener { handleCreateVault() }
        unlockButton.setOnClickListener { handlePasswordUnlock() }
        unlockPasswordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handlePasswordUnlock()
                true
            } else {
                false
            }
        }
        setupConfirmInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleCreateVault()
                true
            } else {
                false
            }
        }

        findViewById<ImageButton>(R.id.vault_back_button).setOnClickListener { handleBackAction() }
        findViewById<ImageButton>(R.id.vault_lock_button).setOnClickListener { smoothLockAndFinish() }
        findViewById<ImageButton>(R.id.vault_sel_close_button).setOnClickListener { exitSelectionMode() }
        findViewById<ImageButton>(R.id.vault_sel_select_all_button).setOnClickListener { toggleSelectAll() }
        findViewById<ImageButton>(R.id.vault_sel_delete_button).setOnClickListener { confirmDeleteSelected() }
        findViewById<ImageButton>(R.id.vault_sel_more_button).setOnClickListener { showSelectionMoreMenu(it) }
        viewToggleBtn.setOnClickListener { openVaultViewOptions() }
        supportFragmentManager.setFragmentResultListener(
            MediaViewOptionsSheet.REQUEST_VAULT,
            this
        ) { _, bundle ->
            onVaultViewOptionsResult(bundle)
        }
        settingsBtn.setOnClickListener { openVaultSettings() }
        findViewById<ImageButton>(R.id.vault_settings_back_button).setOnClickListener { closeVaultSettings() }
        findViewById<ImageButton>(R.id.vault_settings_lock_button).setOnClickListener { smoothLockAndFinish() }
        addFab.setOnClickListener { openInAppVideoPicker() }

        findViewById<ImageButton>(R.id.vault_picker_back_button).setOnClickListener { closeInAppVideoPicker() }
        findViewById<ImageButton>(R.id.vault_picker_select_all_button).setOnClickListener { togglePickerSelectAll() }
        pickerViewOptionsBtn.setOnClickListener { openVaultPickerViewOptions() }
        findViewById<ImageButton>(R.id.vault_picker_browse_system_button).setOnClickListener { launchExternalDocumentPicker() }
        supportFragmentManager.setFragmentResultListener(
            MediaViewOptionsSheet.REQUEST_VAULT_PICKER,
            this
        ) { _, bundle ->
            onVaultPickerViewOptionsResult(bundle)
        }
        pickerGrantBtn.setOnClickListener {
            externalOverlayInFlight = true
            pickerPermissionLauncher.launch(DevicePermissions.required())
        }
        pickerImportBtn.setOnClickListener { importSelectedPickerVideos() }
        pickerSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filterPickerVideos(s?.toString().orEmpty())
            }
        })

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filterGallery(s?.toString().orEmpty())
            }
        })

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackAction()
                }
            }
        )

        bindVaultSettingsControls()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        renderCurrentState(autoTriggerBiometrics = true)
    }

    private fun enforceNoLearningIme(editText: EditText) {
        editText.imeOptions = editText.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    }

    private fun isPasswordManagerEnabled(): Boolean =
        VaultRepository.loadEnvelopeOrNull(this)?.passwordManagerEnabled == true

    private fun applyAutofillPolicy(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mode = if (enabled) View.IMPORTANT_FOR_AUTOFILL_YES else View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (::setupContainer.isInitialized) setupContainer.importantForAutofill = mode
        if (::unlockContainer.isInitialized) unlockContainer.importantForAutofill = mode
        if (::unlockPasswordInput.isInitialized) {
            unlockPasswordInput.importantForAutofill =
                if (enabled) View.IMPORTANT_FOR_AUTOFILL_YES else View.IMPORTANT_FOR_AUTOFILL_NO
            if (enabled) {
                unlockPasswordInput.setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            } else {
                unlockPasswordInput.setAutofillHints(*emptyArray())
            }
        }
        if (::setupPasswordInput.isInitialized && ::setupConfirmInput.isInitialized) {
            val fieldMode = if (enabled) View.IMPORTANT_FOR_AUTOFILL_YES else View.IMPORTANT_FOR_AUTOFILL_NO
            setupPasswordInput.importantForAutofill = fieldMode
            setupConfirmInput.importantForAutofill = fieldMode
            if (enabled) {
                setupPasswordInput.setAutofillHints("newPassword")
                setupConfirmInput.setAutofillHints("newPassword")
            } else {
                setupPasswordInput.setAutofillHints(*emptyArray())
                setupConfirmInput.setAutofillHints(*emptyArray())
            }
        }
    }

    private fun animateSectionIn(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = 14f * resources.displayMetrics.density
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun animateSectionSlideInFromRight(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationX = 32f * resources.displayMetrics.density
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(230L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun animateSectionSlideOutToRight(view: View) {
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationX(32f * resources.displayMetrics.density)
            .setDuration(190L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
                view.translationX = 0f
            }
            .start()
    }

    private fun animateShake(view: View) {
        ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, -16f, 16f, -12f, 12f, -6f, 6f, 0f).apply {
            duration = 320L
            start()
        }
    }

    private fun renderCurrentState(autoTriggerBiometrics: Boolean = false) {
        stopLockoutTicker()
        applyAutofillPolicy(isPasswordManagerEnabled())
        when {
            !VaultRepository.isInitialized(this) -> {
                if (setupContainer.visibility != View.VISIBLE) animateSectionIn(setupContainer)
                unlockContainer.visibility = View.GONE
                galleryContainer.visibility = View.GONE
                settingsContainer.visibility = View.GONE
                pickerContainer.visibility = View.GONE
                setupErrorText.visibility = View.GONE
            }
            !VaultSessionManager.isUnlocked.value -> {
                setupContainer.visibility = View.GONE
                if (unlockContainer.visibility != View.VISIBLE) animateSectionIn(unlockContainer)
                galleryContainer.visibility = View.GONE
                settingsContainer.visibility = View.GONE
                pickerContainer.visibility = View.GONE
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
                setupContainer.visibility = View.GONE
                unlockContainer.visibility = View.GONE
                if (galleryContainer.visibility != View.VISIBLE) {
                    adapter.resetEntranceAnimations()
                    animateSectionIn(galleryContainer)
                }
                loadGallery()
            }
        }
    }

    private fun handleCreateVault() {
        val pw = VaultUiHelpers.readCharsAndClear(setupPasswordInput)
        val confirm = VaultUiHelpers.readCharsAndClear(setupConfirmInput)
        if (pw.size < 4) {
            pw.fill('\u0000')
            confirm.fill('\u0000')
            setupErrorText.setText(R.string.vault_password_too_short)
            setupErrorText.visibility = View.VISIBLE
            return
        }
        if (!pw.contentEquals(confirm)) {
            pw.fill('\u0000')
            confirm.fill('\u0000')
            setupErrorText.setText(R.string.vault_password_mismatch)
            setupErrorText.visibility = View.VISIBLE
            return
        }
        confirm.fill('\u0000')
        setupErrorText.visibility = View.GONE
        showBusy(getString(R.string.vault_create_button))
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
                setupErrorText.text = err.message ?: "Error"
                setupErrorText.visibility = View.VISIBLE
            }
        }
    }

    private fun configureUnlockScreen(autoTriggerBiometrics: Boolean) {
        var env = VaultRepository.loadEnvelopeOrNull(this) ?: return
        applyAutofillPolicy(env.passwordManagerEnabled)
        unlockErrorText.visibility = View.GONE

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
        if (env.lockoutUntilMs > now) {
            startLockoutTicker(env.lockoutUntilMs)
        } else {
            unlockButton.isEnabled = true
            if (invalidatedNow) {
                unlockErrorText.setText(R.string.vault_biometric_invalidated)
                unlockErrorText.visibility = View.VISIBLE
            }
        }

        val canBio = !invalidatedNow &&
            env.biometricEnabled &&
            !env.biometricWrappedDekIvB64.isNullOrBlank() &&
            !env.biometricWrappedDekCtB64.isNullOrBlank() &&
            VaultBiometrics.isSupported(this)

        unlockBioButton.visibility = if (canBio) View.VISIBLE else View.GONE
        if (canBio) {
            unlockBioButton.setOnClickListener { triggerBiometricUnlock() }
            if (autoTriggerBiometrics && env.lockoutUntilMs <= now) {
                triggerBiometricUnlock()
            }
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
                unlockBioButton.visibility = View.GONE
                unlockErrorText.setText(R.string.vault_biometric_invalidated)
                unlockErrorText.visibility = View.VISIBLE
                animateShake(unlockErrorText)
            },
            onError = { msg ->
                externalOverlayInFlight = false
                if (msg.contains("invalidat", ignoreCase = true) ||
                    msg.contains("permanently", ignoreCase = true)
                ) {
                    VaultRepository.clearBiometricEnrollment(this, dueToChange = true)
                    unlockBioButton.visibility = View.GONE
                    unlockErrorText.setText(R.string.vault_biometric_invalidated)
                    unlockErrorText.visibility = View.VISIBLE
                }
            }
        )
    }

    private fun handlePasswordUnlock() {
        val chars = VaultUiHelpers.readCharsAndClear(unlockPasswordInput)
        if (chars.isEmpty()) return
        unlockErrorText.visibility = View.GONE
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
                            unlockErrorText.text = res.remainingBeforeWipe?.let {
                                getString(R.string.vault_wrong_password_remaining, it)
                            } ?: getString(R.string.vault_wrong_password)
                            unlockErrorText.visibility = View.VISIBLE
                            animateShake(unlockPasswordInput)
                            if (res.backoffMs > 0L) {
                                startLockoutTicker(res.updatedEnvelope.lockoutUntilMs)
                            }
                        }
                    }
                }
            }.onFailure { err ->
                unlockErrorText.text = err.message ?: getString(R.string.vault_wrong_password)
                unlockErrorText.visibility = View.VISIBLE
            }
        }
    }

    private fun startLockoutTicker(untilMs: Long) {
        stopLockoutTicker()
        unlockButton.isEnabled = false
        val runnable = object : Runnable {
            override fun run() {
                val remMs = untilMs - System.currentTimeMillis()
                if (remMs <= 0L) {
                    unlockButton.isEnabled = true
                    unlockErrorText.visibility = View.GONE
                    lockoutRunnable = null
                } else {
                    val secs = ((remMs + 999L) / 1000L).toInt()
                    unlockErrorText.text = getString(R.string.vault_locked_out, secs)
                    unlockErrorText.visibility = View.VISIBLE
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
        pickerSearchInput.setText("")
        applyVaultPickerViewSettings(vaultViewSettings)
        syncPickerSelectionUi()
        animateSectionSlideInFromRight(pickerContainer)
        loadPickerVideos()
    }

    private fun closeInAppVideoPicker() {
        if (!::pickerContainer.isInitialized || pickerContainer.visibility != View.VISIBLE) return
        hideKeyboard(pickerSearchInput)
        selectedPickerIds.clear()
        pickerAdapter.updateSelection(emptySet())
        animateSectionSlideOutToRight(pickerContainer)
    }

    private fun loadPickerVideos() {
        if (!VaultSessionManager.isUnlocked.value) return
        if (!DevicePermissions.hasVideo(this)) {
            allPickerVideos = emptyList()
            visiblePickerVideos = emptyList()
            pickerAdapter.submitList(emptyList())
            pickerLoading.visibility = View.GONE
            pickerEmptyText.setText(R.string.vault_picker_permission_title)
            pickerGrantBtn.visibility = View.VISIBLE
            pickerEmptyView.visibility = View.VISIBLE
            syncPickerSelectionUi()
            return
        }
        pickerGrantBtn.visibility = View.GONE
        pickerEmptyView.visibility = View.GONE
        pickerLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val loaded = runCatching {
                pickerRepo.load(folderPath = null, includeAudio = false, includeVideo = true)
            }.getOrDefault(emptyList())
            if (!VaultSessionManager.isUnlocked.value) return@launch
            pickerLoading.visibility = View.GONE
            allPickerVideos = loaded
            val validIds = loaded.mapTo(hashSetOf()) { it.mediaId }
            selectedPickerIds.retainAll(validIds)
            filterPickerVideos(pickerSearchInput.text?.toString().orEmpty())
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
        pickerAdapter.submitList(filtered)
        if (!DevicePermissions.hasVideo(this)) {
            pickerEmptyText.setText(R.string.vault_picker_permission_title)
            pickerGrantBtn.visibility = View.VISIBLE
            pickerEmptyView.visibility = View.VISIBLE
        } else {
            pickerGrantBtn.visibility = View.GONE
            pickerEmptyText.setText(R.string.vault_picker_empty)
            pickerEmptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }
        syncPickerSelectionUi()
    }

    private fun togglePickerSelection(media: DeviceMedia) {
        if (!selectedPickerIds.add(media.mediaId)) {
            selectedPickerIds.remove(media.mediaId)
        }
        syncPickerSelectionUi()
    }

    private fun togglePickerSelectAll() {
        val visibleIds = visiblePickerVideos.map { it.mediaId }
        if (visibleIds.isEmpty()) return
        if (visibleIds.all { it in selectedPickerIds }) {
            selectedPickerIds.removeAll(visibleIds.toSet())
        } else {
            selectedPickerIds.addAll(visibleIds)
        }
        syncPickerSelectionUi()
    }

    private fun syncPickerSelectionUi() {
        val count = selectedPickerIds.size
        pickerTitle.text = if (count > 0) {
            getString(R.string.vault_selected_count, count)
        } else {
            getString(R.string.vault_picker_title)
        }
        pickerImportBtn.visibility = if (count > 0) View.VISIBLE else View.GONE
        if (count > 0) {
            pickerImportBtn.text = getString(R.string.vault_picker_import_action, count)
        }
        pickerAdapter.updateSelection(selectedPickerIds.toSet())
    }

    private fun importSelectedPickerVideos() {
        if (!VaultSessionManager.isUnlocked.value) return
        val selected = allPickerVideos.filter { it.mediaId in selectedPickerIds }
        if (selected.isEmpty()) return
        val requests = selected.map { media ->
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
                playbackMediaId = media.mediaId
            )
        }
        showBatchImportDisclosureAndMove(requests, closePickerOnComplete = true)
    }

    private fun launchExternalDocumentPicker() {
        if (!VaultSessionManager.isUnlocked.value) return
        externalOverlayInFlight = true
        runCatching {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }.recoverCatching {
            pickVideoFallbackLauncher.launch("video/*")
        }.onFailure { err ->
            externalOverlayInFlight = false
            Toast.makeText(this, err.message ?: "Unable to open video picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePickedExternalUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            val requests = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    val name = VaultImporter.queryUriDisplayName(this@VaultActivity, uri)
                        ?: "Selected Video"
                    VaultImporter.MoveSourceRequest(
                        title = "",
                        contentUri = uri,
                        originalFileName = name
                    )
                }
            }
            if (isFinishing || isDestroyed || !VaultSessionManager.isUnlocked.value) return@launch
            if (requests.isEmpty()) {
                Toast.makeText(
                    this@VaultActivity,
                    R.string.vault_picker_unreadable_selection,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            showBatchImportDisclosureAndMove(
                requests = requests,
                closePickerOnComplete = ::pickerContainer.isInitialized &&
                    pickerContainer.visibility == View.VISIBLE
            )
        }
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
        normalHeader.visibility = if (active) View.GONE else View.VISIBLE
        selectionHeader.visibility = if (active) View.VISIBLE else View.GONE
        addFab.visibility = if (active) View.GONE else View.VISIBLE
        if (active) {
            selectionCountText.text = getString(R.string.vault_selected_count, selectedIds.size)
        }
        adapter.updateSelection(selectedIds.toSet(), active)
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
            applyVaultViewSettings(loadedSettings, animate = false)
            applyVaultPickerViewSettings(loadedSettings)
            allItems = loaded
            val validIds = loaded.mapTo(hashSetOf()) { it.meta.id }
            selectedIds.retainAll(validIds)
            if (isSelectionMode && selectedIds.isEmpty()) {
                isSelectionMode = false
            }
            filterGallery(searchInput.text?.toString().orEmpty())
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
        adapter.submitList(filtered)
        emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
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
        applyVaultViewSettings(updated, animate = true)
        filterGallery(searchInput.text?.toString().orEmpty())

        if (!VaultSessionManager.isDecoySession) {
            lifecycleScope.launch(Dispatchers.IO) {
                VaultRepository.updateViewSettings(this@VaultActivity, updated)
            }
        }
    }

    private fun applyVaultViewSettings(settings: VaultSettings, animate: Boolean) {
        val targetGrid = settings.layoutMode == VaultLayoutMode.GRID
        val fieldsChanged = adapter.visibleFields != settings.visibleFields
        adapter.visibleFields = settings.visibleFields

        if (isGridMode != targetGrid) {
            isGridMode = targetGrid
            val applyLayout = {
                adapter.isGridMode = isGridMode
                recyclerView.layoutManager = if (isGridMode) {
                    GridLayoutManager(this, 2)
                } else {
                    LinearLayoutManager(this)
                }
                viewToggleBtn.setImageResource(
                    if (isGridMode) R.drawable.ic_view_grid else R.drawable.baseline_format_list_numbered_24
                )
            }
            if (animate) {
                viewToggleBtn.animate().rotationBy(180f).setDuration(220L).start()
                recyclerView.animate()
                    .alpha(0f)
                    .setDuration(110L)
                    .withEndAction {
                        applyLayout()
                        recyclerView.animate().alpha(1f).setDuration(180L).start()
                    }
                    .start()
            } else {
                applyLayout()
            }
        } else if (fieldsChanged) {
            adapter.notifyDataSetChanged()
        }
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

    private fun onVaultPickerViewOptionsResult(bundle: Bundle) {
        val layout = runCatching {
            VaultLayoutMode.valueOf(bundle.getString(MediaViewOptionsSheet.RESULT_PICKER_LAYOUT).orEmpty())
        }.getOrDefault(VaultLayoutMode.GRID)
        val sort = runCatching {
            VaultPickerSortBy.valueOf(bundle.getString(MediaViewOptionsSheet.RESULT_PICKER_SORT).orEmpty())
        }.getOrDefault(VaultPickerSortBy.DATE)
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
        applyVaultPickerViewSettings(updated)
        filterPickerVideos(pickerSearchInput.text?.toString().orEmpty())

        if (!VaultSessionManager.isDecoySession) {
            lifecycleScope.launch(Dispatchers.IO) {
                VaultRepository.updateViewSettings(this@VaultActivity, updated)
            }
        }
    }

    private fun applyVaultPickerViewSettings(settings: VaultSettings) {
        val targetGrid = settings.pickerLayoutMode == VaultLayoutMode.GRID
        val layoutChanged = pickerAdapter.isGridMode != targetGrid
        val fieldsChanged = pickerAdapter.visibleFields != settings.pickerVisibleFields
        pickerAdapter.isGridMode = targetGrid
        pickerAdapter.visibleFields = settings.pickerVisibleFields
        pickerViewOptionsBtn.setImageResource(
            if (targetGrid) R.drawable.ic_view_grid else R.drawable.baseline_format_list_numbered_24
        )
        if (layoutChanged) {
            pickerRecycler.layoutManager = if (targetGrid) {
                GridLayoutManager(this, 2)
            } else {
                LinearLayoutManager(this)
            }
            pickerRecycler.adapter = pickerAdapter
        } else if (fieldsChanged) {
            pickerAdapter.notifyDataSetChanged()
        }
    }

    private fun openVaultPlayer(item: VaultUiItem) {
        if (!VaultSessionManager.isUnlocked.value) return
        internalChildInFlight = true
        startActivity(VaultVideoPlayerActivity.createIntent(this, item.meta.id, item.meta.title))
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.vault_enter, R.anim.vault_hold)
    }

    private fun showItemMenu(anchor: View, item: VaultUiItem) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.play)
        popup.menu.add(0, 2, 1, R.string.vault_rename)
        popup.menu.add(0, 3, 2, R.string.vault_decrypt)
        popup.menu.add(0, 4, 3, R.string.vault_decrypt_copy)
        popup.menu.add(0, 5, 4, R.string.delete)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> openVaultPlayer(item)
                2 -> showRenameDialog(item)
                3 -> runRestore(listOf(item.meta.id), deleteFromVaultAfter = true)
                4 -> runRestore(listOf(item.meta.id), deleteFromVaultAfter = false)
                5 -> showDeleteConfirmDialog(listOf(item))
            }
            true
        }
        popup.show()
    }

    private fun showSelectionMoreMenu(anchor: View) {
        val uuids = selectedIds.toList()
        if (uuids.isEmpty()) return
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.vault_decrypt)
        popup.menu.add(0, 2, 1, R.string.vault_decrypt_copy)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> runRestore(uuids, deleteFromVaultAfter = true)
                2 -> runRestore(uuids, deleteFromVaultAfter = false)
            }
            true
        }
        popup.show()
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
                            runOnUiThread {
                                progressLabel.text =
                                    "${getString(R.string.vault_exporting_progress)} ($prefix$pct%)"
                            }
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
                progressLabel.text = "$baseLabel (${prefix}0%)"
                val result = runCatching {
                    VaultImporter.moveVideoToVault(
                        context = this@VaultActivity,
                        request = req,
                        onProgress = { written, total ->
                            if (total > 0L) {
                                val pct = ((written * 100L) / total).toInt().coerceIn(0, 100)
                                runOnUiThread {
                                    progressLabel.text = "$baseLabel ($prefix$pct%)"
                                }
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
                batchFailed > 0 ->
                    getString(R.string.vault_import_batch_partial, totalImported, batchFailed)
                batchNeedsManualDelete > 0 ->
                    getString(R.string.vault_import_batch_needs_delete, totalImported, batchNeedsManualDelete)
                else ->
                    getString(R.string.vault_import_batch_success, totalImported)
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

    private val autoWipeOptions = intArrayOf(0, 5, 10, 20)
    private var activeAutoWipeThreshold = VaultSettings.DEFAULT_AUTO_WIPE_THRESHOLD

    private fun bindVaultSettingsControls() {
        settingsBioSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressSettingsListeners) return@setOnCheckedChangeListener
            if (!checked) {
                VaultRepository.clearBiometricEnrollment(this)
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
                            }.onFailure {
                                runOnUiThread {
                                    suppressSettingsListeners = true
                                    settingsBioSwitch.isChecked = false
                                    suppressSettingsListeners = false
                                }
                            }
                        }
                    )
                }
            }
        }

        settingsPmSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressSettingsListeners) return@setOnCheckedChangeListener
            persistQuickSettings(passwordManagerEnabled = checked)
            applyAutofillPolicy(checked)
        }

        settingsKeepOpenSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressSettingsListeners) return@setOnCheckedChangeListener
            persistQuickSettings(keepOpenOnPlayerBack = checked)
        }

        settingsAutoWipeBtn.setOnClickListener {
            val idx = autoWipeOptions.indexOf(activeAutoWipeThreshold).let { if (it < 0) 2 else it }
            activeAutoWipeThreshold = autoWipeOptions[(idx + 1) % autoWipeOptions.size]
            settingsAutoWipeBtn.text = formatAutoWipeLabel(activeAutoWipeThreshold)
            persistQuickSettings(autoWipeThreshold = activeAutoWipeThreshold)
        }

        settingsChangePwBtn.setOnClickListener { showChangePasswordDialog() }

        settingsStealthSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressSettingsListeners) return@setOnCheckedChangeListener
            settingsStealthPinContainer.visibility = if (checked) View.VISIBLE else View.GONE
            settingsStealthPinLayout.error = null
            val env = VaultRepository.loadEnvelopeOrNull(this)
            if (!checked) {
                persistQuickSettings(stealthMode = false)
                settingsStealthStatus.setText(R.string.vault_stealth_pin_not_set)
            } else if (env?.hasStealthPin() == true) {
                persistQuickSettings(stealthMode = true)
                settingsStealthStatus.setText(R.string.vault_stealth_pin_active)
            } else {
                settingsStealthStatus.setText(R.string.vault_stealth_pin_not_set)
                settingsStealthPinInput.requestFocus()
            }
        }

        settingsStealthSaveBtn.setOnClickListener {
            val pinChars = VaultUiHelpers.readCharsAndClear(settingsStealthPinInput)
            if (pinChars.size < 4) {
                pinChars.fill('\u0000')
                settingsStealthPinLayout.error = getString(R.string.vault_password_too_short)
                animateShake(settingsStealthPinLayout)
                return@setOnClickListener
            }
            settingsStealthPinLayout.error = null
            val cur = VaultRepository.loadSettings(this)
            VaultRepository.updateSettings(
                context = this,
                newSettings = cur.copy(stealthMode = true),
                newStealthPinChars = pinChars
            )
            suppressSettingsListeners = true
            settingsStealthSwitch.isChecked = true
            suppressSettingsListeners = false
            settingsStealthStatus.setText(R.string.vault_stealth_pin_active)
            hideKeyboard(settingsStealthPinInput)
            Toast.makeText(this, R.string.vault_stealth_pin_active, Toast.LENGTH_SHORT).show()
        }

        settingsStealthPinInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                settingsStealthSaveBtn.performClick()
                true
            } else {
                false
            }
        }

        settingsDecoyBtn.setOnClickListener { showDecoyPasswordDialog() }
        settingsClearDecoyBtn.setOnClickListener {
            VaultRepository.clearDecoyPassword(this)
            settingsClearDecoyBtn.visibility = View.GONE
        }
        settingsWipeBtn.setOnClickListener { confirmWipeVault() }
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
        // Only disable stealth or keep existing PIN if stealth PIN already exists in envelope
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

        val currentSettings = VaultRepository.loadSettings(this)
        val env = VaultRepository.loadEnvelopeOrNull(this) ?: return

        suppressSettingsListeners = true
        settingsBioSwitch.isEnabled = VaultBiometrics.isSupported(this)
        settingsBioSwitch.isChecked = env.biometricEnabled
        settingsPmSwitch.isChecked = currentSettings.passwordManagerEnabled || env.passwordManagerEnabled
        settingsKeepOpenSwitch.isChecked = currentSettings.keepOpenOnPlayerBack
        activeAutoWipeThreshold = currentSettings.autoWipeThreshold
        settingsAutoWipeBtn.text = formatAutoWipeLabel(activeAutoWipeThreshold)

        settingsStealthSwitch.isChecked = env.stealthEnabled
        settingsStealthPinContainer.visibility = if (env.stealthEnabled) View.VISIBLE else View.GONE
        settingsStealthPinLayout.error = null
        settingsStealthPinInput.setText("")
        settingsStealthStatus.setText(
            if (env.hasStealthPin()) R.string.vault_stealth_pin_active else R.string.vault_stealth_pin_not_set
        )
        settingsClearDecoyBtn.visibility = if (env.hasDecoy()) View.VISIBLE else View.GONE
        suppressSettingsListeners = false

        animateSectionSlideInFromRight(settingsContainer)
    }

    private fun closeVaultSettings() {
        if (settingsContainer.visibility != View.VISIBLE) return
        hideKeyboard(settingsStealthPinInput)
        val env = VaultRepository.loadEnvelopeOrNull(this)
        if (settingsStealthSwitch.isChecked && env?.hasStealthPin() != true) {
            suppressSettingsListeners = true
            settingsStealthSwitch.isChecked = false
            suppressSettingsListeners = false
            settingsStealthPinContainer.visibility = View.GONE
        }
        animateSectionSlideOutToRight(settingsContainer)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun formatAutoWipeLabel(threshold: Int): String =
        if (threshold <= 0) {
            "${getString(R.string.vault_setting_auto_wipe)}: ${getString(R.string.vault_auto_wipe_off)}"
        } else {
            "${getString(R.string.vault_setting_auto_wipe)}: ${getString(R.string.vault_auto_wipe_attempts, threshold)}"
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
                        View.IMPORTANT_FOR_AUTOFILL_YES
                    } else {
                        View.IMPORTANT_FOR_AUTOFILL_NO
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

        val curHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.AUTOFILL_HINT_PASSWORD else "password"
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
            val cur = VaultUiHelpers.readCharsAndClear(curInput)
            val nw = VaultUiHelpers.readCharsAndClear(newInput)
            val cf = VaultUiHelpers.readCharsAndClear(confInput)
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
            val chars = VaultUiHelpers.readCharsAndClear(input)
            if (chars.size < 4) {
                chars.fill('\u0000')
                layout.error = getString(R.string.vault_password_too_short)
                return@setOnClickListener
            }
            lifecycleScope.launch {
                VaultRepository.setDecoyPassword(this@VaultActivity, chars)
                settingsClearDecoyBtn.visibility = View.VISIBLE
                d.dismiss()
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
        progressLabel.text = text
        progressOverlay.visibility = View.VISIBLE
    }

    private fun hideBusy() {
        progressOverlay.visibility = View.GONE
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
        when {
            ::pickerContainer.isInitialized && pickerContainer.visibility == View.VISIBLE ->
                closeInAppVideoPicker()
            ::settingsContainer.isInitialized && settingsContainer.visibility == View.VISIBLE ->
                closeVaultSettings()
            isSelectionMode ->
                exitSelectionMode()
            else ->
                smoothLockAndFinish()
        }
    }

    private fun smoothExitOnly() {
        if (panicExited) return
        smoothExitInFlight = true
        panicExited = true
        stopLockoutTicker()
        finish()
        applyCloseTransition(R.anim.vault_background_enter, R.anim.vault_exit)
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
        applyCloseTransition(R.anim.vault_background_enter, R.anim.vault_exit)
    }

    private fun panicLockAndFinish() {
        if (panicExited) return
        panicExited = true
        stopLockoutTicker()
        if (::blackoutVeil.isInitialized) {
            blackoutVeil.visibility = View.VISIBLE
        }
        allItems = emptyList()
        if (::adapter.isInitialized) {
            adapter.submitList(emptyList())
        }
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
        if (!smoothExitInFlight && !allowPmPause && ::blackoutVeil.isInitialized) {
            blackoutVeil.visibility = View.VISIBLE
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

        // If the child VaultVideoPlayerActivity triggered a lock on Back/Home,
        // close cleanly without revealing unlocked UI or leftover thumbnails.
        if ((returningFromChild || wasUnlockedInSession) && !VaultSessionManager.isUnlocked.value) {
            allItems = emptyList()
            if (::adapter.isInitialized) {
                adapter.submitList(emptyList())
            }
            finishAndRemoveTask()
            applyCloseTransition(R.anim.vault_background_enter, R.anim.vault_exit)
            return
        }

        if (::blackoutVeil.isInitialized) {
            blackoutVeil.visibility = View.GONE
        }
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
