package com.deniscerri.ytdl.ui.more.downloadLogs

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.LogViewModel
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.WorkerEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Download-log viewer (Phase 3).
 *
 * The screen chrome (top bar, bottom app bar with the copy FAB, progress bar, font-size dialog,
 * snackbars) is Compose; the log body stays a `WebView` because the HTML wrapper, auto-scroll
 * handshake and JS bridge are unchanged. This removes `fragment_download_log.xml` and the manual
 * `findViewById` wiring while keeping the exact log rendering.
 */
class DownloadLogFragment : ComposeFragment() {

    private lateinit var mainActivity: MainActivity
    private lateinit var logViewModel: LogViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var webView: WebView

    private var logID: Long = 0L
    private var autoScroll = true
    private var rawLogContent = ""
    private var isWebViewLoaded = false

    private var logTitle by mutableStateOf("")
    private var progress by mutableIntStateOf(100)
    private var scrollDownVisible by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = activity as MainActivity
        logViewModel = ViewModelProvider(this)[LogViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        logID = arguments?.getLong("logID") ?: 0L
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity.hideBottomNavigation()
        if (logID == 0L) {
            mainActivity.onBackPressedDispatcher.onBackPressed()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        var showFontDialog by remember { mutableStateOf(false) }

        val logFlow = remember(logID) { logViewModel.getLogFlowByID(logID).asFlow() }
        val logItem by logFlow.collectAsState(initial = null)

        LaunchedEffect(logID) {
            val item = withContext(Dispatchers.IO) { logViewModel.getItemById(logID) }
            if (item == null) {
                Toast.makeText(requireContext(), "Log is deleted!", Toast.LENGTH_SHORT).show()
                mainActivity.onBackPressedDispatcher.onBackPressed()
            } else {
                logTitle = item.title
            }
        }

        LaunchedEffect(logItem) {
            val content = logItem?.content
            if (!content.isNullOrBlank()) {
                rawLogContent = content
                if (isWebViewLoaded) updateLogInWebView(content)
            }
        }

        LaunchedEffect(logID) {
            WorkerEventBus.events.collectLatest { event ->
                if (event.logItemID == logID) progress = event.progress
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    title = {
                        Text(
                            text = logTitle,
                            modifier = Modifier.clickable {
                                if (::webView.isInitialized) {
                                    webView.evaluateJavascript("window.scrollTo(0, 0);", null)
                                }
                                scrollDownVisible = true
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { mainActivity.onBackPressedDispatcher.onBackPressed() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_back),
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                BottomAppBar(
                    actions = {
                        IconButton(onClick = ::toggleWrap) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_wrap_text_24),
                                contentDescription = stringResource(R.string.wrap_text),
                            )
                        }
                        if (scrollDownVisible) {
                            IconButton(
                                onClick = {
                                    scrollDownVisible = false
                                    autoScroll = true
                                    if (::webView.isInitialized) {
                                        webView.evaluateJavascript("scrollToBottom();", null)
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_down),
                                    contentDescription = stringResource(R.string.scroll_down),
                                )
                            }
                        }
                        IconButton(onClick = { showFontDialog = true }) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_format_size_24),
                                contentDescription = stringResource(R.string.text_size),
                            )
                        }
                        IconButton(
                            onClick = {
                                logViewModel.exportToFile(logID) { file ->
                                    scope.launch {
                                        if (file == null) {
                                            snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.couldnt_parse_file),
                                                duration = SnackbarDuration.Long,
                                            )
                                        } else {
                                            val result = snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.backup_created_successfully),
                                                actionLabel = context.getString(R.string.share),
                                                duration = SnackbarDuration.Long,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                FileUtil.shareFileIntent(
                                                    context,
                                                    listOf(file.absolutePath),
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_save_24),
                                contentDescription = stringResource(R.string.export_file),
                            )
                        }
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            text = { Text(stringResource(R.string.copy_log)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_copy),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("log", rawLogContent))
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.copied_to_clipboard),
                                        duration = SnackbarDuration.Long,
                                    )
                                }
                            },
                        )
                    },
                    windowInsets = WindowInsets(0),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        createWebView(ctx).also { webView = it }
                    },
                    update = {},
                    onRelease = { it.destroy() },
                )

                if (progress < 100) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                }
            }
        }

        if (showFontDialog) {
            FontSizeDialog(
                initial = sharedPreferences.getFloat("log_zoom", 2f),
                onPreview = { value ->
                    if (::webView.isInitialized) {
                        webView.evaluateJavascript("setFontSize(${value + 13f});", null)
                    }
                },
                onDismiss = { showFontDialog = false },
            )
        }
    }

    @Composable
    private fun FontSizeDialog(
        initial: Float,
        onPreview: (Float) -> Unit,
        onDismiss: () -> Unit,
    ) {
        var zoom by remember { mutableFloatStateOf(initial) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.text_size)) },
            text = {
                Slider(
                    value = zoom,
                    onValueChange = {
                        zoom = it
                        onPreview(it)
                    },
                    onValueChangeFinished = {
                        sharedPreferences.edit().putFloat("log_zoom", zoom).apply()
                    },
                    valueRange = 0f..10f,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sharedPreferences.edit().putFloat("log_zoom", zoom).apply()
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    private fun toggleWrap() {
        val newWrap = !sharedPreferences.getBoolean("wrap_text_log", false)
        sharedPreferences.edit().putBoolean("wrap_text_log", newWrap).apply()
        val cssClass = if (newWrap) "pre-wrap" else "pre"
        if (::webView.isInitialized) {
            webView.evaluateJavascript("setWrap('$cssClass');", null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(context: android.content.Context): WebView {
        val view = WebView(context)
        view.setBackgroundColor(AndroidColor.TRANSPARENT)
        view.addJavascriptInterface(WebAppInterface(), "Android")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, true)
        }
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(webView: WebView?, url: String?) {
                super.onPageFinished(webView, url)
                isWebViewLoaded = true
                if (rawLogContent.isNotBlank()) {
                    updateLogInWebView(rawLogContent)
                }
            }
        }

        val fontSize = sharedPreferences.getFloat("log_zoom", 2f) + 13f
        val whiteSpace = if (sharedPreferences.getBoolean("wrap_text_log", false)) "pre-wrap" else "pre"
        view.loadDataWithBaseURL(
            "https://appassets.androidplatform.net/",
            buildHtmlWrapper(fontSize, whiteSpace),
            "text/html",
            "UTF-8",
            null,
        )
        return view
    }

    private fun updateLogInWebView(fullText: String) {
        if (!::webView.isInitialized) return
        val escaped = fullText
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
        webView.evaluateJavascript("updateContent(`$escaped`, $autoScroll);", null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val cacheDir = requireContext().cacheDir
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { File(cacheDir, "log_viewer.html").delete() }
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onScrollStateChanged(canScrollDown: Boolean) {
            activity?.runOnUiThread {
                scrollDownVisible = canScrollDown
            }
        }
    }

    private fun buildHtmlWrapper(fontSizePx: Float, whiteSpaceMode: String): String {
        val wordBreakMode = if (whiteSpaceMode == "pre-wrap") "break-all" else "normal"
        val overflowXMode = if (whiteSpaceMode == "pre-wrap") "hidden" else "auto"

        return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
            :root {
                --text-color: #000000;
            }

            @media (prefers-color-scheme: dark) {
                :root {
                    --text-color: #FFFFFF;
                }
            }

            html, body {
                background-color: transparent;
                margin: 0;
                padding: 0;
                width: 100%;
                overflow-x: $overflowXMode;
            }
            #log-content {
                font-family: monospace;
                font-size: ${fontSizePx}px;
                white-space: $whiteSpaceMode;
                word-break: $wordBreakMode;
                padding: 10px;
                padding-bottom: 90px; /* Leave space for BottomAppBar */
                user-select: text;
                -webkit-user-select: text;
            }
        </style>
    </head>
    <body>
        <div id="log-content"></div>
        <script>
            const el = document.getElementById('log-content');

            function updateContent(text, shouldScroll) {
                el.textContent = text;
                if (shouldScroll) {
                    scrollToBottom();
                } else {
                    checkScrollPosition();
                }
            }

            function scrollToBottom() {
                window.scrollTo(0, document.documentElement.scrollHeight);
                checkScrollPosition();
            }

            function setFontSize(px) {
                el.style.fontSize = px + 'px';
            }

            function setWrap(wrapMode) {
                el.style.whiteSpace = wrapMode;
                if (wrapMode === 'pre-wrap') {
                    el.style.wordBreak = 'break-all';
                    document.body.style.overflowX = 'hidden';
                } else {
                    el.style.wordBreak = 'normal';
                    document.body.style.overflowX = 'auto';
                }
            }

            function checkScrollPosition() {
                const scrollPosition = window.scrollY || window.pageYOffset;
                const viewportHeight = window.innerHeight;
                const totalHeight = Math.max(
                    document.body.scrollHeight, 
                    document.documentElement.scrollHeight
                );

                // True if user is more than 30px away from the very bottom
                const canScrollDown = (scrollPosition + viewportHeight) < (totalHeight - 30);

                if (window.Android && window.Android.onScrollStateChanged) {
                    window.Android.onScrollStateChanged(canScrollDown);
                }
            }

            let scrollTimeout;
            window.addEventListener('scroll', function() {
                if (scrollTimeout) clearTimeout(scrollTimeout);
                scrollTimeout = setTimeout(checkScrollPosition, 50);
            });
        </script>
    </body>
    </html>
""".trimIndent()
    }
}
