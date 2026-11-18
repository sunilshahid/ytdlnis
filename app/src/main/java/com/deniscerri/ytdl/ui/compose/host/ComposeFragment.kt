package com.deniscerri.ytdl.ui.compose.host

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme

/**
 * Base for screens that are being migrated from XML to Compose while keeping their Fragment
 * navigation contract.
 *
 * The composition is disposed with the Fragment's view lifecycle. Without
 * [ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed], every Fragment view recreation
 * leaves a composition attached to a destroyed view tree.
 */
abstract class ComposeFragment : Fragment() {

    /** Screen content. `YtdlnisTheme` is applied by this base class — do not wrap it again. */
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
