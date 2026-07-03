package app.astra.mobile.feature.profile.domain

import app.astra.mobile.feature.profile.domain.model.Profile
import app.astra.mobile.feature.profile.domain.model.ProfileView
import app.astra.mobile.feature.profile.domain.model.UserStatus

interface UserRepository {

    suspend fun me(forceRefresh: Boolean = false): Result<Profile>

    suspend fun profile(userId: String): Result<ProfileView>

    suspend fun updateProfile(
        displayName: String? = null,
        username: String? = null,
        bio: String? = null,
        avatarUrl: String? = null,
        bannerUrl: String? = null,
        bannerColor: String? = null,
        pronouns: String? = null,
        profileTheme: String? = null,
        bannerPositionY: Int? = null,
        bannerScale: Int? = null,
        displayFont: String? = null,
    ): Result<Profile>

    suspend fun changePassword(current: String, new: String): Result<Unit>

    // Primeira senha de conta Google (sem senha); backend rejeita se ja tem.
    suspend fun setPassword(new: String): Result<Unit>

    suspend fun setStatus(status: UserStatus): Result<Unit>
}
