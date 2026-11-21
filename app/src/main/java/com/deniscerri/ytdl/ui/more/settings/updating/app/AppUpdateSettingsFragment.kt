package com.deniscerri.ytdl.ui.more.settings.updating.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.settings.AppUpdateSettingsScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.compose.settings.UpdateStatusCardUtil
import com.deniscerri.ytdl.util.ApkInstallUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppUpdateSettingsFragment : ComposeFragment() {

    private lateinit var installLauncher: ActivityResultLauncher<Intent>
    private var lastCheckText by mutableStateOf("")
    private var isCheckingUpdate by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installLauncher = ApkInstallUtil.registerInstallLauncher(this)
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(R.string.update_app))
        refreshState()
    }

    private fun refreshState() {
        val ctx = context ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val lastCheck = prefs.getLong(UpdateUtil.APP_LAST_CHECK_KEY, 0L)
        lastCheckText = UpdateStatusCardUtil.buildDetail(ctx, lastCheck).orEmpty()
    }

    @Composable
    override fun Content() {
        AppUpdateSettingsScreen(
            lastCheckText = lastCheckText,
            isCheckingUpdate = isCheckingUpdate,
            onCheckForUpdates = {
                isCheckingUpdate = true
                val updateUtil = UpdateUtil(requireContext())
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { updateUtil.tryGetNewVersion() }
                    }.getOrElse { Result.failure(it) }
                    isCheckingUpdate = false
                    refreshState()
                    result.onSuccess { release ->
                        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                        UiUtil.showNewAppUpdateDialog(
                            release,
                            requireActivity(),
                            updateUtil,
                            viewLifecycleOwner,
                            prefs,
                            installLauncher
                        )
                    }.onFailure { e ->
                        view?.let { v ->
                            Snackbar.make(
                                v,
                                e.message ?: getString(R.string.no_updates),
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            },
            onNavigateChangelog = {
                findNavController().navigate(R.id.action_updateSettingsFragment_to_changeLogFragment)
            },
            onReset = { resetPreferences() }
        )
    }

    private fun resetPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        listOf("update_beta", "update_app", "skip_updates").forEach { editor.remove(it) }
        editor.apply()
        com.deniscerri.ytdl.util.DefaultPreferences.resetAppUpdate(requireContext())
        requireActivity().recreate()
    }
}
