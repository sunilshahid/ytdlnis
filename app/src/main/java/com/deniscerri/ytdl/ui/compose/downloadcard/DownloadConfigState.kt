package com.deniscerri.ytdl.ui.compose.downloadcard

import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.Format

/**
 * State representation for the download configuration sheet (Phase 4c).
 *
 * Replaces the ~900 lines of imperative view mutation in UiUtil.configureVideo/configureAudio/configureCommand
 * with a single immutable state and pure reducer function.
 */
data class DownloadConfigState(
    val url: String = "",
    val title: String = "",
    val author: String = "",
    val duration: String = "",
    val thumbUrl: String = "",
    val downloadType: DownloadType = DownloadType.video,
    val selectedFormat: Format? = null,
    val availableFormats: List<Format> = emptyList(),
    val container: String = "mp4",
    val audioBitrate: String = "320",
    val recodeVideo: Boolean = false,
    val recodeAudio: Boolean = false,
    val embedSubtitles: Boolean = false,
    val embedThumbnail: Boolean = true,
    val embedChapters: Boolean = true,
    val sponsorBlock: Boolean = false,
    val cutStart: String = "",
    val cutEnd: String = "",
    val cropEnabled: Boolean = false,
    val customCommand: String = "",
) {
    fun reduce(event: DownloadConfigEvent): DownloadConfigState = when (event) {
        is DownloadConfigEvent.SelectFormat -> copy(selectedFormat = event.format)
        is DownloadConfigEvent.SelectContainer -> copy(container = event.container)
        is DownloadConfigEvent.SelectAudioBitrate -> copy(audioBitrate = event.bitrate)
        is DownloadConfigEvent.ToggleRecodeVideo -> copy(recodeVideo = event.enabled)
        is DownloadConfigEvent.ToggleRecodeAudio -> copy(recodeAudio = event.enabled)
        is DownloadConfigEvent.ToggleEmbedSubtitles -> copy(embedSubtitles = event.enabled)
        is DownloadConfigEvent.ToggleEmbedThumbnail -> copy(embedThumbnail = event.enabled)
        is DownloadConfigEvent.ToggleEmbedChapters -> copy(embedChapters = event.enabled)
        is DownloadConfigEvent.ToggleSponsorBlock -> copy(sponsorBlock = event.enabled)
        is DownloadConfigEvent.SetCutRange -> copy(cutStart = event.start, cutEnd = event.end)
        is DownloadConfigEvent.SetCustomCommand -> copy(customCommand = event.command)
    }
}

sealed interface DownloadConfigEvent {
    data class SelectFormat(val format: Format) : DownloadConfigEvent
    data class SelectContainer(val container: String) : DownloadConfigEvent
    data class SelectAudioBitrate(val bitrate: String) : DownloadConfigEvent
    data class ToggleRecodeVideo(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleRecodeAudio(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleEmbedSubtitles(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleEmbedThumbnail(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleEmbedChapters(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleSponsorBlock(val enabled: Boolean) : DownloadConfigEvent
    data class SetCutRange(val start: String, val end: String) : DownloadConfigEvent
    data class SetCustomCommand(val command: String) : DownloadConfigEvent
}
