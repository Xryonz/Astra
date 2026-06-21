package app.astra.mobile.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// GET /api/servers -> { data: [ { id, name, iconUrl, channels[], _count:{members} } ] }
@Serializable
data class ServerDto(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val ownerId: String? = null, // GET /api/servers ja devolve (select * em servers)
    val inviteCode: String? = null,
    val channels: List<ChannelDto> = emptyList(),
    @SerialName("_count") val count: ServerCountDto? = null,
)

@Serializable
data class ChannelDto(
    val id: String,
    val name: String,
    val type: String = "TEXT", // TEXT | VOICE
    val isPrivate: Boolean = false,
    val lastMessageAt: String? = null, // ISO; null = canal sem mensagens
)

@Serializable
data class ServerCountDto(val members: Int = 0)

@Serializable
data class CreateServerRequest(val name: String)

// PATCH /api/servers/:id — campos null omitidos (explicitNulls=false).
@Serializable
data class UpdateServerRequest(
    val name: String? = null,
    val iconUrl: String? = null,
)

// GET /api/servers/:id/members -> { data: [ { userId, user:{username,displayName,avatarUrl} } ] }
// (ignora role/nameColor/roles[] etc via ignoreUnknownKeys)
@Serializable
data class ServerMemberDto(
    val userId: String,
    val user: MemberUserDto,
)

@Serializable
data class MemberUserDto(
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)
