package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserWrapper(val user: ProfileUserDto)

@Serializable
data class ProfileUserDto(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val pronouns: String? = null,
    val statusEmoji: String? = null,
    val hasPassword: Boolean = true,
    val createdAt: String? = null,
    val effectiveStatus: String? = null,
    val profileTheme: String? = null,
    val bannerPositionY: Int? = null,
    val bannerScale: Int? = null,
    val displayFont: String? = null,
    val onboardedAt: String? = null,
)

@Serializable
data class ProfileViewWrapper(
    val user: ProfileUserDto,
    val mutualServers: List<MutualServerDto> = emptyList(),
)

@Serializable
data class MutualServerDto(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val isGroup: Boolean = false,
    val role: String = "MEMBER",
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val username: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val pronouns: String? = null,
    val profileTheme: String? = null,
    val bannerPositionY: Int? = null,
    val bannerScale: Int? = null,
    val displayFont: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class SetPasswordRequest(
    val newPassword: String,
)

@Serializable
data class SetStatusRequest(val status: String)
