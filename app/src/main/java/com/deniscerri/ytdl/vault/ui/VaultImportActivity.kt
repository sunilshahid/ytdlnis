package com.deniscerri.ytdl.vault.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.ui.compose.vault.VaultImportScreen
import com.deniscerri.ytdl.ui.compose.vault.VaultImportStage
import com.deniscerri.ytdl.ui.compose.vault.VaultImportUiState
import com.deniscerri.ytdl.vault.VaultBiometrics
import com.deniscerri.ytdl.vault.VaultEnvelopeEngine
import com.deniscerri.ytdl.vault.VaultRepository
import com.deniscerri.ytdl.vault.VaultSessionManager
import com.deniscerri.ytdl.vault.ingest.VaultImporter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Isolated secure modal flow for Flow B ("Move to Vault") triggered from Play tab, Device Files
 * tab, or Downloads History. Supports single or multi-video batches with `FLAG_SECURE`,
 * Master Password / Biometric authentication, honest disclosure, encrypted container verification,
 * OS delete permission fallback (Android 10+), and immediate session key wipe on completion.
 */
class VaultImportActivity : AppCompatActivity() {

    private data class PendingOsDelete(
        val title: String,
        val cleanupToken: VaultImporter.PendingTraceCleanup,
        val intentSender: android.content.IntentSender
    )

    private var uiState by mutableStateOf(VaultImportUiState())

    private var batchTotalCount = 0
    private var movedAndDeletedCount = 0
    private var needsManualDeleteCount = 0
    private var failedCount = 0
    private var lastTitle: String = ""
    private var lastFailureMessage: String? = null
    private val pendingOsDeletes = ArrayDeque<PendingOsDelete>()
    private var activeOsDelete: PendingOsDelete? = null

    private var externalPromptInFlight = false
    private var unlockingInFlight = false
    private var importingInFlight = false
    private var completedOrAborted = false
    private var lockoutJob: Job? = null
    private var cachedRequests: List<VaultImporter.MoveSourceRequest> = emptyList()

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                panicLockAndFinish()
            }
        }
    }

    private val osDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        externalPromptInFlight = false
        val current = activeOsDelete
        activeOsDelete = null
        lifecycleScope.launch {
            if (current != null) {
                if (result.resultCode == RESULT_OK) {
                    VaultImporter.cleanupTraces(this@VaultImportActivity, current.cleanupToken)
                    movedAndDeletedCount++
                } else {
                    needsManualDeleteCount++
                }
            }
            if (!launchNextPendingOsDelete()) {
                finishBatchOutcome()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    VaultImportScreen(
                        state = uiState,
                        onContinueDisclosure = { ensureUnlockedThenMove(cachedRequests) },
                        onCancel = {
                            if (!unlockingInFlight && !importingInFlight) {
                                completeAndLock(RESULT_CANCELED)
                            }
                        },
                        onCreateVault = { pw, conf -> submitSetup(pw, conf, cachedRequests) },
                        onUnlockPassword = { pw -> submitPasswordUnlock(pw, cachedRequests) },
                        onUnlockBiometric = { triggerBiometricUnlock(cachedRequests) }
                    )
                }
            }
        }
        setContentView(composeView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!unlockingInFlight && !importingInFlight) {
                    completeAndLock(RESULT_CANCELED)
                }
            }
        })

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        val requests = parseRequests(intent)
        cachedRequests = requests
        if (requests.isEmpty()) {
            completeAndLock(RESULT_CANCELED)
            return
        }
        showDisclosureStep(requests)
    }

    private fun isPasswordManagerEnabled(): Boolean =
        VaultRepository.loadEnvelopeOrNull(this)?.passwordManagerEnabled == true

    private fun shouldAllowPasswordManagerPause(): Boolean =
        !unlockingInFlight && !importingInFlight && !VaultSessionManager.isUnlocked.value && isPasswordManagerEnabled()

    private fun parseRequests(i: Intent): List<VaultImporter.MoveSourceRequest> {
        @Suppress("DEPRECATION")
        val bundleList = i.getParcelableArrayListExtra<Bundle>(EXTRA_BATCH_REQUESTS)
        if (!bundleList.isNullOrEmpty()) {
            val parsed = bundleList.mapNotNull(::requestFromBundle)
            if (parsed.isNotEmpty()) return parsed
        }
        return listOfNotNull(requestFromBundle(i.extras))
    }

    private fun showDisclosureStep(requests: List<VaultImporter.MoveSourceRequest>) {
        val msg = if (requests.size == 1) {
            getString(R.string.vault_import_disclosure_msg, requests.first().title)
        } else {
            getString(R.string.vault_import_disclosure_multi_msg, requests.size)
        }
        uiState = uiState.copy(
            stage = VaultImportStage.DISCLOSURE,
            disclosureMessage = msg
        )
    }

    private fun ensureUnlockedThenMove(requests: List<VaultImporter.MoveSourceRequest>) {
        if (VaultSessionManager.isUnlocked.value) {
            startImport(requests)
            return
        }
        if (!VaultRepository.isInitialized(this)) {
            showSetupStep()
        } else {
            showUnlockStep(requests)
        }
    }

    private fun showSetupStep() {
        uiState = uiState.copy(
            stage = VaultImportStage.SETUP,
            setupError = null
        )
    }

    private fun submitSetup(password: String, confirm: String, requests: List<VaultImporter.MoveSourceRequest>) {
        val pwChars = password.toCharArray()
        val confChars = confirm.toCharArray()
        if (pwChars.size < 4) {
            pwChars.fill('\u0000')
            confChars.fill('\u0000')
            uiState = uiState.copy(setupError = getString(R.string.vault_password_too_short))
            return
        }
        if (!pwChars.contentEquals(confChars)) {
            pwChars.fill('\u0000')
            confChars.fill('\u0000')
            uiState = uiState.copy(setupError = getString(R.string.vault_password_mismatch))
            return
        }
        confChars.fill('\u0000')
        unlockingInFlight = true
        showBusy(getString(R.string.vault_creating_vault))
        lifecycleScope.launch {
            runCatching {
                VaultRepository.createVault(this@VaultImportActivity, pwChars)
            }.onSuccess {
                unlockingInFlight = false
                startImport(requests)
            }.onFailure { err ->
                unlockingInFlight = false
                uiState = uiState.copy(
                    stage = VaultImportStage.SETUP,
                    setupError = err.message ?: "Error"
                )
            }
        }
    }

    private fun showUnlockStep(requests: List<VaultImporter.MoveSourceRequest>) {
        var env = VaultRepository.loadEnvelopeOrNull(this) ?: run {
            showSetupStep()
            return
        }

        var invalidatedNow = env.biometricInvalidated
        if (env.biometricEnabled) {
            val bioIvCheck = env.biometricWrappedDekIvB64
            val bioCtCheck = env.biometricWrappedDekCtB64
            val keyValid = !bioIvCheck.isNullOrBlank() &&
                !bioCtCheck.isNullOrBlank() &&
                VaultBiometrics.checkEnrolledKeyStillValid(this, bioIvCheck)
            if (!keyValid) {
                VaultRepository.clearBiometricEnrollment(this, dueToChange = true)
                env = VaultRepository.loadEnvelopeOrNull(this) ?: env
                invalidatedNow = true
            }
        }

        val bioIv = env.biometricWrappedDekIvB64
        val bioCt = env.biometricWrappedDekCtB64
        val canBio = !invalidatedNow &&
            env.biometricEnabled &&
            !bioIv.isNullOrBlank() &&
            !bioCt.isNullOrBlank() &&
            VaultBiometrics.isSupported(this)

        val now = System.currentTimeMillis()
        if (env.lockoutUntilMs > now) {
            startLockoutTicker(env.lockoutUntilMs)
        }

        uiState = uiState.copy(
            stage = VaultImportStage.UNLOCK,
            unlockError = if (invalidatedNow) getString(R.string.vault_biometric_invalidated) else null,
            canBiometricUnlock = canBio
        )

        if (canBio && !invalidatedNow && env.lockoutUntilMs <= now) {
            triggerBiometricUnlock(requests)
        }
    }

    private fun submitPasswordUnlock(password: String, requests: List<VaultImporter.MoveSourceRequest>) {
        if (unlockingInFlight) return
        val chars = password.toCharArray()
        if (chars.isEmpty()) return
        uiState = uiState.copy(unlockError = null)
        unlockingInFlight = true
        lifecycleScope.launch {
            val outcome = runCatching {
                VaultRepository.unlockWithPassword(this@VaultImportActivity, chars)
            }
            outcome.onSuccess { res ->
                when (res) {
                    is VaultEnvelopeEngine.UnlockOutcome.Success -> {
                        unlockingInFlight = false
                        stopLockoutTicker()
                        startImport(requests)
                    }
                    is VaultEnvelopeEngine.UnlockOutcome.LockedOut -> {
                        unlockingInFlight = false
                        startLockoutTicker(System.currentTimeMillis() + res.remainingMs)
                    }
                    is VaultEnvelopeEngine.UnlockOutcome.WrongPassword -> {
                        unlockingInFlight = false
                        if (res.autoWiped) {
                            Toast.makeText(
                                this@VaultImportActivity,
                                R.string.vault_auto_wiped,
                                Toast.LENGTH_LONG
                            ).show()
                            completeAndLock(RESULT_CANCELED)
                        } else {
                            val errText = res.remainingBeforeWipe?.let {
                                getString(R.string.vault_wrong_password_remaining, it)
                            } ?: getString(R.string.vault_wrong_password)
                            uiState = uiState.copy(unlockError = errText)
                        }
                    }
                }
            }.onFailure { err ->
                unlockingInFlight = false
                uiState = uiState.copy(unlockError = err.message ?: getString(R.string.vault_wrong_password))
            }
        }
    }

    private fun triggerBiometricUnlock(requests: List<VaultImporter.MoveSourceRequest>) {
        val env = VaultRepository.loadEnvelopeOrNull(this) ?: return
        val bioIv = env.biometricWrappedDekIvB64 ?: return
        val bioCt = env.biometricWrappedDekCtB64 ?: return
        if (unlockingInFlight) return
        externalPromptInFlight = true
        VaultBiometrics.unlockWithBiometrics(
            context = this,
            title = getString(R.string.vault_biometric_title),
            subtitle = getString(R.string.vault_biometric_subtitle),
            cancelText = getString(R.string.cancel),
            ivB64 = bioIv,
            ctB64 = bioCt,
            onSuccess = { dek ->
                externalPromptInFlight = false
                unlockingInFlight = true
                lifecycleScope.launch {
                    VaultRepository.finishBiometricUnlock(this@VaultImportActivity, dek)
                    unlockingInFlight = false
                    stopLockoutTicker()
                    startImport(requests)
                }
            },
            onInvalidated = {
                externalPromptInFlight = false
                unlockingInFlight = false
                VaultRepository.clearBiometricEnrollment(this, dueToChange = true)
                uiState = uiState.copy(
                    canBiometricUnlock = false,
                    unlockError = getString(R.string.vault_biometric_invalidated)
                )
            },
            onError = {
                externalPromptInFlight = false
                unlockingInFlight = false
            }
        )
    }

    private fun startLockoutTicker(untilMs: Long) {
        stopLockoutTicker()
        lockoutJob = lifecycleScope.launch {
            while (isActive) {
                val remMs = untilMs - System.currentTimeMillis()
                if (remMs <= 0L) {
                    uiState = uiState.copy(unlockError = null)
                    break
                }
                val secs = ((remMs + 999L) / 1000L).toInt().coerceAtLeast(1)
                uiState = uiState.copy(unlockError = getString(R.string.vault_locked_out, secs))
                delay(500L)
            }
        }
    }

    private fun stopLockoutTicker() {
        lockoutJob?.cancel()
        lockoutJob = null
    }

    private fun startImport(requests: List<VaultImporter.MoveSourceRequest>) {
        importingInFlight = true
        batchTotalCount = requests.size
        movedAndDeletedCount = 0
        needsManualDeleteCount = 0
        failedCount = 0
        lastTitle = requests.firstOrNull()?.title.orEmpty()
        lastFailureMessage = null
        pendingOsDeletes.clear()
        activeOsDelete = null

        val baseLabel = getString(R.string.vault_moving_progress)
        showBusy(if (batchTotalCount > 1) "$baseLabel (1/$batchTotalCount)" else baseLabel)

        lifecycleScope.launch {
            for ((index, req) in requests.withIndex()) {
                if (completedOrAborted) break
                val prefix = if (batchTotalCount > 1) "${index + 1}/$batchTotalCount · " else ""
                uiState = uiState.copy(progressMessage = "$baseLabel (${prefix}0%)")
                val result = runCatching {
                    VaultImporter.moveVideoToVault(
                        context = this@VaultImportActivity,
                        request = req,
                        onProgress = { written, total ->
                            if (total > 0L) {
                                val pct = ((written * 100L) / total).toInt().coerceIn(0, 100)
                                uiState = uiState.copy(progressMessage = "$baseLabel ($prefix$pct%)")
                            }
                        }
                    )
                }
                result.onSuccess { outcome ->
                    when (outcome) {
                        is VaultImporter.MoveOutcome.MovedAndDeleted -> {
                            movedAndDeletedCount++
                            lastTitle = outcome.title
                        }
                        is VaultImporter.MoveOutcome.RequiresOsDeletePermission -> {
                            lastTitle = outcome.title
                            pendingOsDeletes.addLast(
                                PendingOsDelete(
                                    title = outcome.title,
                                    cleanupToken = outcome.cleanupToken,
                                    intentSender = outcome.intentSender
                                )
                            )
                        }
                        is VaultImporter.MoveOutcome.ImportedNeedsManualDelete -> {
                            needsManualDeleteCount++
                            lastTitle = outcome.title
                        }
                    }
                }.onFailure { err ->
                    failedCount++
                    lastFailureMessage = err.message ?: "Error"
                }
            }

            if (!completedOrAborted && !launchNextPendingOsDelete()) {
                finishBatchOutcome()
            }
        }
    }

    private fun launchNextPendingOsDelete(): Boolean {
        val next = pendingOsDeletes.removeFirstOrNull() ?: return false
        activeOsDelete = next
        externalPromptInFlight = true
        osDeleteLauncher.launch(IntentSenderRequest.Builder(next.intentSender).build())
        return true
    }

    private fun finishBatchOutcome() {
        val totalImported = movedAndDeletedCount + needsManualDeleteCount
        if (batchTotalCount <= 1) {
            when {
                movedAndDeletedCount == 1 -> Toast.makeText(
                    this,
                    getString(R.string.vault_import_success_deleted, lastTitle),
                    Toast.LENGTH_SHORT
                ).show()
                needsManualDeleteCount == 1 -> Toast.makeText(
                    this,
                    getString(R.string.vault_import_success_needs_delete, lastTitle),
                    Toast.LENGTH_LONG
                ).show()
                else -> Toast.makeText(
                    this,
                    getString(R.string.vault_import_failed, lastFailureMessage ?: "Error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            val msg = when {
                totalImported == 0 ->
                    getString(R.string.vault_import_failed, lastFailureMessage ?: "Error")
                failedCount > 0 ->
                    getString(R.string.vault_import_batch_partial, totalImported, failedCount)
                needsManualDeleteCount > 0 ->
                    getString(R.string.vault_import_batch_needs_delete, totalImported, needsManualDeleteCount)
                else ->
                    getString(R.string.vault_import_batch_success, totalImported)
            }
            Toast.makeText(
                this,
                msg,
                if (failedCount > 0 || needsManualDeleteCount > 0 || totalImported == 0) {
                    Toast.LENGTH_LONG
                } else {
                    Toast.LENGTH_SHORT
                }
            ).show()
        }
        completeAndLock(if (totalImported > 0) RESULT_OK else RESULT_CANCELED)
    }

    private fun showBusy(label: String) {
        uiState = uiState.copy(
            stage = VaultImportStage.PROGRESS,
            progressMessage = label
        )
    }

    private fun completeAndLock(resultCode: Int) {
        if (completedOrAborted) return
        completedOrAborted = true
        importingInFlight = false
        stopLockoutTicker()
        VaultSessionManager.lockAndZeroize()
        setResult(resultCode)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun panicLockAndFinish() {
        if (completedOrAborted) return
        completedOrAborted = true
        importingInFlight = false
        stopLockoutTicker()
        uiState = uiState.copy(isBlackout = true)
        VaultSessionManager.lockAndZeroize()
        setResult(RESULT_CANCELED)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!externalPromptInFlight && !shouldAllowPasswordManagerPause()) {
            panicLockAndFinish()
        }
    }

    override fun onPause() {
        val allowPmPause = shouldAllowPasswordManagerPause()
        if (!allowPmPause) {
            uiState = uiState.copy(isBlackout = true)
        }
        super.onPause()
        if (!externalPromptInFlight && !allowPmPause && !completedOrAborted) {
            panicLockAndFinish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!completedOrAborted) {
            uiState = uiState.copy(isBlackout = false)
        }
    }

    override fun onDestroy() {
        stopLockoutTicker()
        runCatching { unregisterReceiver(screenOffReceiver) }
        if (!completedOrAborted) {
            VaultSessionManager.lockAndZeroize()
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_FILE_PATH = "extra_file_path"
        private const val EXTRA_CONTENT_URI = "extra_content_uri"
        private const val EXTRA_FILE_NAME = "extra_file_name"
        private const val EXTRA_MIME_TYPE = "extra_mime_type"
        private const val EXTRA_DURATION_MS = "extra_duration_ms"
        private const val EXTRA_SIZE_BYTES = "extra_size_bytes"
        private const val EXTRA_WIDTH = "extra_width"
        private const val EXTRA_HEIGHT = "extra_height"
        private const val EXTRA_PLAYBACK_MEDIA_ID = "extra_playback_media_id"
        private const val EXTRA_HISTORY_ID = "extra_history_id"
        private const val EXTRA_BATCH_REQUESTS = "extra_batch_requests"

        private fun requestToBundle(req: VaultImporter.MoveSourceRequest): Bundle = Bundle().apply {
            putString(EXTRA_TITLE, req.title)
            putString(EXTRA_FILE_PATH, req.filePath)
            putString(EXTRA_CONTENT_URI, req.contentUri?.toString())
            putString(EXTRA_FILE_NAME, req.originalFileName)
            putString(EXTRA_MIME_TYPE, req.mimeType)
            putLong(EXTRA_DURATION_MS, req.durationMs)
            putLong(EXTRA_SIZE_BYTES, req.sizeBytes)
            putInt(EXTRA_WIDTH, req.width)
            putInt(EXTRA_HEIGHT, req.height)
            putString(EXTRA_PLAYBACK_MEDIA_ID, req.playbackMediaId)
            req.historyId?.let { putLong(EXTRA_HISTORY_ID, it) }
        }

        private fun requestFromBundle(bundle: Bundle?): VaultImporter.MoveSourceRequest? {
            bundle ?: return null
            val filePath = bundle.getString(EXTRA_FILE_PATH)
            val uriString = bundle.getString(EXTRA_CONTENT_URI)
            val uri = uriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
            if (filePath.isNullOrBlank() && uri == null) return null

            val rawTitle = bundle.getString(EXTRA_TITLE).orEmpty().ifBlank { "Private Video" }
            return VaultImporter.MoveSourceRequest(
                title = rawTitle,
                filePath = filePath,
                contentUri = uri,
                originalFileName = bundle.getString(EXTRA_FILE_NAME),
                mimeType = bundle.getString(EXTRA_MIME_TYPE),
                durationMs = bundle.getLong(EXTRA_DURATION_MS, 0L),
                sizeBytes = bundle.getLong(EXTRA_SIZE_BYTES, 0L),
                width = bundle.getInt(EXTRA_WIDTH, 0),
                height = bundle.getInt(EXTRA_HEIGHT, 0),
                playbackMediaId = bundle.getString(EXTRA_PLAYBACK_MEDIA_ID),
                historyId = bundle.getLong(EXTRA_HISTORY_ID, -1L).takeIf { it > 0L }
            )
        }

        fun createIntent(
            context: Context,
            title: String,
            filePath: String? = null,
            contentUri: Uri? = null,
            originalFileName: String? = null,
            mimeType: String? = null,
            durationMs: Long = 0L,
            sizeBytes: Long = 0L,
            width: Int = 0,
            height: Int = 0,
            playbackMediaId: String? = null,
            historyId: Long? = null
        ): Intent = Intent(context, VaultImportActivity::class.java).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_FILE_PATH, filePath)
            putExtra(EXTRA_CONTENT_URI, contentUri?.toString())
            putExtra(EXTRA_FILE_NAME, originalFileName)
            putExtra(EXTRA_MIME_TYPE, mimeType)
            putExtra(EXTRA_DURATION_MS, durationMs)
            putExtra(EXTRA_SIZE_BYTES, sizeBytes)
            putExtra(EXTRA_WIDTH, width)
            putExtra(EXTRA_HEIGHT, height)
            putExtra(EXTRA_PLAYBACK_MEDIA_ID, playbackMediaId)
            if (historyId != null) putExtra(EXTRA_HISTORY_ID, historyId)
        }

        fun createBatchIntent(
            context: Context,
            requests: List<VaultImporter.MoveSourceRequest>
        ): Intent = Intent(context, VaultImportActivity::class.java).apply {
            val bundles = ArrayList<Bundle>(requests.size)
            requests.forEach { bundles.add(requestToBundle(it)) }
            putParcelableArrayListExtra(EXTRA_BATCH_REQUESTS, bundles)
        }
    }
}
