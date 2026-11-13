package com.deniscerri.ytdl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.deniscerri.ytdl.database.models.PlaybackQueueItem
import com.deniscerri.ytdl.database.models.PlaybackQueueState
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaybackQueueDao {

    @Query("SELECT * FROM playback_queue WHERE id = 1")
    abstract fun getStateFlow(): Flow<PlaybackQueueState?>

    @Query("SELECT * FROM playback_queue WHERE id = 1")
    abstract suspend fun getState(): PlaybackQueueState?

    @Query("SELECT * FROM playback_queue_items ORDER BY position ASC")
    abstract fun getItemsFlow(): Flow<List<PlaybackQueueItem>>

    @Query("SELECT * FROM playback_queue_items ORDER BY position ASC")
    abstract suspend fun getItems(): List<PlaybackQueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertState(state: PlaybackQueueState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertItems(items: List<PlaybackQueueItem>)

    @Query("DELETE FROM playback_queue_items")
    abstract suspend fun deleteItems()

    @Query("DELETE FROM playback_queue")
    abstract suspend fun deleteState()

    @Transaction
    open suspend fun replaceQueue(state: PlaybackQueueState, items: List<PlaybackQueueItem>) {
        deleteItems()
        deleteState()
        upsertState(state)
        insertItems(items)
    }

    @Transaction
    open suspend fun saveState(state: PlaybackQueueState) {
        upsertState(state)
    }

    @Transaction
    open suspend fun clear() {
        deleteItems()
        deleteState()
    }
}
