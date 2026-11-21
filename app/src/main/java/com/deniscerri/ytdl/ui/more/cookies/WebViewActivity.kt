package com.deniscerri.ytdl.ui.more.cookies

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.CookieViewModel
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Web view shell for capturing cookies (Phase 3).
 *
 * Migrated to Compose using `setContent` wrapping the `WebView` in an `AndroidView`
 * with a Material 3 `TopAppBar` and progress indicator.
 *
 * Replaces `webview_activity.xml`.
 */
open class WebViewActivity : BaseActivity() {

    private lateinit var cookiesViewModel: CookieViewModel
    protected var webView: WebView? = null
    private lateinit var cookieManager: CookieManager
    private lateinit var url: String
    private lateinit var description: String
    private var cookies: String = ""
    private lateinit var preferences: SharedPreferences

    private var currentTitle by mutableStateOf("")
    private var pageProgress by mutableIntStateOf(0)
    private var isLoading by mutableStateOf(true)
    private var generateEnabled by mutableStateOf(true)
    private var isDesktop by mutableStateOf(false)

    @SuppressLint("SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = intent.getStringExtra("url") ?: return finish()
        description = intent.getStringExtra("description") ?: ""

        val incognito = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            this@WebViewActivity.packageName
        }.endsWith(":incognito_process")

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        cookiesViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        cookieManager = CookieManager.getInstance()

        if (savedInstanceState == null) {
            cookieManager.flush()
        }
        if (incognito) {
            cookieManager.removeAllCookies(null)
        }

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
                WebViewShellContent(
                    title = currentTitle,
                    progress = pageProgress,
                    isLoading = isLoading,
                    generateEnabled = generateEnabled,
                    isDesktop = isDesktop,
                    onBack = { finishAndRemoveTask() },
                    onGenerate = { onGenerateClicked() },
                    onToggleDesktop = {
                        isDesktop = !isDesktop
                        webView?.let {
                            configureDesktopMode(it, isDesktop)
                            it.reload()
                        }
                    },
                    webViewFactory = { context ->
                        createAndConfigureWebView(context, incognito)
                    },
                )
            }
        }
    }

    private fun onGenerateClicked() {
        generateEnabled = false
        if (url.contains("youtube.com")) {
            // redirect to robots.txt so cookies last longer for youtube
            webView?.loadUrl("https://www.youtube.com/robots.txt")
        } else {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    generateCookies()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createAndConfigureWebView(context: Context, incognito: Boolean): WebView {
        return WebView(context).apply {
            webView = this
            settings.run {
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = true

                if (!incognito) {
                    cacheMode = WebSettings.LOAD_DEFAULT
                    domStorageEnabled = true
                    setGeolocationEnabled(true)
                } else {
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    domStorageEnabled = false
                    setGeolocationEnabled(false)
                    WebStorage.getInstance().deleteAllData()

                    clearHistory()
                    clearCache(true)
                    clearFormData()
                }

                if (Build.VERSION.SDK_INT >= 26) {
                    safeBrowsingEnabled = true
                }
            }

            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            this.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    webView = view
                    super.onPageFinished(view, pageUrl)
                    isLoading = false
                    runCatching {
                        currentTitle = view?.title ?: ""
                        cookies = cookieManager.getCookie(view?.url) ?: ""
                    }

                    if (pageUrl?.contains("robots.txt") == true) {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                generateCookies()
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

            loadUrl(this@WebViewActivity.url)
        }
    }

    private suspend fun generateCookies() = withContext(Dispatchers.IO) {
        cookiesViewModel.getCookiesFromDB(url).getOrNull()?.let {
            runCatching {
                cookiesViewModel.insert(
                    com.deniscerri.ytdl.database.models.CookieItem(
                        0,
                        url,
                        it,
                        description,
                        true
                    )
                )
                cookiesViewModel.updateCookiesFile()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@WebViewActivity,
                        "Something went wrong",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        withContext(Dispatchers.Main) {
            this@WebViewActivity.setResult(RESULT_OK)
            this@WebViewActivity.finish()
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
    protected open fun WebViewShellContent(
        title: String,
        progress: Int,
        isLoading: Boolean,
        generateEnabled: Boolean,
        isDesktop: Boolean,
        onBack: () -> Unit,
        onGenerate: () -> Unit,
        onToggleDesktop: () -> Unit,
        webViewFactory: (Context) -> WebView,
        extraActions: @Composable () -> Unit = {},
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

                            extraActions()

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
        const val TAG = "WebViewActivity"
    }

    data class CookieItem(
        val domain: String = "",
        val name: String = "",
        val value: String = "",
        val includeSubdomains: Boolean = true,
        val path: String = "/",
        val secure: Boolean = true,
        val expiry: Long = 0L,
    ) {
        constructor(
            url: String,
            name: String,
            value: String
        ) : this(domain = url.replace(Regex("""http(s)?://(\w*(www|m|account|sso))?|/.*"""), ""), name = name, value = value)

        fun toNetscapeFormat(): String {
            val stringList = listOf(
                domain,
                includeSubdomains.toString().uppercase(),
                path,
                secure.toString().uppercase(),
                expiry.toString(),
                name,
                value
            )

            val builder = StringBuilder(stringList.first())

            for (s in stringList.subList(1, stringList.size)) {
                if (s.isNotEmpty()) {
                    if (builder.isNotEmpty())
                        builder.append("\u0009")
                    builder.append(s)
                }
            }
            return builder.toString()
        }
    }
}
