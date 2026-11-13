package com.deniscerri.ytdl.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import com.deniscerri.ytdl.playback.model.PlaybackItem

/**
 * Builds merged video+audio sources for resolved YouTube items without leaking resolver details
 * into the session. The two URLs travel in [MediaItem] extras, which survive the controller ->
 * service hand-off; if only one URL exists the factory delegates to DefaultMediaSourceFactory.
 */
@UnstableApi
class PlaybackMediaSourceFactory(
    context: Context,
    dataSourceFactory: DataSource.Factory
) : MediaSource.Factory {

    private val delegate = DefaultMediaSourceFactory(dataSourceFactory)

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
    ): MediaSource.Factory {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val audio = extra(mediaItem, PlaybackItem.KEY_AUDIO_URI)
        val video = extra(mediaItem, PlaybackItem.KEY_VIDEO_URI)
        val mimeType = extra(mediaItem, PlaybackItem.KEY_MIME_TYPE)

        if (!audio.isNullOrBlank() && !video.isNullOrBlank()) {
            val videoMetadata = mediaItem.mediaMetadata.buildUpon()
                .setExtras(null)
                .build()
            val videoItem = mediaItem.buildUpon()
                .setUri(Uri.parse(video))
                .setMediaMetadata(videoMetadata)
                .build()
            val audioItem = MediaItem.Builder()
                .setUri(Uri.parse(audio))
                .setMediaId("${mediaItem.mediaId}:audio")
                .build()
            return MergingMediaSource(
                delegate.createMediaSource(videoItem),
                delegate.createMediaSource(audioItem)
            )
        }

        val singleUri = audio ?: video
        val item = if (!singleUri.isNullOrBlank()) {
            mediaItem.buildUpon()
                .setUri(Uri.parse(singleUri))
                .apply { if (!mimeType.isNullOrBlank()) setMimeType(mimeType) }
                .build()
        } else {
            mediaItem
        }
        return delegate.createMediaSource(item)
    }

    /**
     * Reads resolver output stored in mediaMetadata extras so it produces a playable
     * source instead of falling back to the raw URL.
     */
    private fun extra(mediaItem: MediaItem, key: String): String? =
        mediaItem.mediaMetadata.extras?.getString(key)?.takeIf { it.isNotBlank() }
}
