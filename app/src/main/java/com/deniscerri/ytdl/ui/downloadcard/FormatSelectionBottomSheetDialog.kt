package com.deniscerri.ytdl.ui.downloadcard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.viewmodel.FormatViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.compose.downloadcard.FormatDetailsSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.FormatSelectionSheet
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * FormatSelectionBottomSheetDialog migrated to Jetpack Compose (eleXML §5 A9-A14).
 * Replaces format_select_bottom_sheet.xml, format_category_sheet.xml, format_item.xml,
 * format_item_shimmer.xml, format_type_label.xml, selectable_textview_filter.xml, and FormatAdapter.kt.
 */
class FormatSelectionBottomSheetDialog(
    private val _listener: OnFormatClickListener? = null,
    private val _multipleFormatsListener: OnMultipleFormatClickListener? = null
) : BottomSheetDialogFragment() {

    interface OnFormatClickListener {
        fun onFormatClick(format: Format)
    }

    interface OnMultipleFormatClickListener {
        fun onMultipleFormatClick(formats: List<Format>)
    }

    // Declared here (instead of the deleted XML dialog) because FormatViewModel and the
    // download-card view models still reference them by this qualified name.
    enum class FormatSorting { filesize, container, codec, id }
    enum class FormatCategory { ALL, SUGGESTED, SMALLEST, GENERIC }

    private lateinit var formatViewModel: FormatViewModel
    private lateinit var resultViewModel: ResultViewModel
    private var listener: OnFormatClickListener? = _listener
    private var multipleFormatsListener: OnMultipleFormatClickListener? = _multipleFormatsListener

    fun setListener(listener: OnFormatClickListener) {
        this.listener = listener
    }

    fun setMultipleFormatsListener(listener: OnMultipleFormatClickListener) {
        this.multipleFormatsListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        formatViewModel = ViewModelProvider(requireActivity())[FormatViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    val formatRows by formatViewModel.formats.collectAsState(initial = emptyList())
                    val formats = remember(formatRows) { formatRows.mapNotNull { it.format } }
                    val isUpdating by resultViewModel.updatingFormats.collectAsState()
                    var selectedFormat by remember { mutableStateOf<Format?>(null) }
                    var detailFormat by remember { mutableStateOf<Format?>(null) }

                    FormatSelectionSheet(
                        formats = formats,
                        selectedFormat = selectedFormat,
                        isLoading = isUpdating,
                        onFormatSelected = { format ->
                            selectedFormat = format
                            listener?.onFormatClick(format)
                            multipleFormatsListener?.onMultipleFormatClick(listOf(format))
                            dismiss()
                        },
                        onShowFormatDetails = { format ->
                            detailFormat = format
                        },
                        onDismiss = { dismiss() }
                    )

                    if (detailFormat != null) {
                        FormatDetailsSheet(
                            format = detailFormat!!,
                            onDismiss = { detailFormat = null }
                        )
                    }
                }
            }
        }
    }
}

class MultipleItemFormatTuple internal constructor(
    var url: String,
    var formatTuple: FormatTuple
)

class FormatTuple internal constructor(
    var format: Format?,
    var audioFormats: List<Format>?
)
