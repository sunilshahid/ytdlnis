package com.deniscerri.ytdl.ui.more.settings.folder.temporary

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.settings.TemporaryFilesSettingsScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.TemporaryFilesUtil
import com.deniscerri.ytdl.util.TemporaryFilesUtil.Category
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.MoveCacheFilesWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TemporaryFilesFragment : ComposeFragment() {

    private var totalSize by mutableStateOf("")
    private var categorySizes by mutableStateOf<Map<Category, String>>(emptyMap())
    private var hasFilesToClear by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(R.string.temporary_files))
        refreshSizes()
    }

    private fun refreshSizes() {
        val ctx = context ?: return
        lifecycleScope.launch {
            val sizes = withContext(Dispatchers.IO) {
                Category.values().associateWith { TemporaryFilesUtil.getSize(ctx, it) }
            }
            val total = sizes.values.sum()
            totalSize = TemporaryFilesUtil.formatSize(total)
            categorySizes = sizes.mapValues { TemporaryFilesUtil.formatSize(it.value) }
            hasFilesToClear = total > 0L
        }
    }

    @Composable
    override fun Content() {
        TemporaryFilesSettingsScreen(
            totalSize = totalSize,
            categorySizes = categorySizes,
            onClearCategory = { category ->
                clearCategories(listOf(category), getString(category.title))
            },
            onMoveCache = {
                val ctx = requireContext()
                val workRequest = OneTimeWorkRequestBuilder<MoveCacheFilesWorker>()
                    .addTag("cacheFiles")
                    .build()

                WorkManager.getInstance(ctx).beginUniqueWork(
                    System.currentTimeMillis().toString(),
                    ExistingWorkPolicy.KEEP,
                    workRequest
                ).enqueue()

                WorkManager.getInstance(ctx)
                    .getWorkInfosByTagLiveData("cacheFiles")
                    .observe(viewLifecycleOwner) { list ->
                        if (!list.isNullOrEmpty() && list.first().state == WorkInfo.State.SUCCEEDED) {
                            refreshSizes()
                        }
                    }
            },
            onClearAll = {
                clearCategories(Category.values().toList(), getString(R.string.clear_everything))
            },
            hasFilesToClear = hasFilesToClear
        )
    }

    private fun clearCategories(categories: List<Category>, title: String) {
        val ctx = requireContext()
        lifecycleScope.launch {
            val size = withContext(Dispatchers.IO) {
                categories.sumOf { TemporaryFilesUtil.getSize(ctx, it) }
            }

            UiUtil.showGenericConfirmDialog(
                requireActivity(),
                title,
                ctx.getString(R.string.clear_temporary_files_confirm, TemporaryFilesUtil.formatSize(size))
            ) {
                lifecycleScope.launch {
                    if (categories.any { it.needsIdleDownloads }) {
                        val downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
                        val activeDownloadCount = withContext(Dispatchers.IO) {
                            downloadViewModel.getActiveDownloads().size
                        }
                        if (activeDownloadCount > 0) {
                            UiUtil.showGenericConfirmDialog(
                                requireActivity(),
                                ctx.getString(R.string.active_downloads_warning_title),
                                ctx.getString(R.string.active_downloads_warning)
                            ) {
                                executeClear(categories)
                            }
                            return@launch
                        }
                    }
                    executeClear(categories)
                }
            }
        }
    }

    private fun executeClear(categories: List<Category>) {
        val ctx = requireContext()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                categories.forEach { TemporaryFilesUtil.clear(ctx, it) }
            }
            refreshSizes()
        }
    }
}
