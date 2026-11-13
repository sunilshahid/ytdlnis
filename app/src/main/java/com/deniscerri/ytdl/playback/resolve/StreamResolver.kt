package com.deniscerri.ytdl.playback.resolve

import android.net.Uri
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.playback.model.PlaybackItem

data class ResolveOptions(
    val audioOnly: Boolean = false,
    val videoHeightCap: Int = 720,
    val preferHlsForLive: Boolean = true,
    val allowNewPipeFallback: Boolean = true
)

data class ResolvedStream(
    val audioUri: String? = null,
    val videoUri: String? = null,
    val singleUri: String? = null,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val isLive: Boolean = false,
    val chapters: List<ChapterItem> = emptyList(),
    val playbackChapters: List<com.deniscerri.ytdl.playback.model.PlaybackChapter> = emptyList(),
    val subtitles: List<com.deniscerri.ytdl.playback.model.PlaybackSubtitle> = emptyList(),
    val expiresAtMs: Long? = null,
    val resolverName: String = "unknown"
) {
    val isEmpty: Boolean get() = audioUri.isNullOrBlank() && videoUri.isNullOrBlank() && singleUri.isNullOrBlank()

    fun mainUri(): Uri? = (singleUri ?: videoUri ?: audioUri)?.let(Uri::parse)
}

interface StreamResolver {
    suspend fun resolve(item: PlaybackItem, options: ResolveOptions = ResolveOptions()): ResolvedStream
}

class PlaybackResolutionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
