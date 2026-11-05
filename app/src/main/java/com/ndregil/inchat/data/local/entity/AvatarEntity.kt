package com.ndregil.inchat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "avatars")
data class AvatarEntity(
    @PrimaryKey val jid: String,
    val avatarBytes: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AvatarEntity
        if (jid != other.jid) return false
        if (!avatarBytes.contentEquals(other.avatarBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = jid.hashCode()
        result = 31 * result + avatarBytes.contentHashCode()
        return result
    }
}
