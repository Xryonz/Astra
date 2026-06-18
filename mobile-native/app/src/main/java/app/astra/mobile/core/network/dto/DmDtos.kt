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
