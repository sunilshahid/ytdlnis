package com.deniscerri.ytdl.ui.more

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.more.terminal.TerminalActivity
import com.deniscerri.ytdl.util.NavbarUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/**
 * MoreFragment hosting the Jetpack Compose [MoreScreen].
 */
class MoreFragment : ComposeFragment() {
    private lateinit var mainSharedPreferences: SharedPreferences
    private lateinit var mainSharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var mainActivity: MainActivity
    private lateinit var downloadViewModel: DownloadViewModel

    private var uiState by mutableStateOf(MoreUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = activity as MainActivity
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        mainSharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        mainSharedPreferencesEditor = mainSharedPreferences.edit()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateState()
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    private fun updateState() {
        if (!isAdded) return

        var showingTerminal = false
        var showingDownloads = false
        var showingDownloadQueue = false

        NavbarUtil.getNavBarItems(requireContext()).apply {
            showingTerminal = any { n -> n.itemId == R.id.terminalActivity && n.isVisible }
            showingDownloads = any { n -> n.itemId == R.id.historyFragment && n.isVisible }
            showingDownloadQueue = any { n -> n.itemId == R.id.downloadQueueMainFragment && n.isVisible }
        }

        val appIconTintPrimary = mainSharedPreferences.getString("theme_accent", "blue") == "Default" && Build.VERSION.SDK_INT >= 32
        val showPrivateVault = !com.deniscerri.ytdl.vault.VaultRepository.isStealthEnabled(requireContext())

        uiState = MoreUiState(
            showTerminal = !showingTerminal,
            showDownloads = !showingDownloads,
            showDownloadQueue = !showingDownloadQueue,
            showPrivateVault = showPrivateVault,
            appIconTintPrimary = appIconTintPrimary,
        )
    }

    @Composable
    override fun Content() {
        val slideNavOptions = remember {
            NavOptions.Builder()
                .setEnterAnim(R.anim.nav_slide_in_right)
                .setExitAnim(R.anim.nav_slide_out_left)
                .setPopEnterAnim(R.anim.nav_slide_in_left)
                .setPopExitAnim(R.anim.nav_slide_out_right)
                .build()
        }

        MoreScreen(
            state = uiState,
            onTerminalClick = {
                val intent = Intent(context, TerminalActivity::class.java)
                startActivity(intent)
            },
            onLogsClick = {
                findNavController().navigate(R.id.downloadLogListFragment, null, slideNavOptions)
            },
            onCommandTemplatesClick = {
                findNavController().navigate(R.id.commandTemplatesFragment, null, slideNavOptions)
            },
            onDownloadsClick = {
                findNavController().navigate(R.id.historyFragment, null, slideNavOptions)
            },
            onDownloadQueueClick = {
                findNavController().navigate(R.id.downloadQueueMainFragment, null, slideNavOptions)
            },
            onCookiesClick = {
                findNavController().navigate(R.id.cookiesFragment, null, slideNavOptions)
            },
            onObserveSourcesClick = {
                findNavController().navigate(R.id.observeSourcesFragment, null, slideNavOptions)
            },
            onPrivateVaultClick = {
                startActivity(com.deniscerri.ytdl.vault.ui.VaultActivity.createIntent(requireContext()))
            },
            onSettingsClick = {
                val intent = Intent(context, SettingsActivity::class.java)
                startActivity(intent)
            },
            onTerminateClick = {
                showTerminateConfirmationDialog()
            },
            onTerminateLongClick = {
                showTerminateConfirmationDialog(skipPreference = true)
            },
        )
    }

    fun showTerminateConfirmationDialog(skipPreference: Boolean = false) {
        val shouldAskToTerminate = mainSharedPreferences.getBoolean("ask_terminate_app", true)
        if (!shouldAskToTerminate && !skipPreference) {
            terminateApp()
            return
        }

        var doNotShowAgainFinalState = !shouldAskToTerminate

        lateinit var dialog: AlertDialog
        val terminateDialog = MaterialAlertDialogBuilder(requireContext())
        terminateDialog.setTitle(getString(R.string.kill_app))
        val dialogView = layoutInflater.inflate(R.layout.dialog_terminate_app, null)
        val checkbox = dialogView.findViewById<CheckBox>(R.id.doNotShowAgain)
        terminateDialog.setView(dialogView)

        checkbox.isChecked = doNotShowAgainFinalState
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            doNotShowAgainFinalState = isChecked
        }

        terminateDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface, _ ->
            dialogInterface.cancel()
        }

        terminateDialog.setPositiveButton(getString(R.string.ok), null)
        dialog = terminateDialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.setCanceledOnTouchOutside(false)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
            mainSharedPreferencesEditor.putBoolean("ask_terminate_app", !doNotShowAgainFinalState).commit()
            terminateApp()
        }
    }

    fun terminateApp() {
        lifecycleScope.launch {
            downloadViewModel.pauseAllDownloads()
            mainActivity.finishAndRemoveTask()
            mainActivity.finishAffinity()
            exitProcess(0)
        }
    }

    companion object {
        const val TAG = "MoreFragment"
    }
}
