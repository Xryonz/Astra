package app.astra.mobile.feature.auth.data

import kotlinx.coroutines.CancellationException
import app.astra.mobile.core.ApiException
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.db.MessageDao
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.RefreshApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.LoginRequest
import app.astra.mobile.core.network.dto.RegisterRequest
import app.astra.mobile.core.network.dto.UserDto
import app.astra.mobile.feature.auth.domain.AuthRepository
import app.astra.mobile.feature.auth.domain.model.AuthUser
import app.astra.mobile.feature.profile.domain.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val refreshApi: RefreshApi,
    private val tokenStore: TokenStore,
    private val messageDao: MessageDao,
    private val userRepository: UserRepository,
    private val json: Json,
) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> =
        tokenStore.refreshToken.map { !it.isNullOrBlank() }

    override suspend fun login(email: String, password: String): Result<AuthUser> {
        return try {
            val resp = authApi.login(LoginRequest(email.trim(), password))
            if (resp.isSuccessful) {
                val data = resp.body()?.data
                    ?: return Result.failure(ApiException("Resposta invalida do servidor"))
                userRepository.clearCache()
                tokenStore.save(data.accessToken, data.refreshToken)
                tokenStore.setUserId(data.user.id)
                Result.success(data.user.toDomain())
            } else {
                Result.failure(ApiException(parseError(resp.errorBody()?.string(), resp.code())))
            }
        } catch (e: IOException) {
            Result.failure(ApiException("Sem conexao com o servidor"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(ApiException("Erro inesperado"))
        }
    }

    override suspend fun register(
        displayName: String,
        username: String,
        email: String,
        password: String,
    ): Result<AuthUser> {
        return try {
            val resp = authApi.register(
                RegisterRequest(
                    email = email.trim(),
                    username = username.trim(),
                    displayName = displayName.trim(),
                    password = password,
                ),
            )
            if (resp.isSuccessful) {
                val data = resp.body()?.data
                    ?: return Result.failure(ApiException("Resposta invalida do servidor"))
                userRepository.clearCache()
                tokenStore.save(data.accessToken, data.refreshToken)
                tokenStore.setUserId(data.user.id)
                Result.success(data.user.toDomain())
            } else {

                Result.failure(ApiException(parseError(resp.errorBody()?.string(), resp.code())))
            }
        } catch (e: IOException) {
            Result.failure(ApiException("Sem conexao com o servidor"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(ApiException("Erro inesperado"))
        }
    }

    override suspend fun completeGoogleLogin(refreshToken: String): Result<Unit> {
        return try {

            val data = refreshApi.refresh("Bearer $refreshToken").data
                ?: return Result.failure(ApiException("Resposta invalida do servidor"))
            userRepository.clearCache()
            tokenStore.save(data.accessToken, data.refreshToken)

            userIdFromJwt(data.accessToken)?.let { tokenStore.setUserId(it) }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(ApiException("Sem conexao com o servidor"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(ApiException("Nao foi possivel entrar com o Google"))
        }
    }

    private fun userIdFromJwt(accessToken: String): String? = runCatching {
        val payload = accessToken.split(".")[1]
        val bytes = android.util.Base64.decode(
            payload,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        json.decodeFromString<JwtPayload>(String(bytes, Charsets.UTF_8)).userId
    }.getOrNull()

    override suspend fun logout() {
        tokenStore.clear()
        userRepository.clearCache()
        messageDao.clearAll()
    }

    private fun parseError(raw: String?, code: Int): String {
        val fromBody = raw?.let {
            runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull()
        }
        return fromBody ?: when (code) {
            401 -> "E-mail ou senha incorretos"
            409 -> "E-mail ou username ja esta em uso"
            429 -> "Muitas tentativas. Tente em instantes."
            else -> "Nao foi possivel concluir"
        }
    }
}

private fun UserDto.toDomain() = AuthUser(
    id = id,
    username = username,
    displayName = displayName ?: username,
    avatarUrl = avatarUrl,
)

@Serializable
private data class JwtPayload(val userId: String? = null)
