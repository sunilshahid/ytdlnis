package com.deniscerri.ytdl.ui.more.settings.processing

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.settings.ProcessingSettingsScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.UiUtil

class ProcessingSettingsFragment : ComposeFragment() {

    private var audioBitrateSummary by mutableStateOf("")
    private var subsLangSummary by mutableStateOf("")

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(R.string.processing))
        updateSummaries()
    }

    private fun updateSummaries() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val currentBitrate = prefs.getString("audio_bitrate", "").orEmpty()
        val entries = resources.getStringArray(R.array.audio_bitrate)
        val entryValues = resources.getStringArray(R.array.audio_bitrate_values)
        val idx = entryValues.indexOf(currentBitrate)
        audioBitrateSummary = if (idx >= 0) entries[idx] else getString(R.string.defaultValue)

        subsLangSummary = prefs.getString("subs_lang", ".*-orig").orEmpty()
    }

    @Composable
    override fun Content() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        ProcessingSettingsScreen(
            onSelectAudioBitrate = {
                val current = prefs.getString("audio_bitrate", "").orEmpty()
                UiUtil.showAudioBitrateDialog(requireActivity(), current) { selected ->
                    prefs.edit().putString("audio_bitrate", selected).apply()
                    updateSummaries()
                }
            },
            audioBitrateSummary = audioBitrateSummary,
            onSelectSubtitleLanguages = {
                val current = prefs.getString("subs_lang", ".*-orig").orEmpty()
                UiUtil.showSubtitleLanguagesDialog(requireActivity(), listOf(), current) { selected ->
                    prefs.edit().putString("subs_lang", selected).apply()
                    updateSummaries()
                }
            },
            subtitleLanguagesSummary = subsLangSummary,
            onReset = { resetPreferences() }
        )
    }

    private fun resetPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        listOf(
            "use_sponsorblock",
            "sponsorblock_filters",
            "sponsorblock_url",
            "mtime",
            "write_description",
            "use_extra_commands",
            "force_keyframes",
            "embed_metadata",
            "audio_bitrate",
            "embed_thumbnail",
            "crop_thumbnail",
            "embed_lyrics",
            "auto_embed_lyrics",
            "save_lrc",
            "embed_ttml",
            "playlist_as_album",
            "audio_language",
            "audio_codec",
            "prefer_container_over_codec_audio",
            "audio_format",
            "format_id_audio",
            "prefer_drc_audio",
            "embed_subtitles",
            "write_subtitles",
            "write_auto_subtitles",
            "no_keep_subs",
            "subs_lang",
            "sub_format",
            "write_thumbnail",
            "video_embed_thumbnail",
            "thumbnail_format",
            "add_chapters",
            "video_format",
            "recode_video",
            "compatible_video",
            "video_codec",
            "video_quality",
            "format_id",
            "internal_plugin_ffmpeg_preset",
            "remove_audio",
            "also_download_audio",
            "preferred_format_size",
            "preferred_format_size_audio"
        ).forEach { editor.remove(it) }
        editor.apply()
        com.deniscerri.ytdl.util.DefaultPreferences.resetProcessing(requireContext())
        requireActivity().recreate()
    }
}
