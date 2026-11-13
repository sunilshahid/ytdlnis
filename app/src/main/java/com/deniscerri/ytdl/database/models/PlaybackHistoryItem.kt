package com.deniscerri.ytdl.database.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Recently played + resume position. Deliberately separate from the download history: a completed
 * download can appear in both, but they answer different questions.
 */
@Entity(
    tableName = "playback_history",
    indices = [Index(value = ["mediaId"], unique = true)]
)
data class PlaybackHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: String,
    /** AUDIO or VIDEO */
    val type: String = "AUDIO",
    val title: String,
    val artist: String? = null,
    val artworkUri: String? = null,
    val source: String,
    val videoId: String? = null,
    val webpageUrl: String? = null,
    val localUri: String? = null,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val positionMs: Long = 0,
    val durationMs: Long = 0
)
