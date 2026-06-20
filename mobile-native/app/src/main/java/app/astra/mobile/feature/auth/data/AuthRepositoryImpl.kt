package app.astra.mobile.feature.auth.data

import app.astra.mobile.core.ApiException
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.LoginRequest
import app.astra.mobile.core.network.dto.RegisterRequest
import app.astra.mobile.core.network.dto.UserDto
import app.astra.mobile.feature.auth.domain.AuthRepository
import app.astra.mobile.feature.auth.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
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
                tokenStore.save(data.accessToken, data.refreshToken)
                tokenStore.setUserId(data.user.id)
                Result.success(data.user.toDomain())
            } else {
                Result.failure(ApiException(parseError(resp.errorBody()?.string(), resp.code())))
            }
        } catch (e: IOException) {
            Result.failure(ApiException("Sem conexao com o servidor"))
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
                tokenStore.save(data.accessToken, data.refreshToken)
                tokenStore.setUserId(data.user.id)
                Result.success(data.user.toDomain())
            } else {
                // 409/400 mandam { error } amigavel no corpo; parseError le isso.
                Result.failure(ApiException(parseError(resp.errorBody()?.string(), resp.code())))
            }
        } catch (e: IOException) {
            Result.failure(ApiException("Sem conexao com o servidor"))
        } catch (e: Exception) {
            Result.failure(ApiException("Erro inesperado"))
        }
    }

    override suspend fun logout() {
        tokenStore.clear()
    }

    // Le a mensagem amigavel do backend; se nao der, cai num fallback por status.
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
