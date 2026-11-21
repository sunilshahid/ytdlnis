package com.deniscerri.ytdl.ui.compose.downloadcard

import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format

/**
 * State representation for the download configuration sheet (Phase 4c / eleXML §5 Group A).
 *
 * Replaces the ~900 lines of imperative view mutation in UiUtil.configureVideo/configureAudio/configureCommand
 * with a single immutable state and pure reducer function, ensuring 100% command parity.
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
    val selectedAudioFormat: Format? = null,
    val availableAudioFormats: List<Format> = emptyList(),
    val container: String = "mp4",
    val audioBitrate: String = "320",
    val recodeVideo: Boolean = false,
    val recodeAudio: Boolean = false,
    val compatibilityMode: Boolean = false,
    val removeAudio: Boolean = false,
    val alsoDownloadAsAudio: Boolean = false,
    val embedSubtitles: Boolean = false,
    val writeSubtitles: Boolean = false,
    val writeAutoSubtitles: Boolean = false,
    val burnSubtitles: Boolean = false,
    val subtitleLanguages: String = ".*-orig",
    val embedThumbnail: Boolean = true,
    val saveThumbnail: Boolean = false,
    val cropThumbnail: Boolean = false,
    val embedChapters: Boolean = true,
    val splitByChapters: Boolean = false,
    val embedLyrics: Boolean = true,
    val embedTtml: Boolean = true,
    val saveLrc: Boolean = false,
    val sponsorBlock: Boolean = false,
    val sponsorBlockFilters: List<String> = emptyList(),
    val cutStart: String = "",
    val cutEnd: String = "",
    val mergeCuts: Boolean = false,
    val cropEnabled: Boolean = false,
    val cropValues: String = "",
    val liveFromStart: Boolean = false,
    val waitForVideoMinutes: Int = 0,
    val customCommand: String = "",
    val extraCommands: String = "",
    val customFileNameTemplate: String = "",
    val incognito: Boolean = false,
    val vaultMode: Boolean = false,
    val isScheduled: Boolean = false,
    val scheduledTime: Long = 0L,
) {
    fun reduce(event: DownloadConfigEvent): DownloadConfigState = when (event) {
        is DownloadConfigEvent.SelectDownloadType -> copy(downloadType = event.type)
        is DownloadConfigEvent.SelectFormat -> copy(selectedFormat = event.format)
        is DownloadConfigEvent.SelectAudioFormat -> copy(selectedAudioFormat = event.format)
        is DownloadConfigEvent.SelectContainer -> copy(container = event.container)
        is DownloadConfigEvent.SelectAudioBitrate -> copy(audioBitrate = event.bitrate)
        is DownloadConfigEvent.ToggleRecodeVideo -> copy(recodeVideo = event.enabled)
        is DownloadConfigEvent.ToggleRecodeAudio -> copy(recodeAudio = event.enabled)
        is DownloadConfigEvent.ToggleCompatibilityMode -> copy(compatibilityMode = event.enabled)
        is DownloadConfigEvent.ToggleRemoveAudio -> copy(removeAudio = event.enabled)
        is DownloadConfigEvent.ToggleAlsoDownloadAsAudio -> copy(alsoDownloadAsAudio = event.enabled)
        is DownloadConfigEvent.ToggleEmbedSubtitles -> copy(embedSubtitles = event.enabled)
        is DownloadConfigEvent.ToggleWriteSubtitles -> copy(writeSubtitles = event.enabled)
        is DownloadConfigEvent.ToggleWriteAutoSubtitles -> copy(writeAutoSubtitles = event.enabled)
        is DownloadConfigEvent.ToggleBurnSubtitles -> copy(burnSubtitles = event.enabled)
        is DownloadConfigEvent.SetSubtitleLanguages -> copy(subtitleLanguages = event.languages)
        is DownloadConfigEvent.ToggleEmbedThumbnail -> copy(embedThumbnail = event.enabled)
        is DownloadConfigEvent.ToggleSaveThumbnail -> copy(saveThumbnail = event.enabled)
        is DownloadConfigEvent.ToggleCropThumbnail -> copy(cropThumbnail = event.enabled)
        is DownloadConfigEvent.ToggleEmbedChapters -> copy(embedChapters = event.enabled)
        is DownloadConfigEvent.ToggleSplitByChapters -> copy(splitByChapters = event.enabled)
        is DownloadConfigEvent.ToggleEmbedLyrics -> copy(embedLyrics = event.enabled)
        is DownloadConfigEvent.ToggleEmbedTtml -> copy(embedTtml = event.enabled)
        is DownloadConfigEvent.ToggleSaveLrc -> copy(saveLrc = event.enabled)
        is DownloadConfigEvent.ToggleSponsorBlock -> copy(
            sponsorBlock = event.enabled,
            sponsorBlockFilters = if (event.enabled && sponsorBlockFilters.isEmpty()) {
                listOf("sponsor", "intro", "outro", "selfpromo", "preview", "filler")
            } else if (!event.enabled) emptyList() else sponsorBlockFilters
        )
        is DownloadConfigEvent.SetSponsorBlockFilters -> copy(
            sponsorBlockFilters = event.filters,
            sponsorBlock = event.filters.isNotEmpty()
        )
        is DownloadConfigEvent.SetCutRange -> copy(cutStart = event.start, cutEnd = event.end)
        is DownloadConfigEvent.ToggleMergeCuts -> copy(mergeCuts = event.enabled)
        is DownloadConfigEvent.SetCropValues -> copy(cropValues = event.cropValues, cropEnabled = event.cropValues.isNotBlank())
        is DownloadConfigEvent.ToggleLiveFromStart -> copy(liveFromStart = event.enabled)
        is DownloadConfigEvent.SetWaitForVideoMinutes -> copy(waitForVideoMinutes = event.minutes)
        is DownloadConfigEvent.SetCustomCommand -> copy(customCommand = event.command)
        is DownloadConfigEvent.SetExtraCommands -> copy(extraCommands = event.commands)
        is DownloadConfigEvent.SetCustomFileNameTemplate -> copy(customFileNameTemplate = event.template)
        is DownloadConfigEvent.ToggleIncognito -> copy(incognito = event.enabled)
        is DownloadConfigEvent.ToggleVaultMode -> copy(vaultMode = event.enabled, incognito = if (event.enabled) true else incognito)
        is DownloadConfigEvent.SetScheduled -> copy(isScheduled = event.scheduled, scheduledTime = event.time)
    }

    fun applyToDownloadItem(item: DownloadItem): DownloadItem {
        item.type = downloadType
        item.container = container
        if (selectedFormat != null) {
            item.format = selectedFormat
        }
        if (availableFormats.isNotEmpty()) {
            item.allFormats = availableFormats.toMutableList()
        }

        // Cut / sections
        if (cutStart.isNotBlank() || cutEnd.isNotBlank()) {
            val startStr = cutStart.ifBlank { "00:00:00" }
            val endStr = cutEnd.ifBlank { item.duration.ifBlank { "99:99:99" } }
            item.downloadSections = "*$startStr-$endStr"
        } else {
            item.downloadSections = ""
        }
        item.mergeCuts = mergeCuts

        item.SaveThumb = saveThumbnail
        item.incognito = incognito || vaultMode
        item.vaultMode = vaultMode

        if (customFileNameTemplate.isNotBlank()) {
            item.customFileNameTemplate = customFileNameTemplate
        }
        item.extraCommands = extraCommands

        // Video Preferences
        item.videoPreferences.embedSubs = embedSubtitles
        item.videoPreferences.writeSubs = writeSubtitles
        item.videoPreferences.writeAutoSubs = writeAutoSubtitles
        item.videoPreferences.burnSubs = burnSubtitles
        item.videoPreferences.subsLanguages = subtitleLanguages
        item.videoPreferences.addChapters = embedChapters
        item.videoPreferences.splitByChapters = splitByChapters
        item.videoPreferences.embedThumbnail = embedThumbnail
        item.videoPreferences.removeAudio = removeAudio
        item.videoPreferences.alsoDownloadAsAudio = alsoDownloadAsAudio
        item.videoPreferences.recodeVideo = recodeVideo
        item.videoPreferences.compatibilityMode = compatibilityMode
        item.videoPreferences.liveFromStart = liveFromStart
        item.videoPreferences.waitForVideoMinutes = waitForVideoMinutes
        item.videoPreferences.cropValues = cropValues
        if (selectedAudioFormat != null) {
            item.videoPreferences.audioFormatIDs = arrayListOf(selectedAudioFormat.format_id)
        }
        item.videoPreferences.sponsorBlockFilters = ArrayList(sponsorBlockFilters)

        // Audio Preferences
        item.audioPreferences.bitrate = audioBitrate
        item.audioPreferences.embedThumb = embedThumbnail
        item.audioPreferences.cropThumb = cropThumbnail
        item.audioPreferences.embedLyrics = embedLyrics
        item.audioPreferences.embedTtml = embedTtml
        item.audioPreferences.saveLrc = saveLrc
        item.audioPreferences.splitByChapters = splitByChapters
        item.audioPreferences.sponsorBlockFilters = ArrayList(sponsorBlockFilters)

        if (downloadType == DownloadType.command && customCommand.isNotBlank()) {
            item.customFileNameTemplate = customCommand
        }

        if (isScheduled && scheduledTime > 0L) {
            item.downloadStartTime = scheduledTime
            item.status = com.deniscerri.ytdl.database.repository.DownloadRepository.Status.Scheduled.toString()
        }

        return item
    }

    companion object {
        fun fromDownloadItem(item: DownloadItem): DownloadConfigState {
            val cutPair = if (item.downloadSections.startsWith("*")) {
                val cleaned = item.downloadSections.removePrefix("*")
                val parts = cleaned.split("-")
                Pair(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
            } else {
                Pair("", "")
            }

            val isAudio = item.type == DownloadType.audio
            val isVideo = item.type == DownloadType.video

            return DownloadConfigState(
                url = item.url,
                title = item.title,
                author = item.author,
                duration = item.duration,
                thumbUrl = item.thumb,
                downloadType = item.type,
                selectedFormat = item.format,
                availableFormats = item.allFormats,
                container = item.container.ifBlank { if (isAudio) "mp3" else "mp4" },
                audioBitrate = item.audioPreferences.bitrate.ifBlank { "320" },
                recodeVideo = item.videoPreferences.recodeVideo,
                compatibilityMode = item.videoPreferences.compatibilityMode,
                removeAudio = item.videoPreferences.removeAudio,
                alsoDownloadAsAudio = item.videoPreferences.alsoDownloadAsAudio,
                embedSubtitles = item.videoPreferences.embedSubs,
                writeSubtitles = item.videoPreferences.writeSubs,
                writeAutoSubtitles = item.videoPreferences.writeAutoSubs,
                burnSubtitles = item.videoPreferences.burnSubs,
                subtitleLanguages = item.videoPreferences.subsLanguages.ifBlank { ".*-orig" },
                embedThumbnail = if (isAudio) item.audioPreferences.embedThumb else item.videoPreferences.embedThumbnail,
                saveThumbnail = item.SaveThumb,
                cropThumbnail = item.audioPreferences.cropThumb ?: false,
                embedChapters = if (isVideo) item.videoPreferences.addChapters else false,
                splitByChapters = if (isAudio) item.audioPreferences.splitByChapters else item.videoPreferences.splitByChapters,
                embedLyrics = item.audioPreferences.embedLyrics,
                embedTtml = item.audioPreferences.embedTtml,
                saveLrc = item.audioPreferences.saveLrc,
                sponsorBlock = item.videoPreferences.sponsorBlockFilters.isNotEmpty() || item.audioPreferences.sponsorBlockFilters.isNotEmpty(),
                sponsorBlockFilters = if (isAudio) item.audioPreferences.sponsorBlockFilters else item.videoPreferences.sponsorBlockFilters,
                cutStart = cutPair.first,
                cutEnd = cutPair.second,
                mergeCuts = item.mergeCuts,
                cropEnabled = item.videoPreferences.cropValues.isNotBlank(),
                cropValues = item.videoPreferences.cropValues,
                liveFromStart = item.videoPreferences.liveFromStart,
                waitForVideoMinutes = item.videoPreferences.waitForVideoMinutes,
                customCommand = if (item.type == DownloadType.command) item.customFileNameTemplate else "",
                extraCommands = item.extraCommands,
                customFileNameTemplate = item.customFileNameTemplate,
                incognito = item.incognito,
                vaultMode = item.vaultMode,
                isScheduled = item.downloadStartTime > 0L,
                scheduledTime = item.downloadStartTime
            )
        }
    }
}

sealed interface DownloadConfigEvent {
    data class SelectDownloadType(val type: DownloadType) : DownloadConfigEvent
    data class SelectFormat(val format: Format) : DownloadConfigEvent
    data class SelectAudioFormat(val format: Format) : DownloadConfigEvent
    data class SelectContainer(val container: String) : DownloadConfigEvent
    data class SelectAudioBitrate(val bitrate: String) : DownloadConfigEvent
    data class ToggleRecodeVideo(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleRecodeAudio(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleCompatibilityMode(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleRemoveAudio(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleAlsoDownloadAsAudio(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleEmbedSubtitles(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleWriteSubtitles(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleWriteAutoSubtitles(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleBurnSubtitles(val enabled: Boolean) : DownloadConfigEvent
    data class SetSubtitleLanguages(val languages: String) : DownloadConfigEvent
    data class ToggleEmbedThumbnail(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleSaveThumbnail(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleCropThumbnail(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleEmbedChapters(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleSplitByChapters(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleEmbedLyrics(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleEmbedTtml(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleSaveLrc(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleSponsorBlock(val enabled: Boolean) : DownloadConfigEvent
    data class SetSponsorBlockFilters(val filters: List<String>) : DownloadConfigEvent
    data class SetCutRange(val start: String, val end: String) : DownloadConfigEvent
    data class ToggleMergeCuts(val enabled: Boolean) : DownloadConfigEvent
    data class SetCropValues(val cropValues: String) : DownloadConfigEvent
    data class ToggleLiveFromStart(val enabled: Boolean) : DownloadConfigEvent
    data class SetWaitForVideoMinutes(val minutes: Int) : DownloadConfigEvent
    data class SetCustomCommand(val command: String) : DownloadConfigEvent
    data class SetExtraCommands(val commands: String) : DownloadConfigEvent
    data class SetCustomFileNameTemplate(val template: String) : DownloadConfigEvent
    data class ToggleIncognito(val enabled: Boolean) : DownloadConfigEvent
    data class ToggleVaultMode(val enabled: Boolean) : DownloadConfigEvent
    data class SetScheduled(val scheduled: Boolean, val time: Long) : DownloadConfigEvent
}
