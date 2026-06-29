package app.astra.mobile.feature.dm.domain.model

data class DmMessage(
    val id: String,
    val content: String,
    val authorName: String,
    val authorAvatar: String?,
    val createdAt: String?,
    val mine: Boolean,
    val replyToAuthor: String? = null,
    val replyToContent: String? = null,
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
