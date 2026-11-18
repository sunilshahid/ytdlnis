package com.deniscerri.ytdl.playback.chapters

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.deniscerri.ytdl.core.RuntimeManager
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Locates and executes the bundled ffmpeg / ffprobe binaries.
 *
 * `libffmpeg.so` and `libffprobe.so` ship in the APK's native library directory, but they are
 * dynamically linked against `libav*.so`. Those live in the ffmpeg runtime package, unpacked from
 * `libffmpeg.zip.so` into `noBackupFilesDir/runtime/packages/ffmpeg/usr/lib`.
 *
 * Starting the binaries with only the native library dir on `LD_LIBRARY_PATH` makes the linker
 * abort before `main()`, so the process exits with no output at all. Everything must run with
 * [RuntimeManager]'s environment, which contains the unpacked package directory.
 */
internal object BundledMediaTools {

    private const val TAG = "BundledMediaTools"
    private const val READER_JOIN_MS = 2_000L

    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    fun ffprobe(context: Context): File? = binary(context, "libffprobe.so")

    fun ffmpeg(context: Context): File? = binary(context, "libffmpeg.so")

    private fun binary(context: Context, name: String): File? {
        val runtimeBinDir = runCatching {
            RuntimeManager.getInstance().ffmpegLocation.binDir
        }.getOrNull()
        if (runtimeBinDir != null) {
            File(runtimeBinDir, name).takeIf { it.isFile }?.let { return it }
        }
        return File(context.applicationInfo.nativeLibraryDir, name).takeIf { it.isFile }
    }

    /** Full process environment: library path, PATH, TMPDIR/HOME. */
    fun environment(context: Context): Map<String, String> {
        val env = LinkedHashMap<String, String>()
        runCatching { RuntimeManager.getInstance().getEnvironment() }
            .getOrNull()
            ?.forEach { (key, value) -> if (value != null) env[key] = value }

        if (env["LD_LIBRARY_PATH"].isNullOrBlank()) {
            val libDirs = ArrayList<String>(2)
            runCatching { File(RuntimeManager.getInstance().ffmpegLocation.ldDir, "usr/lib") }
                .getOrNull()
                ?.takeIf { it.isDirectory }
                ?.let { libDirs.add(it.absolutePath) }
            libDirs.add(context.applicationInfo.nativeLibraryDir)
            env["LD_LIBRARY_PATH"] = libDirs.joinToString(":")
        }
        if (env["TMPDIR"].isNullOrBlank()) env["TMPDIR"] = context.cacheDir.absolutePath
        if (env["HOME"].isNullOrBlank()) env["HOME"] = context.filesDir.absolutePath
        return env
    }

    /**
     * Runs [binary] with [arguments] and waits at most [timeoutMs].
     *
     * stdout/stderr are drained on their own threads so a large JSON payload cannot block the
     * child on a full pipe. [onStarted] exposes the process so long jobs can be cancelled.
     */
    fun run(
        context: Context,
        binary: File,
        arguments: List<String>,
        timeoutMs: Long,
        onStarted: (Process) -> Unit = {}
    ): Result? = runCatching {
        val builder = ProcessBuilder(listOf(binary.absolutePath) + arguments)
        builder.environment().putAll(environment(context))
        val process = builder.start()
        onStarted(process)

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutThread = startReader(process.inputStream) { stdout.append(it) }
        val stderrThread = startReader(process.errorStream) { stderr.append(it) }

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            runCatching { process.waitFor(READER_JOIN_MS, TimeUnit.MILLISECONDS) }
        }
        stdoutThread.join(READER_JOIN_MS)
        stderrThread.join(READER_JOIN_MS)

        Result(
            exitCode = if (finished) runCatching { process.exitValue() }.getOrDefault(-1) else -1,
            stdout = stdout.toString(),
            stderr = stderr.toString()
        )
    }.getOrElse { error ->
        Log.w(TAG, "Cannot run ${binary.name}: ${error.message}")
        null
    }

    /** Resolves a `file://`, plain path or MediaStore `content://` URI to a readable file. */
    fun localFile(context: Context, localUri: String): File? {
        val uri = runCatching { Uri.parse(localUri) }.getOrNull() ?: return null
        val path = when {
            uri.scheme.isNullOrBlank() -> uri.path
            uri.scheme.equals("file", ignoreCase = true) -> uri.path
            uri.scheme.equals("content", ignoreCase = true) -> queryDataColumn(context, uri)
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return null

        val file = File(path)
        if (!file.isFile || !file.canRead()) return null
        return file
    }

    private fun queryDataColumn(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun startReader(stream: InputStream, sink: (String) -> Unit): Thread =
        Thread {
            runCatching {
                stream.bufferedReader().use { reader -> sink(reader.readText()) }
            }
        }.apply {
            isDaemon = true
            start()
        }
}
