package com.deniscerri.ytdl.ui.home

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.compose.dialogs.FailedFetchesContent
import com.deniscerri.ytdl.ui.compose.host.ComposeSheetFragment

/**
 * Lists the home screen queries that failed to fetch, with options to retry them or download them anyway.
 * Replaces `failed_fetches_bottom_sheet.xml` and `failed_fetch_item.xml`.
 */
class FailedFetchesBottomSheetDialog : ComposeSheetFragment() {

    interface Listener {
        fun onDownloadAnyway(url: String)
    }

    private lateinit var resultViewModel: ResultViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
    }

    @Composable
    override fun Content() {
        val failedQueries by resultViewModel.failedQueries.collectAsState()

        if (failedQueries.isEmpty()) {
            dismiss()
            return
        }

        FailedFetchesContent(
            failedQueries = failedQueries,
            onDeleteAll = {
                resultViewModel.clearFailedQueries()
            },
            onRetryAll = {
                retry(resultViewModel.failedQueries.value.map { it.query })
            },
            onRetryItem = { item ->
                retry(listOf(item.query))
            },
            onDownloadAnyway = { item ->
                resultViewModel.removeFailedQuery(item.query)
                dismiss()
                (parentFragment as? Listener)?.onDownloadAnyway(item.query)
            }
        )
    }

    private fun retry(queries: List<String>) {
        queries.forEach { resultViewModel.removeFailedQuery(it) }
        resultViewModel.retryFailedQueries(queries)
    }
}
