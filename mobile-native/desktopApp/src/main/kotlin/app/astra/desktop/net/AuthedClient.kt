package app.astra.desktop.net

import app.astra.desktop.auth.SessionStore
import app.astra.mobile.core.network.RefreshApi
import coil3.map.Mapper
import coil3.request.Options
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.nio.ByteBuffer
import java.util.Base64

// Anexa o Bearer da sessão em toda request (espelho do AuthInterceptor do Android).
class AuthInterceptor(private val store: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = store.load()?.accessToken ?: return chain.proceed(chain.request())
        return chain.proceed(
            chain.request().newBuilder().header("Authorization", "Bearer $token").build(),
        )
    }
}

// Manda o X-Device-Id em TODA request — nos DOIS clients (plain e authed). Precisa
// estar no plain porque login/register/refresh rodam la (sem Bearer), e são esses
// que criam a sessão. Backend deduplica sessões do mesmo PC por esse id (#4).
class DeviceInterceptor(private val store: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder().header("X-Device-Id", store.deviceId()).build(),
        )
}

// 401 -> renova com o refresh token e repete a request. SINGLE-FLIGHT (mesma
// logica do TokenRefresher do Android): o boot dispara varias chamadas em
// paralelo e o refresh e single-use no backend — sem o lock, todas tentavam
// rotacionar o MESMO token, so a primeira vencia e as outras matavam a sessão
// (bug do "so o nome do usuário carrega").
class DesktopTokenAuthenticator(
    private val store: SessionStore,
    private val refreshApi: Lazy<RefreshApi>,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null // já tentou renovar
        val staleAuth = response.request.header("Authorization")
        synchronized(lock) {
            val session = store.load() ?: return null
            // Outro fio renovou enquanto esperavamos o lock: repete com o novo.
            val currentAuth = "Bearer ${session.accessToken}"
            if (staleAuth != null && staleAuth != currentAuth) {
                return response.request.newBuilder().header("Authorization", currentAuth).build()
            }
            val renewed = runBlocking {
                try {
                    refreshApi.value.refresh("Bearer ${session.refreshToken}").data
                } catch (e: HttpException) {
                    // Refresh rejeitado de verdade = sessão morta.
                    if (e.code() == 401 || e.code() == 403) store.clear()
                    null
                } catch (e: Exception) {
                    null // rede caiu/timeout: não desloga por isso
                }
            } ?: return null
            store.save(session.copy(accessToken = renewed.accessToken, refreshToken = renewed.refreshToken))
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${renewed.accessToken}")
                .build()
        }
    }
}

// Avatares/ícones do Astra vivem como data-URIs no banco -> Coil precisa deste
// mapper (porta do CoilMappers do Android, Base64 do java.util).
class DataUriMapper : Mapper<String, ByteBuffer> {
    override fun map(data: String, options: Options): ByteBuffer? {
        if (!data.startsWith("data:")) return null
        val idx = data.indexOf("base64,")
        if (idx < 0) return null
        val b64 = data.substring(idx + 7)
        val bytes = runCatching { Base64.getDecoder().decode(b64) }.getOrNull() ?: return null
        return ByteBuffer.wrap(bytes)
    }
}

class RelativeUrlMapper(private val base: String) : Mapper<String, String> {
    override fun map(data: String, options: Options): String? =
        if (data.startsWith("/")) base.trimEnd('/') + data else null
}
