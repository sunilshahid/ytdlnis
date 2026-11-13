package com.deniscerri.ytdl.core

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.auth.YouTubeSessionEngine
import com.deniscerri.ytdl.util.EphemeralCookieExporter
import com.deniscerri.ytdl.util.network.CookieHealthChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Coordinates YouTube session health across downloads.
 *
 * All healing is performed by [YouTubeSessionEngine] through the user's persistent, interactive
 * WebView login. There is no microG/GMS AccountManager dependency.
 */
object SessionOrchestrator {
    private const val TAG = "SessionOrchestrator"

    /** Minimum interval between automatic health probes (app open and download pre-flight). */
    private const val HEALTH_CHECK_INTERVAL_MS = 30 * 60 * 1000L

    // Only one silent rotation may run across all concurrent downloads.
    private val sessionMutex = Mutex()

    enum class RefreshOutcome {
        /** Session cookies were rotated and persisted successfully. */
        SUCCESS,

        /** No authenticated session exists; the UI must open the sign-in screen. */
        NEEDS_MANUAL_LOGIN,

        /** A session exists but rotation failed (network, timeout, etc.). */
        FAILED
    }

    /**
     * Rotates the YouTube session using the persistent WebView and persists the new cookies.
     */
    suspend fun refreshYoutubeSession(context: Context): RefreshOutcome = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            try {
                if (!YouTubeSessionEngine.hasValidSession() &&
                    !YouTubeSessionEngine.hasGoogleSession()
                ) {
                    Log.d(TAG, "No YouTube session available; interactive sign-in is required")
                    return@withLock RefreshOutcome.NEEDS_MANUAL_LOGIN
                }

                val rotated = YouTubeSessionEngine.performSilentSessionRefresh(
                    context = context,
                    force = true
                )

                if (!rotated) {
                    Log.w(TAG, "Persistent WebView session rotation failed")
                    return@withLock RefreshOutcome.FAILED
                }

                val email = YouTubeSessionEngine.resolveAndCacheAccountIdentity(context)
                YouTubeSessionEngine.syncCookiesToDatabase(context, email)
                com.deniscerri.ytdl.util.extractors.newpipe.potoken.PoTokenCoordinator.invalidateSessionTokens(context)
                Log.i(TAG, "YouTube session refreshed successfully")
                RefreshOutcome.SUCCESS
            } catch (e: Exception) {
                Log.e(TAG, "Exception while refreshing YouTube session", e)
                RefreshOutcome.FAILED
            }
        }
    }

    /**
     * Pre-flight health check used before a YouTube download. Expired cookies trigger a silent
     * rotation; a network error is treated as non-fatal so the download can still try.
     */
    suspend fun checkAndHealYoutubeSessionIfNeeded(
        context: Context,
        force: Boolean = false
    ): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val useCookies = preferences.getBoolean(YouTubeSessionEngine.PREF_USE_COOKIES, false)
        val youtubeAccountCookies =
            preferences.getBoolean(YouTubeSessionEngine.PREF_YOUTUBE_ACCOUNT_COOKIES, false)

        if (!useCookies || !youtubeAccountCookies) return true

        if (!YouTubeSessionEngine.hasValidSession() &&
            !YouTubeSessionEngine.hasGoogleSession()
        ) {
            YouTubeSessionEngine.recordCookieCheck(
                context,
                YouTubeSessionEngine.COOKIE_STATUS_SIGNED_OUT
            )
            return true
        }

        val now = System.currentTimeMillis()
        if (!force) {
            val lastCheck = YouTubeSessionEngine.getLastCookieCheckTime(context)
            if (lastCheck > 0L && now - lastCheck in 0 until HEALTH_CHECK_INTERVAL_MS) {
                return true
            }
        }

        return when (val health = CookieHealthChecker.checkYoutubeCookieHealth(context)) {
            is CookieHealthChecker.HealthStatus.Healthy -> {
                YouTubeSessionEngine.recordCookieCheck(
                    context,
                    YouTubeSessionEngine.COOKIE_STATUS_HEALTHY,
                    now
                )
                health.accountName?.takeIf { it.isNotBlank() }?.let {
                    YouTubeSessionEngine.saveCachedEmail(context, it)
                }
                // The engine self-throttles to 6 h, so this is a no-op for a recently
                // refreshed session and a quiet rotation when the interval has elapsed.
                YouTubeSessionEngine.performSilentSessionRefresh(context, force = false)
                true
            }

            is CookieHealthChecker.HealthStatus.ExpiredOrLoggedOut -> {
                YouTubeSessionEngine.recordCookieCheck(
                    context,
                    YouTubeSessionEngine.COOKIE_STATUS_EXPIRED,
                    now
                )
                Log.d(TAG, "YouTube cookies are expired; attempting a silent refresh")
                val refreshed = refreshYoutubeSession(context) == RefreshOutcome.SUCCESS
                if (refreshed) {
                    YouTubeSessionEngine.recordCookieCheck(
                        context,
                        YouTubeSessionEngine.COOKIE_STATUS_HEALTHY
                    )
                }
                refreshed
            }

            is CookieHealthChecker.HealthStatus.Error -> {
                // Keep the last known identity, but surface that the probe itself failed.
                YouTubeSessionEngine.recordCookieCheck(
                    context,
                    YouTubeSessionEngine.COOKIE_STATUS_ERROR,
                    now
                )
                Log.w(TAG, "Cookie health check failed; allowing download to proceed", health.throwable)
                true
            }
        }
    }

    /**
     * Background task for App.onCreate: gated by the user pref, throttled by
     * [checkAndHealYoutubeSessionIfNeeded], and never clears a healthy session.
     */
    suspend fun runStartupCookieCheck(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!preferences.getBoolean(
                YouTubeSessionEngine.PREF_CHECK_YOUTUBE_COOKIES_ON_OPEN,
                true
            )
        ) {
            return
        }
        checkAndHealYoutubeSessionIfNeeded(context)
    }

    /**
     * Detects yt-dlp output/errors that indicate an expired cookie or a bot check.
     */
    fun isAuthOrCookieError(errorMessage: String?): Boolean {
        if (errorMessage.isNullOrBlank()) return false
        val lower = errorMessage.lowercase()
        val errorPatterns = listOf(
            "http error 403",
            "403 forbidden",
            "sign in to confirm",
            "not a bot",
            "login required",
            "confirm you're not a robot",
            "cookies are expired",
            "could not send request"
        )
        return errorPatterns.any { lower.contains(it) }
    }

    /**
     * Wraps a download execution with:
     *  1. A per-job ephemeral Netscape cookie file (no concurrent file corruption).
     *  2. One transparent heal-and-retry when yt-dlp reports an auth/403 error.
     *  3. Guaranteed deletion of the ephemeral file.
     */
    suspend fun <T> withAutoRetry(
        context: Context,
        jobId: String,
        isYoutube: Boolean,
        useCookies: Boolean = true,
        block: suspend (ephemeralCookieFile: File?) -> T
    ): T {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val youtubeAccountCookies =
            preferences.getBoolean(YouTubeSessionEngine.PREF_YOUTUBE_ACCOUNT_COOKIES, false)

        // Pre-flight heal so the first attempt already uses freshly minted YouTube cookies
        // instead of relying solely on the post-403 retry path.
        if (isYoutube && useCookies && youtubeAccountCookies) {
            checkAndHealYoutubeSessionIfNeeded(context)
        }

        var ephemeralCookieFile: File? = if (useCookies) {
            EphemeralCookieExporter.createEphemeralCookieFile(context, jobId)
        } else null

        return try {
            block(ephemeralCookieFile)
        } catch (e: Exception) {
            if (isYoutube && useCookies && youtubeAccountCookies && isAuthOrCookieError(e.message)) {
                Log.w(
                    TAG,
                    "Auth/cookie error for job $jobId: ${e.message}. Attempting session healing..."
                )
                val outcome = refreshYoutubeSession(context)
                if (outcome == RefreshOutcome.SUCCESS) {
                    // Re-export the freshly rotated session into this job's isolated file.
                    EphemeralCookieExporter.deleteEphemeralCookieFile(ephemeralCookieFile)
                    ephemeralCookieFile = EphemeralCookieExporter.createEphemeralCookieFile(
                        context,
                        jobId
                    )
                    Log.i(TAG, "Session healed; retrying job $jobId once")
                    return block(ephemeralCookieFile)
                }
            }
            throw e
        } finally {
            EphemeralCookieExporter.deleteEphemeralCookieFile(ephemeralCookieFile)
        }
    }
}
