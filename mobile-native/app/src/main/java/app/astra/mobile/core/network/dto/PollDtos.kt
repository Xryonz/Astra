package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PollOptionDto(
    val id: String,
    val text: String = "",
    val votes: List<String> = emptyList(),
)

@Serializable
data class PollDto(
    val question: String = "",
    val options: List<PollOptionDto> = emptyList(),
    val allowMultiple: Boolean = false,
    val expiresAt: String? = null,
    val closed: Boolean = false,
)

@Serializable
data class CreatePollRequest(
    val question: String,
    val options: List<String>,
    val allowMultiple: Boolean = false,
    val durationHours: Int? = null,
)

@Serializable
data class VoteRequest(val optionId: String)

@Serializable
data class PollUpdateDto(
    val messageId: String,
    val channelId: String = "",
    val poll: PollDto,
)
