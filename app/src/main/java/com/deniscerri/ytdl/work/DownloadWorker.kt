package com.deniscerri.ytdl.work

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.SessionOrchestrator
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.models.LogItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.LogRepository
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.util.LocaleUtil
import com.deniscerri.ytdl.util.AlarmScheduler
import com.deniscerri.ytdl.util.Extensions.fixYtdlpPath
import com.deniscerri.ytdl.util.Extensions.getMediaDuration
import com.deniscerri.ytdl.util.Extensions.toStringDuration
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.WorkerEventBus
import com.deniscerri.ytdl.util.extractors.ytdlp.YTDLPUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.collections.addAll
import kotlin.random.Random

class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : YTDLPCoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifUtil = NotificationUtil(App.Companion.instance)
        val workNotif = if (com.deniscerri.ytdl.vault.ingest.VaultPendingDownloadStore.hasAny()) {
            notifUtil.createNeutralWorkerNotification()
        } else {
            notifUtil.createDefaultWorkerNotification()
        }

        return ForegroundInfo(
            1000000000,
            workNotif,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }



    @OptIn(ExperimentalStdlibApi::class)
    @SuppressLint("RestrictedApi")
    override suspend fun runWork(): Result {
        val workManager = WorkManager.Companion.getInstance(context)
        if (workManager.isRunning("download") || isStopped) return Result.Failure()

        setForegroundSafely()

        val parentContext = currentCoroutineContext()
        val workerScope = CoroutineScope(
            parentContext + Dispatchers.IO + SupervisorJob(parentContext[Job.Key])
        )

        val notificationUtil = NotificationUtil(App.Companion.instance)
        val dbManager = DBManager.Companion.getInstance(context)
        val dao = dbManager.downloadDao
        val historyDao = dbManager.historyDao
        val commandTemplateDao = dbManager.commandTemplateDao
        val logRepo = LogRepository(dbManager.logDao)
        val resultRepo = ResultRepository(dbManager.resultDao, commandTemplateDao, context)
        val ytdlpUtil = YTDLPUtil(context, commandTemplateDao)
        val handler = Handler(Looper.getMainLooper())
        val alarmScheduler = AlarmScheduler(context)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val time = System.currentTimeMillis() + 6000
        val priorityItemIDs = (inputData.getLongArray("priority_item_ids") ?: longArrayOf()).toMutableList()
        val continueAfterPriorityIds = inputData.getBoolean("continue_after_priority_ids", true)
        val queuedItems = if (priorityItemIDs.isEmpty()) {
            dao.getQueuedScheduledDownloadsUntil(time)
        }else {
            dao.getQueuedScheduledDownloadsUntilWithPriority(time, priorityItemIDs)
        }

        // this is needed for observe sources call, so it wont create result items
        // [removed]
        //val createResultItem = inputData.getBoolean("createResultItem", true)

        val confTmp = Configuration(context.resources.configuration)
        confTmp.setLocale(LocaleUtil.currentLocale())
        val metrics = DisplayMetrics()
        val resources = Resources(context.assets, metrics, confTmp)

        val openQueueIntent = Intent(context, MainActivity::class.java)
        openQueueIntent.setAction(Intent.ACTION_VIEW)
        openQueueIntent.putExtra("destination", "Queue")
        val openDownloadQueue = PendingIntent.getActivity(
            context,
            1000000000,
            openQueueIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val downloadDelay = sharedPreferences.getString("download_delay", "0-0")!!
        val minDelay = downloadDelay.split("-")[0].toFloat()
        val maxDelay = downloadDelay.split("-")[1].toFloat()
        val hasDownloadDelay = minDelay > 0 || maxDelay > 0

        queuedItems.collectLatest { items ->
            if (this@DownloadWorker.isStopped) return@collectLatest

            runningYTDLInstances.clear()
            val activeDownloads = dao.getActiveDownloadsList()
            activeDownloads.forEach {
                runningYTDLInstances.add(it.id)
            }

            val running = ArrayList(runningYTDLInstances)
            val useScheduler = sharedPreferences.getBoolean("use_scheduler", false)
            if (items.isEmpty() && running.isEmpty()) {
                WorkManager.Companion.getInstance(context).cancelWorkById(this@DownloadWorker.id)
                return@collectLatest
            }

            if (useScheduler){
                if (items.none { it.vaultMode } && items.none{it.downloadStartTime > 0L} && running.isEmpty() && !alarmScheduler.isDuringTheScheduledTime()) {
                    WorkManager.Companion.getInstance(context).cancelWorkById(this@DownloadWorker.id)
                    return@collectLatest
                }
            }

            if (items.any { it.vaultMode }) {
                notificationUtil.notify(1000000000, notificationUtil.createNeutralWorkerNotification())
            }

            if (priorityItemIDs.isEmpty() && !continueAfterPriorityIds) {
                WorkManager.Companion.getInstance(context).cancelWorkById(this@DownloadWorker.id)
                return@collectLatest
            }

            var concurrentDownloads = sharedPreferences.getInt("concurrent_downloads", 1) - running.size
            if (hasDownloadDelay) {
                concurrentDownloads = 1 - running.size
            }

            val eligibleDownloads = if (priorityItemIDs.isNotEmpty()) {
                val tmp = priorityItemIDs.take(concurrentDownloads)
                items.filter { it.id !in running && tmp.contains(it.id) }
            }else{
                items.take(concurrentDownloads).filter {  it.id !in running }
            }

            eligibleDownloads.forEach{ dbItem ->
                priorityItemIDs.remove(dbItem.id)
                val isVault = dbItem.vaultMode
                val vaultUuid = dbItem.vaultJobUuid

                if (!isVault) {
                    val notification = notificationUtil.createDownloadServiceNotification(openDownloadQueue, dbItem.title.ifEmpty { dbItem.url })
                    notificationUtil.notify(dbItem.id.toInt(), notification)
                }

                workerScope.launch {
                    val processDownloadBlock : suspend () -> Unit = block@{
                        val tempFileDir = if (isVault) {
                            com.deniscerri.ytdl.vault.VaultStorage.tempDirForJob(context, vaultUuid)
                        } else {
                            File(FileUtil.getCacheDownloadsPath(context), dbItem.id.toString()).apply {
                                delete()
                                mkdirs()
                            }
                        }

                        val downloadItem = if (isVault) {
                            com.deniscerri.ytdl.vault.ingest.VaultPendingDownloadStore.hydrateForWorker(dbItem, tempFileDir)
                                ?: run {
                                    com.deniscerri.ytdl.vault.VaultStorage.releaseTempDirForJob(context, vaultUuid)
                                    dao.delete(dbItem.id)
                                    return@block
                                }
                        } else {
                            dbItem
                        }

                        dbItem.status = DownloadRepository.Status.Active.toString()
                        dao.update(dbItem)

                        if (hasDownloadDelay) {
                            val delaySec = if (minDelay >= maxDelay) minDelay else Random.Default.nextFloat() * (maxDelay - minDelay) + minDelay
                            if (delaySec > 0) {
                                if (!isVault) {
                                    workerScope.launch {
                                        delay(1000L)
                                        WorkerEventBus.post(
                                            WorkerProgress(
                                                0,
                                                context.getString(R.string.waiting_download_delay, "%.2f".format(delaySec)),
                                                downloadItem.id,
                                                downloadItem.logID
                                            )
                                        )
                                    }
                                }

                                delay((delaySec * 1000).toLong())
                            }
                        }

                        val request = ytdlpUtil.buildYTDLRequest(downloadItem)

                        // DISABLED BECAUSE YT_DLP CONSIDERS DOWNLOAD FAILURE IF -U PART FAILS, ytdlnis #1043
    //                    val updateYTDLP = sharedPreferences.getBoolean("update_ytdlp_while_downloading", false)
    //                    if (updateYTDLP) {
    //                        request.addOption("-U")
    //                    }

                        if (!isVault) {
                            downloadItem.status = DownloadRepository.Status.Active.toString()
                            CoroutineScope(Dispatchers.IO).launch {
                                delay(1500)
                                //update item if its incomplete
                                resultRepo.updateDownloadItem(downloadItem)?.apply {
                                    val status = dao.checkStatus(this.id)
                                    if (status == DownloadRepository.Status.Active) {
                                        dao.updateWithoutUpsert(this)
                                    }
                                }
                            }
                        }

                        val downloadLocation = downloadItem.downloadPath
                        val keepCache = !isVault && sharedPreferences.getBoolean("keep_cache", false)
                        val logDownloads = !isVault && sharedPreferences.getBoolean(
                            "log_downloads",
                            false
                        ) && !downloadItem.incognito

                        val targetDir = File(FileUtil.formatPath(downloadItem.downloadPath))
                        val cacheDownloads = sharedPreferences.getBoolean("cache_downloads", true)
                        val writtenPath = downloadItem.type == DownloadType.command && downloadItem.format.format_note.contains("-P ")
                        val writeDirectly = !isVault && (writtenPath || (!cacheDownloads && targetDir.canWrite()))


                        val commandString = if (isVault) "" else com.deniscerri.ytdl.util.extractors.newpipe.potoken.PoTokenFormatting
                            .redactSensitiveArgs(ytdlpUtil.parseYTDLRequestString(request))
                        val initialLogDetails = if (isVault) "" else "Downloading:\n" +
                                "Title: ${downloadItem.title}\n" +
                                "URL: ${downloadItem.url}\n" +
                                "Type: ${downloadItem.type}\n" +
                                "Command:\n$commandString \n\n"
                        val logString = StringBuilder(initialLogDetails)
                        val logItem = LogItem(
                            0,
                            if (isVault) "" else downloadItem.title.ifBlank { downloadItem.playlistTitle.ifEmpty { downloadItem.url } },
                            logString.toString(),
                            downloadItem.format,
                            downloadItem.type,
                            System.currentTimeMillis(),
                        )


                        if (!isVault) {
                            runBlocking {
                                if (logDownloads) logItem.id = logRepo.insert(logItem)
                                downloadItem.logID = logItem.id
                                dao.update(downloadItem)
                            }
                        }

                        runCatching {
                            val isYoutube = downloadItem.url.contains("youtube.com") || downloadItem.url.contains("youtu.be")
                            val useCookies = sharedPreferences.getBoolean("use_cookies", false)
                            SessionOrchestrator.withAutoRetry(
                                context = context,
                                jobId = downloadItem.id.toString(),
                                isYoutube = isYoutube,
                                useCookies = useCookies
                            ) {
                                if (isYoutube) {
                                    com.deniscerri.ytdl.util.extractors.newpipe.potoken.PoTokenCoordinator
                                        .getOrMintForYtDlp(context, downloadItem.url)
                                }
                                val activeRequest = ytdlpUtil.buildYTDLRequest(downloadItem)

                                // The command logged above is built before the per-job cookie
                                // snapshot exists, so it shows the shared cookies.txt path. Log
                                // the command that is actually executed to avoid confusion when
                                // debugging auth problems.
                                if (logDownloads) {
                                    val actualCommand = "Actual command:\n" +
                                        com.deniscerri.ytdl.util.extractors.newpipe.potoken.PoTokenFormatting
                                            .redactSensitiveArgs(ytdlpUtil.parseYTDLRequestString(activeRequest)) + "\n\n"
                                    logString.append(actualCommand)
                                    logRepo.update(actualCommand, logItem.id)
                                }

                                RuntimeManager.getInstance().destroyProcessById(downloadItem.id.toString())
                                RuntimeManager.getInstance().execute(
                                    request = activeRequest,
                                    processId = downloadItem.id.toString(),
                                    redirectErrorStream = true,
                                    usingCacheDir = !isVault,
                                    envOverrides = if (isVault) mapOf("TMPDIR" to tempFileDir.absolutePath) else emptyMap()
                                ) { progress, _, line ->
                                    if (!isVault) {
                                        WorkerEventBus.post(
                                            WorkerProgress(
                                                progress.toInt(),
                                                line,
                                                downloadItem.id,
                                                downloadItem.logID
                                            )
                                        )
                                        val title: String = downloadItem.title.ifEmpty { downloadItem.url }
                                        notificationUtil.updateDownloadNotification(
                                            downloadItem.id.toInt(),
                                            line, progress.toInt(), 0, title,
                                            NotificationUtil.Companion.DOWNLOAD_SERVICE_CHANNEL_ID
                                        )
                                        CoroutineScope(Dispatchers.IO).launch {
                                            if (logDownloads) {
                                                logRepo.update(line, logItem.id)
                                            }
                                            logString.append("$line\n")
                                        }
                                    }
                                }
                            }
                        }.onSuccess {
                            if (isVault) {
                                try {
                                    FileUtil.deleteConfigFiles(request)
                                    val mediaFile = tempFileDir.listFiles()
                                        ?.filter { f ->
                                            f.isFile && f.length() > 0L &&
                                                f.name != ".nomedia" &&
                                                !f.name.endsWith(".part", true) &&
                                                !f.name.endsWith(".ytdl", true) &&
                                                !f.name.endsWith(".tmp", true) &&
                                                !f.name.endsWith(".txt", true) &&
                                                !f.name.endsWith(".jpg", true) &&
                                                !f.name.endsWith(".jpeg", true) &&
                                                !f.name.endsWith(".png", true) &&
                                                !f.name.endsWith(".webp", true)
                                        }
                                        ?.maxByOrNull { f -> f.length() }
                                    if (mediaFile != null) {
                                        val durationMs = runCatching {
                                            val d = mediaFile.getMediaDuration(context)
                                            if (d > 0) d * 1000L else 0L
                                        }.getOrDefault(0L)
                                        val stagedOriginalDir = com.deniscerri.ytdl.vault.ingest.VaultPendingDownloadStore
                                            .peek(vaultUuid)
                                            ?.originalDownloadPath
                                            ?.takeIf { it.isNotBlank() }
                                            ?: FileUtil.getDefaultVideoPath()
                                        val cleanTitle = downloadItem.title.ifEmpty { downloadItem.playlistTitle }
                                        val safeBase = cleanTitle.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
                                        val friendlyFileName = if (mediaFile.nameWithoutExtension == vaultUuid && safeBase.isNotEmpty()) {
                                            val ext = mediaFile.extension.ifBlank { "mp4" }
                                            "$safeBase.$ext"
                                        } else {
                                            mediaFile.name
                                        }
                                        runBlocking {
                                            com.deniscerri.ytdl.vault.ingest.VaultImporter.ingestStagedDownload(
                                                context = context,
                                                sourceFile = mediaFile,
                                                jobTempDir = tempFileDir,
                                                preferredTitle = cleanTitle,
                                                sourceUrl = downloadItem.url,
                                                durationFallbackMs = durationMs,
                                                originalFileName = friendlyFileName,
                                                originalDir = stagedOriginalDir,
                                                fromDownloadHistory = true,
                                                author = downloadItem.author,
                                                website = downloadItem.website,
                                                thumbUrl = downloadItem.thumb,
                                                durationText = downloadItem.duration,
                                                formatJson = runCatching { com.google.gson.Gson().toJson(downloadItem.format) }.getOrDefault(""),
                                                downloadId = downloadItem.id
                                            )
                                        }
                                    }
                                } finally {
                                    com.deniscerri.ytdl.vault.VaultStorage.releaseTempDirForJob(context, vaultUuid)
                                    com.deniscerri.ytdl.vault.ingest.VaultPendingDownloadStore.consume(vaultUuid)
                                    runBlocking { dao.delete(dbItem.id) }
                                }
                                return@onSuccess
                            }

                            notificationUtil.clearNotificationThrottleState(downloadItem.id.toInt())
                            resultRepo.updateDownloadItem(downloadItem)?.apply {
                                dao.updateWithoutUpsert(this)
                            }
                            //val wasQuickDownloaded = resultDao.getCountInt() == 0
                            runBlocking {
                                var finalPaths = mutableListOf<String>()

                                //after move paths
                                WorkerEventBus.post(
                                    WorkerProgress(
                                        100,
                                        "Scanning Files",
                                        downloadItem.id,
                                        downloadItem.logID
                                    )
                                )
                                val outputSequence = it.out.split("\n")

                                if (writeDirectly) {
                                    val fromAfterMove = outputSequence.asSequence()
                                        .filter { o -> o.startsWith("'/storage") }
                                    val fromSplitChapters = outputSequence.asSequence()
                                        .filter { o -> o.startsWith("[SplitChapters]") && o.contains("Destination: ") }
                                        .map { o -> o.substringAfter("Destination: ") }

                                    val moveFilesRegex = Regex("""^\[MoveFiles]\s+Moving file ".*" to "(.*)"\s*$""")
                                    val fromMoveFiles = outputSequence.asSequence()
                                        .mapNotNull { moveFilesRegex.find(it)?.groupValues?.get(1) }

                                    finalPaths = (fromAfterMove + fromSplitChapters + fromMoveFiles)
                                        .map { o -> o.trim().removeSurrounding("'").removeSurrounding("\"") }
                                        .map { o -> o.fixYtdlpPath() }
                                        .filter { o -> o.isNotBlank() }
                                        .distinct()
                                        .toMutableList()
                                }

                                //move files manually if external provider
                                try {
                                    val otherPaths = withContext(Dispatchers.IO) {
                                        FileUtil.moveFile(
                                            tempFileDir.absoluteFile,
                                            context,
                                            downloadLocation,
                                            keepCache
                                        ) { p ->
                                            WorkerEventBus.post(
                                                WorkerProgress(
                                                    p,
                                                    "Moving file to ${
                                                        FileUtil.formatPath(downloadLocation)
                                                    }",
                                                    downloadItem.id,
                                                    downloadItem.logID
                                                )
                                            )
                                        }
                                    }.filter { p -> !p.matches("\\.(description)|(txt)\$".toRegex()) }
                                        .toMutableList()

                                    if (otherPaths.isNotEmpty()) {
                                        WorkerEventBus.post(
                                            WorkerProgress(
                                                100,
                                                "Moved file to ${
                                                    FileUtil.formatPath(
                                                        downloadLocation
                                                    )
                                                }",
                                                downloadItem.id,
                                                downloadItem.logID
                                            )
                                        )
                                    }

                                    finalPaths.addAll(otherPaths)

                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    if (e.message?.isNotBlank() == true) {
                                        handler.postDelayed({
                                            Toast.makeText(
                                                context,
                                                e.message,
                                                Toast.LENGTH_SHORT
                                            )
                                                .show()
                                        }, 1000)
                                    }

                                }

                                val nonMediaExtensions = mutableListOf<String>().apply {
                                    addAll(context.getStringArray(R.array.thumbnail_containers_values))
                                    addAll(
                                        context.getStringArray(R.array.sub_formats_values)
                                            .filter { it.isNotBlank() })
                                    add("description")
                                    add("txt")
                                }
                                finalPaths = finalPaths.filter { path ->
                                    !nonMediaExtensions.any {
                                        path.endsWith(it)
                                    }
                                }.distinct().toMutableList()
                                finalPaths.sortBy { p -> File(p).lastModified() }

                                FileUtil.scanMedia(finalPaths, context)

                                FileUtil.deleteConfigFiles(request)

                                //put download in history
                                if (!downloadItem.incognito) {
                                    if (request.hasOption("--download-archive") && finalPaths.isEmpty()) {
                                        handler.postDelayed({
                                            Toast.makeText(
                                                context,
                                                resources.getString(R.string.download_already_exists),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }, 100)
                                    } else {
                                        if (finalPaths.isNotEmpty()) {
                                            val unixTime = System.currentTimeMillis() / 1000
                                            finalPaths.first().apply {
                                                val file = File(this)
                                                var duration = downloadItem.duration
                                                val d = file.getMediaDuration(context)
                                                if (d > 0) duration = d.toStringDuration(Locale.US)

                                                downloadItem.format.filesize = file.length()
                                                downloadItem.format.container = file.extension
                                                downloadItem.duration = duration
                                            }

                                            val historyItem = HistoryItem(
                                                0,
                                                downloadItem.url,
                                                downloadItem.title.ifEmpty { downloadItem.playlistTitle },
                                                downloadItem.author,
                                                downloadItem.duration,
                                                downloadItem.thumb,
                                                downloadItem.type,
                                                unixTime,
                                                finalPaths,
                                                downloadItem.website,
                                                downloadItem.format,
                                                downloadItem.format.filesize,
                                                downloadItem.id,
                                                commandString
                                            )
                                            historyDao.insert(historyItem)
                                        }
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    notificationUtil.cancelDownloadNotification(downloadItem.id.toInt())
                                    notificationUtil.createDownloadFinished(
                                        downloadItem.id,
                                        downloadItem.title,
                                        downloadItem.type,
                                        if (finalPaths.isEmpty()) null else finalPaths,
                                        resources
                                    )
                                }

                                //                            if (wasQuickDownloaded && createResultItem){
                                //                                runCatching {
                                //                                    eventBus.post(WorkerProgress(100, "Creating Result Items", downloadItem.id))
                                //                                    runBlocking {
                                //                                        infoUtil.getFromYTDL(downloadItem.url).forEach { res ->
                                //                                            if (res != null) {
                                //                                                resultDao.insert(res)
                                //                                            }
                                //                                        }
                                //                                    }
                                //                                }
                                //                            }

                                dao.delete(downloadItem.id)

                                if (logDownloads) {
                                    logRepo.update(initialLogDetails + it.out, logItem.id, true)
                                }
                            }

                        }.onFailure {
                            FileUtil.deleteConfigFiles(request)
                            tempFileDir.deleteRecursively()
                            if (isVault) {
                                com.deniscerri.ytdl.vault.VaultStorage.releaseTempDirForJob(context, vaultUuid)
                                com.deniscerri.ytdl.vault.ingest.VaultPendingDownloadStore.consume(vaultUuid)
                                runBlocking { dao.delete(dbItem.id) }
                                return@onFailure
                            }

                            notificationUtil.clearNotificationThrottleState(downloadItem.id.toInt())
                            withContext(Dispatchers.Main) {
                                notificationUtil.cancelDownloadNotification(downloadItem.id.toInt())
                            }

                            // If incognito, immediately wipe partial files from download storage
                            if (downloadItem.incognito) {
                                FileUtil.cleanPartialDownloadFiles(downloadItem.downloadPath, tempFileDir, downloadItem.title)
                            }

                            if (this@DownloadWorker.isStopped) return@onFailure
                            if (it is RuntimeManager.CanceledException) return@onFailure
                            if (it.message?.contains("JSONDecodeError") == true) {
                                val cachePath = FileUtil.getInfoJsonPath(context)
                                val infoJsonName = MessageDigest.getInstance("MD5")
                                    .digest(downloadItem.url.toByteArray()).toHexString()
                                FileUtil.deleteFile("${cachePath}/${infoJsonName}.info.json")
                            }

                            if (logDownloads) {
                                logRepo.update(it.message ?: "", logItem.id)
                            } else {
                                logString.append("${it.message ?: it.stackTraceToString()}\n")
                                logItem.content = logString.toString()
                                val logID = logRepo.insert(logItem)
                                downloadItem.logID = logID
                            }

                            Log.e(TAG, context.getString(R.string.failed_download), it)
                            notificationUtil.cancelDownloadNotification(downloadItem.id.toInt())

                            downloadItem.status = DownloadRepository.Status.Error.toString()
                            runBlocking {
                                dao.update(downloadItem)
                            }

                            // Suppress system notifications for incognito to avoid leaking sensitive titles/urls
                            if (!downloadItem.incognito) {
                                notificationUtil.createDownloadErrored(
                                    downloadItem.id,
                                    downloadItem.title.ifEmpty { downloadItem.url },
                                    it.message,
                                    downloadItem.logID,
                                    resources
                                )
                            }

                            WorkerEventBus.post(
                                WorkerProgress(
                                    100,
                                    it.toString(),
                                    downloadItem.id,
                                    downloadItem.logID
                                )
                            )
                        }
                    }

                    if (hasDownloadDelay) {
                        downloadLock.withLock {
                            processDownloadBlock()
                        }
                    } else {
                        processDownloadBlock()
                    }
                }

            }

            if (eligibleDownloads.isNotEmpty()){
                eligibleDownloads.forEach {
                    it.status = DownloadRepository.Status.Active.toString()
                    priorityItemIDs.remove(it.id)
                }
                dao.updateMultiple(eligibleDownloads)
            }
        }

        return Result.success()
    }



    companion object {
        val runningYTDLInstances: MutableList<Long> = mutableListOf()
        const val TAG = "DownloadWorker"
        private val downloadLock = Mutex()
    }

    class WorkerProgress(
        val progress: Int,
        val output: String,
        val downloadItemID: Long,
        val logItemID: Long?
    )

}