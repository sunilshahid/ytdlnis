package com.deniscerri.ytdl.util.network

import android.content.Context
import android.util.Log
import com.deniscerri.ytdl.auth.YouTubeSessionEngine
import com.deniscerri.ytdl.core.packages.PackageBase
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object CookieHealthChecker {
    private const val TAG = "CookieHealthChecker"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

    private val httpClient: OkHttpClient by lazy {
        PackageBase.sharedClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    sealed class HealthStatus {
        data class Healthy(val accountName: String?, val channelHandle: String?) : HealthStatus()
        object ExpiredOrLoggedOut : HealthStatus()
        data class Error(val throwable: Throwable) : HealthStatus()
    }

    /**
     * Executes a fast, lightweight OkHttp ping to YouTube's InnerTube account menu
     * to verify whether existing session cookies are valid.
     */
    suspend fun checkYoutubeCookieHealth(
        context: Context,
        cookieFile: File? = null
    ): HealthStatus = withContext(Dispatchers.IO) {
        try {
            val cookiesMap = if (cookieFile != null) {
                if (!cookieFile.exists() || cookieFile.length() == 0L) {
                    return@withContext HealthStatus.ExpiredOrLoggedOut
                }
                extractCookies(cookieFile.readText())
            } else if (YouTubeSessionEngine.hasValidSession() ||
                YouTubeSessionEngine.hasGoogleSession()
            ) {
                // The persistent WebView is the source of truth. cacheDir/cookies.txt is a
                // derived copy and can lag behind a rotated __Secure-1PSIDTS cookie.
                extractCookies(YouTubeSessionEngine.buildNetscapeCookies())
            } else {
                val fallback = File(context.cacheDir, "cookies.txt")
                if (!fallback.exists() || fallback.length() == 0L) {
                    return@withContext HealthStatus.ExpiredOrLoggedOut
                }
                extractCookies(fallback.readText())
            }

            if (cookiesMap.isEmpty() || !hasEssentialYoutubeCookies(cookiesMap)) {
                return@withContext HealthStatus.ExpiredOrLoggedOut
            }

            val origin = "https://www.youtube.com"
            val cookieHeader = cookiesMap.map { "${it.key}=${it.value}" }.joinToString("; ")

            val requestBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookieHeader)
                .header("Origin", origin)
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "1")
                .header("X-YouTube-Client-Version", "2.20241126.01.00")

            // Calculate SAPISIDHASH signature if SAPISID or __Secure-3PAPISID is available
            val sapisid = cookiesMap["SAPISID"] ?: cookiesMap["__Secure-3PAPISID"]
            if (!sapisid.isNullOrBlank()) {
                val ts = System.currentTimeMillis() / 1000
                val hash = MessageDigest.getInstance("SHA-1")
                    .digest("$ts $sapisid $origin".toByteArray())
                    .joinToString("") { "%02x".format(it) }
                requestBuilder.header("Authorization", "SAPISIDHASH ${ts}_$hash")
                requestBuilder.header("X-Goog-AuthUser", "0")
            }

            val jsonPayload = """
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

            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = requestBuilder.post(body).build()

            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    Log.d(TAG, "Cookie health check returned HTTP ${resp.code}; session expired")
                    return@withContext HealthStatus.ExpiredOrLoggedOut
                }
                if (!resp.isSuccessful) {
                    // 5xx/429/network proxy pages are transient, not proof of an expired login.
                    Log.d(TAG, "Cookie health check failed with HTTP ${resp.code}")
                    return@withContext HealthStatus.Error(
                        IllegalStateException("HTTP ${resp.code}")
                    )
                }

                val bodyString = resp.body.string()
                if (bodyString.isBlank()) {
                    return@withContext HealthStatus.ExpiredOrLoggedOut
                }

                val json = runCatching { JsonParser.parseString(bodyString).asJsonObject }.getOrNull()
                    ?: return@withContext HealthStatus.ExpiredOrLoggedOut

                val header = findAccountHeader(json)
                    ?: return@withContext HealthStatus.ExpiredOrLoggedOut
                val name = extractText(header.get("accountName"))
                val handle = extractText(header.get("channelHandle"))
                HealthStatus.Healthy(name, handle)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during cookie health check", e)
            HealthStatus.Error(e)
        }
    }

    /** Finds `activeAccountHeaderRenderer` regardless of how deeply YouTube nests it. */
    private fun findAccountHeader(node: JsonElement?): JsonObject? {
        when {
            node == null || node.isJsonNull -> return null
            node.isJsonObject -> {
                val obj = node.asJsonObject
                obj.getAsJsonObject("activeAccountHeaderRenderer")?.let { return it }
                obj.entrySet().forEach { entry ->
                    findAccountHeader(entry.value)?.let { return it }
                }
            }

            node.isJsonArray -> {
                node.asJsonArray.forEach { element ->
                    findAccountHeader(element)?.let { return it }
                }
            }
        }
        return null
    }

    private fun extractText(node: com.google.gson.JsonElement?): String? {
        if (node == null || !node.isJsonObject) return null
        val obj = node.asJsonObject
        if (obj.has("simpleText") && !obj.get("simpleText").isJsonNull) {
            val s = obj.get("simpleText").asString
            if (s.isNotBlank()) return s
        }
        if (obj.has("runs") && obj.get("runs").isJsonArray) {
            val runs = obj.getAsJsonArray("runs")
            if (runs.size() > 0 && runs[0].isJsonObject) {
                val text = runs[0].asJsonObject.get("text")?.asString
                if (!text.isNullOrBlank()) return text
            }
        }
        return null
    }

    private fun hasEssentialYoutubeCookies(cookies: Map<String, String>): Boolean {
        val hasPrimary = cookies.containsKey("SID") || cookies.containsKey("__Secure-1PSID")
        val hasSecondary = cookies.containsKey("SAPISID") ||
            cookies.containsKey("__Secure-3PAPISID") ||
            cookies.containsKey("HSID") ||
            cookies.containsKey("LOGIN_INFO")
        return hasPrimary && hasSecondary
    }

    private fun extractCookies(content: String): Map<String, String> {
        // Only youtube.com-scoped cookies are actually sent to youtube.com. Including
        // `.google.com` rows here would make an anonymous jar look authenticated.
        val targetDomains = listOf("youtube.com")
        return try {
            content.lineSequence()
                .map { it.trim() }
                .map { it.removePrefix("#HttpOnly_") }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.split("\t") }
                .filter { it.size >= 7 }
                .filter { cols ->
                    val domain = cols[0].lowercase().trimStart('.')
                    targetDomains.any { domain == it || domain.endsWith(".$it") }
                }
                .associate { it[5] to it[6] }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cookie content", e)
            emptyMap()
        }
    }

    private fun extractCookies(file: File): Map<String, String> {
        return try {
            extractCookies(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cookie file: ${file.absolutePath}", e)
            emptyMap()
        }
    }
}
