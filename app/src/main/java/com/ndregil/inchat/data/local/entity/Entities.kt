package com.ndregil.inchat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ndregil.inchat.data.model.DeliveryStatus
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.OccupantAffiliation
import com.ndregil.inchat.data.model.OccupantRole
import com.ndregil.inchat.data.model.RosterContact
import com.ndregil.inchat.data.model.UserPresence

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val jid: String,
    val username: String,
    val domain: String,
    val nickname: String,
    val host: String,
    val port: Int,
    val useTls: Boolean,
    val isDemoMode: Boolean,
    val statusMessage: String,
    val password: String = "",
    val isActive: Boolean = true,
    val lastActiveTime: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "rooms",
    indices = [
        Index(value = ["isJoined"]),
        Index(value = ["lastMessageTime"])
    ]
)
data class RoomEntity(
    @PrimaryKey val jid: String,
    val name: String,
    val topic: String,
    val myNickname: String,
    val occupantCount: Int,
    val unreadCount: Int,
    val lastMessageText: String,
    val lastMessageTime: Long,
    val isJoined: Boolean,
    val isBookmarked: Boolean,
    val myRole: OccupantRole,
    val myAffiliation: OccupantAffiliation,
    val colorHex: String,
    val password: String? = null,
    val isAutoJoin: Boolean = true,
    val bubbleTheme: String = "dregil-theme",
    val wallpaperStyle: String = "default",
    val isGroupChat: Boolean = true
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["roomJid"]),
        Index(value = ["roomJid", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val roomJid: String,
    val senderJid: String,
    val senderNickname: String,
    val body: String,
    val timestamp: Long,
    val isMine: Boolean,
    val messageType: MessageType,
    val status: DeliveryStatus,
    val recipientNickname: String? = null,
    val serverMsgId: String? = null,
    val fileUrl: String? = null,
    val fileMimeType: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val replyToMessageId: String? = null,
    val replyToSender: String? = null,
    val replyToText: String? = null,
    val voiceDurationSeconds: Int? = null
)

typealias ChatMessageEntity = MessageEntity

@Entity(
    tableName = "room_configs",
    indices = [
        Index(value = ["isAutoJoin"]),
        Index(value = ["isBookmarked"])
    ]
)
data class RoomConfigEntity(
    @PrimaryKey val roomJid: String,
    val roomName: String,
    val nickname: String,
    val password: String? = null,
    val isAutoJoin: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val notificationMode: Int = 0, // 0: All, 1: Mentions, 2: Muted
    val subject: String = "",
    val isBookmarked: Boolean = true,
    val colorHex: String = "#0284C7",
    val createdAt: Long = System.currentTimeMillis(),
    val lastJoinedAt: Long = 0L,
    val bubbleTheme: String = "dregil-theme",
    val wallpaperStyle: String = "default",
    val isGroupChat: Boolean = true
)

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["subscriptionType"]),
        Index(value = ["isPendingApproval"])
    ]
)
data class ContactEntity(
    @PrimaryKey val jid: String,
    val name: String,
    val nickname: String? = null,
    val presence: UserPresence = UserPresence.OFFLINE,
    val statusMessage: String = "",
    val groups: String = "",
    val subscriptionType: String = "none",
    val isPendingApproval: Boolean = false,
    val avatar: ByteArray? = null,
    val clientCapsNode: String = "",
    val clientCapsVer: String = "",
    val clientCapsName: String = "",
    val pepActivity: String? = null,
    val pepMood: String? = null,
    val pepTune: String? = null,
    val pepStatus: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toModel(): RosterContact {
        val groupList = if (groups.isNotBlank()) groups.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
        val caps = com.ndregil.inchat.data.model.ClientCaps.resolveFor(
            name = nickname ?: name.ifBlank { jid.substringBefore("@") },
            jid = jid,
            node = clientCapsNode,
            ver = clientCapsVer
        )
        return RosterContact(
            jid = jid,
            name = name,
            nickname = nickname,
            presence = presence,
            statusMessage = statusMessage,
            groups = groupList,
            subscriptionType = subscriptionType,
            isPendingApproval = isPendingApproval,
            avatar = avatar,
            clientCaps = caps,
            pepActivity = pepActivity,
            pepMood = pepMood,
            pepTune = pepTune,
            pepStatus = pepStatus
        )
    }

    companion object {
        fun fromModel(contact: RosterContact): ContactEntity {
            return ContactEntity(
                jid = contact.jid,
                name = contact.name,
                nickname = contact.nickname,
                presence = contact.presence,
                statusMessage = contact.statusMessage,
                groups = contact.groups.joinToString(","),
                subscriptionType = contact.subscriptionType,
                isPendingApproval = contact.isPendingApproval,
                avatar = contact.avatar,
                clientCapsNode = contact.clientCaps.rawNode,
                clientCapsVer = contact.clientCaps.ver,
                clientCapsName = contact.clientCaps.clientName,
                pepActivity = contact.pepActivity,
                pepMood = contact.pepMood,
                pepTune = contact.pepTune,
                pepStatus = contact.pepStatus
            )
        }
    }
}

