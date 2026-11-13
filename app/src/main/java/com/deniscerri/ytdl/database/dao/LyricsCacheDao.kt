package com.deniscerri.ytdl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.deniscerri.ytdl.database.models.LyricsCacheItem
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LyricsCacheDao {

    @Query("SELECT * FROM lyrics_cache WHERE mediaId = :mediaId LIMIT 1")
    abstract suspend fun getByMediaId(mediaId: String): LyricsCacheItem?

    @Query("SELECT * FROM lyrics_cache WHERE mediaId = :mediaId LIMIT 1")
    abstract fun getByMediaIdFlow(mediaId: String): Flow<LyricsCacheItem?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(item: LyricsCacheItem)

    @Query("UPDATE lyrics_cache SET offsetMs = :offsetMs WHERE mediaId = :mediaId")
    abstract suspend fun updateOffset(mediaId: String, offsetMs: Long)

    @Query("DELETE FROM lyrics_cache WHERE mediaId = :mediaId")
    abstract suspend fun delete(mediaId: String)

    @Query("DELETE FROM lyrics_cache")
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun upsert(item: LyricsCacheItem) {
        val existing = getByMediaId(item.mediaId)
        insert(if (existing == null) item else item.copy(id = existing.id))
    }
}
