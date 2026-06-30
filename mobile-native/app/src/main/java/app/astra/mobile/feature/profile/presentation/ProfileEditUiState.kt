package app.astra.mobile.feature.profile.presentation

import app.astra.mobile.feature.profile.domain.model.UserStatus

data class ProfileEditUiState(
    val loading: Boolean = true,
    val displayName: String = "",
    val username: String = "",

    val status: UserStatus = UserStatus.ONLINE,
    val avatarUrl: String = "",
    val bannerUrl: String = "",
    val bio: String = "",
    val pronouns: String = "",
    val bannerColor: String = "",
    val profileTheme: String = "",
    val bannerPositionY: Int = 50,
    val bannerScale: Int = 100,
    val displayFont: String = "serif",
    val origAvatarUrl: String = "",
    val origBannerUrl: String = "",
    val origBio: String = "",
    val origPronouns: String = "",
    val origBannerColor: String = "",
    val origProfileTheme: String = "",
    val origBannerPositionY: Int = 50,
    val origBannerScale: Int = 100,
    val origDisplayFont: String = "serif",
    val uploadingAvatar: Boolean = false,
    val uploadingBanner: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val dirty: Boolean
        get() = avatarUrl != origAvatarUrl || bannerUrl != origBannerUrl || bio != origBio ||
            pronouns != origPronouns || bannerColor != origBannerColor ||
            profileTheme != origProfileTheme || bannerPositionY != origBannerPositionY ||
            bannerScale != origBannerScale || displayFont != origDisplayFont
}
