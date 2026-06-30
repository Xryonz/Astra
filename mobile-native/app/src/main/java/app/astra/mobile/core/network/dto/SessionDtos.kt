package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SessionDto(
    val id: String,
    val createdAt: String? = null,
    val lastUsedAt: String? = null,
    val expiresAt: String? = null,
    val userAgent: String? = null,
    val ip: String? = null,
)

@Serializable
data class SessionsResponse(val sessions: List<SessionDto> = emptyList())

@Serializable
data class RevokeOthersRequest(val refreshToken: String)

@Serializable
data class RevokeOthersResponse(val revokedCount: Int = 0)
