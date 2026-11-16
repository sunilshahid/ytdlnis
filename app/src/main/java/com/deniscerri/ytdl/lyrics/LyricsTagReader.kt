package com.deniscerri.ytdl.lyrics

import android.content.Context
import android.net.Uri
import com.deniscerri.ytdl.lyrics.parse.LyricsParserRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Locale
import kotlin.math.min

/**
 * Reads embedded lyrics without pulling in a tag library:
 * - ID3v2 USLT/SYLT (MP3),
 * - MP4/M4A `©lyr`,
 * - FLAC/Ogg Vorbis comments (`LYRICS`, `UNSYNCEDLYRICS`, `SYNCEDLYRICS`).
 */
object LyricsTagReader {

    data class EmbeddedLyrics(val raw: String, val format: LyricsFormat)

    /** Enough for every container whose comment block sits near the start of the file. */
    private const val DEFAULT_TAG_BYTES = 4 * 1024 * 1024

    /** Upper bound for an ID3v2 tag whose declared size pushes it past the default window. */
    private const val MAX_TAG_BYTES = 32 * 1024 * 1024

    private val ORIGINAL_LYRICS_KEYS = setOf(
        "LYRICS", "LYRIC", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "UNSYNCED LYRICS"
    )

    fun read(context: Context, uriString: String?): EmbeddedLyrics? {
        if (uriString.isNullOrBlank()) return null
        var bytes = runCatching { readBytes(context, uriString, DEFAULT_TAG_BYTES) }.getOrNull()
            ?: return null
        if (bytes.size < 16) return null
        // An ID3v2 header declares the exact tag size. Only when artwork pushes the tag past
        // the default window do we re-read; everything else keeps the single-open fast path.
        if (hasId3Header(bytes)) {
            val limit = id3ByteLimit(bytes)
            if (limit > bytes.size) {
                runCatching { readBytes(context, uriString, limit) }.getOrNull()?.let { bytes = it }
            }
        }
        val direct = runCatching { readId3(bytes) }.getOrNull()
            ?: runCatching { readMp4(bytes) }.getOrNull()
            ?: runCatching { readFlac(bytes) }.getOrNull()
            ?: runCatching { readOgg(bytes) }.getOrNull()
        if (direct != null) return direct

        // Dynamic buffer expansion for Ogg / Opus / FLAC if tags were pushed past 4MB
        if (hasOggHeader(bytes) || hasFlacHeader(bytes)) {
            val expanded = runCatching { readBytes(context, uriString, 16 * 1024 * 1024) }.getOrNull()
            if (expanded != null && expanded.size > bytes.size) {
                val expandedDirect = if (hasOggHeader(expanded)) {
                    runCatching { readOgg(expanded) }.getOrNull()
                } else {
                    runCatching { readFlac(expanded) }.getOrNull()
                }
                if (expandedDirect != null) return expandedDirect
            }
        }

        // For MP4 / M4A containers without faststart, the moov atom sits at the end of the file.
        if (hasMp4Header(bytes)) {
            val tailBytes = runCatching { readBytesFromEnd(context, uriString, DEFAULT_TAG_BYTES) }.getOrNull()
            if (tailBytes != null && tailBytes.isNotEmpty()) {
                runCatching { readMp4(tailBytes) }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun hasMp4Header(bytes: ByteArray): Boolean {
        return bytes.size >= 8 && String(bytes, 4, 4, Charsets.ISO_8859_1) == "ftyp"
    }

    private fun readBytesFromEnd(context: Context, uriString: String, limit: Int): ByteArray? {
        return runCatching {
            if (uriString.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")?.use { pfd ->
                    val fileLength = pfd.statSize
                    if (fileLength <= 0) return@use null
                    val offset = kotlin.math.max(0L, fileLength - limit)
                    val toRead = (fileLength - offset).toInt()
                    val buffer = ByteArray(toRead)
                    FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                        channel.position(offset)
                        var readTotal = 0
                        while (readTotal < toRead) {
                            val r = channel.read(ByteBuffer.wrap(buffer, readTotal, toRead - readTotal))
                            if (r <= 0) break
                            readTotal += r
                        }
                    }
                    buffer
                }
            } else {
                val file = if (uriString.startsWith("file://")) {
                    File(Uri.parse(uriString).path ?: return@runCatching null)
                } else {
                    File(uriString)
                }
                if (!file.exists()) return@runCatching null
                val fileLength = file.length()
                val offset = kotlin.math.max(0L, fileLength - limit)
                val toRead = (fileLength - offset).toInt()
                val buffer = ByteArray(toRead)
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(offset)
                    raf.readFully(buffer)
                }
                buffer
            }
        }.getOrNull()
    }

    private fun hasId3Header(bytes: ByteArray): Boolean {
        return bytes.size >= 10 && bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()
    }

    private fun hasOggHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 4 && bytes[0] == 'O'.code.toByte() &&
            bytes[1] == 'g'.code.toByte() && bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()
    }

    private fun hasFlacHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 4 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "fLaC"
    }

    /** Reads the ID3v2 header's syncsafe size so the whole tag window can be requested. */
    private fun id3ByteLimit(head: ByteArray): Int {
        val declared = syncSafe(head, 6)
        val needed = 10L + declared + 4096L
        return needed.coerceIn(64L * 1024, MAX_TAG_BYTES.toLong()).toInt()
    }

    private fun readBytes(context: Context, uriString: String, limit: Int): ByteArray? {
        val input: InputStream = if (uriString.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(uriString)) ?: return null
        } else {
            val file = if (uriString.startsWith("file://")) {
                File(Uri.parse(uriString).path ?: return null)
            } else {
                File(uriString)
            }
            if (!file.exists()) return null
            FileInputStream(file)
        }
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (total < limit) {
                val read = stream.read(buffer, 0, min(buffer.size, limit - total))
                if (read <= 0) break
                output.write(buffer, 0, read)
                total += read
            }
            output.toByteArray()
        }
    }

    private fun readId3(bytes: ByteArray): EmbeddedLyrics? {
        if (bytes.size < 10 || bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() ||
            bytes[2] != '3'.code.toByte()
        ) return null
        val version = bytes[3].toInt() and 0xFF
        val tagSize = syncSafe(bytes, 6)
        val end = min(bytes.size, 10 + tagSize)
        if (version == 2) {
            // ID3v2.2: 3-char frame ids, 6-byte headers, 24-bit (not syncsafe) sizes.
            var pos = 10
            while (pos + 6 <= end) {
                val frameId = String(bytes, pos, 3, Charsets.ISO_8859_1)
                if (frameId[0] == '\u0000') break
                val frameSize = ((bytes[pos + 3].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 4].toInt() and 0xFF) shl 8) or
                    (bytes[pos + 5].toInt() and 0xFF)
                if (frameSize <= 0 || pos + 6 + frameSize > end) break
                val payloadStart = pos + 6
                val payloadEnd = payloadStart + frameSize
                when (frameId) {
                    "ULT" -> readUslt(bytes, payloadStart, payloadEnd)?.let { return it }
                    "SLT" -> readSylt(bytes, payloadStart, payloadEnd)?.let { return it }
                    "TXX" -> readTxxx(bytes, payloadStart, payloadEnd)?.let { return it }
                    "COM" -> readComm(bytes, payloadStart, payloadEnd)?.let { return it }
                }
                pos = payloadEnd
            }
            return null
        }
        var pos = 10
        // Skip the optional extended header so lyric frames behind it are reachable.
        val flags = bytes[5].toInt() and 0xFF
        if (flags and 0x40 != 0 && pos + 4 <= end) {
            val extSize = if (version >= 4) syncSafe(bytes, 10) else int32(bytes, 10)
            pos += if (version >= 4) extSize else 4 + extSize
        }
        while (pos + 10 <= end) {
            val frameId = String(bytes, pos, 4, Charsets.ISO_8859_1)
            if (frameId[0] == '\u0000') break
            val frameSize = if (version >= 4) syncSafe(bytes, pos + 4) else int32(bytes, pos + 4)
            if (frameSize <= 0 || pos + 10 + frameSize > end) break
            val payloadStart = pos + 10
            val payloadEnd = payloadStart + frameSize
            when (frameId) {
                "USLT" -> readUslt(bytes, payloadStart, payloadEnd)?.let { return it }
                "SYLT" -> readSylt(bytes, payloadStart, payloadEnd)?.let { return it }
                "TXXX" -> readTxxx(bytes, payloadStart, payloadEnd)?.let { return it }
                "COMM" -> readComm(bytes, payloadStart, payloadEnd)?.let { return it }
            }
            pos = payloadEnd
        }
        return null
    }

    private fun readTxxx(bytes: ByteArray, start: Int, end: Int): EmbeddedLyrics? {
        if (start + 2 >= end) return null
        val encoding = bytes[start].toInt() and 0xFF
        val textStart = skipDescriptor(bytes, start + 1, end, encoding)
        val desc = decodeText(bytes, start + 1, textStart, encoding).trimEnd('\u0000').trim().uppercase()
        if (desc in ORIGINAL_LYRICS_KEYS || desc.contains("LYRIC")) {
            val text = decodeText(bytes, textStart, end, encoding).trimEnd('\u0000', '\n', '\r').trim()
            if (text.isNotBlank()) {
                return EmbeddedLyrics(text, LyricsParserRegistry.detect(text))
            }
        }
        return null
    }

    private fun readComm(bytes: ByteArray, start: Int, end: Int): EmbeddedLyrics? {
        if (start + 4 >= end) return null
        val encoding = bytes[start].toInt() and 0xFF
        val textStart = skipDescriptor(bytes, start + 4, end, encoding)
        val desc = decodeText(bytes, start + 4, textStart, encoding).trimEnd('\u0000').trim().uppercase()
        if (desc in ORIGINAL_LYRICS_KEYS || desc.contains("LYRIC")) {
            val text = decodeText(bytes, textStart, end, encoding).trimEnd('\u0000', '\n', '\r').trim()
            if (text.isNotBlank()) {
                return EmbeddedLyrics(text, LyricsParserRegistry.detect(text))
            }
        }
        return null
    }

    private fun readUslt(bytes: ByteArray, start: Int, end: Int): EmbeddedLyrics? {
        if (start + 4 >= end) return null
        val encoding = bytes[start].toInt() and 0xFF
        val textStart = skipDescriptor(bytes, start + 4, end, encoding)
        val text = decodeText(bytes, textStart, end, encoding).trimEnd('\u0000', '\n', '\r').trim()
        if (text.isBlank()) return null
        return EmbeddedLyrics(text, LyricsParserRegistry.detect(text))
    }

    private fun readSylt(bytes: ByteArray, start: Int, end: Int): EmbeddedLyrics? {
        if (start + 6 >= end) return null
        val encoding = bytes[start].toInt() and 0xFF
        var pos = skipDescriptor(bytes, start + 6, end, encoding)
        val entries = mutableListOf<Pair<String, Long>>()
        while (pos + 4 < end) {
            val textStart = pos
            val terminator = findTerminator(bytes, pos, end, encoding)
            if (terminator < 0) break
            val text = decodeText(bytes, textStart, terminator, encoding).trim()
            pos = terminator + if (encoding == 1 || encoding == 2) 2 else 1
            if (pos + 4 > end) break
            val time = int32(bytes, pos).toLong()
            pos += 4
            if (text.isNotEmpty()) entries.add(text to time)
        }
        if (entries.isEmpty()) return null
        val lrc = buildString {
            entries.forEach { (text, time) ->
                val minutes = time / 60_000
                val seconds = (time % 60_000) / 1000
                val centis = (time % 1000) / 10
                append(
                    String.format(
                        Locale.US,
                        "[%02d:%02d.%02d]%s\n",
                        minutes,
                        seconds,
                        centis,
                        text
                    )
                )
            }
        }
        return EmbeddedLyrics(lrc, LyricsFormat.LRC)
    }

    private fun readMp4(bytes: ByteArray): EmbeddedLyrics? {
        val marker = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        var index = indexOf(bytes, marker)
        while (index >= 0) {
            val innerSizePos = index + 4
            if (innerSizePos + 8 <= bytes.size) {
                val innerType = String(bytes, innerSizePos + 4, 4, Charsets.ISO_8859_1)
                if (innerType == "data") {
                    val innerSize = int32(bytes, innerSizePos)
                    val dataEnd = min(bytes.size, innerSizePos + innerSize)
                    val textStart = innerSizePos + 16
                    if (textStart < dataEnd) {
                        val text = decodeText(bytes, textStart, dataEnd, 3).trim()
                        if (text.isNotBlank()) {
                            return EmbeddedLyrics(text, LyricsParserRegistry.detect(text))
                        }
                    }
                }
            }
            index = indexOf(bytes, marker, index + 1)
        }
        return null
    }

    private fun readFlac(bytes: ByteArray): EmbeddedLyrics? {
        if (bytes.size < 8 || String(bytes, 0, 4, Charsets.ISO_8859_1) != "fLaC") return null
        var pos = 4
        while (pos + 4 <= bytes.size) {
            val header = bytes[pos].toInt() and 0xFF
            val type = header and 0x7F
            val length = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + length > bytes.size) break
            if (type == 4) {
                readVorbisComment(bytes, pos, pos + length)?.let { return it }
            }
            pos += length
            if (header and 0x80 != 0) break
        }
        return null
    }

    private fun readOgg(bytes: ByteArray): EmbeddedLyrics? {
        if (bytes.size < 16 || String(bytes, 0, 4, Charsets.ISO_8859_1) != "OggS") return null
        // Demux Ogg pages before parsing. The comment header (OpusTags / \u0003vorbis) is often
        // larger than one page — a big METADATA_BLOCK_PICTURE comment is the usual offender —
        // so parsing the raw file bytes desyncs at the page header that splits the comment
        // stream and never reaches the LYRICS entry. Reassemble page bodies per stream serial.
        val streams = LinkedHashMap<Int, ByteArrayOutputStream>()
        val streamsWithTags = mutableSetOf<Int>()
        var pos = 0
        while (pos + 27 <= bytes.size) {
            if (String(bytes, pos, 4, Charsets.ISO_8859_1) != "OggS") break
            val segmentCount = bytes[pos + 26].toInt() and 0xFF
            val tableStart = pos + 27
            val bodyStart = tableStart + segmentCount
            if (bodyStart > bytes.size) break
            var bodyLength = 0
            var packetEnds = false
            for (i in 0 until segmentCount) {
                val seg = bytes[tableStart + i].toInt() and 0xFF
                bodyLength += seg
                if (seg < 255) packetEnds = true
            }
            if (bodyStart + bodyLength > bytes.size) break
            val serial = int32Le(bytes, pos + 14)
            val stream = streams.getOrPut(serial) { ByteArrayOutputStream() }
            stream.write(bytes, bodyStart, bodyLength)

            // Early exit: once the OpusTags / Vorbis comment packet finishes, stop buffering audio pages!
            if (!streamsWithTags.contains(serial)) {
                val currentData = stream.toByteArray()
                if (indexOf(currentData, "OpusTags".toByteArray(Charsets.ISO_8859_1)) >= 0 ||
                    indexOf(currentData, "\u0003vorbis".toByteArray(Charsets.ISO_8859_1)) >= 0
                ) {
                    streamsWithTags.add(serial)
                }
            } else if (packetEnds) {
                // The comment packet ended; remaining pages are audio packets, so exit early!
                break
            }
            pos = bodyStart + bodyLength
        }
        for (stream in streams.values) {
            val data = stream.toByteArray()
            listOf("\u0003vorbis", "OpusTags").forEach { marker ->
                val index = indexOf(data, marker.toByteArray(Charsets.ISO_8859_1))
                if (index >= 0) {
                    readVorbisComment(data, index + marker.length, data.size)?.let { return it }
                }
            }
        }
        return null
    }

    private fun readVorbisComment(bytes: ByteArray, start: Int, end: Int): EmbeddedLyrics? {
        var pos = start
        if (pos + 4 > end) return null
        val vendorLength = int32Le(bytes, pos)
        if (vendorLength < 0 || pos.toLong() + 4 + vendorLength > end) return null
        pos += 4 + vendorLength
        if (pos + 4 > end) return null
        val count = int32Le(bytes, pos)
        pos += 4

        var syncedResult: EmbeddedLyrics? = null
        var unsyncedResult: EmbeddedLyrics? = null
        var romanizedResult: EmbeddedLyrics? = null

        var i = 0
        while (i < count && pos + 4 <= end) {
            val length = int32Le(bytes, pos)
            pos += 4
            if (length < 0) break
            if (pos.toLong() + length > end) {
                // Break on truncation instead of continuing with corrupted pos
                break
            }

            // Lightweight Tag Header Peeking (Zero-Allocation Skip):
            // Scan only the first up to 64 bytes for '=' to avoid decoding huge pictures
            val peekLen = min(length, 64)
            var sep = -1
            for (p in 0 until peekLen) {
                if (bytes[pos + p] == '='.code.toByte()) {
                    sep = p
                    break
                }
            }

            if (sep <= 0) {
                pos += length
                i++
                continue
            }

            val key = String(bytes, pos, sep, Charsets.US_ASCII).uppercase(Locale.US)
            if (key == "METADATA_BLOCK_PICTURE") {
                // Zero-allocation skip for giant base64 album art!
                pos += length
                i++
                continue
            }

            val isRomanized = key.contains("LYRIC") && key.contains("ROMANIZ")
            val isLyricsKey = key in ORIGINAL_LYRICS_KEYS || key.contains("LYRIC") || key == "USLT" || key == "SYLT"

            if (isLyricsKey || isRomanized) {
                val valueStart = pos + sep + 1
                val valueLen = length - (sep + 1)
                if (valueLen > 0) {
                    val text = String(bytes, valueStart, valueLen, Charsets.UTF_8).trim()
                    if (text.isNotBlank()) {
                        val format = LyricsParserRegistry.detect(text)
                        val embedded = EmbeddedLyrics(text, format)
                        if (format.isSynchronized) {
                            if (!isRomanized) {
                                // High priority: synchronized lyrics return immediately!
                                return embedded
                            } else if (romanizedResult == null) {
                                romanizedResult = embedded
                            }
                        } else {
                            if (isRomanized) {
                                if (romanizedResult == null) romanizedResult = embedded
                            } else if (unsyncedResult == null) {
                                unsyncedResult = embedded
                            }
                        }
                    }
                }
            }

            pos += length
            i++
        }

        if (syncedResult != null) return syncedResult
        if (unsyncedResult != null) return unsyncedResult
        if (romanizedResult != null) return romanizedResult

        // Fallback Direct Byte Scanner if loop desynced or ended without lyrics
        return scanFallbackLyrics(bytes, start, end)
    }

    private fun scanFallbackLyrics(bytes: ByteArray, start: Int, end: Int): EmbeddedLyrics? {
        val markers = listOf(
            "SYNCEDLYRICS=".toByteArray(Charsets.ISO_8859_1),
            "LYRICS=".toByteArray(Charsets.ISO_8859_1),
            "UNSYNCEDLYRICS=".toByteArray(Charsets.ISO_8859_1),
            "lyrics=".toByteArray(Charsets.ISO_8859_1),
            "syncedlyrics=".toByteArray(Charsets.ISO_8859_1)
        )
        for (marker in markers) {
            val idx = indexOf(bytes, marker, start)
            if (idx in start until end) {
                val valStart = idx + marker.size
                val valEnd = min(end, valStart + 64 * 1024)
                if (valStart < valEnd) {
                    val text = String(bytes, valStart, valEnd - valStart, Charsets.UTF_8)
                        .substringBefore("\u0000")
                        .trim()
                    if (text.isNotBlank()) {
                        val format = LyricsParserRegistry.detect(text)
                        return EmbeddedLyrics(text, format)
                    }
                }
            }
        }
        return null
    }

    private fun skipDescriptor(bytes: ByteArray, start: Int, end: Int, encoding: Int): Int {
        if (start >= end) return start
        // If the payload immediately starts with a UTF-16 BOM, there is no descriptor preceding the text.
        if ((encoding == 1 || encoding == 2) && end - start >= 2) {
            val b0 = bytes[start].toInt() and 0xFF
            val b1 = bytes[start + 1].toInt() and 0xFF
            if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) {
                if (end - start >= 4 && bytes[start + 2].toInt() == 0 && bytes[start + 3].toInt() == 0) {
                    return start + 4
                }
                return start
            }
        }
        if (encoding == 1 || encoding == 2) {
            var i = start
            while (i + 1 < end) {
                if (bytes[i].toInt() == 0 && bytes[i + 1].toInt() == 0) return i + 2
                i += 2
            }
            return start
        }
        var i = start
        while (i < end) {
            if (bytes[i].toInt() == 0) return i + 1
            i++
        }
        return start
    }

    private fun findTerminator(bytes: ByteArray, start: Int, end: Int, encoding: Int): Int {
        if (encoding == 1 || encoding == 2) {
            var i = start
            while (i + 1 < end) {
                if (bytes[i].toInt() == 0 && bytes[i + 1].toInt() == 0) return i
                i += 2
            }
            return -1
        }
        var i = start
        while (i < end) {
            if (bytes[i].toInt() == 0) return i
            i++
        }
        return -1
    }

    private fun decodeText(bytes: ByteArray, start: Int, end: Int, encoding: Int): String {
        if (start >= end) return ""
        var offset = start
        var charset: Charset = Charsets.ISO_8859_1
        when (encoding) {
            1 -> {
                if (end - start >= 2 && bytes[start] == 0xFF.toByte() && bytes[start + 1] == 0xFE.toByte()) {
                    charset = Charsets.UTF_16LE
                    offset += 2
                } else if (end - start >= 2 && bytes[start] == 0xFE.toByte() && bytes[start + 1] == 0xFF.toByte()) {
                    charset = Charsets.UTF_16BE
                    offset += 2
                } else {
                    charset = Charsets.UTF_16LE
                }
            }
            2 -> charset = Charsets.UTF_16BE
            3 -> charset = Charsets.UTF_8
        }
        return String(bytes, offset, end - offset, charset)
    }

    private fun syncSafe(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun int32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun int32Le(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int = 0): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in from..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
