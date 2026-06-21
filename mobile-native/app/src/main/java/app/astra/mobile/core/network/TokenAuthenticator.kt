package app.astra.mobile.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quando uma request autenticada volta 401, o OkHttp chama isto ANTES de
 * desistir. Aqui: troca o access token via [TokenRefresher] e re-tenta a
 * request original com o token novo. Transparente pras camadas de cima.
 *
 * O refresh em si vive no TokenRefresher (Mutex) pra serializar 401s paralelos
 * e nao deslogar na corrida de rotacao do refresh token.
 *
 * runBlocking e ok: o Authenticator ja roda fora da main thread.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenRefresher: TokenRefresher,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Ja tentamos refresh nesta cadeia e tomamos 401 de novo -> desiste.
        if (responseCount(response) >= 2) return null

        // O access token que ESTA request usou e falhou — passado como "stale"
        // pro refresher detectar se outra chamada ja rotacionou nesse meio tempo.
        val staleAccess = response.request.header("Authorization")
            ?.removePrefix("Bearer ")?.trim()

        val newAccess = runBlocking { tokenRefresher.refresh(staleAccess) } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccess")
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
