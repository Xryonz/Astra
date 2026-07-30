package app.astra.desktop.auth

import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.RefreshApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.LoginRequest
import app.astra.mobile.core.network.dto.RegisterRequest
import kotlinx.serialization.json.Json
import java.io.IOException

// Login por email/senha (Google OAuth fica pra uma fatia futura — precisa de
// loopback local). Persiste a sessão no SessionStore.
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

    // Cria a conta e já loga (o backend responde 201 com os tokens, mesmo shape do
    // login). Email de verificacao esta desligado no Render -> a conta nasce pronta.
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

    // Login com Google via loopback (GoogleAuthFlow abre o navegador). Volta um
    // refresh token; troca-o por um access (o /refresh so devolve tokens, sem user),
    // salva sessão provisoria pra o interceptor autenticar o /me, e so entao monta a
    // sessão completa. Qualquer falha depois do save provisorio limpa o disco pra o
    // app não reabrir numa sessão meia-boca (userId vazio quebraria o shell).
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

    // Limpa a sessão PRIMEIRO — e a parte critica: apaga o session.bin do disco,
    // senao reabrir o app loga de novo na conta que "saiu". So depois desconecta o
    // socket, e defensivo: se disconnect() estourasse, não pode levar o clear()
    // junto (era esse o bug — a sessão sobrevivia ao logout). O socket precisa cair
    // mesmo: e single do Koin, e sem desconectar o connect() da próxima conta ve
    // connected()==true e o servidor segue te tratando como a conta anterior.
    fun logout() {
        store.clear()
        runCatching { socket.disconnect() }
    }
}
