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
    val pinned: Boolean = false,
    val reactions: List<ReactionDto> = emptyList(),
    val replyTo: ReplyToDto? = null,
    val author: MsgAuthorDto? = null,
)

// Resumo de reacao: { emoji, count, users[] }. mine = uid esta em users.
@Serializable
data class ReactionDto(
    val emoji: String,
    val count: Int = 0,
    val users: List<String> = emptyList(),
)

// Resposta REST do toggle de reacao: { action, reactions[] }. Aplicada de forma
// otimista no cache (o socket reaction_update reaplica o mesmo resumo).
@Serializable
data class ReactResultDto(
    val action: String = "",
    val reactions: List<ReactionDto> = emptyList(),
)

// Evento socket reaction_update: { messageId, channelId, reactions[] }.
@Serializable
data class ReactionUpdateDto(
    val messageId: String,
    val channelId: String,
    val reactions: List<ReactionDto> = emptyList(),
)

// GET /api/channels/:id/messages -> { data: { items, nextCursor, hasMore } }
@Serializable
data class ChannelMessagesPageDto(
    val items: List<ChannelMessageDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class SendChannelRequest(val content: String, val replyToId: String? = null)

@Serializable
data class EditChannelRequest(val content: String)

@Serializable
data class ReactRequest(val emoji: String)
