package com.deniscerri.ytdl.util.extractors.newpipe.potoken

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoTokenModelsTest {

    @Test
    fun policyFromPref_defaultsAndMatches() {
        assertEquals(PoTokenPolicy.AUTO, PoTokenPolicy.fromPref(null))
        assertEquals(PoTokenPolicy.AUTO, PoTokenPolicy.fromPref("unexpected"))
        assertEquals(PoTokenPolicy.SESSION_ONLY, PoTokenPolicy.fromPref("session_only"))
        assertEquals(PoTokenPolicy.ANONYMOUS_ONLY, PoTokenPolicy.fromPref("anonymous_only"))
        assertEquals(PoTokenPolicy.MANUAL_ONLY, PoTokenPolicy.fromPref("manual_only"))
    }

    @Test
    fun bundleExpiry_respectsSafetyMargin() {
        val now = 1_000_000L
        val bundle = PoTokenBundle(
            visitorData = "CgtABCDEF123456",
            streamingToken = "GVS_TOKEN_VALUE",
            generatedAtMs = now,
            expiresAtMs = now + 60 * 60 * 1000L,
            source = PoTokenSource.SESSION_WEBVIEW
        )
        assertFalse(bundle.isExpired(now + 10_000L))
        assertTrue(bundle.isExpired(now + 55 * 60 * 1000L))
        assertTrue(bundle.copy(visitorData = "").isExpired(now))
        assertTrue(bundle.copy(streamingToken = "").isExpired(now))
    }

    @Test
    fun extractYoutubeVideoId_supportsAllCommonFormats() {
        assertEquals("dQw4w9WgXcQ", PoTokenFormatting.extractYoutubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", PoTokenFormatting.extractYoutubeVideoId("https://youtu.be/dQw4w9WgXcQ?t=10"))
        assertEquals("dQw4w9WgXcQ", PoTokenFormatting.extractYoutubeVideoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", PoTokenFormatting.extractYoutubeVideoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RD"))
        assertEquals("dQw4w9WgXcQ", PoTokenFormatting.extractYoutubeVideoId("https://www.youtube.com/live/dQw4w9WgXcQ"))
        assertNull(PoTokenFormatting.extractYoutubeVideoId("https://vimeo.com/123456789"))
        assertNull(PoTokenFormatting.extractYoutubeVideoId("https://www.youtube.com/feed/subscriptions"))
    }

    @Test
    fun buildYtDlpArgs_bindsVisitorDataAndClientsConsistently() {
        val bundle = PoTokenBundle(
            visitorData = "SESSION_VD_123456",
            streamingToken = "GVS_AAA",
            playerToken = "PLY_BBB",
            generatedAtMs = 1L,
            expiresAtMs = 999_999L,
            source = PoTokenSource.SESSION_WEBVIEW
        )
        val (visitorArg, poArg) = PoTokenFormatting.buildYtDlpPoTokenArgs(bundle, listOf("mweb", "web"))
        assertEquals("visitor_data=SESSION_VD_123456", visitorArg)
        assertEquals(
            "po_token=mweb.gvs+GVS_AAA,mweb.player+PLY_BBB,web.gvs+GVS_AAA,web.player+PLY_BBB",
            poArg
        )
    }

    @Test
    fun sensitiveRedaction_masksTokensAndVisitorData() {
        assertEquals("***123456", PoTokenFormatting.maskTokenTail("CgtXYZ123456"))
        assertEquals("none", PoTokenFormatting.maskTokenTail(""))

        val rawCmd = "yt-dlp --extractor-args \"youtube:visitor_data=SECRET_VD;po_token=mweb.gvs+SECRET_GVS,web.player+SECRET_P\" https://youtu.be/dQw4w9WgXcQ"
        val redacted = PoTokenFormatting.redactSensitiveArgs(rawCmd)
        assertFalse(redacted.contains("SECRET_VD"))
        assertFalse(redacted.contains("SECRET_GVS"))
        assertFalse(redacted.contains("SECRET_P"))
        assertTrue(redacted.contains("visitor_data=***REDACTED***"))
        assertTrue(redacted.contains("po_token=***REDACTED***"))
    }
}
