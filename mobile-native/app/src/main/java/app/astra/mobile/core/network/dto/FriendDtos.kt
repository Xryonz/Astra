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

// GET /api/friends
@Serializable
data class FriendDto(
    val friendshipId: String,
    val user: FriendUserDto,
    val presence: String = "OFFLINE",
    val since: String? = null,
)

// GET /api/friends/requests e /outgoing (user pode vir null se o registro sumiu).
@Serializable
data class FriendRequestDto(
    val friendshipId: String,
    val user: FriendUserDto? = null,
    val createdAt: String? = null,
)

@Serializable
data class SendFriendRequest(val username: String)
