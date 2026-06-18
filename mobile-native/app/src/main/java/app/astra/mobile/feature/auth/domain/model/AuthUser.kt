package app.astra.mobile.feature.auth.domain.model

// Modelo de dominio (limpo, sem anotacoes de serializacao). A presentation
// fala com isto, nunca com o UserDto da rede.
data class AuthUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
)
