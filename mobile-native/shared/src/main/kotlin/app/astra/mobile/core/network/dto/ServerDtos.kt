package app.astra.mobile.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerDto(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerPositionY: Int = 50,
    val bannerScale: Int = 100,
    val iconScale: Int = 100,
    val description: String? = null,
    val messageRetentionDays: Int? = null,
    val ownerId: String? = null,
    val inviteCode: String? = null,
    val isPublic: Boolean = false,
    val isGroup: Boolean = false,
    val botNoticeChannelId: String? = null,
    val botDisabledCommands: String? = null,
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
    val botEnabled: Boolean? = null,
    val botKeepReplies: Boolean = true,
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val position: Int = 0,
    val botEnabled: Boolean? = null,
)

@Serializable
data class ServerCountDto(val members: Int = 0, val online: Int = 0)

@Serializable
data class CreateServerRequest(val name: String, val isGroup: Boolean = false)

@Serializable
data class UpdateServerRequest(
    val name: String? = null,
    val iconUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerPositionY: Int? = null,
    val bannerScale: Int? = null,
    val iconScale: Int? = null,
    val description: String? = null,
    val messageRetentionDays: Int? = null,
    val isPublic: Boolean? = null,
    val botNoticeChannelId: String? = null,
    val botDisabledCommands: List<String>? = null,
)

@Serializable
data class InviteCodeResponse(val inviteCode: String)

@Serializable
data class EmojiDto(
    val id: String,
    val serverId: String = "",
    val name: String,
    val url: String,
)

@Serializable
data class RenameEmojiRequest(val name: String)

@Serializable
data class ChannelVisibilityDto(
    val isPrivate: Boolean = false,
    val roleIds: List<String> = emptyList(),
)

@Serializable
data class ChannelVisibilityRequest(
    val isPrivate: Boolean,
    val roleIds: List<String> = emptyList(),
)

@Serializable
data class UpdateChannelNameRequest(val name: String)

@Serializable
data class UpdateChannelBotRequest(val botEnabled: Boolean)

@Serializable
data class UpdateChannelKeepRequest(val botKeepReplies: Boolean)

@Serializable
data class MoveChannelRequest(val position: Int, val categoryId: String? = null)

@Serializable
data class CreateChannelRequest(
    val name: String,
    val type: String = "TEXT",
    val categoryId: String? = null,
)

@Serializable
data class CreateCategoryRequest(val name: String)

@Serializable
data class UpdateCategoryRequest(
    val name: String? = null,
    val position: Int? = null,
    val botEnabled: Boolean? = null,
)

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
    val iconUrl: String? = null,
    val position: Int = 0,
    val hoist: Boolean = false,
)

@Serializable
data class MemberUserDto(
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val displayFont: String? = null,
)

@Serializable
data class PresenceUpdateDto(val userId: String, val status: String = "OFFLINE")

@Serializable
data class ProfileUpdatedDto(
    val userId: String,
    val publico: MemberUserDto? = null,
)

@Serializable
data class MembroEntrouDto(
    val serverId: String,
    val membro: ServerMemberDto,
    val presenca: String = "OFFLINE",
)

@Serializable
data class MembroSaiuDto(val serverId: String, val userId: String)

@Serializable
data class MembroMudouDeCargoDto(val serverId: String, val memberId: String, val role: String)

@Serializable
data class ActivityUpdateDto(
    val userId: String,
    val activity: String? = null,
    val since: Long? = null,
)

@Serializable
data class AtividadeDto(val text: String, val since: Long = 0L)

@Serializable
data class ServerScopedEventDto(
    val serverId: String,
    val motivo: String = "saiu",
    val reason: String? = null,
)

@Serializable
data class BotCommandDto(
    val name: String,
    val description: String = "",
    val category: String = "",
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
    val iconUrl: String? = null,
    val position: Int = 0,
    val hoist: Boolean = false,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class RoleRequest(
    val name: String,
    val color: String? = null,
    val iconUrl: String? = null,
    val permissions: List<String> = emptyList(),
    val hoist: Boolean = false,
)
