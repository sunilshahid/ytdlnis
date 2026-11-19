package com.deniscerri.ytdl.ui.more.cookies

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.auth.YouTubeSessionEngine
import com.deniscerri.ytdl.core.SessionOrchestrator
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.database.viewmodel.CookieViewModel
import com.deniscerri.ytdl.ui.compose.components.ConfirmDialog
import com.deniscerri.ytdl.ui.compose.cookies.CookieBottomSheet
import com.deniscerri.ytdl.ui.compose.cookies.CookiesScreen
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fragment for managing saved cookies and YouTube authentication session (Phase 3).
 * Migrated to Compose from `fragment_cookies.xml`.
 */
class CookiesFragment : ComposeFragment() {

    private lateinit var cookiesViewModel: CookieViewModel
    private lateinit var preferences: SharedPreferences
    private lateinit var youtubeLoginLauncher: ActivityResultLauncher<Intent>

    private var useCookies by mutableStateOf(false)
    private var youtubeAccountCookies by mutableStateOf(false)
    private var checkYoutubeCookies by mutableStateOf(true)
    private var youtubeAccountSummary by mutableStateOf("")
    private var youtubeCookieStatusText by mutableStateOf<String?>(null)
    private var swipeGestures by mutableStateOf(false)

    private var activeSheetItem by mutableStateOf<CookieItem?>(null)
    private var isSheetOpen by mutableStateOf(false)
    private var cookieToDelete by mutableStateOf<CookieItem?>(null)
    private var showDeleteAllConfirm by mutableStateOf(false)
    private var showAccountDialog by mutableStateOf(false)
    private var showRemoveAccountConfirm by mutableStateOf(false)

    private val snackbarHostState = SnackbarHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        cookiesViewModel = ViewModelProvider(this)[CookieViewModel::class.java]

        youtubeLoginLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (isAdded) {
                if (result.resultCode == Activity.RESULT_OK) {
                    lifecycleScope.launch {
                        snackbarHostState.showSnackbar(getString(R.string.youtube_sign_in_success))
                    }
                }
                updateYoutubeAccountCookiesState(useCookies)
            }
        }

        readInitialPreferences()
    }

    override fun onResume() {
        super.onResume()
        readInitialPreferences()
    }

    private fun readInitialPreferences() {
        if (!isAdded) return
        val context = requireContext()
        useCookies = preferences.getBoolean(YouTubeSessionEngine.PREF_USE_COOKIES, false)
        youtubeAccountCookies = preferences.getBoolean(YouTubeSessionEngine.PREF_YOUTUBE_ACCOUNT_COOKIES, false)
        checkYoutubeCookies = preferences.getBoolean(YouTubeSessionEngine.PREF_CHECK_YOUTUBE_COOKIES_ON_OPEN, true)

        val defaultGestures = context.resources.getStringArray(R.array.swipe_gestures_values).toSet()
        val userGestures = preferences.getStringSet("swipe_gesture", defaultGestures) ?: defaultGestures
        swipeGestures = userGestures.contains("cookies")

        updateYoutubeAccountCookiesState(useCookies)
    }

    private fun updateYoutubeAccountCookiesState(masterEnabled: Boolean) {
        if (!isAdded) return
        val context = context ?: return
        val ytAccountEnabled = masterEnabled && youtubeAccountCookies

        if (ytAccountEnabled) {
            val status = preferences.getString(YouTubeSessionEngine.PREF_LAST_COOKIE_CHECK_STATUS, null)
            val lastCheckTime = preferences.getLong(YouTubeSessionEngine.PREF_LAST_COOKIE_CHECK_TIMESTAMP, 0L)
            if (status != null && lastCheckTime > 0) {
                val statusString = when (status) {
                    YouTubeSessionEngine.COOKIE_STATUS_HEALTHY -> getString(R.string.youtube_cookie_status_healthy)
                    YouTubeSessionEngine.COOKIE_STATUS_EXPIRED -> getString(R.string.youtube_cookie_status_expired)
                    YouTubeSessionEngine.COOKIE_STATUS_SIGNED_OUT -> getString(R.string.youtube_cookie_status_signed_out)
                    YouTubeSessionEngine.COOKIE_STATUS_ERROR -> getString(R.string.youtube_cookie_status_error)
                    else -> getString(R.string.youtube_cookie_status_unknown)
                }
                val relative = DateUtils.getRelativeTimeSpanString(
                    lastCheckTime,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
                youtubeCookieStatusText = getString(R.string.youtube_cookie_status_last_checked, relative, statusString)
            } else {
                youtubeCookieStatusText = null
            }
        } else {
            youtubeCookieStatusText = null
        }

        val sessionActive = YouTubeSessionEngine.hasValidSession()
        val email = YouTubeSessionEngine.getCachedEmail(context)
        youtubeAccountSummary = when {
            !masterEnabled -> getString(R.string.youtube_account_cookies_desc)
            sessionActive && !email.isNullOrBlank() -> getString(R.string.signed_in_as, email)
            sessionActive -> getString(R.string.signed_in_as, getString(R.string.select_youtube_account))
            else -> getString(R.string.youtube_not_signed_in)
        }
    }

    @Composable
    override fun Content() {
        val cookies by cookiesViewModel.items.observeAsState(emptyList())
        val coroutineScope = rememberCoroutineScope()

        CookiesScreen(
            cookies = cookies,
            useCookies = useCookies,
            onUseCookiesChanged = { isChecked ->
                useCookies = isChecked
                preferences.edit().putBoolean(YouTubeSessionEngine.PREF_USE_COOKIES, isChecked).apply()
                updateYoutubeAccountCookiesState(isChecked)
            },
            youtubeAccountCookies = youtubeAccountCookies,
            onYoutubeAccountCookiesChanged = { isChecked ->
                youtubeAccountCookies = isChecked
                preferences.edit().putBoolean(YouTubeSessionEngine.PREF_YOUTUBE_ACCOUNT_COOKIES, isChecked).apply()
                updateYoutubeAccountCookiesState(useCookies)
            },
            checkYoutubeCookies = checkYoutubeCookies,
            onCheckYoutubeCookiesChanged = { isChecked ->
                checkYoutubeCookies = isChecked
                preferences.edit().putBoolean(YouTubeSessionEngine.PREF_CHECK_YOUTUBE_COOKIES_ON_OPEN, isChecked).apply()
            },
            youtubeAccountSummary = youtubeAccountSummary,
            youtubeCookieStatusText = youtubeCookieStatusText,
            onCheckSessionNow = { checkYoutubeSessionNow() },
            onManageYoutubeAccount = { showYoutubeAccountDialog() },
            swipeGestures = swipeGestures,
            snackbarHostState = snackbarHostState,
            onBack = {
                (activity as? MainActivity)?.onBackPressedDispatcher?.onBackPressed()
                    ?: activity?.onBackPressedDispatcher?.onBackPressed()
            },
            onNewCookie = {
                activeSheetItem = null
                isSheetOpen = true
            },
            onCookieClick = { item ->
                activeSheetItem = item
                isSheetOpen = true
            },
            onCookieEnabledChanged = { item, isEnabled ->
                lifecycleScope.launch(Dispatchers.IO) {
                    cookiesViewModel.changeCookieEnabledState(item.id, isEnabled)
                }
            },
            onDeleteCookie = { item ->
                cookieToDelete = item
            },
            onDeleteAllCookies = {
                showDeleteAllConfirm = true
            },
            onImportClipboard = {
                lifecycleScope.launch(Dispatchers.IO) {
                    cookiesViewModel.importFromClipboard()
                }
            },
            onExportClipboard = {
                cookiesViewModel.exportToClipboard()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(getString(R.string.copied_to_clipboard))
                }
            },
            onExportFile = {
                cookiesViewModel.exportToFile { file ->
                    coroutineScope.launch {
                        if (file == null) {
                            snackbarHostState.showSnackbar(getString(R.string.couldnt_parse_file))
                        } else {
                            val action = snackbarHostState.showSnackbar(
                                message = getString(R.string.backup_created_successfully),
                                actionLabel = getString(R.string.share),
                                duration = SnackbarDuration.Long,
                            )
                            if (action == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                FileUtil.shareFileIntent(requireContext(), listOf(file.absolutePath))
                            }
                        }
                    }
                }
            },
        )

        // Bottom sheet for new or existing cookie
        if (isSheetOpen) {
            CookieBottomSheet(
                item = activeSheetItem,
                onDismissRequest = {
                    isSheetOpen = false
                    activeSheetItem = null
                },
                onSave = { updated ->
                    cookiesViewModel.update(updated)
                },
                onGetCookies = { url, description, incognito ->
                    val intent = Intent(
                        requireContext(),
                        if (incognito) WebViewActivityIncognito::class.java else WebViewActivity::class.java
                    )
                    intent.putExtra("url", url)
                    intent.putExtra("description", description)
                    startActivity(intent)
                },
                onCopyContent = { textToCopy ->
                    activity?.let { UiUtil.copyToClipboard(textToCopy, it) }
                },
            )
        }

        // Delete single cookie dialog
        val deletingItem = cookieToDelete
        if (deletingItem != null) {
            ConfirmDialog(
                title = stringResource(R.string.you_are_going_to_delete) + " \"" + deletingItem.url + "\"!",
                onConfirm = {
                    cookiesViewModel.delete(deletingItem)
                    cookieToDelete = null
                },
                onDismiss = { cookieToDelete = null },
            )
        }

        // Delete all cookies dialog
        if (showDeleteAllConfirm) {
            ConfirmDialog(
                title = stringResource(R.string.confirm_delete_history),
                message = stringResource(R.string.confirm_delete_cookies_desc),
                onConfirm = {
                    cookiesViewModel.deleteAll()
                    runCatching {
                        FileUtil.getCookieFile(requireContext(), true) { path ->
                            File(path).writeText("")
                        }
                    }
                    showDeleteAllConfirm = false
                },
                onDismiss = { showDeleteAllConfirm = false },
            )
        }

        // YouTube Account management dialog
        if (showAccountDialog) {
            val email = YouTubeSessionEngine.getCachedEmail(requireContext())
            val accountLabel = email ?: stringResource(R.string.select_youtube_account)

            AlertDialog(
                onDismissRequest = { showAccountDialog = false },
                title = { Text(stringResource(R.string.manage_youtube_account)) },
                text = { Text(stringResource(R.string.signed_in_as, accountLabel)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showAccountDialog = false
                            refreshYoutubeSession()
                        },
                    ) {
                        Text(stringResource(R.string.refresh_session))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAccountDialog = false
                            showRemoveAccountConfirm = true
                        },
                    ) {
                        Text(stringResource(R.string.remove_youtube_account))
                    }
                },
            )
        }

        // Confirm Remove YouTube Account dialog
        if (showRemoveAccountConfirm) {
            ConfirmDialog(
                title = stringResource(R.string.remove_youtube_account),
                message = stringResource(R.string.remove_youtube_account_confirmation),
                onConfirm = {
                    showRemoveAccountConfirm = false
                    YouTubeSessionEngine.removeAccountAndWipeSession(requireContext()) {
                        if (!isAdded) return@removeAccountAndWipeSession
                        updateYoutubeAccountCookiesState(useCookies)
                        lifecycleScope.launch {
                            snackbarHostState.showSnackbar(getString(R.string.youtube_account_removed))
                        }
                    }
                },
                onDismiss = { showRemoveAccountConfirm = false },
            )
        }
    }

    private fun showYoutubeAccountDialog() {
        if (!YouTubeSessionEngine.hasValidSession()) {
            startYoutubeLogin()
            return
        }
        showAccountDialog = true
    }

    private fun startYoutubeLogin() {
        youtubeLoginLauncher.launch(Intent(requireContext(), YouTubeLoginActivity::class.java))
    }

    private fun refreshYoutubeSession() {
        lifecycleScope.launch {
            snackbarHostState.showSnackbar(getString(R.string.refreshing_youtube_cookies))
            val outcome = SessionOrchestrator.refreshYoutubeSession(requireContext())
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                updateYoutubeAccountCookiesState(useCookies)

                when (outcome) {
                    SessionOrchestrator.RefreshOutcome.SUCCESS -> {
                        snackbarHostState.showSnackbar(getString(R.string.account_cookies_refreshed))
                    }
                    SessionOrchestrator.RefreshOutcome.NEEDS_MANUAL_LOGIN -> {
                        val result = snackbarHostState.showSnackbar(
                            message = getString(R.string.youtube_sign_in_required),
                            actionLabel = getString(R.string.youtube_sign_in_now),
                        )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            startYoutubeLogin()
                        }
                    }
                    SessionOrchestrator.RefreshOutcome.FAILED -> {
                        val result = snackbarHostState.showSnackbar(
                            message = getString(R.string.cookie_refresh_failed),
                            actionLabel = getString(R.string.youtube_sign_in_now),
                        )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            startYoutubeLogin()
                        }
                    }
                }
            }
        }
    }

    private fun checkYoutubeSessionNow() {
        lifecycleScope.launch {
            snackbarHostState.showSnackbar(getString(R.string.youtube_cookie_status_checking))
            val healthy = SessionOrchestrator.checkAndHealYoutubeSessionIfNeeded(
                requireContext(),
                force = true,
            )
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                updateYoutubeAccountCookiesState(useCookies)

                val status = YouTubeSessionEngine.getCookieCheckStatus(requireContext())
                val messageRes = when (status) {
                    YouTubeSessionEngine.COOKIE_STATUS_HEALTHY -> R.string.youtube_cookie_status_healthy
                    YouTubeSessionEngine.COOKIE_STATUS_EXPIRED -> R.string.youtube_cookie_status_expired
                    YouTubeSessionEngine.COOKIE_STATUS_SIGNED_OUT -> R.string.youtube_cookie_status_signed_out
                    YouTubeSessionEngine.COOKIE_STATUS_ERROR -> R.string.youtube_cookie_status_error
                    else -> R.string.youtube_cookie_status_unknown
                }

                if (!healthy && status == YouTubeSessionEngine.COOKIE_STATUS_EXPIRED) {
                    val result = snackbarHostState.showSnackbar(
                        message = getString(messageRes),
                        actionLabel = getString(R.string.youtube_sign_in_now),
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        startYoutubeLogin()
                    }
                } else {
                    snackbarHostState.showSnackbar(getString(messageRes))
                }
            }
        }
    }
}
