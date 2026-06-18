package app.astra.mobile.core.network

import app.astra.mobile.core.data.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quando uma request autenticada volta 401, o OkHttp chama isto ANTES de
 * desistir. Aqui: troca o access token via /refresh e re-tenta a request
 * original com o token novo. Transparente pras camadas de cima.
 *
 * runBlocking e ok: o Authenticator ja roda fora da main thread.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshApi: RefreshApi,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Ja tentamos refresh nesta cadeia e tomamos 401 de novo -> desiste.
        if (responseCount(response) >= 2) return null

        val refresh = runBlocking { tokenStore.currentRefresh() } ?: return null

        val newTokens = runBlocking {
            try {
                refreshApi.refresh("Bearer $refresh").data
            } catch (e: Exception) {
                null
            }
        }

        // Refresh morreu (expirou/revogado) -> limpa a sessao. O app reage ao
        // refreshToken sumir e volta pra tela de login.
        if (newTokens == null) {
            runBlocking { tokenStore.clear() }
            return null
        }

        runBlocking { tokenStore.save(newTokens.accessToken, newTokens.refreshToken) }

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${newTokens.accessToken}")
            .build()
    }

    // Conta quantas respostas ja encadearam (original + retries) pra cortar loop.
    private fun responseCount(response: Response): Int {
        var prior = response.priorResponse
        var count = 1
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
