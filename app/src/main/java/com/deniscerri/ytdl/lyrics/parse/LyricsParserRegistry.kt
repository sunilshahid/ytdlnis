package com.deniscerri.ytdl.lyrics.parse

import android.util.Log
import android.util.Xml
import com.deniscerri.ytdl.lyrics.LyricsDocument
import com.deniscerri.ytdl.lyrics.LyricsFormat
import com.deniscerri.ytdl.lyrics.LyricsLine
import com.deniscerri.ytdl.lyrics.LyricsSource
import com.deniscerri.ytdl.lyrics.LyricsWord
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Parses every lyrics format YTDLnis accepts. The parser is intentionally independent of the
 * player: it only needs the raw text, which keeps the pipeline stable if the engine changes.
 */
object LyricsParserRegistry {

    private const val TAG = "LyricsParser"

    private val lrcStamp = Regex("""\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)]""")
    private val lrcWordStamp = Regex("""<(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)>""")
    // Apple-Music / BetterLyrics TTML opens with an <?xml ...?> prolog (and sometimes a
    // DOCTYPE or comment) before the root element, so a plain startsWith("<tt") check misses it.
    private val ttmlRoot = Regex("""<tt(?::tt)?[\s>]""")
    private val yrcLine = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val yrcWord = Regex("""\((\d+),(\d+),\d+\)([^(]*)""")
    private val qrcLine = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val qrcWord = Regex("""\((\d+),(\d+)\)([^(]*)""")
    private val assKaraoke = Regex("""\\k[fo]?(\d+)""")
    private val assOverride = Regex("""\{[^}]*\}""")

    fun detect(raw: String): LyricsFormat {
        // Strip a UTF-8 BOM before sniffing: trimStart() doesn't remove \uFEFF, so a BOM-prefixed
        // "WEBVTT"/"<tt" file would misdetect as PLAIN and the TTML pull-parser would choke.
        val cleaned = raw.removePrefix("\uFEFF")
        val trimmed = cleaned.trimStart()
        return when {
            cleaned.contains("[Script Info]") || Regex("""(?m)^Dialogue:""").containsMatchIn(cleaned) -> LyricsFormat.ASS
            trimmed.startsWith("WEBVTT", ignoreCase = true) -> LyricsFormat.VTT
            Regex("""(?m)^\d{2}:\d{2}:\d{2},\d{1,3}\s*-->\s*""").containsMatchIn(cleaned) -> LyricsFormat.SRT
            trimmed.startsWith("<tt") || ttmlRoot.containsMatchIn(cleaned) -> LyricsFormat.TTML
            isYrc(cleaned) -> LyricsFormat.YRC
            isQrc(cleaned) -> LyricsFormat.QRC
            lrcWordStamp.containsMatchIn(cleaned) -> LyricsFormat.LRC_A2
            lrcStamp.containsMatchIn(cleaned) -> LyricsFormat.LRC
            else -> LyricsFormat.PLAIN
        }
    }

    fun parse(
        raw: String,
        formatHint: LyricsFormat? = null,
        mediaId: String = "",
        title: String = "",
        artist: String? = null,
        album: String? = null,
        source: LyricsSource = LyricsSource.CACHE,
        providerId: String? = null,
        providerName: String? = null,
        offsetMs: Long = 0L
    ): LyricsDocument {
        val format = when {
            formatHint == null -> detect(raw)
            formatHint == LyricsFormat.PLAIN && detect(raw) != LyricsFormat.PLAIN -> detect(raw)
            else -> formatHint
        }
        // The pull-based TTML parser throws on a leading BOM; every parser gets BOM-free text.
        val cleanedRaw = raw.removePrefix("\uFEFF")
        val lines = try {
            val parsed = when (format) {
                LyricsFormat.LRC, LyricsFormat.LRC_A2 -> parseLrc(cleanedRaw)
                LyricsFormat.YRC -> parseYrc(cleanedRaw)
                LyricsFormat.QRC -> parseQrc(cleanedRaw)
                LyricsFormat.ASS -> parseAss(cleanedRaw)
                LyricsFormat.TTML -> parseTtml(cleanedRaw)
                LyricsFormat.VTT -> parseVtt(cleanedRaw)
                LyricsFormat.SRT -> parseSrt(cleanedRaw)
                LyricsFormat.SPL -> parseLrc(cleanedRaw)
                LyricsFormat.SYLT -> parseLrc(cleanedRaw)
                LyricsFormat.PLAIN -> parsePlain(cleanedRaw)
            }
            parsed.ifEmpty { parsePlain(cleanedRaw) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse ${format.name} lyrics; falling back to plain", e)
            parsePlain(cleanedRaw)
        }
        return LyricsDocument(
            mediaId = mediaId,
            title = title,
            artist = artist,
            album = album,
            lines = finish(lines),
            source = source,
            format = format,
            raw = raw,
            providerId = providerId,
            providerName = providerName,
            offsetMs = offsetMs
        )
    }

    private fun parsePlain(raw: String): List<LyricsLine> {
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isPlainNoiseLine(it) }
            .map { LyricsLine(it) }
    }

    /**
     * YouTube-sourced plain lyrics open with boilerplate ("2 Contributors", "Translations",
     * "Romanization", "<title> Lyrics", "Provided to YouTube by ..."). Keep section markers
     * like [Verse 1] — those are part of the song.
     */
    private fun isPlainNoiseLine(line: String): Boolean {
        val lowered = line.lowercase()
        if (line.startsWith("[") || line.startsWith("（[")) return false
        return lowered.matches(Regex("""\d+ contributors?.*""")) ||
            lowered == "translations" ||
            lowered == "romanization" ||
            lowered == "lyrics" ||
            lowered == "read more" ||
            lowered.startsWith("read more ") ||
            lowered == "you might also like" ||
            lowered == "embed" ||
            lowered == "up next" ||
            lowered == "scroll to top" ||
            lowered.startsWith("provided to youtube") ||
            lowered.startsWith("powered by") ||
            lowered.startsWith("release date") ||
            lowered.startsWith("songwriters:") ||
            lowered.startsWith("songwriter:") ||
            lowered.startsWith("composers:") ||
            lowered.startsWith("composer:") ||
            lowered.startsWith("producers:") ||
            lowered.startsWith("producer:") ||
            lowered == "musixmatch" ||
            lowered.startsWith("auto-generated by youtube") ||
            lowered.startsWith("℗") ||
            lowered.startsWith("(p)") ||
            // "<title> Lyrics" header: a short line that is nothing but "<something> lyrics".
            Regex("""^.{0,90}\blyrics$""").matches(lowered) && !lowered.contains("[")
    }

    private fun parseLrc(raw: String): List<LyricsLine> {
        var globalOffset = 0L
        Regex("""(?m)^\[offset:([+-]?\d+)]""").find(raw)?.let {
            globalOffset = it.groupValues[1].toLongOrNull() ?: 0L
        }
        // Bilingual LRC files put the translation on a second line with the SAME timestamp.
        // If no timestamp is repeated anywhere, an 80 ms window is not evidence of a
        // translation — it just swallows a fast second line, so disable pairing entirely.
        val stampValues = lrcStamp.findAll(raw).map { it.value }.toList()
        val hasDuplicateStamps = stampValues.size != stampValues.toSet().size
        val result = mutableListOf<LyricsLine>()

        raw.lines().forEach { rawLine ->
            val stamps = lrcStamp.findAll(rawLine).toList().ifEmpty { lrcWordStamp.findAll(rawLine).toList() }
            if (stamps.isEmpty()) return@forEach
            var content = rawLine.substring(stamps.last().range.last + 1)
            if (content.isBlank()) {
                // Trailing-stamp format ("text [00:01.00]"): recover the text that appears
                // before the first stamp instead of dropping it.
                val firstStampStart = stamps.first().range.first
                if (firstStampStart > 0) {
                    content = rawLine.substring(0, firstStampStart)
                }
            }
            if (content.isBlank()) return@forEach
            val wordMatches = lrcWordStamp.findAll(content).toList()
            val plainText = content
                .replace(lrcWordStamp, "")
                .trim()
                .ifEmpty { return@forEach }

            val words = if (wordMatches.isEmpty()) {
                emptyList()
            } else {
                wordMatches.mapIndexedNotNull { index, match ->
                    val start = parseLrcTime(match.groupValues[1], match.groupValues[2]) + globalOffset
                    val end = wordMatches.getOrNull(index + 1)?.let {
                        parseLrcTime(it.groupValues[1], it.groupValues[2]) + globalOffset
                    }
                    val text = content.substring(
                        match.range.last + 1,
                        wordMatches.getOrNull(index + 1)?.range?.first ?: content.length
                    ).trim()
                    if (text.isEmpty()) null else LyricsWord(text, start, end)
                }
            }

            stamps.forEach { stamp ->
                val start = parseLrcTime(stamp.groupValues[1], stamp.groupValues[2]) + globalOffset
                val duplicate = if (hasDuplicateStamps) {
                    result.lastOrNull()?.takeIf {
                        it.startMs != null && kotlin.math.abs(it.startMs!! - start) < 80
                    }
                } else {
                    null
                }
                if (duplicate != null && duplicate.translation == null && duplicate.text != plainText) {
                    result[result.lastIndex] = duplicate.copy(translation = plainText)
                } else {
                    result.add(
                        LyricsLine(
                            text = plainText,
                            startMs = start,
                            words = words
                        )
                    )
                }
            }
        }
        return result
    }

    private fun parseYrc(raw: String): List<LyricsLine> {
        val result = mutableListOf<LyricsLine>()
        raw.lines().forEach { line ->
            val match = yrcLine.find(line.trim()) ?: return@forEach
            val lineStart = match.groupValues[1].toLongOrNull() ?: return@forEach
            val lineDuration = match.groupValues[2].toLongOrNull() ?: 0L
            val content = match.groupValues[3]
            val words = yrcWord.findAll(content).map { word ->
                val start = word.groupValues[1].toLongOrNull() ?: lineStart
                val dur = word.groupValues[2].toLongOrNull() ?: 0L
                val absolute = if (start < lineStart) lineStart + start else start
                LyricsWord(
                    text = word.groupValues[3].trim(),
                    startMs = absolute,
                    endMs = absolute + dur
                )
            }.filter { it.text.isNotEmpty() }.toList()
            val text = if (words.isEmpty()) content.trim() else words.joinToString("") { it.text }
            if (text.isNotEmpty()) {
                result.add(
                    LyricsLine(
                        text = text,
                        startMs = lineStart,
                        endMs = lineStart + lineDuration,
                        words = words
                    )
                )
            }
        }
        return result
    }

    private fun parseQrc(raw: String): List<LyricsLine> {
        val result = mutableListOf<LyricsLine>()
        raw.lines().forEach { line ->
            val match = qrcLine.find(line.trim()) ?: return@forEach
            val lineStart = match.groupValues[1].toLongOrNull() ?: return@forEach
            val lineDuration = match.groupValues[2].toLongOrNull() ?: 0L
            val content = match.groupValues[3]
            val wordMatches = qrcWord.findAll(content).toList()
            val words = wordMatches.mapIndexed { index, word ->
                val start = word.groupValues[1].toLongOrNull() ?: lineStart
                val dur = word.groupValues[2].toLongOrNull() ?: 0L
                val text = content.substring(
                    word.range.last + 1,
                    wordMatches.getOrNull(index + 1)?.range?.first ?: content.length
                ).trim()
                LyricsWord(text, start, start + dur)
            }.filter { it.text.isNotEmpty() }
            val text = if (words.isEmpty()) content.trim() else words.joinToString("") { it.text }
            if (text.isNotEmpty()) {
                result.add(LyricsLine(text, lineStart, lineStart + lineDuration, words))
            }
        }
        return result
    }

    private fun parseAss(raw: String): List<LyricsLine> {
        val result = mutableListOf<LyricsLine>()
        raw.lines().forEach { line ->
            if (!line.startsWith("Dialogue:", ignoreCase = true)) return@forEach
            val body = line.substringAfter(':').trimStart()
            val fields = body.split(',', limit = 10)
            if (fields.size < 10) return@forEach
            val start = parseClock(fields[1].trim()) ?: return@forEach
            val end = parseClock(fields[2].trim()) ?: start
            val marked = fields[9]
                .replace("\\N", " ")
                .replace("\\n", " ")
                .replace("\\h", " ")

            val words = mutableListOf<LyricsWord>()
            var cursor = 0
            var wordStart = start
            var pendingDuration: Long? = null
            assOverride.findAll(marked).forEach { match ->
                val segment = marked.substring(cursor, match.range.first)
                    .replace(assOverride, "")
                    .trim()
                if (segment.isNotEmpty()) {
                    val duration = pendingDuration
                    if (duration != null) {
                        words.add(LyricsWord(segment, wordStart, wordStart + duration))
                        wordStart += duration
                        pendingDuration = null
                    } else if (words.isEmpty()) {
                        // Text before the first karaoke tag: keep it as an untimed lead-in.
                        words.add(LyricsWord(segment, null, null))
                    } else {
                        val last = words.removeAt(words.lastIndex)
                        words.add(last.copy(text = last.text + " " + segment))
                    }
                }
                assKaraoke.find(match.value)?.groupValues?.get(1)?.toLongOrNull()?.let {
                    pendingDuration = it * 10L
                }
                cursor = match.range.last + 1
            }
            val tail = marked.substring(cursor).replace(assOverride, "").trim()
            if (tail.isNotEmpty()) {
                if (pendingDuration != null) {
                    words.add(LyricsWord(tail, wordStart, wordStart + pendingDuration!!))
                } else if (words.isEmpty()) {
                    words.add(LyricsWord(tail, start, end))
                } else {
                    val last = words.removeAt(words.lastIndex)
                    words.add(last.copy(text = last.text + " " + tail))
                }
            }
            val text = marked.replace(assOverride, "").trim()
            if (text.isNotEmpty()) {
                result.add(LyricsLine(text, start, end, words))
            }
        }
        return result
    }

    private fun parseVtt(raw: String): List<LyricsLine> {
        val result = mutableListOf<LyricsLine>()
        val lines = raw.replace("\r\n", "\n").split('\n')
        var i = 0
        val timing = Regex("""(\d{1,2}:)?\d{1,2}:\d{2}\.\d{1,3}\s*-->\s*(\d{1,2}:)?\d{1,2}:\d{2}\.\d{1,3}""")
        while (i < lines.size) {
            val line = lines[i].trim()
            val timingMatch = timing.find(line)
            if (timingMatch == null) {
                i++
                continue
            }
            val start = parseClock(line.substringBefore("-->").trim()) ?: 0L
            val end = parseClock(
                line.substringAfter("-->").trim().substringBefore(' ').trim()
            ) ?: start
            i++
            val textLines = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank()) {
                textLines.add(lines[i])
                i++
            }
            val text = textLines.joinToString(" ")
            if (text.isNotBlank()) {
                result.add(LyricsLine(text = stripHtml(text), startMs = start, endMs = end))
            }
        }
        return result
    }

    private fun parseSrt(raw: String): List<LyricsLine> {
        val result = mutableListOf<LyricsLine>()
        val normalized = raw.replace("\r\n", "\n")
        val blockRegex = Regex("""(?m)^\s*\d+\s*\n(\d{2}:\d{2}:\d{2},\d{1,3})\s*-->\s*(\d{2}:\d{2}:\d{2},\d{1,3}).*$""")
        val matches = blockRegex.findAll(normalized).toList()
        matches.forEachIndexed { index, match ->
            val start = parseClock(match.groupValues[1]) ?: return@forEachIndexed
            val end = parseClock(match.groupValues[2]) ?: start
            val textStart = match.range.last + 1
            val textEnd = matches.getOrNull(index + 1)?.range?.first ?: normalized.length
            val text = normalized.substring(textStart, textEnd)
                .replace(Regex("""(?m)^\s*\d+\s*$"""), "")
                .trim()
                .replace("\n", " ")
            if (text.isNotEmpty()) {
                result.add(LyricsLine(stripHtml(text), start, end))
            }
        }
        return result
    }

    private fun parseTtml(raw: String): List<LyricsLine> {
        val trimmed = raw.trim().removePrefix("\uFEFF").trim()
        val xmlToParse = if (trimmed.startsWith("<?xml")) {
            trimmed
        } else if (trimmed.contains("<?xml")) {
            trimmed.substring(trimmed.indexOf("<?xml"))
        } else {
            trimmed
        }

        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlToParse))

            val result = mutableListOf<LyricsLine>()
            var lineStart: Long? = null
            var lineEnd: Long? = null
            var agent: String? = null
            var isBgLine = false
            var lineText = StringBuilder()
            val words = mutableListOf<LyricsWord>()
            var wordStart: Long? = null
            var wordEnd: Long? = null
            var wordText = StringBuilder()

            fun flushWord(nextStart: Long?) {
                if (wordText.isNotBlank()) {
                    val end = wordEnd ?: nextStart
                    words.add(LyricsWord(wordText.toString().trim(), wordStart, end))
                }
                wordText = StringBuilder()
                wordStart = nextStart
                wordEnd = null
            }

            fun flushLine() {
                flushWord(null)
                var text = lineText.toString().trim()
                // Safety fallback: if malformed TTML containing raw <mm:ss.xx> tags was embedded
                // (e.g. from an ELRC file converted to TTML without stripping word stamps),
                // extract words and strip tags from line text.
                if (words.isEmpty() && lrcWordStamp.containsMatchIn(text)) {
                    val wordMatches = lrcWordStamp.findAll(text).toList()
                    val fallbackWords = wordMatches.mapIndexedNotNull { index, match ->
                        val start = parseLrcTime(match.groupValues[1], match.groupValues[2])
                        val nextMatch = wordMatches.getOrNull(index + 1)
                        val end = nextMatch?.let { parseLrcTime(it.groupValues[1], it.groupValues[2]) }
                            ?: lineEnd ?: (start + 500L)
                        val wText = text.substring(
                            match.range.last + 1,
                            nextMatch?.range?.first ?: text.length
                        ).trim()
                        if (wText.isEmpty()) null else LyricsWord(wText, start, end)
                    }
                    if (fallbackWords.isNotEmpty()) {
                        words.addAll(fallbackWords)
                        if (lineStart == null) {
                            lineStart = fallbackWords.first().startMs
                        }
                    }
                    text = text.replace(lrcWordStamp, "").trim()
                }
                if (text.isNotEmpty()) {
                    result.add(
                        LyricsLine(
                            text = text,
                            startMs = lineStart,
                            endMs = lineEnd,
                            words = words.toList(),
                            agentId = agent,
                            isBackground = isBgLine
                        )
                    )
                }
                lineStart = null
                lineEnd = null
                agent = null
                isBgLine = false
                lineText = StringBuilder()
                words.clear()
                wordStart = null
                wordEnd = null
                wordText = StringBuilder()
            }

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.substringAfter(':')
                        when (name) {
                            "p" -> {
                                if (lineStart != null) flushLine()
                                lineStart = attr(parser, "begin")?.let(::parseClock)
                                lineEnd = attr(parser, "end")?.let(::parseClock)
                                agent = attr(parser, "agent")
                                val role = attr(parser, "role")
                                if (role == "x-bg" || role?.contains("bg") == true) {
                                    isBgLine = true
                                }
                            }
                            "span" -> {
                                val begin = attr(parser, "begin")?.let(::parseClock)
                                val end = attr(parser, "end")?.let(::parseClock)
                                val role = attr(parser, "role")
                                if (role == "x-bg" || role?.contains("bg") == true) {
                                    isBgLine = true
                                }
                                if (begin != null) {
                                    flushWord(begin)
                                    wordEnd = end
                                } else if (end != null) {
                                    wordEnd = end
                                }
                            }
                            "br" -> {
                                if (lineText.isNotEmpty()) lineText.append(' ')
                                if (wordText.isNotEmpty()) wordText.append(' ')
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text
                        if (lineStart != null) {
                            if (text.isNotBlank()) {
                                lineText.append(text)
                                wordText.append(text)
                            } else if (text.isNotEmpty() && lineText.isNotEmpty()) {
                                lineText.append(' ')
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.substringAfter(':')
                        if (name == "span") {
                            flushWord(null)
                        } else if (name == "p") {
                            flushLine()
                        }
                    }
                }
                event = parser.next()
            }
            if (lineStart != null) flushLine()
            result.ifEmpty { parseTtmlRegexFallback(xmlToParse) }
        } catch (e: Exception) {
            Log.w(TAG, "XmlPullParser failed on TTML, falling back to regex: ${e.message}")
            parseTtmlRegexFallback(xmlToParse)
        }
    }

    private fun parseTtmlRegexFallback(raw: String): List<LyricsLine> {
        val pRegex = Regex("""<p\b([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val beginRegex = Regex("""begin=["']([^"']+)["']""")
        val endRegex = Regex("""end=["']([^"']+)["']""")
        val result = mutableListOf<LyricsLine>()
        pRegex.findAll(raw).forEach { match ->
            val attrs = match.groupValues[1]
            val inner = match.groupValues[2]
            val begin = beginRegex.find(attrs)?.groupValues?.get(1)?.let(::parseClock)
            val end = endRegex.find(attrs)?.groupValues?.get(1)?.let(::parseClock)
            var clean = stripHtml(inner).trim()
            if (lrcWordStamp.containsMatchIn(clean)) {
                clean = clean.replace(lrcWordStamp, "").trim()
            }
            if (clean.isNotEmpty()) {
                result.add(LyricsLine(text = clean, startMs = begin, endMs = end))
            }
        }
        return result
    }

    private fun attr(parser: XmlPullParser, wanted: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).substringAfter(':') == wanted) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun stripHtml(text: String): String = text
        .replace(Regex("""<[^>]+>"""), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()

    private fun isYrc(raw: String): Boolean {
        return raw.lineSequence().any { line ->
            val match = yrcLine.find(line.trim()) ?: return@any false
            yrcWord.containsMatchIn(match.groupValues[3]) &&
                Regex("""\(\d+,\d+,\d+\)""").containsMatchIn(match.groupValues[3])
        }
    }

    private fun isQrc(raw: String): Boolean {
        return raw.lineSequence().any { line ->
            val match = qrcLine.find(line.trim()) ?: return@any false
            match.groupValues[3].contains(Regex("""\(\d+,\d+\)"""))
        }
    }

    private fun parseLrcTime(minutes: String, seconds: String): Long {
        val mins = minutes.toLongOrNull() ?: 0L
        val secs = seconds.replace(':', '.').toDoubleOrNull() ?: 0.0
        return mins * 60_000L + (secs * 1000).toLong()
    }

    fun parseClock(value: String): Long? {
        val clean = value.trim()
        if (clean.isEmpty()) return null
        if (clean.endsWith("ms", ignoreCase = true)) {
            return clean.dropLast(2).toDoubleOrNull()?.toLong()
        }
        if (clean.endsWith("s", ignoreCase = true)) {
            return (clean.dropLast(1).toDoubleOrNull() ?: return null).let { (it * 1000).toLong() }
        }
        if (clean.endsWith("m", ignoreCase = true)) {
            return (clean.dropLast(1).toDoubleOrNull() ?: return null).let { (it * 60_000).toLong() }
        }
        if (clean.endsWith("h", ignoreCase = true)) {
            return (clean.dropLast(1).toDoubleOrNull() ?: return null).let { (it * 3_600_000).toLong() }
        }

        val parts = clean.replace(',', '.').split(':')
        return when (parts.size) {
            1 -> (parts[0].toDoubleOrNull() ?: return null).let { (it * 1000).toLong() }
            2 -> {
                val m = parts[0].toLongOrNull() ?: return null
                val s = parts[1].toDoubleOrNull() ?: return null
                m * 60_000L + (s * 1000).toLong()
            }
            3 -> {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val s = parts[2].toDoubleOrNull() ?: return null
                h * 3_600_000L + m * 60_000L + (s * 1000).toLong()
            }
            4 -> {
                // TTML frames: hh:mm:ss:ff (assume 25 fps when no frame rate is supplied)
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val s = parts[2].toLongOrNull() ?: return null
                val frames = parts[3].toLongOrNull() ?: 0L
                h * 3_600_000L + m * 60_000L + s * 1000L + frames * 40L
            }
            else -> null
        }
    }

    /** Fills missing line/word end times so the UI and karaoke wipe never divide by zero. */
    private fun finish(lines: List<LyricsLine>): List<LyricsLine> {
        val sorted = lines.sortedWith(
            compareBy<LyricsLine> { it.startMs == null }.thenBy { it.startMs ?: Long.MAX_VALUE }
        )
        return sorted.mapIndexed { index, line ->
            val nextStart = sorted.getOrNull(index + 1)?.startMs
            val end = line.endMs
                ?: nextStart
                ?: line.startMs?.plus(DEFAULT_LINE_DURATION_MS)
            val words = line.words.mapIndexed { wordIndex, word ->
                val wordEnd = word.endMs
                    ?: line.words.getOrNull(wordIndex + 1)?.startMs
                    ?: end
                word.copy(endMs = wordEnd)
            }
            line.copy(endMs = end, words = words)
        }
    }

    private const val DEFAULT_LINE_DURATION_MS = 5_000L
}
