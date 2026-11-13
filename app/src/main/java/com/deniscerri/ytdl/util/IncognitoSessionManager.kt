package com.deniscerri.ytdl.util

import android.content.Context
import android.util.Log
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.database.DBManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object IncognitoSessionManager {
    private const val TAG = "IncognitoSessionManager"

    suspend fun purgeIncognitoSession(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val dbManager = DBManager.getInstance(context)
            val downloadDao = dbManager.downloadDao
            val logDao = dbManager.logDao
            val notificationUtil = NotificationUtil(context)
            val cacheBase = File(FileUtil.getCacheDownloadsPath(context))

            // 1. Fetch all downloads marked incognito
            val incognitoDownloads = downloadDao.getAllIncognitoDownloads()

            // 2. Stop running processes, cancel notifications, and clean files for each
            for (item in incognitoDownloads) {
                runCatching {
                    RuntimeManager.getInstance().destroyProcessById(item.id.toString())
                }
                runCatching {
                    notificationUtil.cancelDownloadNotification(item.id.toInt())
                }
                runCatching {
                    val tempDir = File(cacheBase, item.id.toString())
                    FileUtil.cleanPartialDownloadFiles(item.downloadPath, tempDir, item.title)
                }
            }

            // 3. Collect associated logs and delete them
            val logIds = incognitoDownloads.mapNotNull { it.logID }.filter { it > 0 }
            if (logIds.isNotEmpty()) {
                logDao.deleteByIds(logIds)
            }

            // 4. Delete all incognito download records from downloads table
            downloadDao.deleteAllIncognito()

            // 5. Delete search query history / results from database
            dbManager.resultDao.deleteAll()

            Log.d(TAG, "Purged ${incognitoDownloads.size} incognito downloads and ${logIds.size} logs")
        }.onFailure {
            Log.e(TAG, "Error purging incognito session", it)
        }
    }
}
