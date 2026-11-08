package com.ndregil.inchat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ndregil.inchat.data.model.StoryViewer

@Entity(
    tableName = "story_viewers",
    indices = [
        Index(value = ["storyId"]),
        Index(value = ["viewerJid"]),
        Index(value = ["storyId", "viewerJid"], unique = true)
    ]
)
data class StoryViewerEntity(
    @PrimaryKey val id: String,
    val storyId: String,
    val viewerJid: String,
    val viewerName: String,
    val viewedAt: Long
) {
    fun toModel(avatar: ByteArray? = null): StoryViewer {
        return StoryViewer(
            id = id,
            storyId = storyId,
            viewerJid = viewerJid,
            viewerName = viewerName,
            viewedAt = viewedAt,
            viewerAvatar = avatar
        )
    }

    companion object {
        fun fromModel(viewer: StoryViewer): StoryViewerEntity {
            return StoryViewerEntity(
                id = viewer.id,
                storyId = viewer.storyId,
                viewerJid = viewer.viewerJid,
                viewerName = viewer.viewerName,
                viewedAt = viewer.viewedAt
            )
        }
    }
}
