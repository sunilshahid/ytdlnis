package com.deniscerri.ytdl.ui.more.terminal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdl.ui.compose.components.YtdlScaffold

sealed interface TerminalScreenState {
    data object SessionList : TerminalScreenState
    data class Terminal(
        val sessionId: String? = null,
        val isNew: Boolean = false,
        val shareUrl: String? = null,
        val token: Long = System.currentTimeMillis(),
    ) : TerminalScreenState
}

@Composable
fun TerminalActivityScreen(
    terminalViewModel: TerminalViewModel,
    activity: TerminalActivity,
    initialShareText: String?,
    onFinish: () -> Unit,
) {
    var screenState by remember {
        mutableStateOf<TerminalScreenState?>(null)
    }

    val isBound = terminalViewModel.isBound
    val sessionBinder = terminalViewModel.sessionBinder
    val service = sessionBinder?.getService()
    val sessionCount = service?.sessionList?.size ?: 0

    // Set initial screen state once service is bound (or immediately if share text is provided)
    LaunchedEffect(isBound, initialShareText) {
        if (screenState == null) {
            if (!initialShareText.isNullOrBlank()) {
                screenState = TerminalScreenState.Terminal(isNew = true, shareUrl = initialShareText)
            } else if (isBound) {
                screenState = if (sessionCount == 0) {
                    TerminalScreenState.Terminal(isNew = true)
                } else {
                    TerminalScreenState.SessionList
                }
            }
        }
    }

    // Handle incoming share updates while activity is running
    val pendingShare = activity.pendingShare.value
    LaunchedEffect(pendingShare) {
        if (!pendingShare.isNullOrBlank() && screenState != null) {
            screenState = TerminalScreenState.Terminal(isNew = true, shareUrl = pendingShare)
            activity.pendingShare.value = null
        }
    }

    when (val state = screenState) {
        null -> {
            Box(modifier = Modifier.fillMaxSize())
        }
        is TerminalScreenState.SessionList -> {
            BackHandler {
                onFinish()
            }
            YtdlScaffold(
                title = stringResource(R.string.terminal),
                onBack = onFinish,
                actions = {
                    IconButton(onClick = {
                        screenState = TerminalScreenState.Terminal(isNew = true)
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = stringResource(R.string.add),
                        )
                    }
                }
            ) { padding ->
                TerminalSessionListContent(
                    terminalViewModel = terminalViewModel,
                    onOpen = { session ->
                        screenState = TerminalScreenState.Terminal(sessionId = session)
                    },
                    onBackPressed = onFinish,
                    modifier = Modifier.padding(padding)
                )
            }
        }
        is TerminalScreenState.Terminal -> {
            BackHandler {
                val remaining = service?.sessionList?.keys?.size ?: 0
                if (remaining > 1) {
                    screenState = TerminalScreenState.SessionList
                } else {
                    onFinish()
                }
            }
            TerminalScreen(
                terminalViewModel = terminalViewModel,
                activity = activity,
                initialSessionId = state.sessionId,
                isNewSession = state.isNew,
                sessionShareURL = state.shareUrl,
                onBack = {
                    val remaining = service?.sessionList?.keys?.size ?: 0
                    if (remaining > 1) {
                        screenState = TerminalScreenState.SessionList
                    } else {
                        onFinish()
                    }
                },
                onNewSession = {
                    screenState = TerminalScreenState.Terminal(isNew = true)
                },
                onSessionClosed = {
                    val remaining = service?.sessionList?.keys?.size ?: 0
                    if (remaining > 0) {
                        screenState = TerminalScreenState.SessionList
                    } else {
                        onFinish()
                    }
                }
            )
        }
    }
}
