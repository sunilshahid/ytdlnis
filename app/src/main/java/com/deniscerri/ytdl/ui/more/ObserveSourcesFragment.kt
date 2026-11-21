package com.deniscerri.ytdl.ui.more

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ObserveSourcesViewModel
import com.deniscerri.ytdl.ui.compose.components.ConfirmDialog
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.observesources.ObserveLinksDialog
import com.deniscerri.ytdl.ui.compose.observesources.ObserveSourceDetailsSheet
import com.deniscerri.ytdl.ui.compose.observesources.ObserveSourceSettingsSheet
import com.deniscerri.ytdl.ui.compose.observesources.ObserveSourcesScreen
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.background.ObserveSourceWorker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Observe sources screen (Phase 3).
 *
 * Hosts [ObserveSourcesScreen], [ObserveSourceDetailsSheet], [ObserveSourceSettingsSheet]
 * and [ObserveLinksDialog] inside [ComposeFragment].
 *
 * Replaces `fragment_observe_sources.xml` and `ObserveSourcesAdapter`.
 */
class ObserveSourcesFragment : ComposeFragment() {

    private lateinit var mainActivity: MainActivity
    private lateinit var observeSourcesViewModel: ObserveSourcesViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var preferences: SharedPreferences

    private var selectedDetailsItem by mutableStateOf<ObserveSourcesItem?>(null)
    private var settingsItem by mutableStateOf<ObserveSourcesItem?>(null)
    private var linkDialogData by mutableStateOf<Pair<String, List<String>>?>(null)
    private var showDeleteAllConfirm by mutableStateOf(false)
    private var itemToDelete by mutableStateOf<ObserveSourcesItem?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        observeSourcesViewModel = ViewModelProvider(this)[ObserveSourcesViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity = activity as MainActivity
    }

    @Composable
    override fun Content() {
        val itemsFlow = remember { observeSourcesViewModel.items.asFlow() }
        val items by itemsFlow.collectAsState(initial = emptyList())

        var runningIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

        DisposableEffect(viewLifecycleOwner) {
            var previousRunning = emptySet<Long>()
            val liveData = WorkManager.getInstance(requireContext())
                .getWorkInfosByTagLiveData("observeSources")
            val observer = Observer<List<WorkInfo>> { infos ->
                val running = infos
                    .filter { it.state == WorkInfo.State.RUNNING }
                    .flatMap { it.tags }
                    .mapNotNull { it.toLongOrNull() }
                    .toSet()

                (previousRunning - running).forEach { _ ->
                    view?.let { v ->
                        Snackbar.make(v, getString(R.string.check_complete), Snackbar.LENGTH_SHORT).show()
                    }
                }
                previousRunning = running
                runningIds = running
            }
            liveData.observe(viewLifecycleOwner, observer)
            onDispose {
                liveData.removeObserver(observer)
            }
        }

        ObserveSourcesScreen(
            items = items,
            runningIds = runningIds,
            onBack = {
                mainActivity.onBackPressedDispatcher.onBackPressed()
            },
            onNewSource = {
                showDialog(null)
            },
            onDeleteAll = {
                showDeleteAllConfirm = true
            },
            onItemClick = { item ->
                selectedDetailsItem = item
            },
            onItemLongClick = { item ->
                itemToDelete = item
            },
        )

        selectedDetailsItem?.let { item ->
            // Keep the item in details sync with the latest DB state
            val latestItem = items.find { it.id == item.id } ?: item
            ObserveSourceDetailsSheet(
                item = latestItem,
                onDismiss = { selectedDetailsItem = null },
                onOpenSettings = {
                    settingsItem = latestItem
                },
                onCheckNow = {
                    onItemSearch(latestItem)
                },
                onPauseResume = {
                    onPauseResume(latestItem)
                },
                onEdit = {
                    selectedDetailsItem = null
                    showDialog(latestItem.url)
                },
                onDelete = {
                    selectedDetailsItem = null
                    itemToDelete = latestItem
                },
                onOpenLink = { url ->
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
                onCopyLink = { url ->
                    UiUtil.copyToClipboard(url, requireActivity())
                },
                onShowLinkList = { title, links ->
                    linkDialogData = title to links
                },
            )
        }

        settingsItem?.let { item ->
            ObserveSourceSettingsSheet(
                item = item,
                onDismiss = { settingsItem = null },
                onReScanFromScratch = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        observeSourcesViewModel.resetProcessedLinks(item)
                    }
                    settingsItem = null
                },
                onSkipBacklog = {
                    view?.let { v ->
                        Snackbar.make(v, getString(R.string.observe_updating), Snackbar.LENGTH_SHORT).show()
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        observeSourcesViewModel.markCurrentAsProcessed(item)
                    }
                    settingsItem = null
                },
                onUnskipIgnored = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        observeSourcesViewModel.clearIgnoredLinks(item)
                    }
                    settingsItem = null
                },
            )
        }

        linkDialogData?.let { (title, links) ->
            ObserveLinksDialog(
                title = title,
                links = links,
                onCopy = { joined ->
                    UiUtil.copyToClipboard(joined, requireActivity())
                },
                onDismiss = { linkDialogData = null },
            )
        }

        if (showDeleteAllConfirm) {
            ConfirmDialog(
                title = stringResource(R.string.confirm_delete_history),
                message = stringResource(R.string.confirm_delete_sources_desc),
                onConfirm = {
                    observeSourcesViewModel.deleteAll()
                    showDeleteAllConfirm = false
                },
                onDismiss = { showDeleteAllConfirm = false },
            )
        }

        itemToDelete?.let { item ->
            ConfirmDialog(
                title = stringResource(R.string.confirm_delete_history),
                message = item.name,
                onConfirm = {
                    observeSourcesViewModel.delete(item)
                    itemToDelete = null
                },
                onDismiss = { itemToDelete = null },
            )
        }
    }

    private fun showDialog(url: String?) {
        lifecycleScope.launch {
            val bundle = Bundle()
            bundle.putSerializable(
                "type", downloadViewModel.getDownloadType(
                    DownloadType.valueOf(preferences.getString("download_type", "auto")!!), ""
                )
            )

            if (url != null) {
                val item = withContext(Dispatchers.IO) {
                    observeSourcesViewModel.getByURL(url)
                }
                bundle.putParcelable("item", item)
            }

            findNavController().navigate(R.id.observeSourcesBottomSheetDialog, bundle)
        }
    }

    private fun onItemSearch(item: ObserveSourcesItem) {
        runCatching {
            val workConstraints = Constraints.Builder()
            val workRequest = OneTimeWorkRequestBuilder<ObserveSourceWorker>()
                .addTag("observeSources")
                .addTag(item.id.toString())
                .setConstraints(workConstraints.build())
                .setInitialDelay(1000L, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putLong("id", item.id).build())

            WorkManager.getInstance(requireContext()).enqueueUniqueWork(
                "OBSERVE${item.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest.build()
            )
        }.onFailure {
            view?.let { v ->
                Snackbar.make(v, it.message.toString(), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun onPauseResume(item: ObserveSourcesItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (item.status == ObserveSourcesRepository.SourceStatus.ACTIVE) {
                observeSourcesViewModel.stopObserving(item)
            } else {
                item.status = ObserveSourcesRepository.SourceStatus.ACTIVE
                item.runCount = 0
                observeSourcesViewModel.insertUpdate(item)
            }
        }
    }
}
