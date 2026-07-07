package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChannelMessageDto(
    val id: String,
    val content: String = "",
    val authorId: String,
    val channelId: String,
    val authorColor: String? = null,
    val createdAt: String? = null,
    val edited: Boolean = false,
    val pinned: Boolean = false,
    val reactions: List<ReactionDto> = emptyList(),
    val replyTo: ReplyToDto? = null,
    val author: MsgAuthorDto? = null,
    val attachments: List<AttachmentDto> = emptyList(),
    val poll: PollDto? = null,
)

@Serializable
data class ReactionDto(
    val emoji: String,
    val count: Int = 0,
    val users: List<String> = emptyList(),
)

@Serializable
data class ReactResultDto(
    val action: String = "",
    val reactions: List<ReactionDto> = emptyList(),
)

@Serializable
data class ReactionUpdateDto(
    val messageId: String,
    val channelId: String,
    val reactions: List<ReactionDto> = emptyList(),
)

@Serializable
data class ChannelMessagesPageDto(
    val items: List<ChannelMessageDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class SendChannelRequest(
    val content: String,
    val replyToId: String? = null,
    val attachments: List<AttachmentDto> = emptyList(),
)

@Serializable
data class EditChannelRequest(val content: String)

@Serializable
data class MessageEditDto(
    val id: String,
    val content: String = "",
    val editedAt: String? = null,
)

@Serializable
data class ReactRequest(val emoji: String)
