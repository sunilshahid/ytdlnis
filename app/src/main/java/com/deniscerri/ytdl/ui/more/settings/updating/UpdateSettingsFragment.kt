package com.deniscerri.ytdl.ui.more.settings.updating

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.settings.UpdateSettingsScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.UiUtil

class UpdateSettingsFragment : ComposeFragment() {

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(R.string.updating))
    }

    @Composable
    override fun Content() {
        val navController = findNavController()
        UpdateSettingsScreen(
            onNavigateYtdlpUpdate = {
                navController.navigate(R.id.action_updateSettingsFragment_to_ytdlpUpdateSettingsFragment)
            },
            onNavigateAppUpdate = {
                navController.navigate(R.id.action_updateSettingsFragment_to_appUpdateSettingsFragment)
            },
            onNavigatePackages = {
                navController.navigate(R.id.action_updateSettingsFragment_to_packagesFragment)
            },
            onSelectExternalInstaller = {
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                UiUtil.showChooseInstallerAppDialog(requireActivity()) { selected ->
                    prefs.edit().putString("apk_install_external_apk_id", selected).apply()
                    requireActivity().recreate()
                }
            },
            onReset = { resetPreferences() }
        )
    }

    private fun resetPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        listOf(
            "apk_install_method",
            "apk_install_external_apk_id",
            "format_order",
            "format_filter",
            "update_formats"
        ).forEach { editor.remove(it) }
        editor.apply()
        com.deniscerri.ytdl.util.DefaultPreferences.resetUpdating(requireContext())
        requireActivity().recreate()
    }
}
