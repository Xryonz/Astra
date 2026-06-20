package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// Backend embrulha tudo em { data: ... } ou { error, code } no erro.
@Serializable
data class ApiEnvelope<T>(
    val data: T? = null,
    val error: String? = null,
    val code: String? = null,
)

// So o pedaco de erro — usado pra ler errorBody() de respostas 4xx/5xx.
@Serializable
data class ApiError(
    val error: String? = null,
    val code: String? = null,
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

// Subconjunto do user que o M3 usa. ignoreUnknownKeys=true cobre o resto
// (bio, banner, pronouns...) que chega do backend e o cliente ignora por ora.
@Serializable
data class UserDto(
    val id: String,
    val email: String? = null,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)
