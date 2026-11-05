package com.ndregil.inchat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ndregil.inchat.data.local.entity.AvatarEntity

@Dao
interface AvatarDao {
    @Query("SELECT * FROM avatars WHERE jid = :jid")
    suspend fun getAvatarByJid(jid: String): AvatarEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAvatar(avatar: AvatarEntity)

    @Query("DELETE FROM avatars WHERE jid = :jid")
    suspend fun deleteAvatar(jid: String)

    @Query("SELECT * FROM avatars")
    suspend fun getAllAvatars(): List<AvatarEntity>

    @Query("DELETE FROM avatars")
    suspend fun clearAllAvatars()
}
