package app.astra.mobile.feature.profile.domain

import app.astra.mobile.feature.profile.domain.model.Profile
import app.astra.mobile.feature.profile.domain.model.ProfileView
import app.astra.mobile.feature.profile.domain.model.UserStatus

interface UserRepository {
    // Perfil do usuario logado. Cacheia em memoria (Singleton) — forceRefresh
    // recarrega do servidor.
    suspend fun me(forceRefresh: Boolean = false): Result<Profile>

    // Perfil publico de outro usuario (+ presenca + servidores em comum).
    suspend fun profile(userId: String): Result<ProfileView>

    // Campos null = nao mexer; "" limpa. Devolve o perfil atualizado.
    suspend fun updateProfile(
        displayName: String? = null,
        username: String? = null,
        bio: String? = null,
        avatarUrl: String? = null,
        bannerUrl: String? = null,
        bannerColor: String? = null,
        pronouns: String? = null,
    ): Result<Profile>

    suspend fun changePassword(current: String, new: String): Result<Unit>

    // Define o status escolhido (ONLINE/IDLE/DND/INVISIBLE). Persiste no servidor.
    suspend fun setStatus(status: UserStatus): Result<Unit>
}
