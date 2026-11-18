package com.deniscerri.ytdl.ui.more.settings.appearance

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.BaseSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.SettingsRegistry
import com.deniscerri.ytdl.ui.play.device.DeviceFilesPrefs
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.UiUtil

class AppearanceSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.appearance

    @SuppressLint("BatteryLife")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val preferenceXMLRes = R.xml.appearance_preferences
        setPreferencesFromResource(preferenceXMLRes, rootKey)
        SettingsRegistry.bindFragment(this, preferenceXMLRes)
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = preferences.edit()

        findPreference<Preference>("reset_preferences")?.setOnPreferenceClickListener {
            UiUtil.showGenericConfirmDialog(requireContext(), getString(R.string.reset), getString(R.string.reset_preferences_in_screen)) {
                resetPreferences(editor, preferenceXMLRes)
                DeviceFilesPrefs.reset(requireContext())
                DeviceFilesPrefs.clearFolder(requireContext())
                ThemeUtil.updateThemes()
                findNavController().currentDestination?.id?.apply {
                    findNavController().popBackStack(this, true)
                    findNavController().navigate(this)
                }
            }
            true
        }
    }
}
