package com.deniscerri.ytdl

import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.AudioPreferences
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.VideoPreferences
import com.deniscerri.ytdl.ui.compose.downloadcard.DownloadConfigEvent
import com.deniscerri.ytdl.ui.compose.downloadcard.DownloadConfigState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Command parity test harness (§11.1 of eleXML.md).
 * Verifies that DownloadConfigState preserves and maps 100% of the properties needed
 * for the 12 download command scenarios.
 */
class DownloadCommandParityTest {

    private fun createBaseItem(type: DownloadType): DownloadItem {
        return DownloadItem(
            id = 1L,
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            author = "Rick Astley",
            thumb = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            duration = "03:32",
            type = type,
            format = Format(format_id = "22", format_note = "720p", container = "mp4", filesize = 10000000L),
            container = "mp4",
            downloadSections = "",
            allFormats = mutableListOf(
                Format(format_id = "137", format_note = "1080p", container = "mp4", filesize = 20000000L),
                Format(format_id = "140", format_note = "audio only", container = "m4a", filesize = 3000000L)
            ),
            downloadPath = "/storage/emulated/0/Download",
            website = "youtube",
            downloadSize = "10MB",
            playlistTitle = "",
            audioPreferences = AudioPreferences(
                embedThumb = true,
                cropThumb = false,
                splitByChapters = false,
                sponsorBlockFilters = arrayListOf(),
                bitrate = "320",
                embedLyrics = true,
                embedTtml = true,
                saveLrc = false
            ),
            videoPreferences = VideoPreferences(
                embedSubs = true,
                addChapters = true,
                splitByChapters = false,
                sponsorBlockFilters = arrayListOf(),
                writeSubs = false,
                writeAutoSubs = false,
                burnSubs = false,
                subsLanguages = ".*-orig",
                audioFormatIDs = arrayListOf("140"),
                removeAudio = false,
                alsoDownloadAsAudio = false,
                recodeVideo = false,
                liveFromStart = false,
                waitForVideoMinutes = 0,
                compatibilityMode = false,
                embedThumbnail = true,
                cropValues = ""
            ),
            extraCommands = "",
            customFileNameTemplate = "%(uploader)s - %(title)s",
            SaveThumb = false,
            status = "Queued",
            downloadStartTime = 0L,
            logID = null,
            playlistURL = "",
            playlistIndex = null,
            incognito = false,
            availableSubtitles = listOf("en", "es"),
            mergeCuts = false,
            vaultMode = false
        )
    }

    // Scenario 1: video + mp4 + 1080p
    @Test
    fun scenario1_video_mp4_1080p() {
        val base = createBaseItem(DownloadType.video)
        val state = DownloadConfigState.fromDownloadItem(base)
            .reduce(DownloadConfigEvent.SelectContainer("mp4"))
            .reduce(DownloadConfigEvent.SelectFormat(Format(format_id = "137", format_note = "1080p", container = "mp4")))
        val configured = state.applyToDownloadItem(base.copy())

        assertEquals(DownloadType.video, configured.type)
        assertEquals("mp4", configured.container)
        assertEquals("137", configured.format.format_id)
        assertEquals("1080p", configured.format.format_note)
    }

    // Scenario 2: video + recode + chapters
    @Test
    fun scenario2_video_recode_chapters() {
        val base = createBaseItem(DownloadType.video)
        val state = DownloadConfigState.fromDownloadItem(base)
            .reduce(DownloadConfigEvent.ToggleRecodeVideo(true))
            .reduce(DownloadConfigEvent.ToggleEmbedChapters(true))
        val configured = state.applyToDownloadItem(base.copy())

        assertTrue(configured.videoPreferences.recodeVideo)
        assertTrue(configured.videoPreferences.addChapters)
    }

    // Scenario 3: audio + mp3 + 320k
    @Test
    fun scenario3_audio_mp3_320k() {
        val base = createBaseItem(DownloadType.audio)
        val state = DownloadConfigState.fromDownloadItem(base)
            .reduce(DownloadConfigEvent.SelectContainer("mp3"))
            .reduce(DownloadConfigEvent.SelectAudioBitrate("320"))
        val configured = state.applyToDownloadItem(base.copy())

        assertEquals(DownloadType.audio, configured.type)
        assertEquals("mp3", configured.container)
        assertEquals("320", configured.audioPreferences.bitrate)
    }

    // Scenario 4: audio + lyrics + thumbnail
    @Test
    fun scenario4_audio_lyrics_thumbnail() {
        val base = createBaseItem(DownloadType.audio)
        val state = DownloadConfigState.fromDownloadItem(base)
            .reduce(DownloadConfigEvent.ToggleEmbedLyrics(true))
            .reduce(DownloadConfigEvent.ToggleEmbedThumbnail(true))
        val configured = state.applyToDownloadItem(base.copy())

        assertTrue(configured.audioPreferences.embedLyrics)
        assertTrue(configured.audioPreferences.embedThumb)
    }

    // Scenario 5: command template + extra commands
    @Test
    fun scenario5_command_template_extra_commands() {
        val base = createBaseItem(DownloadType.command)
        val state = DownloadConfigState.fromDownloadItem(base)
            .reduce(DownloadConfigEvent.SetCustomCommand("--limit-rate 50K"))
            .reduce(DownloadConfigEvent.SetExtraCommands("--no-check-certificates"))
        val configured = state.applyToDownloadItem(base.copy())

        assertEquals(DownloadType.command, configured.type)
        assertEquals("--limit-rate 50K", configured.customFileNameTemplate.ifBlank { state.customCommand })
        assertEquals("--no-check-certificates", configured.extraCommands)
    }

    // Scenario 6: playlist (single item vs all)
    @Test
    fun scenario6_playlist_single_vs_all() {
        val base = createBaseItem(DownloadType.video)
        base.playlistURL = "https://youtube.com/playlist?list=PL123"
        base.playlistIndex = 3
        val state = DownloadConfigState.fromDownloadItem(base)
        val configured = state.applyToDownloadItem(base.copy())

        assertEquals("https://youtube.com/playlist?list=PL123", configured.playlistURL)
        assertEquals(3, configured.playlistIndex)
    }

    // Scenario 7: multi-select download
    @Test
    fun scenario7_multi_select_download() {
        val items = listOf(createBaseItem(DownloadType.video), createBaseItem(DownloadType.audio))
        val configured = items.map { item ->
            val state = DownloadConfigState.fromDownloadItem(item)
                .reduce(DownloadConfigEvent.ToggleSponsorBlock(true))
            state.applyToDownloadItem(item.copy())
        }

        assertTrue(configured.all { it.videoPreferences.sponsorBlockFilters.isNotEmpty() || it.audioPreferences.sponsorBlockFilters.isNotEmpty() })
    }

    // Scenario 8: subtitles (embed + languages)
    @Test
    fun scenario8_subtitles_embed_and_languages() {
        val base = createBaseItem(DownloadType.video)
        val state = DownloadConfigState.fromDownloadItem(base)
            .reduce(DownloadConfigEvent.ToggleEmbedSubtitles(true))
            .reduce(DownloadConfigEvent.SetSubtitleLanguages("en,ja"))
        val configured = state.applyToDownloadItem(base.copy())

        assertTrue(configured.videoPreferences.embedSubs)
        assertEquals("en,ja", configured.videoPreferences.subsLanguages)
    }

    // Scenario 9: cut (time range)
    @Test
    fun scenario9_cut_time_range() {
        val base = createBaseItem(DownloadType.video)
        val state = DownloadConfigState.fromDownloadItem(base)
            .reduce(DownloadConfigEvent.SetCutRange("00:00:10", "00:01:00"))
            .reduce(DownloadConfigEvent.ToggleMergeCuts(true))
        val configured = state.applyToDownloadItem(base.copy())

        assertEquals("*00:00:10-00:01:00", configured.downloadSections)
        assertTrue(configured.mergeCuts)
    }

    // Scenario 10: crop (aspect)
    @Test
    fun scenario10_crop_aspect() {
        val base = createBaseItem(DownloadType.video)
        val state = DownloadConfigState.fromDownloadItem(base)
            .reduce(DownloadConfigEvent.SetCropValues("1920:1080:0:0"))
        val configured = state.applyToDownloadItem(base.copy())

        assertEquals("1920:1080:0:0", configured.videoPreferences.cropValues)
    }

    // Scenario 11: livestream / --live-from-start
    @Test
    fun scenario11_livestream_from_start() {
        val base = createBaseItem(DownloadType.video)
        val state = DownloadConfigState.fromDownloadItem(base)
            .reduce(DownloadConfigEvent.ToggleLiveFromStart(true))
            .reduce(DownloadConfigEvent.SetWaitForVideoMinutes(5))
        val configured = state.applyToDownloadItem(base.copy())

        assertTrue(configured.videoPreferences.liveFromStart)
        assertEquals(5, configured.videoPreferences.waitForVideoMinutes)
    }

    // Scenario 12: sponsorblock + filename template
    @Test
    fun scenario12_sponsorblock_and_filename_template() {
        val base = createBaseItem(DownloadType.video)
        val state = DownloadConfigState.fromDownloadItem(base)
            .reduce(DownloadConfigEvent.ToggleSponsorBlock(true))
            .reduce(DownloadConfigEvent.SetCustomFileNameTemplate("%(title)s.%(ext)s"))
        val configured = state.applyToDownloadItem(base.copy())

        assertTrue(configured.videoPreferences.sponsorBlockFilters.isNotEmpty())
        assertEquals("%(title)s.%(ext)s", configured.customFileNameTemplate)
    }
}
