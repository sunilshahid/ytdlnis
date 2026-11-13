package com.deniscerri.ytdl.util

import android.content.Context
import android.util.Log
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.LogItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class CrashListener(private val context: Context) : Thread.UncaughtExceptionHandler {
    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(p0: Thread, p1: Throwable) {
        com.deniscerri.ytdl.vault.VaultSessionManager.lockAndZeroize()
        com.deniscerri.ytdl.vault.ingest.VaultPendingDownloadStore.clearAll()

        val rawStack = "${p1.message.orEmpty()}\n${p1.stackTrace.joinToString("\n")}"
        val isVaultRelated = rawStack.contains(".vault.", ignoreCase = true) ||
            rawStack.contains("vault://", ignoreCase = true) ||
            rawStack.contains("vault_tmp", ignoreCase = true)

        if (!isVaultRelated) {
            Log.e("CrashListener", "FATAL EXCEPTION in thread ${p0.name}", p1)
            p1.printStackTrace()
            CoroutineScope(SupervisorJob()).launch(Dispatchers.IO) {
                createLog("${p1.message}\n\n${p1.stackTrace.joinToString("\n")}")
            }
        }
        defaultHandler?.uncaughtException(p0, p1) ?: exitProcess(1)
    }

    private suspend fun createLog(message: String){
        kotlin.runCatching {
            val db = DBManager.getInstance(context)
            val dao = db.logDao
            dao.insert(LogItem(
                id = 0L,
                title = "APP CRASH",
                content = message,
                format = Format("", "", "", "", "", 0, "", "", "", "", "", ""),
                downloadType = DownloadType.command,
                downloadTime = System.currentTimeMillis()
            ))
        }
    }

    fun registerExceptionHandler(){
        Thread.setDefaultUncaughtExceptionHandler(this)
    }
}
