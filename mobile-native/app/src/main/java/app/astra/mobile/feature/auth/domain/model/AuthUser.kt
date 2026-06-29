package app.astra.mobile.feature.auth.domain.model

data class AuthUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
)
