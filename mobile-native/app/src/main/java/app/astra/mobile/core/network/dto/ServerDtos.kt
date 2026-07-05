package app.astra.mobile.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerDto(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val ownerId: String? = null,
    val inviteCode: String? = null,
    val isPublic: Boolean = false,
    val isGroup: Boolean = false,
    val channels: List<ChannelDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    @SerialName("_count") val count: ServerCountDto? = null,
)

@Serializable
data class ChannelDto(
    val id: String,
    val name: String,
    val type: String = "TEXT",
    val isPrivate: Boolean = false,
    val categoryId: String? = null,
    val position: Int = 0,
    val lastMessageAt: String? = null,
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val position: Int = 0,
)

@Serializable
data class ServerCountDto(val members: Int = 0)

@Serializable
data class CreateServerRequest(val name: String, val isGroup: Boolean = false)

@Serializable
data class UpdateServerRequest(
    val name: String? = null,
    val iconUrl: String? = null,
    val isPublic: Boolean? = null,
)

@Serializable
data class CreateChannelRequest(val name: String, val type: String = "TEXT")

@Serializable
data class ServerMemberDto(
    val id: String = "",
    val userId: String,
    val role: String = "MEMBER",
    val nameColor: String? = null,
    val user: MemberUserDto,
    val roles: List<MemberRoleDto> = emptyList(),
    val topColor: String? = null,
)

@Serializable
data class MemberRoleDto(
    val id: String,
    val name: String,
    val color: String? = null,
    val position: Int = 0,
    val hoist: Boolean = false,
)

@Serializable
data class MemberUserDto(
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class MyPermsDto(
    val isOwner: Boolean = false,
    val isAdmin: Boolean = false,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class MemberRoleRequest(val role: String)

@Serializable
data class MemberRoleResponse(val id: String, val role: String)

@Serializable
data class BanRequest(val userId: String, val reason: String? = null)

@Serializable
data class BanDto(
    val id: String,
    val userId: String,
    val reason: String? = null,
    val createdAt: String? = null,
    val user: MemberUserDto,
)

@Serializable
data class RoleDto(
    val id: String,
    val name: String,
    val color: String? = null,
    val position: Int = 0,
    val hoist: Boolean = false,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class RoleRequest(
    val name: String,
    val color: String? = null,
    val permissions: List<String> = emptyList(),
    val hoist: Boolean = false,
)
