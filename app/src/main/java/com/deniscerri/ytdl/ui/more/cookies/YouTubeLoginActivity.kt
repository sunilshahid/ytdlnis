package com.deniscerri.ytdl.ui.more.cookies

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.auth.YouTubeSessionEngine
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.compose.cookies.YoutubeLoginScreen
import kotlinx.coroutines.launch

/**
 * Foreground sign-in screen for YouTube.
 *
 * It deliberately shares the app's default WebView profile (CookieManager, DOM storage and
 * Chromium disk database) with [YouTubeSessionEngine]. The user's interactive login therefore
 * becomes the permanent session that the background engine rotates later.
 *
 * No microG account or token is involved: the user signs in directly on youtube.com.
 */
class YouTubeLoginActivity : BaseActivity() {
    private lateinit var webView: WebView
    private var isLoading by mutableStateOf(false)
    private var completingLogin = false
    private var requestedSigninCompletion = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initWebView(savedInstanceState)

        setContent {
            YoutubeLoginScreen(
                isLoading = isLoading,
                onBackClick = {
                    if (!completingLogin) {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                },
                onDoneClick = {
                    finishWithCurrentSession()
                },
                webViewFactory = { webView }
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(savedInstanceState: Bundle?) {
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = YouTubeSessionEngine.resolveUserAgent(this@YouTubeLoginActivity)
                mediaPlaybackRequiresUserGesture = true
                setSupportMultipleWindows(false)
            }
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()

                if (url == null || view == null) return

                // Only complete when YouTube's own domain cookies exist. A Google-only cookie
                // jar looks "signed in" but is anonymous to yt-dlp.
                if (YouTubeSessionEngine.hasValidSession()) {
                    completeLogin()
                    return
                }

                // A Google identity session exists but YouTube has not minted its domain
                // cookies yet (interrupted hand-off or a previous partial login). Ask
                // YouTube's sign-in endpoint to finish it once per screen.
                if (!requestedSigninCompletion &&
                    url.contains("youtube.com") &&
                    YouTubeSessionEngine.hasGoogleSession()
                ) {
                    requestedSigninCompletion = true
                    isLoading = true
                    view.loadUrl(YouTubeSessionEngine.YOUTUBE_SIGNIN_COMPLETION_URL)
                }
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl(YouTubeSessionEngine.YOUTUBE_MOBILE_URL)
        }
    }

    private fun finishWithCurrentSession() {
        CookieManager.getInstance().flush()

        if (YouTubeSessionEngine.hasValidSession()) {
            completeLogin()
            return
        }

        // The user may have authenticated with Google while YouTube-domain cookies are still
        // missing (or pressed Done too early). Try the sign-in hand-off once before giving up.
        if (!requestedSigninCompletion && YouTubeSessionEngine.hasGoogleSession()) {
            requestedSigninCompletion = true
            isLoading = true
            Toast.makeText(this, R.string.youtube_finishing_sign_in, Toast.LENGTH_SHORT).show()
            webView.loadUrl(YouTubeSessionEngine.YOUTUBE_SIGNIN_COMPLETION_URL)
            return
        }

        Toast.makeText(this, R.string.youtube_sign_in_failed, Toast.LENGTH_LONG).show()
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun completeLogin() {
        if (completingLogin) return
        completingLogin = true

        isLoading = true
        webView.visibility = View.INVISIBLE

        lifecycleScope.launch {
            val email = YouTubeSessionEngine.resolveAndCacheAccountIdentity(this@YouTubeLoginActivity)
            YouTubeSessionEngine.syncCookiesToDatabase(this@YouTubeLoginActivity, email)

            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
        super.onDestroy()
    }
}
