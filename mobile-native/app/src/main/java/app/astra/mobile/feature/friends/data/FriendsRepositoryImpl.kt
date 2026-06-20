package app.astra.mobile.feature.friends.data

import app.astra.mobile.core.ApiException
import app.astra.mobile.core.network.FriendsApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.FriendDto
import app.astra.mobile.core.network.dto.FriendRequestDto
import app.astra.mobile.core.network.dto.SendFriendRequest
import app.astra.mobile.feature.friends.domain.FriendsRepository
import app.astra.mobile.feature.friends.domain.model.Friend
import app.astra.mobile.feature.friends.domain.model.FriendRequest
import app.astra.mobile.feature.friends.domain.model.Presence
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendsRepositoryImpl @Inject constructor(
    private val api: FriendsApi,
    private val json: Json,
) : FriendsRepository {

    override suspend fun friends(): Result<List<Friend>> =
        guard { api.friends().data.orEmpty().map { it.toDomain() } }

    override suspend fun incoming(): Result<List<FriendRequest>> =
        guard { api.incoming().data.orEmpty().mapNotNull { it.toRequest() } }

    override suspend fun outgoing(): Result<List<FriendRequest>> =
        guard { api.outgoing().data.orEmpty().mapNotNull { it.toRequest() } }

    override suspend fun sendRequest(username: String): Result<Unit> =
        guard { api.sendRequest(SendFriendRequest(username.trim())) }

    override suspend fun accept(friendshipId: String): Result<Unit> =
        guard { api.accept(friendshipId) }

    override suspend fun remove(friendshipId: String): Result<Unit> =
        guard { api.remove(friendshipId) }

    private suspend fun <T> guard(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(mapError(e))
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
            404 -> "Usuario nao encontrado"
            else -> "Nao foi possivel concluir"
        }
    }
}

private fun FriendDto.toDomain() = Friend(
    friendshipId = friendshipId,
    userId = user.id,
    username = user.username,
    displayName = user.displayName ?: user.username,
    avatarUrl = user.avatarUrl,
    presence = when (presence.uppercase()) {
        "ONLINE" -> Presence.ONLINE
        "IDLE" -> Presence.IDLE
        "DND" -> Presence.DND
        else -> Presence.OFFLINE
    },
    customStatus = user.customStatus,
)

private fun FriendRequestDto.toRequest(): FriendRequest? {
    val u = user ?: return null
    return FriendRequest(
        friendshipId = friendshipId,
        userId = u.id,
        username = u.username,
        displayName = u.displayName ?: u.username,
        avatarUrl = u.avatarUrl,
    )
}
