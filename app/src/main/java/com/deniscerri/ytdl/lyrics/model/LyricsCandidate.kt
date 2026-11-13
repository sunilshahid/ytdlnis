package com.deniscerri.ytdl.lyrics.model

import com.deniscerri.ytdl.lyrics.LyricsFormat

data class LyricsQuery(
    val mediaId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val localUri: String? = null
)

data class ProviderLyrics(
    val raw: String,
    val format: LyricsFormat,
    val providerId: String,
    val providerName: String,
    val matchedTitle: String? = null,
    val matchedArtist: String? = null,
    val matchedDurationMs: Long? = null
)

data class LyricsCandidate(
    val providerId: String,
    val providerName: String,
    val trackName: String,
    val artistName: String?,
    val albumName: String?,
    val durationSec: Int,
    val format: LyricsFormat,
    val isSynced: Boolean,
    val hasWordTimings: Boolean,
    val preview: String,
    val raw: String
)
