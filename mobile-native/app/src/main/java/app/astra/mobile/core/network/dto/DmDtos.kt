package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// GET /api/dm -> { data: [ { id, otherUser, lastMessage, updatedAt } ] }
// updatedAt e o resto sao ignorados (ignoreUnknownKeys).
@Serializable
data class ConversationDto(
    val id: String,
    val otherUser: UserDto? = null,
    val lastMessage: LastMessageDto? = null,
)

// lastMessage no GET /api/dm e a row crua (sem author). So o preview interessa.
@Serializable
data class LastMessageDto(
    val content: String = "",
    val senderId: String? = null,
)

// Mensagem de DM — shape de GET messages, POST message E do evento new_dm.
// Campos extras (attachments, replyTo, expiresAt, edited...) caem no ignoreUnknownKeys.
@Serializable
data class DmMessageDto(
    val id: String,
    val content: String = "",
    val senderId: String,
    val conversationId: String,
    val createdAt: String? = null,
    val replyTo: ReplyToDto? = null,
    val author: MsgAuthorDto? = null,
)

@Serializable
data class MsgAuthorDto(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

// Snapshot do pai citado numa resposta (canal + DM): { id, content, authorName, authorAvatar }.
@Serializable
data class ReplyToDto(
    val id: String,
    val content: String = "",
    val authorName: String? = null,
    val authorAvatar: String? = null,
)

// GET /api/dm/:id/messages -> { data: { items, nextCursor, hasMore } }
@Serializable
data class MessagesPageDto(
    val items: List<DmMessageDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

// POST /api/dm/open -> { data: { conversationId, otherUser } }
@Serializable
data class OpenDmDto(
    val conversationId: String,
    val otherUser: UserDto? = null,
)

@Serializable
data class OpenDmRequest(val username: String)

@Serializable
data class SendDmRequest(val content: String, val replyToId: String? = null)
