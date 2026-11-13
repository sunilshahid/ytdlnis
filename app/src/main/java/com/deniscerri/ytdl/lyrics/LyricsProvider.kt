package com.deniscerri.ytdl.lyrics

import android.content.Context
import com.deniscerri.ytdl.lyrics.model.LyricsCandidate
import com.deniscerri.ytdl.lyrics.model.LyricsQuery
import com.deniscerri.ytdl.lyrics.model.ProviderLyrics

interface LyricsProvider {
    val id: String
    val displayName: String
    fun isEnabled(context: Context): Boolean
    suspend fun fetch(query: LyricsQuery): ProviderLyrics?
    suspend fun search(query: LyricsQuery): List<LyricsCandidate> = emptyList()
}
