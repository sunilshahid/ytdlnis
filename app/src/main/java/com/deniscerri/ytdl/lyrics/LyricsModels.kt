package com.deniscerri.ytdl.lyrics

enum class LyricsFormat {
    PLAIN,
    LRC,
    LRC_A2,
    SPL,
    TTML,
    YRC,
    QRC,
    ASS,
    SYLT,
    VTT,
    SRT;

    val isSynchronized: Boolean get() = this != PLAIN
}

enum class LyricsSource {
    EMBEDDED,
    SIDECAR,
    CACHE,
    LRCLIB,
    USER
}

data class LyricsWord(
    val text: String,
    val startMs: Long?,
    val endMs: Long? = null
) {
    val isSynchronized: Boolean get() = startMs != null
}

data class LyricsLine(
    val text: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val words: List<LyricsWord> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null,
    val agentId: String? = null
) {
    val isSynchronized: Boolean get() = startMs != null

    val hasWordTimings: Boolean
        get() = words.size > 1 && words.all { it.startMs != null }
}

data class LyricsDocument(
    val mediaId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val lines: List<LyricsLine>,
    val source: LyricsSource,
    val format: LyricsFormat,
    val raw: String,
    val providerId: String? = null,
    val offsetMs: Long = 0L
) {
    val isSynced: Boolean get() = lines.any { it.isSynchronized }
    val hasWordTimings: Boolean get() = lines.any { it.hasWordTimings }

    /** Index of the last line whose start is <= the position, or -1 before the first line. */
    fun activeLineIndex(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        val shifted = positionMs - offsetMs
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val start = lines[mid].startMs
            if (start == null) {
                low = mid + 1
            } else if (start <= shifted) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun activeLine(positionMs: Long): LyricsLine? = lines.getOrNull(activeLineIndex(positionMs))

    /** Progress through the active line in the 0..1 range. */
    fun lineProgress(positionMs: Long): Float {
        val index = activeLineIndex(positionMs)
        val line = lines.getOrNull(index) ?: return 0f
        val start = line.startMs ?: return 0f
        val end = line.endMs ?: lines.getOrNull(index + 1)?.startMs ?: return 0f
        val shifted = positionMs - offsetMs
        if (end <= start) return 0f
        return ((shifted - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    /** Progress of a word in the 0..1 range, or null when it has no end timestamp. */
    fun wordProgress(word: LyricsWord, nextWord: LyricsWord?, positionMs: Long): Float? {
        val start = word.startMs ?: return null
        val end = word.endMs ?: nextWord?.startMs ?: return null
        if (end <= start) return null
        val shifted = positionMs - offsetMs
        return ((shifted - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    /**
     * Derives deterministic, explicitly approximate per-word intervals weighted by non-whitespace
     * character count when a line has only line-level timing. Never flips [hasWordTimings].
     */
    fun approximateWordsForLine(lineIndex: Int): List<LyricsWord> {
        val line = lines.getOrNull(lineIndex) ?: return emptyList()
        if (line.hasWordTimings) return line.words
        val lineStart = line.startMs ?: return emptyList()
        val tokens = line.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        val nextStart = lines.getOrNull(lineIndex + 1)?.startMs?.takeIf { it > lineStart }
        val fallbackDuration = (line.text.length * 80L).coerceIn(1_800L, 6_000L)
        val lineEnd = line.endMs?.takeIf { it > lineStart } ?: nextStart ?: (lineStart + fallbackDuration)
        val totalDuration = (lineEnd - lineStart).coerceAtLeast(tokens.size * 40L)

        val weights = tokens.map { token -> token.count { it.isLetterOrDigit() }.coerceAtLeast(1) }
        val totalWeight = weights.sum().coerceAtLeast(1)

        var accumulatedWeight = 0
        val result = ArrayList<LyricsWord>(tokens.size)
        tokens.forEachIndexed { idx, token ->
            val wStart = lineStart + (totalDuration * accumulatedWeight) / totalWeight
            accumulatedWeight += weights[idx]
            val wEnd = if (idx == tokens.lastIndex) {
                lineEnd.coerceAtLeast(wStart + 1L)
            } else {
                (lineStart + (totalDuration * accumulatedWeight) / totalWeight).coerceAtLeast(wStart + 1L)
            }
            result.add(LyricsWord(text = token, startMs = wStart, endMs = wEnd))
        }
        return result
    }

    fun effectiveWordProgress(startMs: Long?, endMs: Long?, positionMs: Long): Float? {
        val s = startMs ?: return null
        val e = endMs ?: return null
        val shifted = positionMs - offsetMs
        if (shifted < s) return 0f
        if (shifted >= e) return 1f
        val span = (e - s).coerceAtLeast(1L)
        return ((shifted - s).toFloat() / span).coerceIn(0f, 1f)
    }

    fun withOffset(offsetMs: Long): LyricsDocument = copy(offsetMs = offsetMs)
}
