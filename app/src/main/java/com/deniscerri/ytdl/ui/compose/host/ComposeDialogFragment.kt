package com.deniscerri.ytdl.ui.compose.host

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme

/**
 * Base for dialogs whose content is Compose. Used instead of `MaterialAlertDialogBuilder` while the
 * 129 existing call-sites are migrated one by one.
 *
 * Callers that want a bare dialog window should invoke `setStyle(STYLE_NO_TITLE, theme)` in
 * `onCreate`; callers that want a Material container should put `Surface`/`AlertDialog` inside
 * [Content].
 */
abstract class ComposeDialogFragment : DialogFragment() {

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
                this@ComposeDialogFragment.Content()
            }
        }
    }
}
