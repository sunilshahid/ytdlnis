package com.deniscerri.ytdl.database.models

data class SearchSettingsItem(
    val title: String,
    val summary: String? = null,
    val key: String,
    val route: String,
    val groupTitle: String? = null,
    val destinationId: Int = 0,
    val isHeader: Boolean = false,
)
