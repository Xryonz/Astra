package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// GET /api/auth/me e PATCH /api/profile devolvem { data: { user: {...} } }.
@Serializable
data class UserWrapper(val user: ProfileUserDto)

// Subconjunto do perfil que o app usa. ignoreUnknownKeys cobre o resto
// (profileTheme, banner*, statusEmoji extras...) que o cliente ignora por ora.
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
    val effectiveStatus: String? = null, // so vem no GET /api/profile/:id
)

// GET /api/profile/:id -> { user, mutualServers }
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

// PATCH /api/profile — explicitNulls=false (NetworkModule) omite campos null,
// que o backend trata como "nao mexer". String vazia ("") limpa o campo.
@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val username: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val bannerColor: String? = null,
    val pronouns: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

// PATCH /api/profile/status — ONLINE | IDLE | DND | INVISIBLE
@Serializable
data class SetStatusRequest(val status: String)
