package com.deniscerri.ytdl.ui.downloadcard.crop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.compose.downloadcard.CropVideoSheet
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * CropVideoBottomSheetDialog migrated to Jetpack Compose (eleXML §5 A20).
 * Replaces crop_video_sheet.xml.
 */
class CropVideoBottomSheetDialog(
    private val _item: DownloadItem? = null,
    private val _listener: VideoCropListener? = null
) : BottomSheetDialogFragment() {

    private var item: DownloadItem? = _item
    private var listener: VideoCropListener? = _listener
    private lateinit var resultViewModel: ResultViewModel

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

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    CropVideoSheet(
                        videoUrl = currentItem.url,
                        initialCropValues = currentItem.videoPreferences.cropValues,
                        videoWidth = currentItem.format.width ?: 1920,
                        videoHeight = currentItem.format.height ?: 1080,
                        onConfirm = { cropValues ->
                            if (cropValues.isBlank()) {
                                listener?.onClearCrop()
                            } else {
                                val parts = cropValues.split(":")
                                if (parts.size >= 6) {
                                    val x = parts[0].toIntOrNull() ?: 0
                                    val y = parts[1].toIntOrNull() ?: 0
                                    val w = parts[2].toIntOrNull() ?: (currentItem.format.width ?: 1920)
                                    val h = parts[3].toIntOrNull() ?: (currentItem.format.height ?: 1080)
                                    val refW = parts[4].toIntOrNull() ?: 1920
                                    val refH = parts[5].toIntOrNull() ?: 1080
                                    listener?.onChangeCrop(x, y, w, h, refW, refH)
                                } else {
                                    listener?.onClearCrop()
                                }
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

interface VideoCropListener {
    fun onChangeCrop(x: Int, y: Int, w: Int, h: Int, refW: Int, refH: Int)
    fun onClearCrop()
}
