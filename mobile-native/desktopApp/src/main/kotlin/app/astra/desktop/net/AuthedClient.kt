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
import java.nio.ByteBuffer
import java.util.Base64

// Anexa o Bearer da sessao em toda request (espelho do AuthInterceptor do Android).
class AuthInterceptor(private val store: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = store.load()?.accessToken ?: return chain.proceed(chain.request())
        return chain.proceed(
            chain.request().newBuilder().header("Authorization", "Bearer $token").build(),
        )
    }
}

// 401 -> tenta UMA vez renovar com o refresh token (rotacao single-use no
// backend) e repete a request. Falhou = sessao morta, limpa e desloga.
class DesktopTokenAuthenticator(
    private val store: SessionStore,
    private val refreshApi: Lazy<RefreshApi>,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null // ja tentou renovar
        val session = store.load() ?: return null
        val renewed = runBlocking {
            runCatching { refreshApi.value.refresh("Bearer ${session.refreshToken}").data }.getOrNull()
        } ?: run {
            store.clear()
            return null
        }
        store.save(session.copy(accessToken = renewed.accessToken, refreshToken = renewed.refreshToken))
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${renewed.accessToken}")
            .build()
    }
}

// Avatares/icones do Astra vivem como data-URIs no banco -> Coil precisa deste
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
