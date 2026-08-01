package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class FriendUserDto(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val customStatus: String? = null,
)

// Quem EU bloqueei. Nao existe o contrario: quem foi bloqueado nao fica sabendo,
// entao nao ha lista "quem me bloqueou" pra pedir.
@Serializable
data class BlockedUserDto(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val blockedAt: String? = null,
)

@Serializable
data class FriendDto(
    val friendshipId: String,
    val user: FriendUserDto,
    val presence: String = "OFFLINE",
    val since: String? = null,
)

@Serializable
data class FriendRequestDto(
    val friendshipId: String,
    val user: FriendUserDto? = null,
    val createdAt: String? = null,
)

@Serializable
data class SendFriendRequest(val username: String)
