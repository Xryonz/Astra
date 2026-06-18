package app.astra.mobile.feature.channel.domain.model

data class ChannelMessage(
    val id: String,
    val content: String,
    val authorName: String,
    val authorAvatar: String?,
    val createdAt: String?,
    val mine: Boolean,
)

data class ChannelMessagesPage(
    val messages: List<ChannelMessage>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
