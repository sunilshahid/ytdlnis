package com.deniscerri.ytdl.ui.more.settings.advanced.generateyoutubepotokens.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.extractors.newpipe.potoken.NewPipePoTokenGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Web view shell for minting YouTube PO tokens (Phase 3).
 *
 * Migrated to Compose using `setContent` wrapping the `WebView` in an `AndroidView`
 * with a Material 3 `TopAppBar` and progress indicator.
 *
 * Replaces `webview_potoken_activity.xml`.
 */
class PoTokenWebViewLoginActivity : BaseActivity() {

    private var webView: WebView? = null
    private lateinit var cookieManager: CookieManager
    private lateinit var preferences: SharedPreferences

    private val sampleVideoID = "aqz-KE-bpKQ" // Big Buck Bunny
    private var currentTitle by mutableStateOf("")
    private var pageProgress by mutableIntStateOf(0)
    private var isLoading by mutableStateOf(true)
    private var generateEnabled by mutableStateOf(true)
    private var isDesktop by mutableStateOf(false)

    @SuppressLint("SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url") ?: return finish()
        var redirectUrl = intent.getStringExtra("redirect_url")
        val noAuth = intent.getBooleanExtra("no_auth", false)

        cookieManager = CookieManager.getInstance()
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.edit().putString("genenerate_youtube_po_token_preferred_url", url).apply()

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView?.canGoBack() == true) {
                    webView?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        setContent {
            YtdlnisTheme {
                PoTokenWebViewContent(
                    title = currentTitle,
                    progress = pageProgress,
                    isLoading = isLoading,
                    generateEnabled = generateEnabled,
                    isDesktop = isDesktop,
                    onBack = { finishAndRemoveTask() },
                    onGenerate = {
                        generateEnabled = false
                        webView?.loadUrl("https://www.youtube.com/robots.txt")
                    },
                    onGetDataSyncId = {
                        webView?.evaluateJavascript("ytcfg.get('DATASYNC_ID')") { id ->
                            UiUtil.copyToClipboard(id.replace("\"", ""), this@PoTokenWebViewLoginActivity)
                        }
                    },
                    onToggleDesktop = {
                        isDesktop = !isDesktop
                        webView?.let {
                            configureDesktopMode(it, isDesktop)
                            it.reload()
                        }
                    },
                    webViewFactory = { context ->
                        createAndConfigureWebView(context, url, redirectUrl, noAuth) { nextRedirect ->
                            redirectUrl = nextRedirect
                        }
                    },
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createAndConfigureWebView(
        context: Context,
        url: String,
        initialRedirectUrl: String?,
        noAuth: Boolean,
        onClearRedirect: (String?) -> Unit,
    ): WebView {
        var redirectUrl = initialRedirectUrl
        return WebView(context).apply {
            webView = this
            clearHistory()
            clearCache(true)
            clearFormData()
            settings.run {
                cacheMode = WebSettings.LOAD_NO_CACHE
                domStorageEnabled = false
                setGeolocationEnabled(false)
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                if (Build.VERSION.SDK_INT >= 26) {
                    safeBrowsingEnabled = true
                }
            }
            cookieManager.setAcceptThirdPartyCookies(this, true)

            this.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    super.onPageFinished(view, pageUrl)
                    isLoading = false
                    runCatching {
                        currentTitle = view?.title ?: ""
                    }

                    // finish generation
                    if (pageUrl?.contains("robots.txt") == true) {
                        lifecycleScope.launch {
                            val generator = NewPipePoTokenGenerator()
                            val res = withContext(Dispatchers.IO) {
                                generator.getWebClientPoToken(sampleVideoID)
                            }

                            if (res == null) {
                                setResult(RESULT_CANCELED)
                            } else {
                                val intent = Intent()
                                intent.putExtra("visitor_data", res.visitorData)
                                intent.putExtra("player_potoken", res.playerRequestPoToken)
                                intent.putExtra("subs_potoken", res.playerRequestPoToken)
                                intent.putExtra("streaming_potoken", res.streamingDataPoToken)
                                setResult(RESULT_OK, intent)
                            }

                            // The manual generator only mints tokens. It must never write a
                            // synthetic "Po Token Generated Cookies" row or toggle cookie use:
                            // the signed-in session owned by YouTubeSessionEngine is the single
                            // source of truth for account cookies.

                            webView?.clearCache(true)
                            // ensures that the WebView isn't doing anything when destroying it
                            webView?.loadUrl("about:blank")
                            webView?.onPause()
                            webView?.removeAllViews()
                            webView?.destroy()
                            finish()
                        }
                    }

                    val canRedirect = noAuth || pageUrl?.contains("youtube.com/account") == true
                    if (canRedirect) {
                        webView?.evaluateJavascript("(function(){return document.readyState;})()") { value ->
                            if (value?.trim('"') == "complete") {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    redirectUrl?.apply {
                                        val extraHeaders: MutableMap<String?, String?> = HashMap()
                                        extraHeaders["Referer"] = "https://www.google.com/"
                                        webView?.loadUrl(this, extraHeaders)
                                    }
                                    redirectUrl = null
                                    onClearRedirect(null)
                                }, 2500)
                            }
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    if (request?.url?.scheme?.contains("http") == false) {
                        return true
                    }
                    return super.shouldOverrideUrlLoading(view, request)
                }
            }

            this.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    pageProgress = newProgress
                    isLoading = newProgress < 100
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrBlank()) {
                        currentTitle = title
                    }
                }
            }

            loadUrl(url)
        }
    }

    private fun configureDesktopMode(webView: WebView, desktop: Boolean) {
        webView.settings.apply {
            if (desktop) {
                userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                useWideViewPort = true
                loadWithOverviewMode = true
            } else {
                userAgentString = WebSettings.getDefaultUserAgent(webView.context)
                useWideViewPort = false
                loadWithOverviewMode = false
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PoTokenWebViewContent(
        title: String,
        progress: Int,
        isLoading: Boolean,
        generateEnabled: Boolean,
        isDesktop: Boolean,
        onBack: () -> Unit,
        onGenerate: () -> Unit,
        onGetDataSyncId: () -> Unit,
        onToggleDesktop: () -> Unit,
        webViewFactory: (Context) -> WebView,
    ) {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopAppBar(
                        title = {
                            Text(
                                text = title.ifEmpty { stringResource(R.string.app_name) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_back),
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        },
                        actions = {
                            Button(
                                onClick = onGenerate,
                                enabled = generateEnabled,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.ok))
                            }

                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        painter = painterResource(R.drawable.baseline_more_vert_24),
                                        contentDescription = null,
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.get_data_sync_id)) },
                                        onClick = {
                                            menuExpanded = false
                                            onGetDataSyncId()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.desktop)) },
                                        trailingIcon = {
                                            if (isDesktop) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_check),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onToggleDesktop()
                                        },
                                    )
                                }
                            }
                        },
                    )
                    if (isLoading && progress < 100) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
        ) { innerPadding ->
            AndroidView(
                factory = webViewFactory,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    companion object {
        const val TAG = "PoTokenWebViewActivity"
    }
}
