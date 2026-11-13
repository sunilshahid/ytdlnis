package com.deniscerri.ytdl.ui.more.cookies

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.auth.YouTubeSessionEngine
import com.deniscerri.ytdl.ui.BaseActivity
import com.google.android.material.appbar.MaterialToolbar
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
    private lateinit var progressBar: ProgressBar
    private var completingLogin = false
    private var requestedSigninCompletion = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_login)

        val toolbar = findViewById<MaterialToolbar>(R.id.youtube_login_toolbar)
        progressBar = findViewById(R.id.youtube_login_progress)

        toolbar.setNavigationOnClickListener {
            if (!completingLogin) {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.youtube_login_done) {
                finishWithCurrentSession()
                true
            } else {
                false
            }
        }

        webView = findViewById(R.id.youtube_login_webview)
        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = YouTubeSessionEngine.resolveUserAgent(this@YouTubeLoginActivity)
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
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
                    progressBar.visibility = View.VISIBLE
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
            progressBar.visibility = View.VISIBLE
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

        progressBar.visibility = View.VISIBLE
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
