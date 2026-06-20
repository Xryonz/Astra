package app.astra.mobile.feature.profile.domain.model

// Perfil do usuario (proprio ou de terceiro). displayName cai pro username
// quando nulo. createdAt e ISO-8601 ("membro desde").
data class Profile(
    val id: String,
    val username: String,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val bio: String?,
    val bannerUrl: String?,
    val bannerColor: String?,
    val pronouns: String?,
    val statusEmoji: String?,
    val hasPassword: Boolean,
    val createdAt: String?,
)
