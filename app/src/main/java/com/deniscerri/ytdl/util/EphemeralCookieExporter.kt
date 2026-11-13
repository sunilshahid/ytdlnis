package com.deniscerri.ytdl.util

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.auth.YouTubeSessionEngine
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.repository.CookieRepository
import java.io.File
import java.io.FilenameFilter

/**
 * Creates isolated per-job cookie files for yt-dlp.
 *
 * When the persistent YouTube session is enabled, the snapshot is taken directly from the
 * in-memory CookieManager (sub-millisecond) instead of copying a potentially stale cookies.txt.
 */
object EphemeralCookieExporter {
    private const val TAG = "EphemeralCookieExporter"

    fun createEphemeralCookieFile(context: Context, jobId: String): File? {
        try {
            val cacheDir = context.cacheDir
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val youtubeAccountCookies =
                preferences.getBoolean(YouTubeSessionEngine.PREF_YOUTUBE_ACCOUNT_COOKIES, false)

            // Preferred path: export the live persistent WebView session.
            if (youtubeAccountCookies && YouTubeSessionEngine.hasValidSession()) {
                val liveFile = YouTubeSessionEngine.exportEphemeralCookieFile(context, jobId)
                if (hasCookieLines(liveFile)) return liveFile
                runCatching { liveFile.delete() }
            }

            val ephemeralFile = File(cacheDir, "cookies_job_${sanitizeJobId(jobId)}.txt")
            val masterCookieFile = File(cacheDir, "cookies.txt")

            // Fallback 1: fast copy of the master file.
            if (masterCookieFile.exists() && masterCookieFile.length() > 0L) {
                masterCookieFile.copyTo(ephemeralFile, overwrite = true)
                if (hasCookieLines(ephemeralFile)) return ephemeralFile
            }

            // Fallback 2: build from enabled Room cookie rows.
            val dbManager = DBManager.getInstance(context)
            val enabledCookies = dbManager.cookieDao.getAllEnabledCookies()
            if (enabledCookies.isNotEmpty()) {
                val content = StringBuilder(CookieRepository.cookieHeader).append("\n")
                enabledCookies.forEach { item ->
                    if (item.content.isNotBlank()) {
                        content.append(item.content.trim()).append("\n")
                    }
                }
                ephemeralFile.writeText(content.toString())

                if (!masterCookieFile.exists() || masterCookieFile.length() == 0L) {
                    runCatching { masterCookieFile.writeText(content.toString()) }
                }

                if (hasCookieLines(ephemeralFile)) return ephemeralFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ephemeral cookie file for job $jobId", e)
        }

        return null
    }

    fun createEphemeralCookieFile(context: Context, jobId: Long): File? {
        return createEphemeralCookieFile(context, jobId.toString())
    }

    private fun hasCookieLines(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return runCatching {
            file.useLines { lines ->
                lines.any { line ->
                    line.isNotBlank() && !line.startsWith("#") && line.contains('\t')
                }
            }
        }.getOrDefault(false)
    }

    fun deleteEphemeralCookieFile(file: File?) {
        try {
            if (file != null && file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete ephemeral cookie file: ${file?.absolutePath}", e)
        }
    }

    fun deleteEphemeralCookieFile(context: Context, jobId: String) {
        try {
            val safeJobId = sanitizeJobId(jobId)
            deleteEphemeralCookieFile(File(context.cacheDir, "cookies_job_$safeJobId.txt"))
            deleteEphemeralCookieFile(
                File(File(context.cacheDir, "auth_cookies"), "cookies_job_$safeJobId.txt")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete ephemeral cookie file for job $jobId", e)
        }
    }

    fun deleteEphemeralCookieFile(context: Context, jobId: Long) {
        deleteEphemeralCookieFile(context, jobId.toString())
    }

    /**
     * Returns the File reference for a given jobId without checking or creating it.
     * Downloads use the auth_cookies directory when the persistent session is active.
     */
    fun getEphemeralCookieFile(context: Context, jobId: String): File {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val youtubeAccountCookies =
            preferences.getBoolean(YouTubeSessionEngine.PREF_YOUTUBE_ACCOUNT_COOKIES, false)

        val safeJobId = sanitizeJobId(jobId)
        return if (youtubeAccountCookies) {
            File(File(context.cacheDir, "auth_cookies"), "cookies_job_$safeJobId.txt")
        } else {
            File(context.cacheDir, "cookies_job_$safeJobId.txt")
        }
    }

    private fun sanitizeJobId(jobId: String): String {
        return jobId.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    fun getEphemeralCookieFile(context: Context, jobId: Long): File {
        return getEphemeralCookieFile(context, jobId.toString())
    }

    fun cleanOrphanedEphemeralFiles(context: Context) {
        try {
            val predicate = FilenameFilter { _, name ->
                name.startsWith("cookies_job_") && name.endsWith(".txt")
            }
            context.cacheDir.listFiles(predicate)?.forEach { it.delete() }
            File(context.cacheDir, "auth_cookies").listFiles(predicate)?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean orphaned ephemeral cookie files", e)
        }
    }
}
