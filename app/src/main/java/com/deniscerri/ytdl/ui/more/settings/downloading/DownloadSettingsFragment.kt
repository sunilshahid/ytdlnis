package com.deniscerri.ytdl.ui.more.settings.downloading

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.settings.DownloadSettingsScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.AlarmScheduler
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import java.util.Calendar

class DownloadSettingsFragment : ComposeFragment() {

    private var downloadDelayText by mutableStateOf("")

    private val archivePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            prefs.edit().putString("download_archive_path", uri.toString()).apply()
            requireActivity().recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(R.string.downloads))
        updateDelayText()
    }

    private fun updateDelayText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val parts = prefs.getString("download_delay", "0-0")?.split("-")?.mapNotNull { it.toFloatOrNull() } ?: listOf(0f, 0f)
        val first = parts.getOrElse(0) { 0f }
        val second = parts.getOrElse(1) { 0f }
        downloadDelayText = if (first == 0f && second == 0f) {
            getString(R.string.no_delay)
        } else {
            "${first}s - ${second}s"
        }
    }

    @Composable
    override fun Content() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        DownloadSettingsScreen(
            onSelectDownloadDelay = {
                UiUtil.showDownloadDelayDialog(
                    requireActivity(),
                    prefs,
                    rangeSelected = { res ->
                        prefs.edit().putString("download_delay", "${res.first}-${res.second}").apply()
                        updateDelayText()
                    },
                    resetSelected = {
                        prefs.edit().putString("download_delay", "0-0").apply()
                        updateDelayText()
                    }
                )
            },
            downloadDelayText = downloadDelayText,
            onSelectArchivePath = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }
                archivePickerLauncher.launch(intent)
            },
            onSelectScheduleStart = {
                val scheduler = AlarmScheduler(requireContext())
                UiUtil.showTimePicker(parentFragmentManager, prefs) { cal ->
                    val hr = cal.get(Calendar.HOUR_OF_DAY)
                    val mn = cal.get(Calendar.MINUTE)
                    val formattedTime = String.format("%02d:%02d", hr, mn)
                    prefs.edit().putString("schedule_start", formattedTime).apply()
                    scheduler.schedule()
                    requireActivity().recreate()
                }
            },
            onSelectScheduleEnd = {
                val scheduler = AlarmScheduler(requireContext())
                UiUtil.showTimePicker(parentFragmentManager, prefs) { cal ->
                    val hr = cal.get(Calendar.HOUR_OF_DAY)
                    val mn = cal.get(Calendar.MINUTE)
                    val formattedTime = String.format("%02d:%02d", hr, mn)
                    prefs.edit().putString("schedule_end", formattedTime).apply()
                    scheduler.schedule()
                    requireActivity().recreate()
                }
            },
            onReset = { resetPreferences() }
        )
    }

    private fun resetPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        listOf(
            "incognito",
            "download_card",
            "quick_download",
            "prevent_duplicate_downloads",
            "download_delay",
            "download_archive_path",
            "metered_networks",
            "force_ipv4",
            "use_cookies",
            "proxy",
            "preferred_download_type",
            "remember_download_type",
            "concurrent_fragments",
            "concurrent_downloads",
            "limit_rate",
            "buffer_size",
            "socket_timeout",
            "aria2",
            "log_downloads",
            "cleanup_leftover_downloads",
            "retries",
            "fragment_retries",
            "use_alarm_for_scheduling",
            "use_scheduler",
            "schedule_start",
            "schedule_end"
        ).forEach { editor.remove(it) }
        editor.apply()
        com.deniscerri.ytdl.util.DefaultPreferences.resetDownloading(requireContext())
        requireActivity().recreate()
    }
}
