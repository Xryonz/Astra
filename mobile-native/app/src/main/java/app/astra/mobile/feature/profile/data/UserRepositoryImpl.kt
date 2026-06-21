package app.astra.mobile.feature.profile.data

import app.astra.mobile.core.ApiException
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.ChangePasswordRequest
import app.astra.mobile.core.network.dto.MutualServerDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.SetStatusRequest
import app.astra.mobile.core.network.dto.UpdateProfileRequest
import app.astra.mobile.feature.friends.domain.model.Presence
import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.feature.profile.domain.model.MutualServer
import app.astra.mobile.feature.profile.domain.model.Profile
import app.astra.mobile.feature.profile.domain.model.ProfileView
import app.astra.mobile.feature.profile.domain.model.UserStatus
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val api: UserApi,
    private val json: Json,
) : UserRepository {

    // Cache em memoria do proprio perfil (espelha o authStore.user do web).
    private var cached: Profile? = null

    override suspend fun me(forceRefresh: Boolean): Result<Profile> {
        cached?.let { if (!forceRefresh) return Result.success(it) }
        return try {
            val data = api.me().data
                ?: return Result.failure(ApiException("Resposta vazia do servidor"))
            val p = data.user.toDomain()
            cached = p
            Result.success(p)
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    override suspend fun profile(userId: String): Result<ProfileView> {
        return try {
            val data = api.profile(userId).data
                ?: return Result.failure(ApiException("Resposta vazia do servidor"))
            Result.success(
                ProfileView(
                    profile = data.user.toDomain(),
                    presence = parsePresence(data.user.effectiveStatus),
                    mutual = data.mutualServers.map { it.toDomain() },
                ),
            )
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    override suspend fun updateProfile(
        displayName: String?,
        username: String?,
        bio: String?,
        avatarUrl: String?,
        bannerUrl: String?,
        bannerColor: String?,
        pronouns: String?,
    ): Result<Profile> {
        return try {
            val data = api.updateProfile(
                UpdateProfileRequest(displayName, username, bio, avatarUrl, bannerUrl, bannerColor, pronouns),
            ).data ?: return Result.failure(ApiException("Resposta vazia do servidor"))
            val p = data.user.toDomain()
            cached = p
            Result.success(p)
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    override suspend fun changePassword(current: String, new: String): Result<Unit> {
        return try {
            api.changePassword(ChangePasswordRequest(current, new))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    override suspend fun setStatus(status: UserStatus): Result<Unit> {
        return try {
            api.setStatus(SetStatusRequest(status.name))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    private fun mapError(e: Exception): ApiException = when (e) {
        is ApiException -> e
        is IOException -> ApiException("Sem conexao com o servidor")
        is HttpException -> ApiException(parseError(e.response()?.errorBody()?.string(), e.code()))
        else -> ApiException("Erro inesperado")
    }

    private fun parseError(raw: String?, code: Int): String {
        val fromBody = raw?.let {
            runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull()
        }
        return fromBody ?: when (code) {
            401 -> "Senha atual incorreta"
            409 -> "Username ja esta em uso"
            else -> "Nao foi possivel salvar"
        }
    }
}

private fun ProfileUserDto.toDomain() = Profile(
    id = id,
    username = username,
    displayName = displayName ?: username,
    email = email,
    avatarUrl = avatarUrl,
    bio = bio,
    bannerUrl = bannerUrl,
    bannerColor = bannerColor,
    pronouns = pronouns,
    statusEmoji = statusEmoji,
    hasPassword = hasPassword,
    createdAt = createdAt,
)

private fun MutualServerDto.toDomain() = MutualServer(id, name, iconUrl, isGroup, role)

private fun parsePresence(s: String?): Presence = when (s?.uppercase()) {
    "ONLINE" -> Presence.ONLINE
    "IDLE" -> Presence.IDLE
    "DND" -> Presence.DND
    else -> Presence.OFFLINE
}
