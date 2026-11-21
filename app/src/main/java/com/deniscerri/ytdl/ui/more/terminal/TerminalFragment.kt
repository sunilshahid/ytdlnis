package com.deniscerri.ytdl.ui.more.terminal

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment

/**
 * Terminal Shell screen (Phase 9 Compose migration).
 *
 * Fully migrated from `fragment_terminal.xml` to [ComposeFragment] hosting [TerminalScreen].
 */
class TerminalFragment : ComposeFragment() {

    private lateinit var terminalViewModel: TerminalViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        terminalViewModel = ViewModelProvider(requireActivity())[TerminalViewModel::class.java]
    }

    @Composable
    override fun Content() {
        val activity = requireActivity() as TerminalActivity
        val sessionId = arguments?.getString("id")
        val isNew = arguments?.getBoolean("new") ?: false
        val shareUrl = arguments?.getString("share")?.ifEmpty { null }

        TerminalScreen(
            terminalViewModel = terminalViewModel,
            activity = activity,
            initialSessionId = sessionId,
            isNewSession = isNew,
            sessionShareURL = shareUrl,
            onBack = {
                activity.onBackPressedDispatcher.onBackPressed()
            },
            onNewSession = {
                findNavController().navigate(
                    R.id.terminalFragment,
                    bundleOf("new" to true),
                    NavOptions.Builder().setPopUpTo(R.id.terminalFragment, true).build()
                )
            },
            onSessionClosed = {
                activity.onBackPressedDispatcher.onBackPressed()
            }
        )
    }
}
