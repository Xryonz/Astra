package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val data: T? = null,
    val error: String? = null,
    val code: String? = null,
)

@Serializable
data class ApiError(
    val error: String? = null,
    val code: String? = null,
    // Quanto falta pra sair do silenciamento (429 MUTED/SPAM_MUTED).
    val secondsLeft: Int? = null,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val displayName: String,
    val password: String,
)

@Serializable
data class VerifyEmailRequest(
    val code: String,
)

@Serializable
data class AuthData(
    val user: UserDto,
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class RefreshData(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String? = null,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)
