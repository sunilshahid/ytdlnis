package com.deniscerri.ytdl.ui.more.settings.appearance

import androidx.preference.Preference
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.ui.more.settings.general.GeneralSettingsModule

object AppearanceSettingsModule : SettingModule {
    override fun bindLogic(
        pref: Preference,
        host: SettingHost
    ) {
        GeneralSettingsModule.bindLogic(pref, host)
    }
}
