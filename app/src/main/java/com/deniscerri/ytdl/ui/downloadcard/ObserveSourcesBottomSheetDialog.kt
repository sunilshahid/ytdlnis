package com.deniscerri.ytdl.ui.downloadcard

import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ObserveSourcesViewModel
import com.deniscerri.ytdl.ui.compose.host.ComposeSheetFragment
import com.deniscerri.ytdl.ui.compose.observesources.ObserveSourceConfigContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Observe sources bottom sheet dialog.
 * Replaces `observe_sources_bottom_sheet.xml`.
 */
class ObserveSourcesBottomSheetDialog : ComposeSheetFragment() {

    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var observeSourcesViewModel: ObserveSourcesViewModel
    private var currentItem: ObserveSourcesItem? = null
    private var initialType: DownloadType = DownloadType.audio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        observeSourcesViewModel = ViewModelProvider(requireActivity())[ObserveSourcesViewModel::class.java]

        currentItem = if (Build.VERSION.SDK_INT >= 33) {
            arguments?.getParcelable("item", ObserveSourcesItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("item")
        }

        @Suppress("DEPRECATION")
        initialType = currentItem?.downloadItemTemplate?.type
            ?: (arguments?.getSerializable("type") as? DownloadType)
            ?: DownloadType.audio
    }

    @Composable
    override fun Content() {
        ObserveSourceConfigContent(
            currentItem = currentItem,
            initialType = initialType,
            downloadViewModel = downloadViewModel,
            fragmentManager = parentFragmentManager,
            onDismiss = { dismiss() },
            onSave = { finalItem ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        observeSourcesViewModel.insertUpdate(finalItem)
                    }
                    dismiss()
                }
            }
        )
    }
}
