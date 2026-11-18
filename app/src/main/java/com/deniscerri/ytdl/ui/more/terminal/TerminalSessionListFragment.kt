package com.deniscerri.ytdl.ui.more.terminal

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.google.android.material.appbar.MaterialToolbar

/**
 * Terminal session list (Phase 3).
 *
 * The toolbar belongs to [TerminalActivity] (`activity_terminal.xml`), so only the list half of the
 * screen is Compose. Navigation and the "new session" menu action are unchanged.
 *
 * Replaces `fragment_terminal_session_list.xml`, `terminal_session_card.xml` and
 * `TerminalSessionsAdapter`.
 */
class TerminalSessionListFragment : ComposeFragment() {

    private lateinit var terminalViewModel: TerminalViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        terminalViewModel = ViewModelProvider(requireActivity())[TerminalViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Activity-owned toolbar: same contract as the View implementation.
        val topAppBar = requireActivity().findViewById<MaterialToolbar>(R.id.custom_command_toolbar)
        topAppBar.setNavigationOnClickListener { requireActivity().finish() }
        topAppBar.menu.forEach { it.isVisible = false }
        topAppBar.menu.get(0).isVisible = true
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            if (m.itemId == R.id.add) {
                findNavController().navigate(
                    R.id.terminalFragment,
                    bundleOf("new" to true),
                    NavOptions.Builder().setPopUpTo(R.id.terminalFragment, true).build(),
                )
            }
            true
        }
    }

    @Composable
    override fun Content() {
        // `isBound` and `sessionBinder` are Compose state inside TerminalViewModel, so the list is
        // recomposed as soon as the service binds. The session map itself is not observable; the
        // version counter refreshes the snapshot after a termination (same behaviour as the
        // ListAdapter implementation, which also re-read the map manually).
        val isBound = terminalViewModel.isBound
        var version by remember { mutableIntStateOf(0) }
        val sessions: List<String> = remember(isBound, version) {
            if (isBound) {
                terminalViewModel.sessionBinder?.getService()?.sessionList?.keys?.toList().orEmpty()
            } else {
                emptyList()
            }
        }

        val deleteSession: (String) -> Unit = { id ->
            terminalViewModel.sessionBinder?.terminateSession(id)
            version++
            val remaining = terminalViewModel.sessionBinder?.getService()?.sessionList?.keys?.toList()
            if (remaining.isNullOrEmpty()) {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        val columns = LocalContext.current.resources.getInteger(R.integer.grid_size)

        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 10.dp),
            ) {
                items(sessions, key = { it }) { session ->
                    SessionCard(
                        name = session,
                        onOpen = {
                            findNavController().navigate(
                                R.id.terminalFragment,
                                bundleOf("id" to session),
                            )
                        },
                        onDelete = { deleteSession(session) },
                    )
                }
            }

            // `no_results` was only ever shown once the service was bound, never on first frame.
            if (isBound && sessions.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.no_results),
                    iconRes = R.drawable.ic_no_results,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/** 1:1 with `terminal_session_card.xml`: 50 dp, 20 dp corners, surfaceContainerLow, 10 dp margins. */
@Composable
private fun SessionCard(
    name: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    // Preserves the existing `View.popup()` bind animation (0.75 → 1, 300 ms, ease-out quint).
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.75f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "sessionCardPop",
    )
    LaunchedEffect(Unit) { appeared = true }

    Surface(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(bottom = 10.dp)
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.baseline_delete_24),
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
