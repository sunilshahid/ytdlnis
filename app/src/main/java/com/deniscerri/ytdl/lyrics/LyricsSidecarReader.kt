package com.deniscerri.ytdl.lyrics

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Finds same-name sidecar lyrics next to a local audio/video file.
 * Content URIs are supported for the embedded tag reader, but Android does not expose sibling
 * document lookup for a single content URI, so sidecar discovery is file-path based.
 */
object LyricsSidecarReader {

    data class SidecarLyrics(val raw: String, val format: LyricsFormat, val file: File)

    private val extensions = listOf("lrc", "ttml", "ass", "vtt", "srt", "yrc", "qrc", "sylt", "txt")

    fun find(context: Context, uriString: String?, title: String? = null): SidecarLyrics? {
        val file = localFile(context, uriString) ?: return null
        val parent = file.parentFile ?: return null
        val baseName = file.nameWithoutExtension

        val candidates = mutableListOf<File>()
        extensions.forEach { ext ->
            candidates.add(File(parent, "$baseName.$ext"))
            candidates.add(File(parent, "$baseName.${ext.uppercase()}"))
        }
        if (!title.isNullOrBlank()) {
            extensions.forEach { ext ->
                candidates.add(File(parent, "$title.$ext"))
            }
        }

        val sibling = parent.listFiles()?.associateBy { it.name.lowercase() } ?: emptyMap()
        candidates.forEach { candidate ->
            val found = if (candidate.exists()) candidate else sibling[candidate.name.lowercase()]
            if (found != null && found.isFile) {
                val raw = runCatching { found.readText() }.getOrNull() ?: return@forEach
                if (raw.isNotBlank()) {
                    return SidecarLyrics(raw, formatFor(found.extension), found)
                }
            }
        }
        return null
    }

    fun localFile(context: Context, uriString: String?): File? {
        if (uriString.isNullOrBlank()) return null
        return when {
            uriString.startsWith("content://") -> null
            uriString.startsWith("file://") -> Uri.parse(uriString).path?.let(::File)
            else -> File(uriString)
        }?.takeIf { it.isFile }
    }

    private fun formatFor(extension: String): LyricsFormat = when (extension.lowercase()) {
        "lrc" -> LyricsFormat.LRC
        "ttml" -> LyricsFormat.TTML
        "ass", "ssa" -> LyricsFormat.ASS
        "vtt" -> LyricsFormat.VTT
        "srt" -> LyricsFormat.SRT
        "yrc" -> LyricsFormat.YRC
        "qrc" -> LyricsFormat.QRC
        "sylt" -> LyricsFormat.SYLT
        else -> LyricsFormat.PLAIN
    }
}
