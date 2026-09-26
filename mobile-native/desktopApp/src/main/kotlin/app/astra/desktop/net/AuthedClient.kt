package app.astra.desktop.net

import app.astra.desktop.auth.SessionStore
import app.astra.mobile.core.network.RefreshApi
import app.astra.shared.AstraShared
import coil3.map.Mapper
import coil3.request.Options
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.nio.ByteBuffer
import java.util.Base64

private val ORIGEM_DA_API = AstraShared.BASE_URL.toHttpUrl()

private fun Request.vaiParaAApi(): Boolean =
    url.scheme == ORIGEM_DA_API.scheme && url.host == ORIGEM_DA_API.host && url.port == ORIGEM_DA_API.port

class AuthInterceptor(private val store: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val pedido = chain.request()
        if (!pedido.vaiParaAApi()) return chain.proceed(pedido)
        val token = store.load()?.accessToken ?: return chain.proceed(pedido)
        return chain.proceed(pedido.newBuilder().header("Authorization", "Bearer $token").build())
    }
}

private val IDENTIDADE = buildString {
    append("Astra-Desktop/")
    append(System.getProperty("astra.version") ?: "dev")
    append(" (")
    append(System.getProperty("os.name") ?: "desconhecido")
    append(")")
}.filter { it.code in 0x20..0x7E }

class DeviceInterceptor(private val store: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val pedido = chain.request().newBuilder().header("User-Agent", IDENTIDADE)
        if (chain.request().vaiParaAApi()) pedido.header("X-Device-Id", store.deviceId())
        return chain.proceed(pedido.build())
    }
}

class DesktopTokenAuthenticator(
    private val store: SessionStore,
    private val refreshApi: Lazy<RefreshApi>,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null || !response.request.vaiParaAApi()) return null
        val staleAuth = response.request.header("Authorization")
        synchronized(lock) {
            val session = store.load() ?: return null
            val currentAuth = "Bearer ${session.accessToken}"
            if (staleAuth != null && staleAuth != currentAuth) {
                return response.request.newBuilder().header("Authorization", currentAuth).build()
            }
            val renewed = runBlocking {
                try {
                    refreshApi.value.refresh("Bearer ${session.refreshToken}").data
                } catch (e: HttpException) {
                    if (e.code() == 401 || e.code() == 403) store.clear()
                    null
                } catch (e: Exception) {
                    null
                }
            } ?: return null
            store.save(session.copy(accessToken = renewed.accessToken, refreshToken = renewed.refreshToken))
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${renewed.accessToken}")
                .build()
        }
    }
}

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
