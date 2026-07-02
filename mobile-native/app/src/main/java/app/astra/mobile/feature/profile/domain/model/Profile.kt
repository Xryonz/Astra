package app.astra.mobile.feature.profile.domain.model

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

    val status: UserStatus = UserStatus.ONLINE,
    val profileTheme: String? = null,
    val bannerPositionY: Int = 50,
    val bannerScale: Int = 100,
    val displayFont: String = "serif",
    // null = nunca viu o onboarding cosmico (gate compartilhado com o web).
    val onboardedAt: String? = null,
)
