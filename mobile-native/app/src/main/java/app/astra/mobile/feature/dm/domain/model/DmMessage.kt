package app.astra.mobile.feature.dm.domain.model

import app.astra.mobile.core.model.Attachment

data class DmMessage(
    val id: String,
    val content: String,
    val authorId: String? = null,
    val authorName: String,
    val authorAvatar: String?,
    val authorFont: String? = null,
    val createdAt: String?,
    val mine: Boolean,
    val replyToAuthor: String? = null,
    val replyToContent: String? = null,
    val attachments: List<Attachment> = emptyList(),
)

data class MessagesPage(
    val messages: List<DmMessage>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

data class OpenedConversation(
    val conversationId: String,
    val otherName: String,
    val otherAvatar: String?,
)

data class TypingUser(
    val userId: String,
    val username: String,
    val typing: Boolean,
)
