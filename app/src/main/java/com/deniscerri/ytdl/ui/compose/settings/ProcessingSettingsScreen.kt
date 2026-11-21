package com.deniscerri.ytdl.ui.compose.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R

@Composable
fun ProcessingSettingsScreen(
    onSelectAudioBitrate: () -> Unit,
    audioBitrateSummary: String,
    onSelectSubtitleLanguages: () -> Unit,
    subtitleLanguagesSummary: String,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val sponsorBlockEntries = stringArrayResource(R.array.sponsorblock_settings_entries).toList()
    val sponsorBlockValues = stringArrayResource(R.array.sponsorblock_settings_values).toList()

    val languageNames = stringArrayResource(R.array.language_names).toList()
    val languageCodes = stringArrayResource(R.array.language_codes).toList()

    val audioCodecs = stringArrayResource(R.array.audio_codec).toList()
    val audioCodecValues = stringArrayResource(R.array.audio_codec_values).toList()

    val audioContainers = stringArrayResource(R.array.audio_containers).toList()
    val audioContainerValues = stringArrayResource(R.array.audio_containers_values).toList()

    val subFormats = stringArrayResource(R.array.sub_formats).toList()
    val subFormatValues = stringArrayResource(R.array.sub_formats_values).toList()

    val thumbContainers = stringArrayResource(R.array.thumbnail_containers).toList()
    val thumbContainerValues = stringArrayResource(R.array.thumbnail_containers_values).toList()

    val videoContainers = stringArrayResource(R.array.video_containers).toList()
    val videoContainerValues = stringArrayResource(R.array.video_containers_values).toList()

    val videoCodecs = stringArrayResource(R.array.video_codec).toList()
    val videoCodecValues = stringArrayResource(R.array.video_codec_values).toList()

    val videoFormats = stringArrayResource(R.array.video_formats).toList()
    val videoFormatValues = stringArrayResource(R.array.video_formats_values).toList()

    val ffmpegPresets = stringArrayResource(R.array.internal_plugin_ffmpeg_preset).toList()
    val ffmpegPresetValues = stringArrayResource(R.array.internal_plugin_ffmpeg_preset_values).toList()

    val preferredFormatSizes = stringArrayResource(R.array.preferred_format_size).toList()
    val preferredFormatSizeValues = stringArrayResource(R.array.preferred_format_size_values).toList()

    var useSponsorBlock by remember { mutableStateOf(prefs.getBoolean("use_sponsorblock", true)) }
    var embedThumbnail by remember { mutableStateOf(prefs.getBoolean("embed_thumbnail", true)) }
    var compatibleVideo by remember { mutableStateOf(prefs.getBoolean("compatible_video", false)) }
    var recodeVideo by remember { mutableStateOf(prefs.getBoolean("recode_video", false)) }

    SettingsScreenColumn(modifier = modifier) {
        // General
        SettingsCategory(title = stringResource(R.string.general))

        // SponsorBlock
        SettingsSwitchPreference(
            title = stringResource(R.string.use_sponsorblock),
            key = "use_sponsorblock",
            defaultValue = true,
            iconRes = R.drawable.ic_money,
            onCheckedChange = { useSponsorBlock = it }
        )

        SettingsMultiSelectListPreference(
            title = "SponsorBlock",
            dialogTitle = stringResource(R.string.select_sponsorblock_filtering),
            summary = stringResource(R.string.select_sponsorblock_filtering),
            key = "sponsorblock_filters",
            defaultValue = emptySet(),
            entries = sponsorBlockEntries,
            entryValues = sponsorBlockValues,
            enabled = useSponsorBlock,
            iconRes = R.drawable.ic_money,
        )

        SettingsEditTextPreference(
            title = stringResource(R.string.sponsorblock_api_url),
            key = "sponsorblock_url",
            defaultValue = "",
            enabled = useSponsorBlock,
            iconRes = R.drawable.ic_money,
        )

        // mtime
        SettingsSwitchPreference(
            title = stringResource(R.string.enable_mtime),
            summary = stringResource(R.string.enable_mtime_summary),
            key = "mtime",
            defaultValue = false,
            iconRes = R.drawable.ic_clock,
        )

        // Write description
        SettingsSwitchPreference(
            title = stringResource(R.string.write_description),
            summary = stringResource(R.string.write_description_summary),
            key = "write_description",
            defaultValue = false,
            iconRes = R.drawable.baseline_description_24,
        )

        // Extra commands
        SettingsSwitchPreference(
            title = stringResource(R.string.use_extra_commands),
            summary = stringResource(R.string.use_extra_commands_summary),
            key = "use_extra_commands",
            defaultValue = true,
            iconRes = R.drawable.ic_terminal,
        )

        // Force keyframes
        SettingsSwitchPreference(
            title = stringResource(R.string.force_keyframes),
            summary = stringResource(R.string.force_keyframes_summary),
            key = "force_keyframes",
            defaultValue = false,
            iconRes = R.drawable.ic_cut,
        )

        // Embed metadata
        SettingsSwitchPreference(
            title = stringResource(R.string.embed_metadata),
            summary = stringResource(R.string.embed_metadata_summary),
            key = "embed_metadata",
            defaultValue = true,
            iconRes = R.drawable.baseline_video_metadata,
        )

        // Audio
        SettingsCategory(title = stringResource(R.string.audio))

        SettingsPreference(
            title = stringResource(R.string.bitrate),
            summary = audioBitrateSummary.ifBlank { stringResource(R.string.defaultValue) },
            iconRes = R.drawable.baseline_high_quality_24,
            showChevron = true,
            onClick = onSelectAudioBitrate,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.embed_thumb),
            summary = stringResource(R.string.embed_thumb_summary),
            key = "embed_thumbnail",
            defaultValue = true,
            iconRes = R.drawable.ic_image,
            onCheckedChange = { embedThumbnail = it }
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.crop_thumb),
            summary = stringResource(R.string.crop_thumb_summary),
            key = "crop_thumbnail",
            defaultValue = true,
            enabled = embedThumbnail,
            iconRes = R.drawable.ic_cut,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.embed_lyrics),
            summary = stringResource(R.string.embed_lyrics_summary),
            key = "embed_lyrics",
            defaultValue = true,
            iconRes = R.drawable.ic_lyrics,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.auto_embed_lyrics),
            summary = stringResource(R.string.auto_embed_lyrics_summary),
            key = "auto_embed_lyrics",
            defaultValue = true,
            iconRes = R.drawable.ic_lyrics,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.save_lrc_file),
            summary = stringResource(R.string.save_lrc_file_summary),
            key = "save_lrc",
            defaultValue = false,
            iconRes = R.drawable.ic_lyrics,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.embed_ttml),
            summary = stringResource(R.string.embed_ttml_summary),
            key = "embed_ttml",
            defaultValue = true,
            iconRes = R.drawable.ic_lyrics,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.playlist_as_album),
            summary = stringResource(R.string.playlist_as_album_summary),
            key = "playlist_as_album",
            defaultValue = true,
            iconRes = R.drawable.baseline_album_24,
        )

        SettingsListPreference(
            title = stringResource(R.string.preferred_audio_language),
            key = "audio_language",
            defaultValue = "",
            entries = languageNames,
            entryValues = languageCodes,
            iconRes = R.drawable.ic_language,
        )

        SettingsListPreference(
            title = stringResource(R.string.preferred_audio_codec),
            key = "audio_codec",
            defaultValue = "",
            entries = audioCodecs,
            entryValues = audioCodecValues,
            enabled = !compatibleVideo,
            iconRes = R.drawable.ic_code,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.prefer_container_over_codec_audio),
            summary = stringResource(R.string.prefer_container_over_codec_audio_summary),
            key = "prefer_container_over_codec_audio",
            defaultValue = false,
            iconRes = R.drawable.ic_format,
        )

        SettingsListPreference(
            title = stringResource(R.string.audio_format),
            key = "audio_format",
            defaultValue = "",
            entries = audioContainers,
            entryValues = audioContainerValues,
            iconRes = R.drawable.ic_code,
        )

        SettingsEditTextPreference(
            title = "${stringResource(R.string.preferred_format_id)} [${stringResource(R.string.audio)}]",
            summary = stringResource(R.string.preferred_format_id_summary),
            key = "format_id_audio",
            defaultValue = "",
            iconRes = R.drawable.ic_format_id,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.prefer_drc_audio),
            key = "prefer_drc_audio",
            defaultValue = false,
            iconRes = R.drawable.baseline_audio_file_24,
        )

        // Video
        SettingsCategory(title = stringResource(R.string.video))

        SettingsSwitchPreference(
            title = stringResource(R.string.embed_subtitles),
            summary = stringResource(R.string.embed_subs_summary),
            key = "embed_subtitles",
            defaultValue = true,
            iconRes = R.drawable.ic_subtitles,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.save_subs),
            summary = stringResource(R.string.save_subs_desc),
            key = "write_subtitles",
            defaultValue = false,
            iconRes = R.drawable.ic_subtitles_alternative,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.save_auto_subs),
            summary = stringResource(R.string.save_subs_desc),
            key = "write_auto_subtitles",
            defaultValue = false,
            iconRes = R.drawable.ic_subtitles_alternative,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.no_keep_subs),
            summary = stringResource(R.string.no_keep_subs_summary),
            key = "no_keep_subs",
            defaultValue = true,
            iconRes = R.drawable.ic_subtitles,
        )

        SettingsPreference(
            title = stringResource(R.string.subtitle_languages),
            summary = subtitleLanguagesSummary,
            iconRes = R.drawable.baseline_subject_24,
            showChevron = true,
            onClick = onSelectSubtitleLanguages,
        )

        SettingsListPreference(
            title = stringResource(R.string.subtitle_format),
            key = "sub_format",
            defaultValue = "",
            entries = subFormats,
            entryValues = subFormatValues,
            iconRes = R.drawable.baseline_format_quote_24,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.save_thumb),
            summary = stringResource(R.string.save_thumb_summary),
            key = "write_thumbnail",
            defaultValue = false,
            iconRes = R.drawable.ic_image,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.embed_thumb),
            summary = stringResource(R.string.embed_thumb_summary),
            key = "video_embed_thumbnail",
            defaultValue = false,
            iconRes = R.drawable.ic_image,
        )

        SettingsListPreference(
            title = stringResource(R.string.thumbnail_format),
            key = "thumbnail_format",
            defaultValue = "jpg",
            entries = thumbContainers,
            entryValues = thumbContainerValues,
            iconRes = R.drawable.ic_image,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.add_chapters),
            summary = stringResource(R.string.add_chapters_summary),
            key = "add_chapters",
            defaultValue = true,
            iconRes = R.drawable.ic_chapters,
        )

        SettingsListPreference(
            title = stringResource(R.string.video_format),
            key = "video_format",
            defaultValue = "",
            entries = videoContainers,
            entryValues = videoContainerValues,
            enabled = !compatibleVideo,
            iconRes = R.drawable.ic_code,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.recode_video),
            summary = stringResource(R.string.recode_video_summary),
            key = "recode_video",
            defaultValue = false,
            iconRes = R.drawable.baseline_video_metadata,
            onCheckedChange = { checked ->
                recodeVideo = checked
                if (checked && compatibleVideo) {
                    compatibleVideo = false
                    prefs.edit().putBoolean("compatible_video", false).apply()
                }
            }
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.video_compatible),
            summary = stringResource(R.string.video_compatible_summary),
            key = "compatible_video",
            defaultValue = false,
            iconRes = R.drawable.baseline_video_metadata,
            onCheckedChange = { checked ->
                compatibleVideo = checked
                if (checked && recodeVideo) {
                    recodeVideo = false
                    prefs.edit().putBoolean("recode_video", false).apply()
                }
            }
        )

        SettingsListPreference(
            title = stringResource(R.string.preferred_video_codec),
            key = "video_codec",
            defaultValue = "",
            entries = videoCodecs,
            entryValues = videoCodecValues,
            enabled = !compatibleVideo,
            iconRes = R.drawable.ic_code,
        )

        SettingsListPreference(
            title = stringResource(R.string.video_quality),
            key = "video_quality",
            defaultValue = "best",
            entries = videoFormats,
            entryValues = videoFormatValues,
            iconRes = R.drawable.ic_video,
        )

        SettingsEditTextPreference(
            title = "${stringResource(R.string.preferred_format_id)} [${stringResource(R.string.video)}]",
            summary = stringResource(R.string.preferred_format_id_summary),
            key = "format_id",
            defaultValue = "",
            iconRes = R.drawable.ic_format_id,
        )

        SettingsListPreference(
            title = stringResource(R.string.internal_plugin_ffmpeg_preset),
            summary = stringResource(R.string.internal_plugin_ffmpeg_preset_summary),
            key = "internal_plugin_ffmpeg_preset",
            defaultValue = "",
            entries = ffmpegPresets,
            entryValues = ffmpegPresetValues,
            iconRes = R.drawable.baseline_fast_forward_24,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.remove_audio),
            key = "remove_audio",
            defaultValue = false,
            iconRes = R.drawable.baseline_delete,
        )

        SettingsSwitchPreference(
            title = stringResource(R.string.also_download_audio),
            key = "also_download_audio",
            defaultValue = false,
            iconRes = R.drawable.ic_plus,
        )

        // Format
        SettingsCategory(title = stringResource(R.string.format))

        SettingsListPreference(
            title = stringResource(R.string.preferred_format_size),
            key = "preferred_format_size",
            defaultValue = "",
            entries = preferredFormatSizes,
            entryValues = preferredFormatSizeValues,
            iconRes = R.drawable.ic_format,
        )

        SettingsListPreference(
            title = stringResource(R.string.preferred_format_size_audio),
            key = "preferred_format_size_audio",
            defaultValue = "",
            entries = preferredFormatSizes,
            entryValues = preferredFormatSizeValues,
            iconRes = R.drawable.ic_format,
        )

        // Reset
        ResetPreferencesPreference(onReset = onReset)
    }
}
