package com.deniscerri.ytdl.database.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Parsed-lyrics cache. We store the raw lyric text plus enough metadata to re-parse it, which
 * keeps format upgrades cheap and avoids caching a serialized object graph.
 */
@Entity(
    tableName = "lyrics_cache",
    indices = [Index(value = ["mediaId"], unique = true)]
)
data class LyricsCacheItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: String,
    val videoId: String? = null,
    val title: String = "",
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0,
    val rawLyrics: String,
    /** PLAIN, LRC, LRC_A2, SPL, TTML, YRC, QRC, ASS, SYLT, VTT, SRT */
    val format: String,
    /** EMBEDDED, SIDECAR, CACHE, LRCLIB, USER */
    val source: String,
    val providerId: String? = null,
    val offsetMs: Long = 0,
    val isSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
