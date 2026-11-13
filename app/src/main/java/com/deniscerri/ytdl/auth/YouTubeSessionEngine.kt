package com.deniscerri.ytdl.auth

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.os.Handler
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.database.repository.CookieRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilenameFilter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Persistent YouTube session engine.
 *
 * Keeps a single off-screen Chromium WebView alive for the entire application process. The
 * WebView shares the same persistent WebView storage partition as the in-app sign-in screen,
 * so cookies written during an interactive login remain available for background rotation.
 *
 * Unlike the old microG/MergeSession bridge, this engine never requests a Google auth token.
 * It simply loads YouTube in the same Chromium profile that the user signed in with, allowing
 * Google's own JavaScript to rotate the short lived __Secure-1PSIDTS cookie.
 */
object YouTubeSessionEngine {
    private const val TAG = "YouTubeSessionEngine"

    private const val PREFS_NAME = "ytdlnis_youtube_auth_prefs"
    private const val KEY_LOGGED_IN_EMAIL = "logged_in_email"
    private const val KEY_LAST_REFRESH_TIME = "last_refresh_timestamp"

    const val PREF_YOUTUBE_ACCOUNT_COOKIES = "youtube_account_cookies"
    const val PREF_USE_COOKIES = "use_cookies"
    const val PREF_CHECK_YOUTUBE_COOKIES_ON_OPEN = "check_youtube_cookies_on_open"
    const val PREF_LAST_COOKIE_CHECK_TIMESTAMP = "last_cookie_check_timestamp"
    const val PREF_LAST_COOKIE_CHECK_STATUS = "last_cookie_check_status"
    const val PREF_SESSION_VISITOR_DATA = "session_visitor_data"
    const val PREF_SESSION_VISITOR_DATA_UPDATED_AT = "session_visitor_data_updated_at"

    const val COOKIE_STATUS_HEALTHY = "healthy"
    const val COOKIE_STATUS_EXPIRED = "expired"
    const val COOKIE_STATUS_ERROR = "error"
    const val COOKIE_STATUS_SIGNED_OUT = "signed_out"

    const val YOUTUBE_DESKTOP_URL = "https://www.youtube.com"
    const val YOUTUBE_MOBILE_URL = "https://m.youtube.com"
    private const val GOOGLE_URL = "https://www.google.com"

    /**
     * YouTube's sign-in hand-off endpoint. Authentication at accounts.google.com only sets
     * `.google.com` cookies; loading this endpoint while those cookies are present makes
     * youtube.com issue the domain-scoped auth cookies (SID, __Secure-1PSID, SAPISID, ...)
     * that yt-dlp actually sends to youtube.com.
     */
    const val YOUTUBE_SIGNIN_COMPLETION_URL =
        "https://www.youtube.com/signin?action_handle_signin=true&app=m&hl=en&next=%2F"

    /**
     * Fallback Chrome user agent used when the system WebView user agent cannot be resolved.
     * The "wv" marker is stripped from the real WebView UA in [resolveUserAgent] because Google
     * treats it as an embedded browser during sign-in.
     */
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** Desktop UA used for InnerTube identity calls; matches the shape YouTube's WEB client expects. */
    private const val IDENTITY_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private const val REFRESH_TIMEOUT_MS = 20_000L
    private const val SESSION_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var persistentWebView: WebView? = null

    @Volatile
    private var isRefreshing = false

    @Volatile
    private var resolvedUserAgent: String = DEFAULT_USER_AGENT
    @Volatile
    private var cachedSessionVisitorData: String? = null
    @Volatile
    private var visitorDataInvalidationListener: ((Context) -> Unit)? = null
    private const val VISITOR_DATA_MAX_AGE_MS = 4 * 60 * 60 * 1000L

    // =========================================================================
    // 1. PERSISTENT SINGLETON WEBVIEW MANAGEMENT
    // =========================================================================

    /**
     * Returns the long-lived, off-screen WebView. It is intentionally created with the
     * application context so it never keeps an Activity alive.
     */
    @SuppressLint("SetJavaScriptEnabled")
    @Synchronized
    fun getOrCreatePersistentWebView(context: Context): WebView {
        persistentWebView?.let { return it }

        val appContext = context.applicationContext
        val webView = WebView(appContext).apply {
            // Give the off-screen Chromium compositor a real viewport. Without dimensions some
            // page scripts (including YouTube's rotation scripts) never execute.
            layout(0, 0, 1080, 1920)

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = resolveUserAgent(appContext)
                mediaPlaybackRequiresUserGesture = true
                loadsImagesAutomatically = false
            }

            webViewClient = createPersistentClient()
        }

        persistentWebView = webView
        return webView
    }

    /**
     * Builds the WebViewClient used by the background rotation WebView. Media, fonts and
     * analytics are dropped so a rotation costs only a few hundred kilobytes.
     */
    private fun createPersistentClient(onPageFinished: ((String?) -> Unit)? = null): WebViewClient {
        return object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val path = request?.url?.path?.lowercase(Locale.ROOT) ?: ""
                if (path.endsWith(".mp4") || path.endsWith(".m4s") ||
                    path.endsWith(".webm") || path.endsWith(".webp") ||
                    path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                    path.endsWith(".png") || path.endsWith(".gif") ||
                    path.endsWith(".css") || path.endsWith(".woff") ||
                    path.endsWith(".woff2") || path.endsWith(".ttf")
                ) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream(byteArrayOf())
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url == null) return
                if (url.contains("youtube.com")) {
                    CookieManager.getInstance().flush()
                }
                if (view != null && url != null && url.contains("youtube.com", ignoreCase = true) && !url.contains("accounts.google.com", ignoreCase = true)) {
                    scheduleVisitorDataCapture(view, view.context)
                }
                onPageFinished?.invoke(url)
            }
        }
    }

    /**
     * Resolves a Chrome-like user agent for the persistent WebView and the sign-in screen.
     * Google blocks the literal "wv" WebView marker on its identity endpoints.
     */
    fun resolveUserAgent(context: Context): String {
        val systemUserAgent = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
        if (systemUserAgent.isNullOrBlank()) return DEFAULT_USER_AGENT

        val chromeLike = systemUserAgent
            .replace("; wv", "")
            .replace(" Version/4.0", "")
            .trim()
            .ifBlank { DEFAULT_USER_AGENT }

        resolvedUserAgent = chromeLike
        return chromeLike
    }

    // =========================================================================
    // 2. BACKGROUND AUTO-HEALING & SILENT ROTATION
    // =========================================================================

    /**
     * Silently loads m.youtube.com through the pre-authenticated persistent WebView. Google's
     * own JavaScript performs the token exchange and rotates __Secure-1PSIDTS.
     *
     * @param force when false, a rotation is skipped if one happened within the last 6 hours.
     */
    suspend fun performSilentSessionRefresh(
        context: Context,
        force: Boolean = false
    ): Boolean {
        // A full YouTube session can rotate normally. A Google-only session (half-finished
        // sign-in) can still be healed by loading YouTube's sign-in hand-off endpoint.
        val hasFullSession = hasValidSession()
        if (!hasFullSession && !hasGoogleSession()) return false

        if (!force) {
            val elapsed = System.currentTimeMillis() - getLastRefreshTime(context)
            if (elapsed in 0 until SESSION_REFRESH_INTERVAL_MS) return true
        }

        val rotated = withContext(Dispatchers.Main) {
            if (isRefreshing) return@withContext false

            isRefreshing = true
            try {
                val webView = getOrCreatePersistentWebView(context)
                withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                    suspendCancellableCoroutine<Boolean> { continuation ->
                        var resumed = false

                        fun finish(value: Boolean) {
                            if (!resumed && continuation.isActive) {
                                resumed = true
                                continuation.resume(value)
                            }
                        }

                        webView.webViewClient = createPersistentClient { url ->
                            if (url?.contains("accounts.google.com") == true) {
                                // Only bail out when there is no Google session to hand off.
                                // During a valid hand-off this endpoint is visited transiently.
                                if (!hasGoogleSession() && !hasValidSession()) finish(false)
                            } else if (url?.contains("youtube.com") == true) {
                                finish(hasValidSession())
                            }
                        }

                        webView.onResume()
                        webView.loadUrl(
                            if (hasFullSession) YOUTUBE_MOBILE_URL
                            else YOUTUBE_SIGNIN_COMPLETION_URL
                        )

                        continuation.invokeOnCancellation {
                            mainHandler.post {
                                runCatching { webView.stopLoading() }
                            }
                        }
                    }
                } ?: false
            } catch (e: Exception) {
                Log.w(TAG, "Silent YouTube session refresh failed", e)
                false
            } finally {
                isRefreshing = false
            }
        }

        if (rotated) {
            withContext(Dispatchers.IO) {
                saveLastRefreshTime(context)
            }
        }
        return rotated
    }

    // =========================================================================
    // 3. ACCOUNT STATE & IDENTITY RESOLUTION
    // =========================================================================

    /**
     * Confirms that an authenticated session exists **for the youtube.com domain**.
     *
     * This distinction matters: signing in at accounts.google.com only writes `.google.com`
     * cookies. yt-dlp (and every real HTTP client) will not send those to youtube.com, so a
     * `.google.com`-only cookie jar behaves as anonymous. Requiring YouTube-scoped cookies
     * prevents the login screen from reporting success before the hand-off has completed.
     *
     * Cookie names are compared exactly because substrings like "SAPISID=" also contain "SID=".
     */
    fun hasValidSession(): Boolean {
        val names = youtubeCookieNames()
        val hasPrimary = names.contains("SID") || names.contains("__Secure-1PSID")
        val hasSecondary = names.contains("SAPISID") ||
                names.contains("__Secure-3PAPISID") ||
                names.contains("HSID") ||
                names.contains("LOGIN_INFO")
        return hasPrimary && hasSecondary
    }

    /**
     * True when Google's identity cookies are present but the YouTube-scoped half may still be
     * missing. Used to drive the sign-in hand-off that mints the youtube.com cookies.
     */
    fun hasGoogleSession(): Boolean {
        val names = cookieNames(GOOGLE_URL)
        return names.contains("SID") &&
                (names.contains("__Secure-1PSID") || names.contains("HSID"))
    }

    private fun youtubeCookieNames(): Set<String> {
        return cookieNames(YOUTUBE_DESKTOP_URL) + cookieNames(YOUTUBE_MOBILE_URL)
    }

    private fun cookieNames(url: String): Set<String> {
        val raw = cookieSnapshot(url)
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(';')
            .mapNotNull { pair ->
                val trimmed = pair.trim()
                if (trimmed.isEmpty()) null else trimmed.substringBefore('=').trim()
            }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /**
     * CookieManager is a UI-thread singleton. Downloads, health checks and the queued-job
     * exporter run on background dispatchers, so read the snapshot through the main looper
     * instead of touching WebView state off-thread.
     */
    private fun cookieSnapshot(url: String): String? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        }
        val latch = java.util.concurrent.CountDownLatch(1)
        val holder = arrayOfNulls<String>(1)
        val posted = Handler(Looper.getMainLooper()).post {
            holder[0] = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
            latch.countDown()
        }
        if (!posted) return null
        return if (latch.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
            holder[0]
        } else {
            null
        }
    }

    /**
     * Resolves the signed-in identity from the current WebView cookies and caches it for the
     * UI.
     *
     * YouTube never exposes the account email; its `account_menu` response only contains the
     * display name and channel handle. Google's private ListAccounts endpoint returns an HTML
     * error page for non-browser clients, so the display name is the best available label.
     */
    suspend fun resolveAndCacheAccountIdentity(context: Context): String? = withContext(Dispatchers.IO) {
        if (!hasValidSession() && !hasGoogleSession()) return@withContext null

        val accountName = runCatching {
            requestAccountName(buildRequestCookieHeader())
        }.getOrNull()

        if (!accountName.isNullOrBlank()) {
            saveCachedEmail(context, accountName)
            return@withContext accountName
        }

        getCachedEmail(context)
    }

    /**
     * YouTube session cookies live on both .youtube.com and .google.com. Combine them with
     * YouTube values taking precedence so SAPISIDHASH and the InnerTube request are complete.
     */
    private fun buildRequestCookieHeader(): String {
        val merged = LinkedHashMap<String, String>()

        parseCookiePairs(cookieSnapshot(GOOGLE_URL) ?: "").forEach { (name, value) ->
            merged[name] = value
        }
        parseCookiePairs(
            cookieSnapshot(YOUTUBE_DESKTOP_URL) ?: ""
        ).forEach { (name, value) ->
            merged[name] = value
        }

        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun requestAccountName(cookieString: String): String? {
        val body = requestAccountMenuBody(cookieString) ?: return null
        return parseAccountName(body)
    }

    private fun requestAccountMenuBody(cookieString: String): String? {
        val url = URL("$YOUTUBE_DESKTOP_URL/youtubei/v1/account/account_menu?prettyPrint=false")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("User-Agent", IDENTITY_USER_AGENT)
            setRequestProperty("Cookie", cookieString)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Origin", YOUTUBE_DESKTOP_URL)
            setRequestProperty("X-Origin", YOUTUBE_DESKTOP_URL)
            setRequestProperty("X-YouTube-Client-Name", "1")
            setRequestProperty("X-YouTube-Client-Version", "2.20241126.01.00")
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
        }

        addSapisidHash(connection, cookieString)

        val payload = """
            {
              "context": {
                "client": {
                  "clientName": "WEB",
                  "clientVersion": "2.20241126.01.00",
                  "hl": "en",
                  "gl": "US"
                }
              }
            }
        """.trimIndent()

        return try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "account_menu returned HTTP ${connection.responseCode}")
                return null
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve YouTube account menu", e)
            null
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun addSapisidHash(connection: HttpURLConnection, cookieString: String) {
        val sapisid = parseCookieMap(cookieString)["SAPISID"]
            ?: parseCookieMap(cookieString)["__Secure-3PAPISID"]
            ?: return
        val timestamp = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$timestamp $sapisid $YOUTUBE_DESKTOP_URL".toByteArray())
            .joinToString("") { "%02x".format(it) }
        connection.setRequestProperty("Authorization", "SAPISIDHASH ${timestamp}_$digest")
        connection.setRequestProperty("X-Goog-AuthUser", "0")
    }

    /**
     * Extracts the display name (or channel handle) from an InnerTube account_menu response.
     * YouTube deliberately does not expose the account email here.
     *
     * The text can be rendered either as `{"simpleText": "..."}` (current WEB responses) or as
     * `{"runs": [{"text": "..."}]}` (older responses), so both shapes are accepted.
     */
    private fun parseAccountName(rawJson: String): String? {
        return runCatching {
            val header = findAccountHeader(JSONObject(rawJson)) ?: return@runCatching null
            readTextField(header.optJSONObject("accountName"))
                ?: readTextField(header.optJSONObject("channelHandle"))
        }.getOrNull()
    }

    private fun readTextField(node: JSONObject?): String? {
        if (node == null) return null
        node.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        return node.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
    }

    /** Finds `activeAccountHeaderRenderer` regardless of where YouTube nests it. */
    private fun findAccountHeader(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("activeAccountHeaderRenderer")?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findAccountHeader(node.opt(keys.next()))?.let { return it }
                }
            }

            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findAccountHeader(node.opt(i))?.let { return it }
                }
            }
        }
        return null
    }

    // =========================================================================
    // 4. ATOMIC SESSION TEARDOWN & LOGOUT
    // =========================================================================

    /**
     * Stops the background WebView, clears Chromium's cookie/storage databases, removes the
     * cached identity and deletes the YouTube cookie row. [onComplete] runs on the main thread.
     */
    fun removeAccountAndWipeSession(context: Context, onComplete: (() -> Unit)? = null) {
        mainHandler.post {
            try {
                persistentWebView?.stopLoading()
                persistentWebView?.loadUrl("about:blank")

                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    cachedSessionVisitorData = null
                    visitorDataInvalidationListener?.invoke(context.applicationContext)

                    // CookieManager callbacks are not guaranteed to run on the UI thread, while
                    // WebStorage/WebView teardown must. Marshal them explicitly.
                    mainHandler.post {
                        WebStorage.getInstance().deleteAllData()

                        persistentWebView?.clearCache(true)
                        persistentWebView?.clearFormData()
                        persistentWebView?.clearHistory()
                        persistentWebView?.clearSslPreferences()

                        clearEnginePreferences(context)
                        cleanAllEphemeralCookieFiles(context)

                        backgroundScope.launch {
                            runCatching { deleteYoutubeCookiesFromDatabase(context) }
                                .onFailure { Log.w(TAG, "Failed to remove YouTube cookie row", it) }
                            mainHandler.post { onComplete?.invoke() }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to wipe YouTube session", e)
                clearEnginePreferences(context)
                cleanAllEphemeralCookieFiles(context)
                backgroundScope.launch {
                    runCatching { deleteYoutubeCookiesFromDatabase(context) }
                    mainHandler.post { onComplete?.invoke() }
                }
            }
        }
    }

    private suspend fun deleteYoutubeCookiesFromDatabase(context: Context) {
        val dao = DBManager.getInstance(context).cookieDao
        val youtubeCookies = dao.getAllCookies().filter {
            it.url.contains("youtube.com", ignoreCase = true)
        }
        youtubeCookies.forEach { dao.delete(it.id) }

        val application = context.applicationContext as? Application ?: return
        runCatching { CookieRepository(dao, application).updateCookieFile() }
    }

    // =========================================================================
    // 5. COOKIE EXPORT (NETSCAPE) & DATABASE SYNC
    // =========================================================================

    /**
     * Writes a per-job Netscape cookie file from the in-memory CookieManager snapshot.
     * A separate file per job avoids concurrent yt-dlp processes corrupting one shared file.
     */
    @Synchronized
    fun exportEphemeralCookieFile(context: Context, taskId: String): File {
        val directory = File(context.cacheDir, "auth_cookies").apply { mkdirs() }
        val safeTaskId = taskId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val targetFile = File(directory, "cookies_job_$safeTaskId.txt")
        targetFile.writeText(buildNetscapeCookies(), Charsets.UTF_8)
        return targetFile
    }

    /**
     * Returns the current cookies in Netscape format. Useful for the master cookies.txt file.
     */
    fun buildNetscapeCookies(): String {
        val builder = StringBuilder()
        builder.append("# Netscape HTTP Cookie File\n")
        builder.append("# Generated automatically by YTDLnis YouTubeSessionEngine\n\n")

        val now = System.currentTimeMillis() / 1000
        val expiry = now + (365L * 24 * 60 * 60)
        val seen = HashSet<String>()

        appendCookieDomain(builder, "https://www.youtube.com", ".youtube.com", expiry, seen)
        appendCookieDomain(builder, "https://m.youtube.com", ".youtube.com", expiry, seen)
        appendCookieDomain(builder, GOOGLE_URL, ".google.com", expiry, seen)

        return builder.toString()
    }

    private fun appendCookieDomain(
        builder: StringBuilder,
        url: String,
        domain: String,
        expiry: Long,
        seen: MutableSet<String>
    ) {
        val raw = cookieSnapshot(url)
        if (raw.isNullOrBlank()) return

        parseCookiePairs(raw).forEach { (name, value) ->
            val cleanName = name.replace("\t", "").replace("\n", "").replace("\r", "")
            val cleanValue = value.replace("\t", "").replace("\n", "").replace("\r", "")
            if (cleanName.isBlank() || cleanValue.isBlank()) return@forEach

            val key = "$domain|$cleanName"
            if (!seen.add(key)) return@forEach

            val isHostOnly = cleanName.startsWith("__Host-")
            val cookieDomain = if (isHostOnly) domain.trimStart('.') else domain
            val includeSubdomains = if (isHostOnly) "FALSE" else "TRUE"
            val secure = cleanName.startsWith("__Secure-") ||
                    cleanName.startsWith("__Host-") ||
                    domain.endsWith(".google.com") ||
                    domain.endsWith(".youtube.com")

            builder.append(cookieDomain).append('\t')
                .append(includeSubdomains).append('\t')
                .append('/').append('\t')
                .append(if (secure) "TRUE" else "FALSE").append('\t')
                .append(expiry).append('\t')
                .append(cleanName).append('\t')
                .append(cleanValue).append('\n')
        }
    }

    /**
     * Persists the current session into the Room cookie list and regenerates cookies.txt.
     * The Cookies screen observes Room, so the account cookie appears/updates automatically.
     */
    suspend fun syncCookiesToDatabase(context: Context, email: String? = getCachedEmail(context)) {
        withContext(Dispatchers.IO) {
            val content = buildNetscapeCookies()
            if (!content.contains('\t')) return@withContext

            val application = context.applicationContext as? Application ?: return@withContext
            val dao = DBManager.getInstance(context).cookieDao
            val description = if (email.isNullOrBlank()) {
                "YouTube account session"
            } else {
                "YouTube account ($email)"
            }

            val existing = dao.getByURL(YOUTUBE_DESKTOP_URL)
                ?: dao.getAllCookies().firstOrNull { it.url.contains("youtube.com", ignoreCase = true) }

            if (existing != null) {
                dao.update(
                    existing.copy(
                        content = content,
                        description = description,
                        enabled = true
                    )
                )
            } else {
                dao.insert(
                    CookieItem(
                        id = 0L,
                        url = YOUTUBE_DESKTOP_URL,
                        content = content,
                        description = description,
                        enabled = true
                    )
                )
            }

            runCatching { CookieRepository(dao, application).updateCookieFile() }
                .onFailure { Log.w(TAG, "Failed to update master cookie file", it) }
        }
    }

    // =========================================================================
    // 6. PREFERENCES & SMALL HELPERS
    // =========================================================================

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getCachedEmail(context: Context): String? {
        return getPrefs(context).getString(KEY_LOGGED_IN_EMAIL, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun saveCachedEmail(context: Context, email: String) {
        getPrefs(context).edit().putString(KEY_LOGGED_IN_EMAIL, email).apply()
    }

    fun getLastRefreshTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_REFRESH_TIME, 0L)
    }

    fun getLastCookieCheckTime(context: Context): Long {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getLong(PREF_LAST_COOKIE_CHECK_TIMESTAMP, 0L)
    }

    fun recordCookieCheck(context: Context, status: String, timestamp: Long = System.currentTimeMillis()) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PREF_LAST_COOKIE_CHECK_STATUS, status)
            .putLong(PREF_LAST_COOKIE_CHECK_TIMESTAMP, timestamp)
            .apply()
    }

    fun getCookieCheckStatus(context: Context): String? {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_LAST_COOKIE_CHECK_STATUS, null)
    }

    private fun saveLastRefreshTime(context: Context) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_REFRESH_TIME, System.currentTimeMillis())
            .apply()
    }

    fun registerVisitorDataInvalidationListener(listener: (Context) -> Unit) {
        visitorDataInvalidationListener = listener
    }

    /**
     * Returns only YouTube-scoped cookies (`https://www.youtube.com`). Never includes `.google.com` cookies.
     */
    fun getYoutubeCookieHeader(): String? {
        return cookieSnapshot(YOUTUBE_DESKTOP_URL)?.takeIf { it.isNotBlank() }
    }

    fun getCachedSessionVisitorData(context: Context): String? {
        val current = cachedSessionVisitorData?.takeIf { it.isNotBlank() }
        if (current != null) return current
        return getPrefs(context).getString(PREF_SESSION_VISITOR_DATA, null)
            ?.takeIf { it.isNotBlank() }
            ?.also { cachedSessionVisitorData = it }
    }

    fun saveSessionVisitorData(context: Context, rawVisitorData: String?) {
        val cleaned = rawVisitorData
            ?.trim()
            ?.removeSurrounding("\"")
            ?.replace("\\u003d", "=")
            ?.replace("\\u003D", "=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) && !it.equals("undefined", ignoreCase = true) }
            ?: return

        val previous = getCachedSessionVisitorData(context)
        cachedSessionVisitorData = cleaned
        getPrefs(context).edit()
            .putString(PREF_SESSION_VISITOR_DATA, cleaned)
            .putLong(PREF_SESSION_VISITOR_DATA_UPDATED_AT, System.currentTimeMillis())
            .apply()

        if (previous != null && previous != cleaned) {
            visitorDataInvalidationListener?.invoke(context.applicationContext)
        }
    }

    fun scheduleVisitorDataCapture(webView: WebView, context: Context, attempt: Int = 0) {
        if (!hasValidSession()) return
        val appContext = context.applicationContext
        val delayMs = if (attempt == 0) 150L else 250L
        mainHandler.postDelayed({
            runCatching {
                webView.evaluateJavascript(
                    "(function(){try{var v=(window.ytcfg&&window.ytcfg.get)?window.ytcfg.get('VISITOR_DATA'):null;if(!v&&window.ytInitialData&&window.ytInitialData.responseContext){v=window.ytInitialData.responseContext.visitorData;}return v||'';}catch(e){return '';}})()"
                ) { result ->
                    val cleaned = result?.trim()?.removeSurrounding("\"")?.trim()
                    if (!cleaned.isNullOrEmpty() && !cleaned.equals("null", ignoreCase = true)) {
                        saveSessionVisitorData(appContext, cleaned)
                    } else if (attempt < 5) {
                        scheduleVisitorDataCapture(webView, appContext, attempt + 1)
                    }
                }
            }
        }, delayMs)
    }

    suspend fun ensureSessionVisitorData(context: Context, forceRefresh: Boolean = false): String? {
        if (!hasValidSession()) return null
        val prefs = getPrefs(context)
        if (!forceRefresh) {
            val cached = getCachedSessionVisitorData(context)
            val updatedAt = prefs.getLong(PREF_SESSION_VISITOR_DATA_UPDATED_AT, 0L)
            if (!cached.isNullOrBlank() && System.currentTimeMillis() - updatedAt < VISITOR_DATA_MAX_AGE_MS) {
                return cached
            }
        }

        runCatching { performSilentSessionRefresh(context, force = true) }
        repeat(6) {
            val captured = getCachedSessionVisitorData(context)
            val updatedAt = prefs.getLong(PREF_SESSION_VISITOR_DATA_UPDATED_AT, 0L)
            if (!captured.isNullOrBlank() && (!forceRefresh || System.currentTimeMillis() - updatedAt < 30_000L)) {
                return captured
            }
            kotlinx.coroutines.delay(250L)
        }

        val innerTubeFallback = requestVisitorDataWithSessionCookies(context)
        if (!innerTubeFallback.isNullOrBlank()) {
            saveSessionVisitorData(context, innerTubeFallback)
            return innerTubeFallback
        }

        return getCachedSessionVisitorData(context)
    }

    private suspend fun requestVisitorDataWithSessionCookies(context: Context): String? = withContext(Dispatchers.IO) {
        val ytCookies = getYoutubeCookieHeader() ?: return@withContext null
        val userAgent = resolveUserAgent(context)
        var connection: HttpURLConnection? = null
        try {
            val url = java.net.URL("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Cookie", ytCookies)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Origin", YOUTUBE_DESKTOP_URL)
                setRequestProperty("Referer", "$YOUTUBE_DESKTOP_URL/")
                setRequestProperty("X-Youtube-Client-Name", "1")
                setRequestProperty("X-Youtube-Client-Version", "2.20250101.00.00")
                addSapisidHash(this, ytCookies)
            }
            val payload = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20250101.00.00","hl":"en","gl":"US"}}}"""
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = org.json.JSONObject(body)
                root.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun clearEnginePreferences(context: Context) {
        cachedSessionVisitorData = null
        visitorDataInvalidationListener?.invoke(context.applicationContext)
        getPrefs(context).edit().clear().apply()
    }

    private fun parseCookiePairs(raw: String): List<Pair<String, String>> {
        return raw.split(';').mapNotNull { pair ->
            val trimmed = pair.trim()
            val equals = trimmed.indexOf('=')
            if (equals <= 0) return@mapNotNull null
            trimmed.substring(0, equals).trim() to trimmed.substring(equals + 1).trim()
        }
    }

    private fun parseCookieMap(raw: String): Map<String, String> {
        return parseCookiePairs(raw).toMap()
    }

    private fun cleanAllEphemeralCookieFiles(context: Context) {
        runCatching {
            val filter = FilenameFilter { _, name ->
                name.startsWith("cookies_job_") && name.endsWith(".txt")
            }
            File(context.cacheDir, "auth_cookies").listFiles(filter)?.forEach { it.delete() }
            context.cacheDir.listFiles(filter)?.forEach { it.delete() }
        }
    }

}
