package com.deniscerri.ytdl.playback.model

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.deniscerri.ytdl.database.models.PlaybackHistoryItem
import com.deniscerri.ytdl.database.models.PlaybackQueueItem
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.util.Extensions.convertToTimestamp
import com.deniscerri.ytdl.util.Extensions.getIDFromYoutubeURL
import org.json.JSONObject

enum class PlaybackType { AUDIO, VIDEO }

enum class PlaybackSource { YOUTUBE, LOCAL, DOWNLOAD }

/**
 * Engine-neutral description of one playable item. The player only ever sees this model through
 * [toMediaItem]; everything else (UI, queue, history, lyrics) works with this class.
 */
data class PlaybackItem(
    val mediaId: String,
    val type: PlaybackType,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val artworkUri: String? = null,
    val source: PlaybackSource,
    val videoId: String? = null,
    val webpageUrl: String? = null,
    val localUri: String? = null,
    val extras: Map<String, String> = emptyMap(),
    val isVault: Boolean = false
) {
    val isLocal: Boolean get() = !localUri.isNullOrBlank()

    val folderName: String?
        get() = extras["device.folder.path"]?.trim('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: extras["folderName"]
            ?: localUri?.let { runCatching { java.io.File(it).parentFile?.name }.getOrNull() }
            ?: album

    fun toMediaItem(
        audioUri: String? = null,
        videoUri: String? = null,
        mimeType: String? = null,
        subtitles: List<PlaybackSubtitle> = emptyList(),
        chapters: List<PlaybackChapter> = emptyList()
    ): MediaItem {
        if (isVault) {
            return MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri(localUri?.let(Uri::parse) ?: Uri.EMPTY)
                .setMimeType(mimeType)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(null)
                        .setArtist(null)
                        .setAlbumTitle(null)
                        .setArtworkUri(null)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        }
        val extras = Bundle().apply {
            putString(KEY_ITEM_JSON, toJson().toString())
            audioUri?.let { putString(KEY_AUDIO_URI, it) }
            videoUri?.let { putString(KEY_VIDEO_URI, it) }
            mimeType?.let { putString(KEY_MIME_TYPE, it) }
            if (subtitles.isNotEmpty()) {
                putString(KEY_SUBTITLES_JSON, PlaybackExtrasCodec.subtitlesToJson(subtitles))
            }
            if (chapters.isNotEmpty()) {
                putString(KEY_CHAPTERS_JSON, PlaybackExtrasCodec.chaptersToJson(chapters))
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(if (extras.size() > 0) extras else null)
            .build()

        val subtitleConfigs = subtitles.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.uri))
                .setId(sub.uri)
                .setMimeType(sub.mimeType)
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .setSelectionFlags(0)
                .build()
        }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(localUri?.let(Uri::parse) ?: webpageUrl?.let(Uri::parse) ?: Uri.EMPTY)
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
    }

    fun toQueueItem(position: Int, queueId: Long = 1L): PlaybackQueueItem = PlaybackQueueItem(
        queueId = queueId,
        position = position,
        mediaId = mediaId,
        type = type.name,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUri = artworkUri,
        source = source.name,
        videoId = videoId,
        webpageUrl = webpageUrl,
        localUri = localUri,
        extrasJson = extrasJson()
    )

    fun toHistory(): PlaybackHistoryItem = PlaybackHistoryItem(
        mediaId = mediaId,
        type = type.name,
        title = title,
        artist = artist,
        artworkUri = artworkUri,
        source = source.name,
        videoId = videoId,
        webpageUrl = webpageUrl,
        localUri = localUri,
        durationMs = durationMs ?: 0
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("mediaId", mediaId)
        put("type", type.name)
        put("title", title)
        put("artist", artist ?: JSONObject.NULL)
        put("album", album ?: JSONObject.NULL)
        put("durationMs", durationMs ?: JSONObject.NULL)
        put("artworkUri", artworkUri ?: JSONObject.NULL)
        put("source", source.name)
        put("videoId", videoId ?: JSONObject.NULL)
        put("webpageUrl", webpageUrl ?: JSONObject.NULL)
        put("localUri", localUri ?: JSONObject.NULL)
        put("extras", JSONObject(extras as Map<*, *>))
    }

    private fun extrasJson(): String? = if (extras.isEmpty()) null else JSONObject(extras as Map<*, *>).toString()

    companion object {
        const val KEY_ITEM_JSON = "playback.item.json"
        const val KEY_AUDIO_URI = "playback.audio.uri"
        const val KEY_VIDEO_URI = "playback.video.uri"
        const val KEY_MIME_TYPE = "playback.mime"
        const val KEY_SUBTITLES_JSON = "playback.subtitles.json"
        const val KEY_CHAPTERS_JSON = "playback.chapters.json"
        const val KEY_STREAM_EXPIRES_AT = "playback.expiresAt"

        fun fromJson(raw: String): PlaybackItem? = runCatching {
            val o = JSONObject(raw)
            PlaybackItem(
                mediaId = o.getString("mediaId"),
                type = PlaybackType.valueOf(o.optString("type", PlaybackType.AUDIO.name)),
                title = o.optString("title"),
                artist = o.optStringOrNull("artist"),
                album = o.optStringOrNull("album"),
                durationMs = if (o.isNull("durationMs")) null else o.optLong("durationMs"),
                artworkUri = o.optStringOrNull("artworkUri"),
                source = PlaybackSource.valueOf(o.optString("source", PlaybackSource.YOUTUBE.name)),
                videoId = o.optStringOrNull("videoId"),
                webpageUrl = o.optStringOrNull("webpageUrl"),
                localUri = o.optStringOrNull("localUri"),
                extras = o.optJSONObject("extras")?.let { obj ->
                    buildMap {
                        obj.keys().forEach { key -> put(key, obj.optString(key)) }
                    }
                } ?: emptyMap()
            )
        }.getOrNull()

        fun fromMediaItem(mediaItem: MediaItem): PlaybackItem? {
            mediaItem.mediaMetadata.extras?.getString(KEY_ITEM_JSON)?.let {
                return fromJson(it)
            }
            val uri = mediaItem.localConfiguration?.uri?.toString()
            return PlaybackItem(
                mediaId = mediaItem.mediaId,
                type = PlaybackType.AUDIO,
                title = mediaItem.mediaMetadata.title?.toString() ?: mediaItem.mediaId,
                artist = mediaItem.mediaMetadata.artist?.toString(),
                artworkUri = mediaItem.mediaMetadata.artworkUri?.toString(),
                source = PlaybackSource.YOUTUBE,
                webpageUrl = uri
            )
        }

        fun fromQueueItem(item: PlaybackQueueItem): PlaybackItem = PlaybackItem(
            mediaId = item.mediaId,
            type = runCatching { PlaybackType.valueOf(item.type) }.getOrDefault(PlaybackType.AUDIO),
            title = item.title,
            artist = item.artist,
            album = item.album,
            durationMs = item.durationMs,
            artworkUri = item.artworkUri,
            source = runCatching { PlaybackSource.valueOf(item.source) }.getOrDefault(PlaybackSource.YOUTUBE),
            videoId = item.videoId,
            webpageUrl = item.webpageUrl,
            localUri = item.localUri,
            extras = item.extrasJson?.let { raw ->
                runCatching {
                    val obj = JSONObject(raw)
                    buildMap { obj.keys().forEach { key -> put(key, obj.optString(key)) } }
                }.getOrNull()
            } ?: emptyMap()
        )

        /** Maps a completed download to a playable item. */
        fun fromHistory(item: HistoryItem): PlaybackItem {
            val localPath = item.downloadPath.firstOrNull()
            val isAudio = item.type.name == "audio"
            val videoId = runCatching { item.url.getIDFromYoutubeURL() }.getOrNull()
            return PlaybackItem(
                mediaId = "dl:${item.id}",
                type = if (isAudio) PlaybackType.AUDIO else PlaybackType.VIDEO,
                title = item.title.ifBlank { item.url },
                artist = item.author.takeIf { it.isNotBlank() },
                durationMs = runCatching { item.duration.convertToTimestamp() }
                    .getOrDefault(0L)
                    .takeIf { it > 0 },
                artworkUri = item.thumb.takeIf { it.isNotBlank() },
                source = PlaybackSource.DOWNLOAD,
                videoId = videoId,
                webpageUrl = item.url,
                localUri = localPath?.let {
                    if (it.startsWith("content://")) {
                        it
                    } else {
                        val file = java.io.File(it)
                        if (file.exists()) Uri.fromFile(file).toString() else null
                    }
                }
            )
        }

        /** Maps a recently played item back into a playable item. */
        fun fromPlaybackHistory(item: PlaybackHistoryItem): PlaybackItem = PlaybackItem(
            mediaId = item.mediaId,
            type = runCatching { PlaybackType.valueOf(item.type) }
                .getOrDefault(PlaybackType.AUDIO),
            title = item.title.ifBlank { item.webpageUrl ?: item.localUri ?: "Unknown" },
            artist = item.artist,
            durationMs = item.durationMs.takeIf { it > 0 },
            artworkUri = item.artworkUri,
            source = runCatching { PlaybackSource.valueOf(item.source) }
                .getOrDefault(PlaybackSource.YOUTUBE),
            videoId = item.videoId,
            webpageUrl = item.webpageUrl,
            localUri = item.localUri
        )

        private fun JSONObject.optStringOrNull(key: String): String? {
            if (isNull(key)) return null
            return optString(key).takeIf { it.isNotBlank() && it != "null" }
        }
    }
}
