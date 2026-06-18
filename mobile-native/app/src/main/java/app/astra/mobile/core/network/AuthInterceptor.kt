package app.astra.mobile.core.network

import app.astra.mobile.core.data.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injeta "Authorization: Bearer <access>" em toda request.
 * runBlocking e aceitavel aqui: o OkHttp roda interceptors fora da main thread.
 * Refresh automatico no 401 vive no TokenAuthenticator.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // Request que ja traz Authorization proprio (ex: /refresh com o refresh
        // token) passa intacta.
        if (original.header("Authorization") != null) return chain.proceed(original)

        val token = runBlocking { tokenStore.currentAccess() }
        val request = if (token.isNullOrBlank()) original
        else original.newBuilder().header("Authorization", "Bearer $token").build()
        return chain.proceed(request)
    }
}
