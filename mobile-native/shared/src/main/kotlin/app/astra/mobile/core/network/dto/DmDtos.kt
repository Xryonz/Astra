package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConversationDto(
    val id: String,
    val otherUser: UserDto? = null,
    val lastMessage: LastMessageDto? = null,
    val muted: Boolean = false,
)

@Serializable
data class LastMessageDto(
    val content: String = "",
    val senderId: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class DmReadDto(
    val mine: String? = null,
    val other: String? = null,
)

@Serializable
data class DmMessageDto(
    val id: String,
    val content: String = "",
    val senderId: String,
    val conversationId: String,
    val createdAt: String? = null,
    val replyTo: ReplyToDto? = null,
    val author: MsgAuthorDto? = null,
    val attachments: List<AttachmentDto> = emptyList(),
)

@Serializable
data class MsgAuthorDto(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val displayFont: String? = null,
)

@Serializable
data class ReplyToDto(
    val id: String,
    val content: String = "",
    val authorName: String? = null,
    val authorAvatar: String? = null,
)

@Serializable
data class MessagesPageDto(
    val items: List<DmMessageDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class OpenDmDto(
    val conversationId: String,
    val otherUser: UserDto? = null,
)

@Serializable
data class OpenDmRequest(val username: String)

@Serializable
data class SendDmRequest(
    val content: String,
    val replyToId: String? = null,
    val attachments: List<AttachmentDto> = emptyList(),
)

// Payload do evento de socket dm_deleted (dm.ts).
@Serializable
data class DmDeletedEventDto(
    val messageId: String,
    val conversationId: String,
)
