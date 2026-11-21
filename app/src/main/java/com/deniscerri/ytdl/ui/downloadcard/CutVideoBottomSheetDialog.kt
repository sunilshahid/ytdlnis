package com.deniscerri.ytdl.ui.downloadcard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.compose.downloadcard.CutVideoSheet
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Top-level alias kept for callers that imported the listener directly before the
 * Compose migration (UiUtil.configureVideo / DownloadConfigSheet).
 */
typealias VideoCutListener = CutVideoBottomSheetDialog.VideoCutListener

/**
 * CutVideoBottomSheetDialog migrated to Jetpack Compose (eleXML §5 A19).
 * Replaces cut_video_sheet.xml.
 */
class CutVideoBottomSheetDialog(
    private val _item: DownloadItem? = null,
    private val _listener: VideoCutListener? = null
) : BottomSheetDialogFragment() {

    interface VideoCutListener {
        fun onSaveCut(cutStart: String, cutEnd: String, mergeCuts: Boolean)
        fun onClearCut()
    }

    private var item: DownloadItem? = _item
    private var listener: VideoCutListener? = _listener
    private lateinit var resultViewModel: ResultViewModel
    private var streamingUrl: String = ""
    private var chapters: List<ChapterItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val currentItem = item ?: return ComposeView(requireContext())

        val cutPair = if (currentItem.downloadSections.startsWith("*")) {
            val cleaned = currentItem.downloadSections.removePrefix("*")
            val parts = cleaned.split("-")
            Pair(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
        } else {
            Pair("", "")
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    CutVideoSheet(
                        videoUrl = streamingUrl.ifBlank { currentItem.url },
                        initialStart = cutPair.first,
                        initialEnd = cutPair.second,
                        chapters = chapters,
                        initialMergeCuts = currentItem.mergeCuts,
                        onConfirm = { start, end, mergeCuts ->
                            if (start.isBlank() && end.isBlank()) {
                                listener?.onClearCut()
                            } else {
                                listener?.onSaveCut(start, end, mergeCuts)
                            }
                            dismiss()
                        },
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }
}
