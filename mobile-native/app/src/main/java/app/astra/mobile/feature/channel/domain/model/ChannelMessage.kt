package app.astra.mobile.feature.channel.domain.model

import app.astra.mobile.core.model.Attachment

data class ChannelMessage(
    val id: String,
    val content: String,
    val authorName: String,
    val authorAvatar: String?,
    val authorColor: String? = null,
    val createdAt: String?,
    val mine: Boolean,
    val edited: Boolean = false,
    val pinned: Boolean = false,
    val reactions: List<MessageReaction> = emptyList(),
    val replyToAuthor: String? = null,
    val replyToContent: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val poll: Poll? = null,
)

data class Poll(
    val question: String,
    val options: List<PollOption>,
    val allowMultiple: Boolean,
    val expiresAt: String?,
    val closed: Boolean,
)

data class PollOption(
    val id: String,
    val text: String,
    val votes: Int,
    val mine: Boolean,
)

data class MessageReaction(
    val emoji: String,
    val count: Int,
    val mine: Boolean,
)

data class TypingUser(
    val userId: String,
    val username: String,
    val typing: Boolean,
)

data class ChannelMessagesPage(
    val messages: List<ChannelMessage>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
