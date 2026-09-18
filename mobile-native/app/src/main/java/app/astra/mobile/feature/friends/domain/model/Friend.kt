package app.astra.mobile.feature.friends.domain.model

enum class Presence { ONLINE, IDLE, DND, OFFLINE }

data class Friend(
    val friendshipId: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val presence: Presence,
    val customStatus: String?,
)

data class FriendRequest(
    val friendshipId: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
)
