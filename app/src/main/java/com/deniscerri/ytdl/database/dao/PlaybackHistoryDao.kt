package com.deniscerri.ytdl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.deniscerri.ytdl.database.models.PlaybackHistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaybackHistoryDao {

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    abstract fun getRecent(limit: Int = 30): Flow<List<PlaybackHistoryItem>>

    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId LIMIT 1")
    abstract suspend fun getByMediaId(mediaId: String): PlaybackHistoryItem?

    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId LIMIT 1")
    abstract fun getByMediaIdFlow(mediaId: String): Flow<PlaybackHistoryItem?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(item: PlaybackHistoryItem)

    @Query("UPDATE playback_history SET positionMs = :positionMs, durationMs = :durationMs, lastPlayedAt = :lastPlayedAt WHERE mediaId = :mediaId")
    abstract suspend fun updateProgress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        lastPlayedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM playback_history WHERE mediaId = :mediaId")
    abstract suspend fun delete(mediaId: String)

    @Query("DELETE FROM playback_history WHERE localUri = :localUri")
    abstract suspend fun deleteByLocalUri(localUri: String)

    @Query("DELETE FROM playback_history")
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun record(item: PlaybackHistoryItem) {
        val existing = getByMediaId(item.mediaId)
        if (existing == null) {
            insert(item)
        } else {
            insert(item.copy(id = existing.id))
        }
    }
}
