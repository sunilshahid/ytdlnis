package com.deniscerri.ytdl.util.extractors.newpipe.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.auth.YouTubeSessionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.atomic.AtomicBoolean

object PoTokenCoordinator {
    private const val TAG = "PoTokenCoordinator"
    const val PREF_PO_TOKEN_POLICY = "po_token_source_policy"
    const val PREF_AUTO_PO_TOKEN_ENABLED = "auto_po_token_enabled"
    private const val PLAYER_CACHE_MAX_SIZE = 16
    private const val PLAYER_TOKEN_TTL_MS = 2 * 60 * 60 * 1000L

    data class StatusSnapshot(
        val policy: PoTokenPolicy,
        val activeSource: PoTokenSource?,
        val maskedVisitorData: String?,
        val generatedAtMs: Long?,
        val expiresAtMs: Long?,
        val signedIn: Boolean,
        val cookiesEnabled: Boolean,
        val message: String?
    )

    private val supportsWebView by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private val mutex = Mutex()
    private val hookRegistered = AtomicBoolean(false)

    @Volatile
    private var cachedGenerator: PoTokenWebView? = null

    @Volatile
    private var cachedBundle: PoTokenBundle? = null

    @Volatile
    private var lastErrorMessage: String? = null

    private val playerTokenCache = object : LinkedHashMap<String, Pair<String, Long>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, Long>>?): Boolean {
            return size > PLAYER_CACHE_MAX_SIZE
        }
    }

    private fun ensureLifecycleHook() {
        if (hookRegistered.compareAndSet(false, true)) {
            YouTubeSessionEngine.registerVisitorDataInvalidationListener {
                invalidateSessionTokensSync()
            }
        }
    }

    fun getPolicy(context: Context): PoTokenPolicy {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(PREF_AUTO_PO_TOKEN_ENABLED, true)) {
            return PoTokenPolicy.MANUAL_ONLY
        }
        return PoTokenPolicy.fromPref(prefs.getString(PREF_PO_TOKEN_POLICY, PoTokenPolicy.AUTO.prefValue))
    }

    fun setPolicy(context: Context, policy: PoTokenPolicy) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PREF_PO_TOKEN_POLICY, policy.prefValue)
            .putBoolean(PREF_AUTO_PO_TOKEN_ENABLED, policy != PoTokenPolicy.MANUAL_ONLY)
            .apply()
    }

    fun getCachedBundleIfValid(context: Context, videoId: String? = null): PoTokenBundle? {
        ensureLifecycleHook()
        val policy = getPolicy(context)
        if (policy == PoTokenPolicy.MANUAL_ONLY) return null

        val bundle = cachedBundle ?: return null
        val generator = cachedGenerator ?: return null
        if (generator.isExpired() || bundle.isExpired()) return null

        val cookiesEnabled = isCookiesEnabled(context)
        val signedIn = YouTubeSessionEngine.hasValidSession()

        when (policy) {
            PoTokenPolicy.SESSION_ONLY -> {
                if (!signedIn || bundle.source != PoTokenSource.SESSION_WEBVIEW) return null
            }
            PoTokenPolicy.ANONYMOUS_ONLY -> {
                if (bundle.source != PoTokenSource.ANONYMOUS_WEBVIEW) return null
            }
            PoTokenPolicy.AUTO -> {
                if (signedIn && cookiesEnabled && bundle.source != PoTokenSource.SESSION_WEBVIEW) return null
                if ((!signedIn || !cookiesEnabled) && bundle.source == PoTokenSource.SESSION_WEBVIEW) return null
            }
            PoTokenPolicy.MANUAL_ONLY -> return null
        }

        if (bundle.source == PoTokenSource.SESSION_WEBVIEW) {
            val currentVd = YouTubeSessionEngine.getCachedSessionVisitorData(context)
            if (!currentVd.isNullOrBlank() && currentVd != bundle.visitorData) {
                return null
            }
        }

        val playerToken = videoId?.let { lookupPlayerTokenLocked(bundle, it) }
        return if (playerToken != null) bundle.copy(playerToken = playerToken) else bundle
    }

    suspend fun getOrMintForYtDlp(
        context: Context,
        url: String?,
        forceRefresh: Boolean = false
    ): PoTokenBundle? {
        val videoId = PoTokenFormatting.extractYoutubeVideoId(url)
        return getOrMintBundle(context, videoId, forceRefresh)
    }

    fun getCachedOrMintOnWorker(context: Context, url: String?): PoTokenBundle? {
        val videoId = PoTokenFormatting.extractYoutubeVideoId(url)
        getCachedBundleIfValid(context, videoId)?.let { cached ->
            if (videoId == null || cached.playerToken != null) return cached
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return getCachedBundleIfValid(context, videoId)
        }
        return runCatching {
            runBlocking { getOrMintBundle(context, videoId, forceRefresh = false) }
        }.getOrNull()
    }

    suspend fun getOrMintForNewPipe(
        context: Context,
        videoId: String?,
        forceRecreate: Boolean = false
    ): PoTokenBundle? {
        return getOrMintBundle(context, videoId, forceRecreate)
    }

    suspend fun regenerateNow(context: Context): PoTokenBundle? {
        invalidateAllSync()
        val policy = getPolicy(context)
        if (policy == PoTokenPolicy.MANUAL_ONLY) {
            setPolicy(context, PoTokenPolicy.AUTO)
        }
        return getOrMintBundle(context, videoId = null, forceRefresh = true)
    }

    fun invalidateSessionTokens(context: Context? = null) {
        invalidateSessionTokensSync()
    }

    fun getStatusSnapshot(context: Context): StatusSnapshot {
        ensureLifecycleHook()
        val bundle = cachedBundle?.takeUnless { it.isExpired() || cachedGenerator?.isExpired() == true }
        return StatusSnapshot(
            policy = getPolicy(context),
            activeSource = bundle?.source,
            maskedVisitorData = bundle?.maskedVisitorData,
            generatedAtMs = bundle?.generatedAtMs,
            expiresAtMs = bundle?.expiresAtMs,
            signedIn = YouTubeSessionEngine.hasValidSession(),
            cookiesEnabled = isCookiesEnabled(context),
            message = lastErrorMessage
        )
    }

    private suspend fun getOrMintBundle(
        context: Context,
        videoId: String?,
        forceRefresh: Boolean
    ): PoTokenBundle? {
        ensureLifecycleHook()
        if (!supportsWebView) {
            lastErrorMessage = "WebView unavailable for BotGuard"
            return null
        }

        val appContext = context.applicationContext
        val policy = getPolicy(appContext)
        if (policy == PoTokenPolicy.MANUAL_ONLY) return null

        val signedIn = YouTubeSessionEngine.hasValidSession()
        val cookiesEnabled = isCookiesEnabled(appContext)

        if (!forceRefresh) {
            getCachedBundleIfValid(appContext, videoId)?.let { cached ->
                if (videoId.isNullOrBlank() || !cached.playerToken.isNullOrBlank()) {
                    return cached
                }
            }
        }

        return mutex.withLock {
            if (!forceRefresh) {
                getCachedBundleIfValid(appContext, videoId)?.let { cached ->
                    if (videoId.isNullOrBlank() || !cached.playerToken.isNullOrBlank()) {
                        return@withLock cached
                    }
                }
            }

            when (policy) {
                PoTokenPolicy.SESSION_ONLY -> {
                    if (!signedIn) {
                        lastErrorMessage = "Session PO token policy requires a signed-in YouTube account"
                        return@withLock null
                    }
                    mintSessionBundleLocked(appContext, videoId, forceRefresh)
                }
                PoTokenPolicy.ANONYMOUS_ONLY -> {
                    mintAnonymousBundleLocked(appContext, videoId, forceRefresh)
                }
                PoTokenPolicy.AUTO -> {
                    if (signedIn && cookiesEnabled) {
                        mintSessionBundleLocked(appContext, videoId, forceRefresh)
                            ?: run {
                                Log.w(TAG, "Session PO token mint fell back to anonymous WebView without cookies")
                                mintAnonymousBundleLocked(appContext, videoId, forceRefresh = true)
                            }
                    } else {
                        mintAnonymousBundleLocked(appContext, videoId, forceRefresh)
                    }
                }
                PoTokenPolicy.MANUAL_ONLY -> null
            }
        }
    }

    private suspend fun mintSessionBundleLocked(
        context: Context,
        videoId: String?,
        forceRefresh: Boolean
    ): PoTokenBundle? {
        if (!YouTubeSessionEngine.hasValidSession() || YouTubeSessionEngine.getYoutubeCookieHeader().isNullOrBlank()) {
            lastErrorMessage = "No active YouTube session cookies"
            return null
        }

        val sessionVisitorData = YouTubeSessionEngine.ensureSessionVisitorData(context, forceRefresh = forceRefresh)
        if (sessionVisitorData.isNullOrBlank()) {
            lastErrorMessage = "Could not extract session VISITOR_DATA"
            return null
        }

        val existingGen = cachedGenerator
        val existingBundle = cachedBundle
        val canReuseBase = !forceRefresh &&
            existingGen != null &&
            !existingGen.isExpired() &&
            existingBundle != null &&
            existingBundle.source == PoTokenSource.SESSION_WEBVIEW &&
            !existingBundle.isExpired() &&
            existingBundle.visitorData == sessionVisitorData

        if (canReuseBase && existingGen != null && existingBundle != null) {
            return attachPlayerTokenLocked(existingGen, existingBundle, videoId, retryOnError = {
                mintSessionBundleLocked(context, videoId, forceRefresh = true)
            })
        }

        return try {
            closeGeneratorOnMain(cachedGenerator)
            cachedGenerator = null
            val userAgent = YouTubeSessionEngine.resolveUserAgent(context)
            val generator = PoTokenWebView.getNewPoTokenGenerator(
                context = context,
                userAgent = userAgent,
                cookieHeaderProvider = { YouTubeSessionEngine.getYoutubeCookieHeader() }
            )
            val streamingPot = generator.generatePoToken(sessionVisitorData)
            val now = System.currentTimeMillis()
            val bundle = PoTokenBundle(
                visitorData = sessionVisitorData,
                streamingToken = streamingPot,
                playerToken = null,
                clientName = "mweb",
                generatedAtMs = now,
                expiresAtMs = now + PoTokenBundle.DEFAULT_BUNDLE_TTL_MS,
                source = PoTokenSource.SESSION_WEBVIEW
            )
            cachedGenerator = generator
            cachedBundle = bundle
            lastErrorMessage = null
            attachPlayerTokenLocked(generator, bundle, videoId, retryOnError = null)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to mint signed-in PO token", t)
            lastErrorMessage = "Signed-in BotGuard failed: ${t.message ?: "unknown error"}"
            closeGeneratorOnMain(cachedGenerator)
            cachedGenerator = null
            cachedBundle = null
            null
        }
    }

    private suspend fun mintAnonymousBundleLocked(
        context: Context,
        videoId: String?,
        forceRefresh: Boolean
    ): PoTokenBundle? {
        val existingGen = cachedGenerator
        val existingBundle = cachedBundle
        val canReuseBase = !forceRefresh &&
            existingGen != null &&
            !existingGen.isExpired() &&
            existingBundle != null &&
            existingBundle.source == PoTokenSource.ANONYMOUS_WEBVIEW &&
            !existingBundle.isExpired()

        if (canReuseBase && existingGen != null && existingBundle != null) {
            return attachPlayerTokenLocked(existingGen, existingBundle, videoId, retryOnError = {
                mintAnonymousBundleLocked(context, videoId, forceRefresh = true)
            })
        }

        return try {
            val visitorData = withContext(Dispatchers.IO) {
                val innertubeClientRequestInfo = InnertubeClientRequestInfo.ofWebClient()
                innertubeClientRequestInfo.clientInfo.clientVersion =
                    YoutubeParsingHelper.getClientVersion()
                YoutubeParsingHelper.getVisitorDataFromInnertube(
                    innertubeClientRequestInfo,
                    NewPipe.getPreferredLocalization(),
                    NewPipe.getPreferredContentCountry(),
                    YoutubeParsingHelper.getYouTubeHeaders(),
                    YoutubeParsingHelper.YOUTUBEI_V1_URL,
                    null,
                    false
                )
            }
            if (visitorData.isNullOrBlank()) {
                lastErrorMessage = "Anonymous InnerTube visitorData was empty"
                return null
            }

            closeGeneratorOnMain(cachedGenerator)
            cachedGenerator = null
            val generator = PoTokenWebView.getNewPoTokenGenerator(
                context = context,
                cookieHeaderProvider = null
            )
            val streamingPot = generator.generatePoToken(visitorData)
            val now = System.currentTimeMillis()
            val bundle = PoTokenBundle(
                visitorData = visitorData,
                streamingToken = streamingPot,
                playerToken = null,
                clientName = "mweb",
                generatedAtMs = now,
                expiresAtMs = now + PoTokenBundle.DEFAULT_BUNDLE_TTL_MS,
                source = PoTokenSource.ANONYMOUS_WEBVIEW
            )
            cachedGenerator = generator
            cachedBundle = bundle
            lastErrorMessage = null
            attachPlayerTokenLocked(generator, bundle, videoId, retryOnError = null)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to mint anonymous PO token", t)
            lastErrorMessage = "Anonymous BotGuard failed: ${t.message ?: "unknown error"}"
            closeGeneratorOnMain(cachedGenerator)
            cachedGenerator = null
            cachedBundle = null
            null
        }
    }

    private suspend fun attachPlayerTokenLocked(
        generator: PoTokenWebView,
        bundle: PoTokenBundle,
        videoId: String?,
        retryOnError: (suspend () -> PoTokenBundle?)?
    ): PoTokenBundle? {
        val targetVideoId = videoId?.takeIf { it.isNotBlank() } ?: return bundle
        lookupPlayerTokenLocked(bundle, targetVideoId)?.let { cached ->
            return bundle.copy(playerToken = cached)
        }

        return try {
            val playerPot = generator.generatePoToken(targetVideoId)
            synchronized(playerTokenCache) {
                playerTokenCache[playerCacheKey(bundle, targetVideoId)] =
                    playerPot to (System.currentTimeMillis() + PLAYER_TOKEN_TTL_MS)
            }
            bundle.copy(playerToken = playerPot)
        } catch (t: Throwable) {
            Log.w(TAG, "Player PO token mint failed; recreating generator if eligible", t)
            if (retryOnError != null) {
                retryOnError.invoke()
            } else {
                bundle
            }
        }
    }

    private fun lookupPlayerTokenLocked(bundle: PoTokenBundle, videoId: String): String? {
        val now = System.currentTimeMillis()
        synchronized(playerTokenCache) {
            val entry = playerTokenCache[playerCacheKey(bundle, videoId)] ?: return null
            if (entry.second <= now) {
                playerTokenCache.remove(playerCacheKey(bundle, videoId))
                return null
            }
            return entry.first
        }
    }

    private fun playerCacheKey(bundle: PoTokenBundle, videoId: String): String {
        return "${bundle.source.id}:${bundle.visitorData}:$videoId"
    }

    private fun isCookiesEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(YouTubeSessionEngine.PREF_USE_COOKIES, false) ||
            prefs.getBoolean(YouTubeSessionEngine.PREF_YOUTUBE_ACCOUNT_COOKIES, false)
    }

    private fun invalidateSessionTokensSync() {
        val current = cachedBundle
        if (current == null || current.source == PoTokenSource.SESSION_WEBVIEW) {
            invalidateAllSync()
        }
    }

    private fun invalidateAllSync() {
        val gen = cachedGenerator
        cachedGenerator = null
        cachedBundle = null
        synchronized(playerTokenCache) {
            playerTokenCache.clear()
        }
        closeGeneratorOnMain(gen)
    }

    private fun closeGeneratorOnMain(generator: PoTokenWebView?) {
        generator ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { generator.close() }
        } else {
            Handler(Looper.getMainLooper()).post {
                runCatching { generator.close() }
            }
        }
    }
}
