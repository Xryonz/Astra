package app.astra.mobile.core.network

import app.astra.mobile.core.data.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ponto UNICO de refresh do access token. Serializa refreshes concorrentes
 * (varios 401 em paralelo + a auto-reconexao do socket) num so.
 *
 * Por que existe: o backend ROTACIONA o refresh token (cada /refresh revoga o
 * anterior). Antes, quando o access expirava (15min), as ~6 requests paralelas
 * da Home tomavam 401 juntas e cada uma chamava /refresh com o MESMO refresh
 * token -> so a 1a vencia o claim atomico, as outras tomavam "revogado" (401) e
 * o Authenticator fazia clear() -> logout. Esse bug deslogava o user em minutos.
 *
 * Fix: Mutex serializa, e cada refresh le o refresh token ATUAL dentro do lock
 * (entao a corrente R1->R2->R3 nunca usa um token ja revogado). Se outra chamada
 * ja rotacionou, reusa o access novo em vez de refrescar de novo. So encerra a
 * sessao (clear) quando o /refresh devolve 401 de verdade (refresh de 30d morto)
 * — nunca por erro de rede.
 */
@Singleton
class TokenRefresher @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshApi: RefreshApi,
) {
    private val mutex = Mutex()

    /**
     * Devolve um access token valido. [staleAccess] = o access que acabou de
     * falhar (pra detectar se outra chamada ja refrescou nesse meio tempo).
     * Devolve null se o refresh de 30d morreu (sessao encerrada) ou em falha
     * transitoria (rede) — nesse caso NAO desloga.
     */
    suspend fun refresh(staleAccess: String?): String? = mutex.withLock {
        val current = tokenStore.currentAccess()
        // Outra chamada ja rotacionou enquanto esperavamos o lock -> reusa.
        if (!current.isNullOrBlank() && current != staleAccess) return@withLock current

        val refreshToken = tokenStore.currentRefresh() ?: return@withLock null
        val newTokens = try {
            refreshApi.refresh("Bearer $refreshToken").data
        } catch (e: HttpException) {
            // 401 = refresh token invalido/expirado de verdade -> encerra sessao.
            if (e.code() == 401) tokenStore.clear()
            return@withLock null
        } catch (e: Exception) {
            // Rede/timeout/5xx -> falha a request, mas NAO desloga.
            return@withLock null
        }
        if (newTokens == null) return@withLock null

        tokenStore.save(newTokens.accessToken, newTokens.refreshToken)
        newTokens.accessToken
    }
}
