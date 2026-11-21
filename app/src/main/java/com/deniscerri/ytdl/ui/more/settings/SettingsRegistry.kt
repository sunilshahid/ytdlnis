package com.deniscerri.ytdl.ui.more.settings

import android.content.Context
import com.deniscerri.ytdl.database.models.SearchSettingsItem

object SettingsRegistry {
    fun indexAll(context: Context): List<SearchSettingsItem> {
        return SettingsIndex.getAll(context)
    }
}
