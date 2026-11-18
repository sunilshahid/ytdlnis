package com.deniscerri.ytdl.ui.compose.host

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Compose equivalent of the app's many `BottomSheetDialogFragment`s.
 *
 * The sheet chrome still comes from the Material Views bottom sheet; only the content is Compose.
 * This lets sheets adopt Compose without giving up the existing drag/edge-to-edge behaviour.
 */
abstract class ComposeSheetFragment : BottomSheetDialogFragment() {

    @Composable
    protected abstract fun Content()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            YtdlnisTheme {
                Content()
            }
        }
    }
}
