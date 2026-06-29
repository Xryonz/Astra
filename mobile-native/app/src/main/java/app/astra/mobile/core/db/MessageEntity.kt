package app.astra.mobile.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val authorId: String?,
    val authorName: String,
    val authorAvatar: String?,
    val content: String,
    val createdAt: String?,
    val replyToAuthor: String? = null,
    val replyToContent: String? = null,

    val edited: Boolean = false,
    val pinned: Boolean = false,
    val reactionsJson: String? = null,
)
