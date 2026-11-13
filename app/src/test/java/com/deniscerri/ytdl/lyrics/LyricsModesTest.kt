package com.deniscerri.ytdl.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsModesTest {

    @Test
    fun approximateWordsForLine_distributesProportionalTimingsWithoutFakingRealFlag() {
        val line1 = LyricsLine(text = "Hello wonderful world", startMs = 1_000L, endMs = 4_000L)
        val line2 = LyricsLine(text = "Next line", startMs = 5_000L)
        val doc = LyricsDocument(
            mediaId = "track-1",
            title = "Song",
            artist = "Artist",
            album = null,
            lines = listOf(line1, line2),
            source = LyricsSource.SIDECAR,
            format = LyricsFormat.LRC,
            raw = ""
        )

        assertFalse(doc.hasWordTimings)
        val approx = doc.approximateWordsForLine(0)
        assertEquals(listOf("Hello", "wonderful", "world"), approx.map { it.text })
        assertEquals(1_000L, approx.first().startMs)
        assertEquals(4_000L, approx.last().endMs)
        for (i in 0 until approx.lastIndex) {
            assertEquals(approx[i].endMs, approx[i + 1].startMs)
        }
        val helloDur = approx[0].endMs!! - approx[0].startMs!!
        val wonderfulDur = approx[1].endMs!! - approx[1].startMs!!
        assertTrue("Longer word should receive more duration", wonderfulDur > helloDur)
        assertFalse(doc.hasWordTimings)
    }

    @Test
    fun approximateWordsForLine_returnsTrueWordsUnchangedWhenAvailable() {
        val trueWords = listOf(
            LyricsWord("One", 100L, 500L),
            LyricsWord("Two", 550L, 900L)
        )
        val doc = LyricsDocument(
            mediaId = "track-2",
            title = "Song",
            artist = null,
            album = null,
            lines = listOf(LyricsLine("One Two", startMs = 100L, endMs = 900L, words = trueWords)),
            source = LyricsSource.EMBEDDED,
            format = LyricsFormat.LRC_A2,
            raw = ""
        )

        assertTrue(doc.hasWordTimings)
        assertEquals(trueWords, doc.approximateWordsForLine(0))
    }

    @Test
    fun effectiveWordProgress_tracksBoundsAndOffset() {
        val doc = LyricsDocument(
            mediaId = "track-3",
            title = "Song",
            artist = null,
            album = null,
            lines = emptyList(),
            source = LyricsSource.LRCLIB,
            format = LyricsFormat.LRC,
            raw = "",
            offsetMs = 500L
        )
        assertEquals(0f, doc.effectiveWordProgress(1_000L, 2_000L, 1_400L)!!, 0.001f)
        assertEquals(0.5f, doc.effectiveWordProgress(1_000L, 2_000L, 2_000L)!!, 0.001f)
        assertEquals(1f, doc.effectiveWordProgress(1_000L, 2_000L, 2_600L)!!, 0.001f)
    }
}
