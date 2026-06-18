package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// Mensagem de canal — shape de GET messages, POST e do evento new_message.
// Campos extras (reactions, replyTo, mentions, pinned, poll, authorColor...) caem no ignoreUnknownKeys.
@Serializable
data class ChannelMessageDto(
    val id: String,
    val content: String = "",
    val authorId: String,
    val channelId: String,
    val createdAt: String? = null,
    val edited: Boolean = false,
    val author: MsgAuthorDto? = null,
)

// GET /api/channels/:id/messages -> { data: { items, nextCursor, hasMore } }
@Serializable
data class ChannelMessagesPageDto(
    val items: List<ChannelMessageDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class SendChannelRequest(val content: String)

@Serializable
data class EditChannelRequest(val content: String)
