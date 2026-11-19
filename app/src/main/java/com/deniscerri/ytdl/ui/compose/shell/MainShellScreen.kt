package com.deniscerri.ytdl.ui.compose.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme

/**
 * Phase 10: Single-Activity Compose Shell Screen.
 *
 * Hosts:
 * - Incognito banner at the top
 * - NavigationBar (compact) / NavigationRail (sw600dp / wide)
 * - Material 3 Mini-Player positioned right above the navigation bar
 * - Slot for the main destination content
 */
@Composable
fun MainShellScreen(
    tabs: List<ShellNavTab>,
    selectedTabId: Int,
    onSelectTab: (Int) -> Unit,
    onReselectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isIncognito: Boolean = false,
    showBottomBar: Boolean = true,
    miniPlayerAllowed: Boolean = true,
    onIncognitoDisabled: () -> Unit = {},
    onOpenPlayer: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    YtdlnisTheme {
        if (isWideScreen) {
            // Tablet / sw600dp / Landscape layout with NavigationRail
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                MainNavigationRail(
                    tabs = tabs,
                    selectedId = selectedTabId,
                    onSelect = onSelectTab,
                    onReselect = onReselectTab,
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    IncognitoBanner(
                        isIncognito = isIncognito,
                        onIncognitoDisabled = onIncognitoDisabled
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        content()
                    }

                    MiniPlayer(
                        allowed = miniPlayerAllowed,
                        onOpenPlayer = onOpenPlayer
                    )
                }
            }
        } else {
            // Phone / Compact portrait layout with Scaffold + NavigationBar
            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    IncognitoBanner(
                        isIncognito = isIncognito,
                        onIncognitoDisabled = onIncognitoDisabled
                    )
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        MiniPlayer(
                            allowed = miniPlayerAllowed,
                            onOpenPlayer = onOpenPlayer
                        )
                        MainBottomBar(
                            tabs = tabs,
                            selectedId = selectedTabId,
                            onSelect = onSelectTab,
                            onReselect = onReselectTab,
                            visible = showBottomBar
                        )
                    }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    content()
                }
            }
        }
    }
}
