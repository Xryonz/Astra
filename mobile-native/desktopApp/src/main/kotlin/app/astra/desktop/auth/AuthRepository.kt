package app.astra.desktop.auth

import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.LoginRequest
import kotlinx.serialization.json.Json
import java.io.IOException

// Login por email/senha (Google OAuth fica pra uma fatia futura — precisa de
// loopback local). Persiste a sessao no SessionStore.
class AuthRepository(
    private val api: AuthApi,
    private val store: SessionStore,
    private val json: Json,
    private val socket: DesktopSocket,
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
            } ?: body?.error ?: "Nao foi possivel entrar"
            Result.failure(Exception(msg))
        }
    } catch (e: IOException) {
        Result.failure(Exception("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(Exception("Nao foi possivel entrar"))
    }

    // Fecha o socket ANTES de limpar a sessao. Sem isso, o DesktopSocket (que e
    // single do Koin, vive o processo inteiro) continua conectado com o token
    // antigo; o connect() da proxima conta ve connected()==true e volta sem
    // refazer o handshake — o servidor segue te tratando como a conta anterior.
    fun logout() {
        socket.disconnect()
        store.clear()
    }
}
