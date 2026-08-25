package app.astra.desktop.auth

import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.RefreshApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.LoginRequest
import app.astra.mobile.core.network.dto.RegisterRequest
import app.astra.mobile.core.network.dto.LogoutRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.IOException

class AuthRepository(
    private val api: AuthApi,
    private val store: SessionStore,
    private val json: Json,
    private val socket: DesktopSocket,
    private val refreshApi: RefreshApi,
    private val userApi: UserApi,
) {
    suspend fun login(email: String, password: String): Result<Session> = try {
        val resp = api.login(LoginRequest(email.trim(), password))
        val body = resp.body()
        val data = body?.data
        if (resp.isSuccessful && data != null) {
            val session = Session(
                accessToken = data.accessToken,
                refreshToken = data.refreshToken,
                userId = data.user.id,
                displayName = data.user.displayName ?: data.user.username,
            )
            store.save(session)
            Result.success(session)
        } else {
            val msg = resp.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull()
            } ?: body?.error ?: "Não foi possível entrar"
            Result.failure(Exception(msg))
        }
    } catch (e: IOException) {
        Result.failure(Exception("Sem conexão com o servidor"))
    } catch (e: Exception) {
        Result.failure(Exception("Não foi possível entrar"))
    }

    suspend fun register(
        email: String,
        username: String,
        displayName: String,
        password: String,
    ): Result<Session> = try {
        val resp = api.register(RegisterRequest(email.trim(), username.trim(), displayName.trim(), password))
        val body = resp.body()
        val data = body?.data
        if (resp.isSuccessful && data != null) {
            val session = Session(
                accessToken = data.accessToken,
                refreshToken = data.refreshToken,
                userId = data.user.id,
                displayName = data.user.displayName ?: data.user.username,
            )
            store.save(session)
            Result.success(session)
        } else {
            val msg = resp.errorBody()?.string()?.let {
                runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull()
            } ?: body?.error ?: "Não foi possível criar a conta"
            Result.failure(Exception(msg))
        }
    } catch (e: IOException) {
        Result.failure(Exception("Sem conexão com o servidor"))
    } catch (e: Exception) {
        Result.failure(Exception("Não foi possível criar a conta"))
    }

    suspend fun loginWithGoogle(): Result<Session> = try {
        val token = GoogleAuthFlow.captureRefreshToken().getOrElse { return Result.failure(it) }
        val refreshed = refreshApi.refresh("Bearer $token").data
            ?: return Result.failure(Exception("Login Google invalido"))
        store.save(Session(refreshed.accessToken, refreshed.refreshToken, "", ""))
        val me = runCatching { userApi.me().data?.user }.getOrNull()
        if (me == null) {
            store.clear()
            return Result.failure(Exception("Não foi possível carregar seu perfil"))
        }
        val session = Session(
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken,
            userId = me.id,
            displayName = me.displayName ?: me.username,
        )
        store.save(session)
        Result.success(session)
    } catch (e: IOException) {
        runCatching { store.clear() }
        Result.failure(Exception("Sem conexão com o servidor"))
    } catch (e: Exception) {
        runCatching { store.clear() }
        Result.failure(Exception(e.message ?: "Não foi possível entrar com Google"))
    }

    fun logout(escopo: CoroutineScope) {
        runCatching { socket.disconnect() }
        val refresh = store.load()?.refreshToken
        if (refresh.isNullOrBlank()) {
            store.clear()
            return
        }
        escopo.launch {
            try {
                withTimeoutOrNull(3_000) { runCatching { api.logout(LogoutRequest(refresh)) } }
            } finally {
                store.clear()
            }
        }
    }
}
