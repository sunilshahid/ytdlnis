package com.deniscerri.ytdl.ui.compose.host

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.terminal.virtualkeys.VirtualKeysView
import com.termux.view.TerminalView

/**
 * Hosts any legacy `View` inside Compose. Prefer the specific wrappers below when they exist so the
 * interop contract stays in one place.
 */
@Composable
fun <T : View> LegacyViewHost(
    factory: (Context) -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = {},
    onRelease: (T) -> Unit = {},
) {
    AndroidView(
        factory = factory,
        modifier = modifier,
        update = update,
        onRelease = onRelease,
    )
}

/**
 * The media3 `PlayerView` and its XML controller stay View-based during the whole migration.
 *
 * `exo_player_control_view.xml` owns the chapter bar, the scrub-preview stack and the gesture
 * handling that the video player depends on, so it must not be reimplemented in Compose as part of
 * a UI migration. Compose owns only the chrome around this host.
 *
 * **Why inflation instead of `PlayerView(context)`:** media3 1.9.0 exposes no
 * `setControllerLayoutId` — `controller_layout_id` is only read from attributes at inflation time.
 * [layoutRes] therefore has to be an XML layout whose root is a `PlayerView`.
 */
@Composable
fun PlayerViewHost(
    player: Player,
    modifier: Modifier = Modifier,
    @LayoutRes layoutRes: Int = R.layout.player_view_host,
    onReady: (PlayerView) -> Unit = {},
    onRelease: (PlayerView) -> Unit = {},
) {
    AndroidView(
        factory = { context ->
            val view = LayoutInflater.from(context).inflate(layoutRes, null, false)
            require(view is PlayerView) {
                "PlayerViewHost layout must have a PlayerView root, got ${view.javaClass.name}"
            }
            view.apply {
                this.player = player
                onReady(this)
            }
        },
        modifier = modifier,
        update = { view -> view.player = player },
        onRelease = { view ->
            view.player = null
            onRelease(view)
        },
    )
}

/**
 * Termux `TerminalView` host.
 *
 * `TerminalView` has no single-argument constructor (only `(Context, AttributeSet)`), so `null`
 * must be passed explicitly. The terminal keeps its own IME, key handling and renderer; Compose only
 * supplies the surrounding chrome. Do not replace this with a Compose text field.
 */
@Composable
fun TerminalViewHost(
    modifier: Modifier = Modifier,
    onReady: (TerminalView) -> Unit = {},
    onRelease: (TerminalView) -> Unit = {},
) {
    AndroidView(
        factory = { context -> TerminalView(context, null).also(onReady) },
        modifier = modifier,
        update = {},
        onRelease = onRelease,
    )
}

/**
 * Host for the app's own `VirtualKeysView` (Esc/Ctrl/Alt row). It is a `GridLayout`, so it can be
 * placed in Compose as-is and keeps the existing `TerminalViewClient` wiring.
 */
@Composable
fun VirtualKeysHost(
    modifier: Modifier = Modifier,
    onReady: (VirtualKeysView) -> Unit = {},
    onRelease: (VirtualKeysView) -> Unit = {},
) {
    AndroidView(
        factory = { context -> VirtualKeysView(context).also(onReady) },
        modifier = modifier,
        update = {},
        onRelease = onRelease,
    )
}

/**
 * `WebView` host for the cookie/login and changelog screens.
 *
 * Configuration (JS, DOM storage, clients) stays at the call-site via [configure]; this wrapper
 * exists so those screens can move to Compose without re-implementing WebView lifecycle handling.
 */
@Composable
fun WebViewHost(
    modifier: Modifier = Modifier,
    configure: (WebView) -> Unit = {},
    onRelease: (WebView) -> Unit = {},
) {
    AndroidView(
        factory = { context -> WebView(context).also(configure) },
        modifier = modifier,
        update = {},
        onRelease = onRelease,
    )
}
