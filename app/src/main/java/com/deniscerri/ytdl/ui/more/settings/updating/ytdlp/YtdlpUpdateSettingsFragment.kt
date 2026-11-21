package com.deniscerri.ytdl.ui.more.settings.updating.ytdlp

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.settings.YtdlpUpdateSettingsScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.compose.settings.UpdateStatusCardUtil
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YtdlpUpdateSettingsFragment : ComposeFragment() {

    private lateinit var ytdlpViewModel: YTDLPViewModel
    private var versionText by mutableStateOf("")
    private var channelName by mutableStateOf("")
    private var lastCheckText by mutableStateOf("")
    private var isCheckingUpdate by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ytdlpViewModel = ViewModelProvider(this)[YTDLPViewModel::class.java]
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(R.string.update_ytdl))
        refreshState()
    }

    private fun refreshState() {
        val ctx = context ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val source = prefs.getString("ytdlp_source", "stable").orEmpty()
        val values = resources.getStringArray(R.array.ytdlp_source_values)
        val titles = resources.getStringArray(R.array.ytdlp_source)
        val index = values.indexOf(source)
        channelName = if (index >= 0) titles[index] else prefs.getString("ytdlp_source_label", "").orEmpty().ifBlank { source }

        val lastCheck = prefs.getLong(UpdateUtil.YTDLP_LAST_CHECK_KEY, 0L)
        lastCheckText = UpdateStatusCardUtil.buildDetail(ctx, lastCheck).orEmpty()

        lifecycleScope.launch {
            val v = withContext(Dispatchers.IO) {
                runCatching { ytdlpViewModel.getVersion(source) }.getOrDefault("")
            }
            versionText = v.orEmpty()
        }
    }

    @Composable
    override fun Content() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        YtdlpUpdateSettingsScreen(
            versionText = versionText,
            channelName = channelName,
            lastCheckText = lastCheckText,
            isCheckingUpdate = isCheckingUpdate,
            onCheckForUpdates = {
                isCheckingUpdate = true
                val updateUtil = UpdateUtil(requireContext())
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { updateUtil.updateYTDL() }.getOrElse {
                            UpdateUtil.YTDLPUpdateResponse(
                                UpdateUtil.YTDLPUpdateStatus.ERROR,
                                it.message.orEmpty()
                            )
                        }
                    }
                    isCheckingUpdate = false
                    refreshState()
                    view?.let { v ->
                        val msg = when (result.status) {
                            UpdateUtil.YTDLPUpdateStatus.DONE ->
                                result.message.ifBlank { getString(R.string.no_updates) }
                            UpdateUtil.YTDLPUpdateStatus.ALREADY_UP_TO_DATE ->
                                getString(R.string.no_updates)
                            UpdateUtil.YTDLPUpdateStatus.ERROR ->
                                result.message.ifBlank { getString(R.string.errored) }
                            UpdateUtil.YTDLPUpdateStatus.PROCESSING ->
                                getString(R.string.please_wait)
                        }
                        Snackbar.make(v, msg, Snackbar.LENGTH_LONG).show()
                    }
                }
            },
            onOpenCustomSource = {
                UiUtil.showYTDLCustomSourceBottomSheet(requireActivity(), prefs) { title, source ->
                    prefs.edit().putString("ytdlp_source", source).putString("ytdlp_source_label", title).apply()
                    refreshState()
                }
            },
            onReset = { resetPreferences() }
        )
    }

    private fun resetPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        listOf("ytdlp_source", "ytdlp_source_label", "auto_update_ytdlp").forEach { editor.remove(it) }
        editor.apply()
        com.deniscerri.ytdl.util.DefaultPreferences.resetYtdlpUpdate(requireContext())
        requireActivity().recreate()
    }
}
