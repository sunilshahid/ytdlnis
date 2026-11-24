package com.deniscerri.ytdl.ui.more.settings.appearance

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.settings.AppearanceSettingsScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.ThemeUtil

class AppearanceSettingsFragment : ComposeFragment() {

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(R.string.appearance))
    }

    @Composable
    override fun Content() {
        AppearanceSettingsScreen(
            onOpenIconPicker = null,
            onReset = { resetPreferences() }
        )
    }

    private fun resetPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        listOf(
            "app_language",
            "ytdlnis_theme",
            "ytdlnis_icon",
            "theme_accent",
            "custom_palette_seed",
            "material_you",
            "app_font",
            "high_contrast",
            "haptics_enabled",
            "smooth_motion",
            "reduce_motion",
            "glass_dock",
            "fullscreen_mode",
            "compact_mode",
            "hide_thumbnails",
            "modify_download_card",
            "display_over_apps"
        ).forEach { editor.remove(it) }
        editor.apply()
        com.deniscerri.ytdl.util.DefaultPreferences.resetAppearance(requireContext())
        ThemeUtil.updateThemes()
        requireActivity().recreate()
    }
}
