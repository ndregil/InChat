package com.ndregil.inchat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ndregil.inchat.data.model.StoryMediaType
import com.ndregil.inchat.data.model.UserStory

@Entity(
    tableName = "stories",
    indices = [
        Index(value = ["senderJid"]),
        Index(value = ["timestamp"])
    ]
)
data class StoryEntity(
    @PrimaryKey val id: String,
    val senderJid: String,
    val senderName: String,
    val text: String,
    val mediaUrl: String?,
    val mediaType: String, // TEXT, IMAGE
    val backgroundColorHex: String,
    val timestamp: Long,
    val isMine: Boolean,
    val isViewed: Boolean = false
) {
    fun toModel(avatar: ByteArray? = null, viewers: List<com.ndregil.inchat.data.model.StoryViewer> = emptyList()): UserStory {
        return UserStory(
            id = id,
            senderJid = senderJid,
            senderName = senderName,
            senderAvatar = avatar,
            text = text,
            mediaUrl = mediaUrl,
            mediaType = try { StoryMediaType.valueOf(mediaType) } catch (_: Exception) { StoryMediaType.TEXT },
            backgroundColorHex = backgroundColorHex,
            timestamp = timestamp,
            isMine = isMine,
            isViewed = isViewed,
            viewers = viewers
        )
    }

    companion object {
        fun fromModel(story: UserStory): StoryEntity {
            return StoryEntity(
                id = story.id,
                senderJid = story.senderJid,
                senderName = story.senderName,
                text = story.text,
                mediaUrl = story.mediaUrl,
                mediaType = story.mediaType.name,
                backgroundColorHex = story.backgroundColorHex,
                timestamp = story.timestamp,
                isMine = story.isMine,
                isViewed = story.isViewed
            )
        }
    }
}
