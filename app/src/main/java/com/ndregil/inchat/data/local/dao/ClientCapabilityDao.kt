package com.ndregil.inchat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ndregil.inchat.data.local.entity.ClientCapabilityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientCapabilityDao {
    @Query("SELECT * FROM client_capabilities WHERE jid = :jid LIMIT 1")
    suspend fun getCapability(jid: String): ClientCapabilityEntity?

    @Query("SELECT * FROM client_capabilities WHERE jid = :jid LIMIT 1")
    fun observeCapability(jid: String): Flow<ClientCapabilityEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapability(capability: ClientCapabilityEntity)

    @Query("DELETE FROM client_capabilities WHERE jid = :jid")
    suspend fun deleteCapability(jid: String)

    @Query("DELETE FROM client_capabilities")
    suspend fun clearAll()
}
