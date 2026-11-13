package com.deniscerri.ytdl.playback

import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.playback.model.PlaybackChapter
import com.deniscerri.ytdl.playback.model.PlaybackExtrasCodec
import com.deniscerri.ytdl.playback.resolve.SubtitleSidecarFinder
import com.deniscerri.ytdl.ui.play.VideoPlayerOverlays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VideoExtrasTest {

    @Test
    fun sidecarMatcher_extractsLanguageAndRejectsNonMatches() {
        val exact = SubtitleSidecarFinder.extractSidecarMatch("Concert_2026", "concert_2026.srt")
        assertNotNull(exact)
        assertNull(exact!!.first)
        assertEquals("SRT", exact.second)

        val en = SubtitleSidecarFinder.extractSidecarMatch("Concert_2026", "Concert_2026.en.vtt")
        assertNotNull(en)
        assertEquals("en", en!!.first)

        assertNull(SubtitleSidecarFinder.extractSidecarMatch("Concert_2026", "OtherVideo.en.srt"))
        assertNull(SubtitleSidecarFinder.extractSidecarMatch("Concert_2026", "Concert_2026.mp4"))
    }

    @Test
    fun chapterHelpers_lookupJumpAndFormatAccurately() {
        val chapters = PlaybackExtrasCodec.chaptersFromChapterItems(
            listOf(
                ChapterItem(0L, 60L, "Intro"),
                ChapterItem(60L, 180L, "Verse"),
                ChapterItem(180L, 300L, "Chorus")
            )
        )
        assertEquals(60_000L, chapters[1].startMs)
        assertEquals(0, PlaybackExtrasCodec.findActiveChapterIndex(chapters, 10_000L))
        assertEquals(1, PlaybackExtrasCodec.findActiveChapterIndex(chapters, 60_000L))
        assertEquals(2, PlaybackExtrasCodec.findActiveChapterIndex(chapters, 250_000L))
        assertEquals(-1, PlaybackExtrasCodec.findActiveChapterIndex(emptyList(), 0L))

        assertEquals(60_000L, VideoPlayerOverlays.computeChapterJumpMs(chapters, 10_000L, +1))
        assertEquals(60_000L, VideoPlayerOverlays.computeChapterJumpMs(chapters, 70_000L, -1))
        assertEquals(0L, VideoPlayerOverlays.computeChapterJumpMs(chapters, 61_500L, -1))
        assertEquals("1:02:03", PlaybackExtrasCodec.formatTimestampMs(3_723_000L))
        assertEquals("4:05", PlaybackExtrasCodec.formatTimestampMs(245_000L))
    }

    @Test
    fun subtitleMimeInference_mapsSupportedFormats() {
        assertEquals("text/vtt", PlaybackExtrasCodec.inferSubtitleMimeType("https://example.com/sub.vtt?lang=en"))
        assertEquals("application/x-subrip", PlaybackExtrasCodec.inferSubtitleMimeType("movie.SRT"))
        assertEquals("text/x-ssa", PlaybackExtrasCodec.inferSubtitleMimeType("anime.ass"))
        assertEquals("application/ttml+xml", PlaybackExtrasCodec.inferSubtitleMimeType("ttml"))
    }
}
