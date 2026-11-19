package com.deniscerri.ytdl.ui.more.settings.updating.app

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.updating.ChangelogScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GitHub release changelog hosted by [SettingsActivity] (which owns the app bar, hence no top bar).
 *
 * Presentation moved to [ChangelogScreen]; the network call and date/asset handling are unchanged.
 */
class ChangeLogFragment : ComposeFragment() {

    private var releases by mutableStateOf<List<GithubRelease>>(emptyList())
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity() as SettingsActivity).changeTopAppbarTitle(getString(R.string.changelog))
        loadReleases()
    }

    @Composable
    override fun Content() {
        ChangelogScreen(
            releases = releases,
            isLoading = isLoading,
        )
    }

    private fun loadReleases() {
        isLoading = true
        val updateUtil = UpdateUtil(requireContext())

        lifecycleScope.launch {
            releases = withContext(Dispatchers.IO) {
                updateUtil.getGithubReleases()
            }
            isLoading = false
        }
    }
}
