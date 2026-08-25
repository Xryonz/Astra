package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserBadgesDto(
    val global: List<BadgeDto> = emptyList(),
    val server: List<ServerGrantedBadgeDto> = emptyList(),
)

@Serializable
data class BadgeDto(
    val id: String,
    val name: String,
    val icon: String,
    val color: String? = null,
    val description: String? = null,
)

@Serializable
data class ServerGrantedBadgeDto(
    val badgeId: String,
    val name: String,
    val icon: String,
    val color: String? = null,
    val description: String? = null,
    val serverId: String,
    val serverName: String? = null,
)

@Serializable
data class ServerBadgeDto(
    val id: String,
    val serverId: String,
    val name: String,
    val icon: String,
    val color: String? = null,
    val description: String? = null,
    val grantedUserIds: List<String> = emptyList(),
)

@Serializable
data class CreateBadgeRequest(
    val name: String,
    val icon: String,
    val color: String? = null,
    val description: String? = null,
)

@Serializable
data class GrantBadgeRequest(
    val userId: String,
)

@Serializable
data class CustomStatusRequest(
    val customStatus: String,
)
