package com.ndregil.inchat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ndregil.inchat.data.model.ClientPlatform

@Entity(tableName = "client_capabilities")
data class ClientCapabilityEntity(
    @PrimaryKey val jid: String, // Full JID or Bare JID
    val clientName: String,
    val version: String,
    val os: String,
    val platform: ClientPlatform,
    val nodeUri: String,
    val hash: String,
    val ver: String,
    val iconResId: Int?,
    val rawNode: String,
    val updatedTimestamp: Long = System.currentTimeMillis()
)
