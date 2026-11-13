package com.deniscerri.ytdl.vault.ingest

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.playback.LikedStore
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.util.FrameExtractor
import com.deniscerri.ytdl.vault.VaultItemMeta
import com.deniscerri.ytdl.vault.VaultRepository
import com.deniscerri.ytdl.vault.VaultSessionManager
import com.deniscerri.ytdl.vault.VaultStorage
import com.deniscerri.ytdl.vault.crypto.VaultCryptoPrimitives
import com.deniscerri.ytdl.vault.crypto.VaultFileCipher
import com.deniscerri.ytdl.vault.crypto.VaultHeader
import com.deniscerri.ytdl.vault.crypto.VaultIngestSeal
import com.deniscerri.ytdl.vault.crypto.VaultSidecarCrypto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Handles both ingestion paths into the Private Vault:
 * - **Flow A (`ingestStagedDownload`):** Encrypts a finished `vault_tmp/{jobUuid}/` download into
 *   `noBackupFilesDir/vault/items/{uuid}.*` using an RSA-OAEP sealed per-item seed (works even when
 *   the vault is locked).
 * - **Flow B (`moveVideoToVault`):** Streams an existing local video file or SAF `content://` URI
 *   into the unlocked vault, verifies chunk 0 + header MAC, attempts deterministic source deletion
 *   (including Android 10+ OS confirmation sheets), and purges all YTDLnis/MediaStore references.
 */
object VaultImporter {

    data class MoveSourceRequest(
        val title: String,
        val filePath: String? = null,
        val contentUri: Uri? = null,
        val originalFileName: String? = null,
        val mimeType: String? = null,
        val durationMs: Long = 0L,
        val sizeBytes: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
        val playbackMediaId: String? = null,
        val historyId: Long? = null
    )

    data class PendingTraceCleanup(
        val uuid: String,
        val filePath: String?,
        val contentUri: Uri?,
        val playbackMediaId: String?,
        val historyId: Long?
    )

    sealed class MoveOutcome {
        data class MovedAndDeleted(val uuid: String, val title: String) : MoveOutcome()
        data class RequiresOsDeletePermission(
            val uuid: String,
            val title: String,
            val intentSender: IntentSender,
            val cleanupToken: PendingTraceCleanup
        ) : MoveOutcome()

        data class ImportedNeedsManualDelete(
            val uuid: String,
            val title: String,
            val cleanupToken: PendingTraceCleanup,
            val reason: String
        ) : MoveOutcome()
    }

    /**
     * Flow A: seals a finished yt-dlp download from [jobTempDir] into `vault/items/`.
     * Caller must wipe [jobTempDir] in `finally`.
     */
    suspend fun ingestStagedDownload(
        context: Context,
        sourceFile: File,
        jobTempDir: File,
        preferredTitle: String,
        sourceUrl: String,
        durationFallbackMs: Long = 0L,
        originalFileName: String = "",
        originalDir: String = "",
        fromDownloadHistory: Boolean = false,
        author: String = "",
        website: String = "",
        thumbUrl: String = "",
        durationText: String = "",
        formatJson: String = "",
        downloadId: Long = 0L
    ): String = withContext(Dispatchers.IO) {
        val envelope = VaultRepository.loadEnvelopeOrNull(context)
            ?: throw IllegalStateException("Vault is not initialized")
        if (!sourceFile.exists() || !sourceFile.isFile || sourceFile.length() <= 0L) {
            throw IOException("Staged download missing or empty")
        }

        val uuid = UUID.randomUUID().toString()
        val itemsDir = VaultStorage.itemsDir(context, decoy = false)
        val itemSeed = VaultCryptoPrimitives.randomBytes(32)
        val extracted = VaultMediaExtractor.extractFromFile(context, sourceFile, jobTempDir)

        val videoFile = VaultStorage.videoFile(itemsDir, uuid)
        val metaFile = VaultStorage.metaFile(itemsDir, uuid)
        val thumbFile = VaultStorage.thumbFile(itemsDir, uuid)
        val wrapFile = VaultStorage.wrappedKeyFile(itemsDir, uuid)

        try {
            val length = sourceFile.length()
            sourceFile.inputStream().buffered(64 * 1024).use { input ->
                VaultFileCipher.encryptStream(
                    input = input,
                    plaintextLength = length,
                    outputFile = videoFile,
                    rootKey = itemSeed,
                    uuid = uuid,
                    cipherVersion = VaultHeader.VERSION_GCM_CHUNKS
                )
            }
            VaultFileCipher.verifyFirstChunkAndHeader(videoFile, itemSeed, uuid)

            val resolvedTitle = preferredTitle.trim()
                .ifEmpty { extracted.embeddedTitle.orEmpty().trim() }
                .ifEmpty { sourceFile.nameWithoutExtension.ifEmpty { "Private Video" } }

            val resolvedFileName = originalFileName.trim().ifEmpty { sourceFile.name }
            val meta = VaultItemMeta(
                id = uuid,
                title = resolvedTitle,
                originalFileName = resolvedFileName,
                durationMs = if (extracted.durationMs > 0L) extracted.durationMs else durationFallbackMs,
                filesizeBytes = length,
                width = extracted.width,
                height = extracted.height,
                mimeType = extracted.mimeType ?: inferMimeType(sourceFile.name),
                dateAddedMs = System.currentTimeMillis(),
                sourceUrl = sourceUrl,
                cipherVersion = VaultHeader.VERSION_GCM_CHUNKS,
                originalFilePath = "",
                originalDir = originalDir,
                fromDownloadHistory = fromDownloadHistory,
                author = author,
                website = website,
                thumbUrl = thumbUrl,
                durationText = durationText,
                formatJson = formatJson,
                historyTimeSec = System.currentTimeMillis() / 1000L,
                downloadId = downloadId
            )
            val metaBytes = meta.toJsonString().toByteArray(StandardCharsets.UTF_8)
            try {
                VaultSidecarCrypto.writeEncryptedFile(
                    targetFile = metaFile,
                    rootKey = itemSeed,
                    itemId = uuid,
                    purpose = VaultSidecarCrypto.PURPOSE_META,
                    plaintext = metaBytes
                )
            } finally {
                metaBytes.fill(0)
            }

            extracted.thumbnailWebp?.let { webp ->
                VaultSidecarCrypto.writeEncryptedFile(
                    targetFile = thumbFile,
                    rootKey = itemSeed,
                    itemId = uuid,
                    purpose = VaultSidecarCrypto.PURPOSE_THUMB,
                    plaintext = webp
                )
            }

            val publicKeyDer = VaultCryptoPrimitives.decodeBase64(envelope.ingestPublicKeyB64)
            val sealed = VaultIngestSeal.sealSecret(publicKeyDer, itemSeed)
            val tmpWrap = File(itemsDir, "${wrapFile.name}.writing")
            tmpWrap.writeBytes(sealed)
            if (wrapFile.exists()) wrapFile.delete()
            if (!tmpWrap.renameTo(wrapFile)) {
                tmpWrap.delete()
                throw IOException("Failed to write sealed vault item key")
            }

            VaultRepository.reconcilePendingIfUnlocked(context)
            uuid
        } catch (t: Throwable) {
            videoFile.delete()
            metaFile.delete()
            thumbFile.delete()
            wrapFile.delete()
            throw t
        } finally {
            itemSeed.fill(0)
            extracted.thumbnailWebp?.fill(0)
        }
    }

    /**
     * Flow B: streams an unlocked vault import from a local file or SAF/MediaStore [Uri], verifies
     * the encrypted container, attempts to delete the plaintext original, and removes YTDLnis traces.
     */
    suspend fun moveVideoToVault(
        context: Context,
        request: MoveSourceRequest,
        onProgress: ((writtenBytes: Long, totalBytes: Long) -> Unit)? = null
    ): MoveOutcome = withContext(Dispatchers.IO) {
        val dek = VaultSessionManager.requireKeyCopy()
        val decoy = VaultSessionManager.isDecoySession
        val itemsDir = VaultStorage.itemsDir(context, decoy = decoy)
        val uuid = UUID.randomUUID().toString()
        val jobTempDir = VaultStorage.tempDirForJob(context, "import_$uuid")

        val videoFile = VaultStorage.videoFile(itemsDir, uuid)
        val metaFile = VaultStorage.metaFile(itemsDir, uuid)
        val thumbFile = VaultStorage.thumbFile(itemsDir, uuid)

        try {
            val resolvedFilePath = request.filePath?.takeIf { it.isNotBlank() }
                ?: request.contentUri?.let { resolveLocalFilePath(context, it) }
            val localFile = resolvedFilePath?.let(::File)?.takeIf { it.exists() && it.isFile && it.canRead() }
            val sourceUri = request.contentUri

            val matchedHistory = lookupHistoryItem(context, request.historyId, resolvedFilePath)

            var tempCopy: File? = null
            var effectiveFile: File?
            var extracted: VaultMediaExtractor.ExtractedMetadata
            val displayName: String
            var plaintextLength: Long

            if (localFile != null) {
                effectiveFile = localFile
                displayName = request.originalFileName?.takeIf { it.isNotBlank() } ?: localFile.name
                plaintextLength = localFile.length()
                extracted = VaultMediaExtractor.extractFromFile(context, localFile, jobTempDir)
            } else if (sourceUri != null) {
                val probed = queryUriDisplayNameAndSize(context, sourceUri)
                displayName = request.originalFileName?.takeIf { it.isNotBlank() }
                    ?: probed.first?.takeIf { it.isNotBlank() }
                    ?: "video_${System.currentTimeMillis()}.mp4"
                val knownSize = probed.second.takeIf { it > 0L }
                    ?: runCatching {
                        context.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use { it.length }
                    }.getOrNull()?.takeIf { it > 0L }

                if (knownSize != null) {
                    effectiveFile = null
                    plaintextLength = knownSize
                    extracted = VaultMediaExtractor.extractFromUri(context, sourceUri)
                } else {
                    // Provider with unknown or 0 reported size: stage into backup-excluded temp dir first.
                    val staged = stageUriToTempFile(context, sourceUri, jobTempDir, onProgress)
                    tempCopy = staged
                    effectiveFile = staged
                    plaintextLength = staged.length()
                    extracted = VaultMediaExtractor.extractFromFile(context, staged, jobTempDir)
                }
            } else {
                throw IOException("No readable source file or URI provided")
            }

            if (plaintextLength <= 0L) {
                throw IOException("Selected video file is empty")
            }

            if (effectiveFile != null) {
                effectiveFile.inputStream().buffered(64 * 1024).use { input ->
                    VaultFileCipher.encryptStream(
                        input = input,
                        plaintextLength = plaintextLength,
                        outputFile = videoFile,
                        rootKey = dek,
                        uuid = uuid,
                        cipherVersion = VaultHeader.VERSION_GCM_CHUNKS,
                        onProgress = onProgress
                    )
                }
            } else {
                val uriToRead = sourceUri!!
                try {
                    context.contentResolver.openInputStream(uriToRead)?.buffered(64 * 1024)?.use { input ->
                        VaultFileCipher.encryptStream(
                            input = input,
                            plaintextLength = plaintextLength,
                            outputFile = videoFile,
                            rootKey = dek,
                            uuid = uuid,
                            cipherVersion = VaultHeader.VERSION_GCM_CHUNKS,
                            onProgress = onProgress
                        )
                    } ?: throw IOException("Unable to read selected video")
                } catch (_: IOException) {
                    // Fallback if provider's reported OpenableColumns.SIZE differed from actual stream EOF.
                    videoFile.delete()
                    val staged = stageUriToTempFile(context, uriToRead, jobTempDir, onProgress)
                    tempCopy = staged
                    effectiveFile = staged
                    plaintextLength = staged.length()
                    if (plaintextLength <= 0L) throw IOException("Selected video file is empty")
                    if (extracted.thumbnailWebp == null || extracted.durationMs <= 0L) {
                        extracted = VaultMediaExtractor.extractFromFile(context, staged, jobTempDir)
                    }
                    staged.inputStream().buffered(64 * 1024).use { input ->
                        VaultFileCipher.encryptStream(
                            input = input,
                            plaintextLength = plaintextLength,
                            outputFile = videoFile,
                            rootKey = dek,
                            uuid = uuid,
                            cipherVersion = VaultHeader.VERSION_GCM_CHUNKS,
                            onProgress = onProgress
                        )
                    }
                }
            }

            VaultFileCipher.verifyFirstChunkAndHeader(videoFile, dek, uuid)

            val resolvedTitle = request.title.trim()
                .ifEmpty { matchedHistory?.title?.trim().orEmpty() }
                .ifEmpty { extracted.embeddedTitle.orEmpty().trim() }
                .ifEmpty { File(displayName).nameWithoutExtension.ifEmpty { "Private Video" } }

            val originalPathValue = resolvedFilePath.orEmpty()
            val originalDirValue = originalPathValue
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { File(it).parent }.getOrNull() }
                .orEmpty()

            val meta = VaultItemMeta(
                id = uuid,
                title = resolvedTitle,
                originalFileName = displayName,
                durationMs = when {
                    extracted.durationMs > 0L -> extracted.durationMs
                    request.durationMs > 0L -> request.durationMs
                    else -> 0L
                },
                filesizeBytes = plaintextLength,
                width = if (extracted.width > 0) extracted.width else request.width,
                height = if (extracted.height > 0) extracted.height else request.height,
                mimeType = request.mimeType?.takeIf { it.isNotBlank() }
                    ?: extracted.mimeType
                    ?: inferMimeType(displayName),
                dateAddedMs = System.currentTimeMillis(),
                sourceUrl = matchedHistory?.url.orEmpty(),
                cipherVersion = VaultHeader.VERSION_GCM_CHUNKS,
                originalFilePath = originalPathValue,
                originalDir = originalDirValue,
                fromDownloadHistory = matchedHistory != null,
                author = matchedHistory?.author.orEmpty(),
                website = matchedHistory?.website.orEmpty(),
                thumbUrl = matchedHistory?.thumb.orEmpty(),
                durationText = matchedHistory?.duration.orEmpty(),
                formatJson = matchedHistory?.let { runCatching { Gson().toJson(it.format) }.getOrDefault("") }.orEmpty(),
                historyTimeSec = matchedHistory?.time ?: 0L,
                downloadId = matchedHistory?.downloadId ?: 0L
            )

            val metaBytes = meta.toJsonString().toByteArray(StandardCharsets.UTF_8)
            try {
                VaultSidecarCrypto.writeEncryptedFile(
                    targetFile = metaFile,
                    rootKey = dek,
                    itemId = uuid,
                    purpose = VaultSidecarCrypto.PURPOSE_META,
                    plaintext = metaBytes
                )
            } finally {
                metaBytes.fill(0)
            }

            extracted.thumbnailWebp?.let { webp ->
                try {
                    VaultSidecarCrypto.writeEncryptedFile(
                        targetFile = thumbFile,
                        rootKey = dek,
                        itemId = uuid,
                        purpose = VaultSidecarCrypto.PURPOSE_THUMB,
                        plaintext = webp
                    )
                } finally {
                    webp.fill(0)
                }
            }

            tempCopy?.delete()

            val cleanupToken = PendingTraceCleanup(
                uuid = uuid,
                filePath = resolvedFilePath,
                contentUri = request.contentUri,
                playbackMediaId = request.playbackMediaId,
                historyId = request.historyId ?: matchedHistory?.id
            )

            when (val deleteResult = attemptSourceDeletion(context, resolvedFilePath, request.contentUri)) {
                is SourceDeleteResult.Deleted -> {
                    cleanupTraces(context, cleanupToken)
                    MoveOutcome.MovedAndDeleted(uuid, resolvedTitle)
                }
                is SourceDeleteResult.NeedsPermission -> {
                    MoveOutcome.RequiresOsDeletePermission(
                        uuid = uuid,
                        title = resolvedTitle,
                        intentSender = deleteResult.intentSender,
                        cleanupToken = cleanupToken
                    )
                }
                is SourceDeleteResult.NotDeleted -> {
                    MoveOutcome.ImportedNeedsManualDelete(
                        uuid = uuid,
                        title = resolvedTitle,
                        cleanupToken = cleanupToken,
                        reason = deleteResult.reason
                    )
                }
            }
        } catch (t: Throwable) {
            videoFile.delete()
            metaFile.delete()
            thumbFile.delete()
            throw t
        } finally {
            dek.fill(0)
            VaultStorage.releaseTempDirForJob(context, "import_$uuid")
        }
    }

    /**
     * Purges YTDLnis's History row, PlaybackHistory row, LikedStore entry, active PlaybackController
     * queue items, FrameExtractor cache thumbnail, and refreshes MediaStore after the plaintext
     * source file has been deleted.
     */
    suspend fun cleanupTraces(context: Context, token: PendingTraceCleanup) = withContext(Dispatchers.IO) {
        val db = DBManager.getInstance(context)
        val path = token.filePath?.takeIf { it.isNotBlank() }
        val uriString = token.contentUri?.toString()

        if (path != null) {
            FrameExtractor.deleteCachedFor(context, path)
            runCatching { MediaScannerConnection.scanFile(context, arrayOf(path), null, null) }
        }

        val mediaIds = buildSet {
            token.playbackMediaId?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (path != null) {
                add("local:$path")
            }
            if (uriString != null) {
                add(uriString)
            }
        }
        val pathsOrUris = buildSet {
            if (path != null) {
                add(path)
                add(Uri.fromFile(File(path)).toString())
            }
            if (uriString != null) add(uriString)
        }

        PlaybackController.removeByMediaIdsOrPaths(mediaIds, pathsOrUris)

        LikedStore.init(context)
        LikedStore.all()
            .filter { it.mediaId in mediaIds || (!it.localUri.isNullOrBlank() && it.localUri in pathsOrUris) }
            .forEach { LikedStore.remove(it.mediaId) }

        mediaIds.forEach { db.playbackHistoryDao.delete(it) }
        pathsOrUris.forEach { db.playbackHistoryDao.deleteByLocalUri(it) }

        if (token.historyId != null) {
            val h = db.historyDao.getHistoryItem(token.historyId)
            if (h != null) {
                removePathFromHistoryItem(db, h, path)
            }
        }
        if (path != null) {
            db.historyDao.getAllHistoryList()
                .filter { item -> item.downloadPath.any { it == path } }
                .forEach { item -> removePathFromHistoryItem(db, item, path) }
        }
    }

    private suspend fun removePathFromHistoryItem(
        db: DBManager,
        item: com.deniscerri.ytdl.database.models.HistoryItem,
        path: String?
    ) {
        if (path == null || item.downloadPath.size <= 1) {
            db.historyDao.delete(item.id)
            return
        }
        val remaining = item.downloadPath.filterNot { it == path }
        if (remaining.isEmpty()) {
            db.historyDao.delete(item.id)
        } else {
            db.historyDao.update(item.copy(downloadPath = remaining))
        }
    }

    private sealed class SourceDeleteResult {
        object Deleted : SourceDeleteResult()
        data class NeedsPermission(val intentSender: IntentSender) : SourceDeleteResult()
        data class NotDeleted(val reason: String) : SourceDeleteResult()
    }

    private fun attemptSourceDeletion(
        context: Context,
        filePath: String?,
        providedUri: Uri?
    ): SourceDeleteResult {
        val localFile = filePath?.let(::File)
        if (localFile != null && localFile.exists()) {
            if (runCatching { localFile.delete() }.getOrDefault(false) && !localFile.exists()) {
                return SourceDeleteResult.Deleted
            }
        } else if (localFile != null && !localFile.exists() && providedUri == null) {
            return SourceDeleteResult.Deleted
        }

        val mediaUri = providedUri?.takeIf { isMediaStoreUri(it) }
            ?: filePath?.let { findMediaStoreVideoUri(context, it) }

        if (mediaUri != null) {
            try {
                val deletedRows = context.contentResolver.delete(mediaUri, null, null)
                if (deletedRows > 0 && (localFile == null || !localFile.exists())) {
                    return SourceDeleteResult.Deleted
                }
            } catch (sec: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException) {
                    return SourceDeleteResult.NeedsPermission(sec.userAction.actionIntent.intentSender)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = runCatching {
                        MediaStore.createDeleteRequest(context.contentResolver, listOf(mediaUri))
                    }.getOrNull()
                    if (pendingIntent != null) {
                        return SourceDeleteResult.NeedsPermission(pendingIntent.intentSender)
                    }
                }
            } catch (_: Throwable) {
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = runCatching {
                    MediaStore.createDeleteRequest(context.contentResolver, listOf(mediaUri))
                }.getOrNull()
                if (pendingIntent != null) {
                    return SourceDeleteResult.NeedsPermission(pendingIntent.intentSender)
                }
            }
        }

        if (providedUri != null && DocumentsContract.isDocumentUri(context, providedUri)) {
            val docDeleted = runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, providedUri)
            }.getOrDefault(false)
            if (docDeleted) return SourceDeleteResult.Deleted
            return SourceDeleteResult.NotDeleted(
                "This file provider did not grant delete permission for the original copy."
            )
        }

        if (localFile != null && !localFile.exists()) {
            return SourceDeleteResult.Deleted
        }

        return SourceDeleteResult.NotDeleted(
            "Android storage permissions prevented automatic deletion of the original copy."
        )
    }

    private fun isMediaStoreUri(uri: Uri): Boolean =
        uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY

    private fun findMediaStoreVideoUri(context: Context, filePath: String): Uri? {
        return runCatching {
            val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = "${MediaStore.Video.Media.DATA} = ?"
            context.contentResolver.query(collection, projection, selection, arrayOf(filePath), null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        ContentUris.withAppendedId(collection, id)
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    fun queryUriDisplayName(context: Context, uri: Uri): String? =
        queryUriDisplayNameAndSize(context, uri).first?.takeIf { it.isNotBlank() }

    private fun lookupHistoryItem(
        context: Context,
        historyId: Long?,
        resolvedFilePath: String?
    ): HistoryItem? = runCatching {
        val dao = DBManager.getInstance(context).historyDao
        if (historyId != null && historyId > 0L) {
            dao.getHistoryItem(historyId)?.let { return@runCatching it }
        }
        if (!resolvedFilePath.isNullOrBlank()) {
            val normalized = File(resolvedFilePath).absolutePath
            dao.getAllHistoryList().firstOrNull { item ->
                item.downloadPath.any { path ->
                    !path.startsWith("content://") && File(path).absolutePath.equals(normalized, ignoreCase = true)
                }
            }
        } else {
            null
        }
    }.getOrNull()

    private fun stageUriToTempFile(
        context: Context,
        sourceUri: Uri,
        jobTempDir: File,
        onProgress: ((writtenBytes: Long, totalBytes: Long) -> Unit)?
    ): File {
        val staged = File(jobTempDir, "saf_staged.bin")
        staged.delete()
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(staged).use { out ->
                val buf = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    if (read > 0) {
                        out.write(buf, 0, read)
                        copied += read
                        onProgress?.invoke(copied, -1L)
                    }
                }
                out.flush()
            }
        } ?: throw IOException("Unable to open selected video")
        return staged
    }

    /**
     * Best-effort resolution of a filesystem path from a SAF or MediaStore URI so we know the
     * exact original folder/path for moving, trace cleanup, and restoring back on Decrypt.
     */
    private fun resolveLocalFilePath(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            val p = uri.path
            if (!p.isNullOrBlank() && File(p).let { it.exists() && it.isFile }) return p
        }
        if (uri.scheme != "content") return null

        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull().orEmpty()
            when (uri.authority) {
                "com.android.externalstorage.documents" -> {
                    val colonIdx = docId.indexOf(':')
                    if (colonIdx > 0) {
                        val volume = docId.substring(0, colonIdx)
                        val relPath = docId.substring(colonIdx + 1).trimStart('/')
                        if (volume.equals("primary", ignoreCase = true)) {
                            @Suppress("DEPRECATION")
                            val candidate = File(Environment.getExternalStorageDirectory(), relPath).absolutePath
                            if (File(candidate).let { it.exists() && it.isFile }) return candidate
                        }
                    }
                }
                "com.android.providers.media.documents" -> {
                    val colonIdx = docId.indexOf(':')
                    if (colonIdx > 0 && docId.substring(0, colonIdx) == "video") {
                        val id = docId.substring(colonIdx + 1).toLongOrNull()
                        if (id != null) {
                            val mediaUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            queryMediaDataColumn(context, mediaUri)?.let { return it }
                        }
                    }
                }
                "com.android.providers.downloads.documents" -> {
                    if (docId.startsWith("raw:")) {
                        val raw = docId.removePrefix("raw:")
                        if (File(raw).let { it.exists() && it.isFile }) return raw
                    }
                    val numId = docId.toLongOrNull()
                    if (numId != null) {
                        val dlUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), numId)
                        queryMediaDataColumn(context, dlUri)?.let { return it }
                    }
                }
            }
        }
        return queryMediaDataColumn(context, uri)
    }

    @Suppress("DEPRECATION")
    private fun queryMediaDataColumn(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (idx >= 0) {
                    val path = cursor.getString(idx)
                    if (!path.isNullOrBlank() && File(path).let { it.exists() && it.isFile }) path else null
                } else null
            } else null
        }
    }.getOrNull()

    private fun queryUriDisplayNameAndSize(context: Context, uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = -1L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    private fun inferMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            else -> "video/*"
        }
    }
}
