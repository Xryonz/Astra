package app.astra.mobile.feature.channel.domain.model

data class ChannelMessage(
    val id: String,
    val content: String,
    val authorName: String,
    val authorAvatar: String?,
    val createdAt: String?,
    val mine: Boolean,
    val edited: Boolean = false,
    val reactions: List<MessageReaction> = emptyList(),
)

// mine = o usuario atual reagiu com esse emoji (pra destacar o chip).
data class MessageReaction(
    val emoji: String,
    val count: Int,
    val mine: Boolean,
)

data class ChannelMessagesPage(
    val messages: List<ChannelMessage>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
