package com.ndregil.inchat.data.model

import java.util.UUID

/**
 * Information about a contact who viewed a specific story update.
 */
data class StoryViewer(
    val id: String = UUID.randomUUID().toString(),
    val storyId: String,
    val viewerJid: String,
    val viewerName: String,
    val viewedAt: Long = System.currentTimeMillis(),
    val viewerAvatar: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StoryViewer
        return id == other.id || (storyId == other.storyId && viewerJid.equals(other.viewerJid, ignoreCase = true))
    }

    override fun hashCode(): Int = (storyId + viewerJid.lowercase()).hashCode()
}

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
    val isViewed: Boolean = false,
    val viewers: List<StoryViewer> = emptyList()
) {
    val viewerCount: Int
        get() = viewers.size

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
