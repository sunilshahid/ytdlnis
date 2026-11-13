package com.deniscerri.ytdl.database.models

import androidx.room.Entity
import androidx.room.Index

/**
 * Persisted queue state. The app only ever owns one queue, so [id] is fixed at 1.
 */
@Entity(tableName = "playback_queue")
data class PlaybackQueueState(
    @androidx.room.PrimaryKey
    val id: Long = QUEUE_ID,
    val currentIndex: Int = 0,
    val currentPositionMs: Long = 0,
    val shuffleMode: Int = 0,
    val repeatMode: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val QUEUE_ID = 1L
    }
}

/**
 * One entry of the persisted queue. [position] is the stable ordinal in the queue, independent
 * of shuffle order. Aggregate values are stored as primitives so the table survives migrations
 * without custom type converters.
 */
@Entity(
    tableName = "playback_queue_items",
    primaryKeys = ["queueId", "position"],
    indices = [Index("queueId")]
)
data class PlaybackQueueItem(
    val queueId: Long = PlaybackQueueState.QUEUE_ID,
    val position: Int,
    val mediaId: String,
    /** AUDIO or VIDEO */
    val type: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val artworkUri: String? = null,
    /** YOUTUBE, LOCAL or DOWNLOAD */
    val source: String,
    val videoId: String? = null,
    val webpageUrl: String? = null,
    val localUri: String? = null,
    val extrasJson: String? = null
)
