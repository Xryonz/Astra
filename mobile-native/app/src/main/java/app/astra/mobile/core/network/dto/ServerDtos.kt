package app.astra.mobile.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// GET /api/servers -> { data: [ { id, name, iconUrl, channels[], _count:{members} } ] }
@Serializable
data class ServerDto(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val channels: List<ChannelDto> = emptyList(),
    @SerialName("_count") val count: ServerCountDto? = null,
)

@Serializable
data class ChannelDto(
    val id: String,
    val name: String,
    val type: String = "TEXT", // TEXT | VOICE
    val isPrivate: Boolean = false,
)

@Serializable
data class ServerCountDto(val members: Int = 0)

@Serializable
data class CreateServerRequest(val name: String)
