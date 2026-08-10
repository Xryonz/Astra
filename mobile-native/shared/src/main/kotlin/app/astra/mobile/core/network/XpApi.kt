package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.ProgressoDto
import app.astra.mobile.core.network.dto.RegrasXpDto
import retrofit2.http.GET
import retrofit2.http.Path

interface XpApi {
    // Lido UMA vez ao abrir o app. Dali pra frente quem manda e o evento `xp_gain`
    // do socket, que ja traz o progresso inteiro — nao ha poll.
    @GET("api/xp/me")
    suspend fun me(): ApiEnvelope<ProgressoDto>

    // As taxas e a trilha vem do SERVIDOR. Cravar os numeros no app daria uma tela
    // que passa a mentir no dia em que a taxa mudar.
    @GET("api/xp/regras")
    suspend fun regras(): ApiEnvelope<RegrasXpDto>

    // O nivel de OUTRA pessoa, pro cartao de perfil completo. Uma leitura por
    // abertura de cartao — nao ha socket pro progresso alheio, e nem faria falta:
    // ninguem fica olhando o perfil de alguem esperando o numero subir.
    @GET("api/xp/{userId}")
    suspend fun de(@Path("userId") userId: String): ApiEnvelope<ProgressoDto>
}
