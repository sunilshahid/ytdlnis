package com.deniscerri.ytdl.ui.more.settings.general

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.settings.GeneralSettingsScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GeneralSettingsFragment : ComposeFragment() {

    private lateinit var resultViewModel: ResultViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(R.string.general))
    }

    @Composable
    override fun Content() {
        GeneralSettingsScreen(
            onOpenNavBarOptions = null,
            onReset = { resetPreferences() }
        )
    }

    private fun resetPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        listOf(
            "history_quick_action",
            "locale",
            "navigation_bar",
            "label_visibility",
            "search_engine",
            "check_clipboard_home",
            "search_suggestions",
            "swipe_gesture",
            "swipe_gestures_download_card",
            "show_terminal",
            "show_quick_download_share",
            "show_count_downloads",
            "write_ytdlp_terminal",
            "recommendations_home",
            "custom_home_recommendation_url",
            "api_key",
            "youtube_data_fetching_extractor",
            "formats_source",
            "use_itemurl_instead_playlisturl"
        ).forEach { editor.remove(it) }
        editor.apply()
        com.deniscerri.ytdl.util.DefaultPreferences.resetGeneral(requireContext())

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                resultViewModel.deleteAll()
            }
            requireActivity().recreate()
        }
    }
}
