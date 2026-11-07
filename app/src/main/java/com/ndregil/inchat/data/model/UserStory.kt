package com.ndregil.inchat.data.model

import java.util.UUID

/**
 * Model for WhatsApp-style Status / Story shared among contacts.
 */
data class UserStory(
    val id: String = UUID.randomUUID().toString(),
    val senderJid: String,
    val senderName: String,
    val senderAvatar: ByteArray? = null,
    val text: String = "",
    val mediaUrl: String? = null,
    val mediaType: StoryMediaType = StoryMediaType.TEXT,
    val backgroundColorHex: String = "#0284C7",
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean = false,
    val isViewed: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UserStory
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class StoryMediaType {
    TEXT,
    IMAGE
}

/**
 * Grouped stories by contact (like WhatsApp story tray: My Story, Recent updates, Viewed updates)
 */
data class ContactStoryGroup(
    val jid: String,
    val name: String,
    val avatar: ByteArray? = null,
    val stories: List<UserStory>,
    val lastUpdated: Long = stories.maxOfOrNull { it.timestamp } ?: 0L,
    val hasUnseen: Boolean = stories.any { !it.isViewed && !it.isMine }
)
