package com.ndregil.inchat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ndregil.inchat.data.local.entity.StoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories ORDER BY timestamp DESC")
    fun getAllStories(): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories WHERE timestamp >= :expiryThreshold ORDER BY timestamp DESC")
    fun getActiveStories(expiryThreshold: Long): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories WHERE isMine = 1 ORDER BY timestamp DESC")
    fun getMyStories(): Flow<List<StoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStory(story: StoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<StoryEntity>)

    @Query("UPDATE stories SET isViewed = 1 WHERE id = :storyId")
    suspend fun markStoryViewed(storyId: String)

    @Query("DELETE FROM stories WHERE id = :storyId")
    suspend fun deleteStory(storyId: String)

    @Query("DELETE FROM stories WHERE timestamp < :expiryThreshold")
    suspend fun deleteExpiredStories(expiryThreshold: Long)

    @Query("DELETE FROM stories")
    suspend fun clearAllStories()
}
