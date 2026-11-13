package com.deniscerri.ytdl.playback.resolve

import android.content.Context
import android.util.Log
import com.deniscerri.ytdl.core.SessionOrchestrator
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackSource
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.extractors.ytdlp.YTDLPUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves [PlaybackItem]s into direct stream URLs.
 *
 * Local/download items never touch the network. YouTube items go through yt-dlp with the same
 * session cookies and PO-token bundle as downloads; a 403/auth failure triggers one session heal
 * and one retry, never an infinite loop.
 */
class PlaybackStreamResolver(context: Context) {
    private val appContext = context.applicationContext
    private val ytdlp = YTDLPUtil(appContext, DBManager.getInstance(appContext).commandTemplateDao)

    suspend fun resolve(item: PlaybackItem, options: ResolveOptions = ResolveOptions()): ResolvedStream {
        item.localUri?.takeIf { it.isNotBlank() }?.let { local ->
            val sidecarSubtitles = SubtitleSidecarFinder.findForMedia(appContext, local)
            return ResolvedStream(
                singleUri = local,
                durationMs = item.durationMs,
                subtitles = sidecarSubtitles,
                resolverName = "local"
            )
        }

        val webpageUrl = item.webpageUrl
            ?: throw PlaybackResolutionException("Item has no URL to resolve")
        if (!webpageUrl.isYoutubeURL()) {
            return ResolvedStream(
                singleUri = webpageUrl,
                durationMs = item.durationMs,
                resolverName = "direct"
            )
        }

        var result = withContext(Dispatchers.IO) {
            ytdlp.resolvePlaybackStream(
                url = webpageUrl,
                audioOnly = options.audioOnly || item.type == com.deniscerri.ytdl.playback.model.PlaybackType.AUDIO,
                videoHeightCap = options.videoHeightCap,
                preferHlsForLive = options.preferHlsForLive
            )
        }

        if (result.isFailure && SessionOrchestrator.isAuthOrCookieError(result.exceptionOrNull()?.message)) {
            Log.w(TAG, "Playback resolve hit an auth error; healing the session once")
            if (SessionOrchestrator.refreshYoutubeSession(appContext) ==
                SessionOrchestrator.RefreshOutcome.SUCCESS
            ) {
                result = withContext(Dispatchers.IO) {
                    ytdlp.resolvePlaybackStream(
                        url = webpageUrl,
                        audioOnly = options.audioOnly || item.type == com.deniscerri.ytdl.playback.model.PlaybackType.AUDIO,
                        videoHeightCap = options.videoHeightCap,
                        preferHlsForLive = options.preferHlsForLive
                    )
                }
            }
        }

        val info = result.getOrElse {
            throw PlaybackResolutionException(it.message ?: "Could not resolve stream", it)
        }

        return ResolvedStream(
            singleUri = info.singleUrl,
            audioUri = info.audioUrl,
            videoUri = info.videoUrl,
            mimeType = info.mimeType,
            durationMs = info.durationMs ?: item.durationMs,
            isLive = info.isLive,
            chapters = info.chapters,
            playbackChapters = info.playbackChapters,
            subtitles = info.subtitles,
            expiresAtMs = if (info.isLive) null else System.currentTimeMillis() + DEFAULT_URL_TTL_MS,
            resolverName = "yt-dlp"
        )
    }

    companion object {
        private const val TAG = "PlaybackStreamResolver"

        /** Conservative stream URL lifetime; YouTube URLs can expire after a few hours. */
        const val DEFAULT_URL_TTL_MS = 45 * 60 * 1000L

        fun isNetworkItem(item: PlaybackItem): Boolean =
            item.source == PlaybackSource.YOUTUBE && item.localUri.isNullOrBlank()
    }
}
