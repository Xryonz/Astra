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
    // Presenca escolhida (ONLINE/IDLE/DND/INVISIBLE). me() nao traz do servidor
    // (so /profile/:id traz effectiveStatus), entao default ONLINE; setStatus
    // atualiza o cache em memoria pra refletir na sessao.
    val status: UserStatus = UserStatus.ONLINE,
)
