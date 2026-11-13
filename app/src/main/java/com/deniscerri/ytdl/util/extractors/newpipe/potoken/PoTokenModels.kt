package com.deniscerri.ytdl.util.extractors.newpipe.potoken

import java.net.URI

enum class PoTokenSource(val id: String) {
    SESSION_WEBVIEW("session_webview"),
    ANONYMOUS_WEBVIEW("anonymous_webview"),
    BGUTIL("bgutil"),
    MANUAL("manual")
}

enum class PoTokenPolicy(val prefValue: String) {
    AUTO("auto"),
    SESSION_ONLY("session_only"),
    ANONYMOUS_ONLY("anonymous_only"),
    MANUAL_ONLY("manual_only");

    companion object {
        fun fromPref(value: String?): PoTokenPolicy {
            return entries.firstOrNull { it.prefValue == value } ?: AUTO
        }
    }
}

data class PoTokenBundle(
    val visitorData: String,
    val streamingToken: String,
    val playerToken: String? = null,
    val clientName: String = "mweb",
    val generatedAtMs: Long,
    val expiresAtMs: Long,
    val source: PoTokenSource
) {
    val maskedVisitorData: String
        get() = PoTokenFormatting.maskTokenTail(visitorData)

    fun isExpired(
        nowMs: Long = System.currentTimeMillis(),
        safetyMarginMs: Long = DEFAULT_EXPIRY_MARGIN_MS
    ): Boolean {
        return visitorData.isBlank() ||
            streamingToken.isBlank() ||
            nowMs + safetyMarginMs >= expiresAtMs
    }

    companion object {
        const val DEFAULT_EXPIRY_MARGIN_MS = 10 * 60 * 1000L
        const val DEFAULT_BUNDLE_TTL_MS = 5 * 60 * 60 * 1000L
    }
}

object PoTokenFormatting {
    val DEFAULT_YTDLP_WEB_CLIENTS = listOf("mweb", "web", "web_safari")
    private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    fun maskTokenTail(token: String?): String {
        val trimmed = token?.trim().orEmpty()
        if (trimmed.isEmpty()) return "none"
        return if (trimmed.length > 8) "***${trimmed.takeLast(6)}" else "***"
    }

    fun extractYoutubeVideoId(rawUrl: String?): String? {
        val url = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!host.contains("youtube.com") && !host.contains("youtu.be") && !host.contains("youtube-nocookie.com")) {
            return null
        }

        if (host.endsWith("youtu.be")) {
            val id = uri.path.orEmpty().trim('/').substringBefore('/').substringBefore('?')
            return id.takeIf { VIDEO_ID_REGEX.matches(it) }
        }

        val query = uri.rawQuery.orEmpty()
        query.split('&').forEach { part ->
            val eq = part.indexOf('=')
            if (eq > 0 && part.substring(0, eq) == "v") {
                val id = part.substring(eq + 1).substringBefore('#')
                if (VIDEO_ID_REGEX.matches(id)) return id
            }
        }

        val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.size >= 2 && segments[0] in setOf("shorts", "live", "embed", "v", "e")) {
            val id = segments[1]
            if (VIDEO_ID_REGEX.matches(id)) return id
        }

        return null
    }

    fun buildYtDlpPoTokenArgs(
        bundle: PoTokenBundle,
        clients: List<String> = DEFAULT_YTDLP_WEB_CLIENTS
    ): Pair<String, String> {
        val targetClients = clients.filter { it.isNotBlank() }.distinct().ifEmpty { DEFAULT_YTDLP_WEB_CLIENTS }
        val entries = mutableListOf<String>()
        targetClients.forEach { client ->
            entries.add("$client.gvs+${bundle.streamingToken}")
            bundle.playerToken?.takeIf { it.isNotBlank() }?.let { player ->
                entries.add("$client.player+$player")
            }
        }
        return "visitor_data=${bundle.visitorData}" to "po_token=${entries.joinToString(",")}"
    }

    fun redactSensitiveArgs(commandLine: String): String {
        return commandLine
            .replace(Regex("visitor_data=[^;\\s\"']+"), "visitor_data=***REDACTED***")
            .replace(Regex("po_token=[^;\\s\"']+"), "po_token=***REDACTED***")
    }
}
