package com.deniscerri.ytdl.ui.more.settings.advanced

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.settings.AdvancedSettingsScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity

class AdvancedSettingsFragment : ComposeFragment() {

    private var formatImportanceVideoSummary by mutableStateOf("")
    private var formatImportanceAudioSummary by mutableStateOf("")

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(R.string.advanced))
        updateFormatImportanceSummaries()
    }

    private fun updateFormatImportanceSummaries() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val videoItems = resources.getStringArray(R.array.format_importance_video)
        val videoValues = resources.getStringArray(R.array.format_importance_video_values).toSet()
        val prefVideo = prefs.getString("format_importance_video", videoValues.joinToString(",")).orEmpty()
        formatImportanceVideoSummary = prefVideo.split(",")
            .mapIndexedNotNull { index, s ->
                val idx = videoValues.indexOf(s)
                if (idx >= 0) "${index + 1}. ${videoItems[idx]}" else null
            }.joinToString("\n")

        val audioItems = resources.getStringArray(R.array.format_importance_audio)
        val audioValues = resources.getStringArray(R.array.format_importance_audio_values).toSet()
        val prefAudio = prefs.getString("format_importance_audio", audioValues.joinToString(",")).orEmpty()
        formatImportanceAudioSummary = prefAudio.split(",")
            .mapIndexedNotNull { index, s ->
                val idx = audioValues.indexOf(s)
                if (idx >= 0) "${index + 1}. ${audioItems[idx]}" else null
            }.joinToString("\n")
    }

    @Composable
    override fun Content() {
        val navController = findNavController()
        AdvancedSettingsScreen(
            onNavigatePlayerClients = {
                navController.navigate(R.id.action_advancedSettingsFragment_to_youtubePlayerClientFragment)
            },
            onNavigatePoTokens = {
                navController.navigate(R.id.action_advancedSettingsFragment_to_generateYoutubePoTokensFragment)
            },
            onSelectFormatImportanceVideo = null,
            formatImportanceVideoSummary = formatImportanceVideoSummary,
            onSelectFormatImportanceAudio = null,
            formatImportanceAudioSummary = formatImportanceAudioSummary,
            onReset = { resetPreferences() }
        )
    }

    private fun resetPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        listOf(
            "use_app_language_for_metadata",
            "youtube_other_extractor_args",
            "enable_data_fetching_extra_commands",
            "use_format_sorting",
            "format_importance_video",
            "format_importance_audio",
            "disable_write_info_json",
            "no_check_certificates",
            "use_header",
            "no_flat_playlist",
            "use_original_url_playlist"
        ).forEach { editor.remove(it) }
        editor.apply()
        com.deniscerri.ytdl.util.DefaultPreferences.resetAdvanced(requireContext())
        requireActivity().recreate()
    }
}
