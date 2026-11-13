package com.deniscerri.ytdl.lyrics

import android.content.Context
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.lyrics.providers.BetterLyricsProvider
import com.deniscerri.ytdl.lyrics.providers.LrcLibProvider
import com.deniscerri.ytdl.lyrics.providers.LyricsPlusProvider
import com.deniscerri.ytdl.lyrics.providers.YouTubeSubtitleProvider

object LyricsProviderRegistry {
    private val providerMap: Map<String, LyricsProvider> = linkedMapOf(
        BetterLyricsProvider.id to BetterLyricsProvider,
        LyricsPlusProvider.id to LyricsPlusProvider,
        LrcLibProvider.id to LrcLibProvider,
        YouTubeSubtitleProvider.id to YouTubeSubtitleProvider
    )

    val defaultOrder: List<String> = listOf(
        BetterLyricsProvider.id,
        LyricsPlusProvider.id,
        LrcLibProvider.id,
        YouTubeSubtitleProvider.id
    )

    private const val PREF_PROVIDER_ORDER = "lyrics_provider_order"

    fun getProvider(id: String?): LyricsProvider? = id?.let { providerMap[it.lowercase()] }

    fun allProviders(): List<LyricsProvider> = providerMap.values.toList()

    fun deserializeOrder(saved: String?): List<String> {
        if (saved.isNullOrBlank()) return defaultOrder
        val list = saved.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() && providerMap.containsKey(it) }
        val missing = providerMap.keys.filter { it !in list }
        return list + missing
    }

    fun serializeOrder(order: List<String>): String {
        return order.filter { providerMap.containsKey(it) }.joinToString(",")
    }

    fun ordered(context: Context): List<LyricsProvider> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.getString(PREF_PROVIDER_ORDER, null)
        val order = deserializeOrder(saved)
        return order.mapNotNull { providerMap[it] }
    }
}
