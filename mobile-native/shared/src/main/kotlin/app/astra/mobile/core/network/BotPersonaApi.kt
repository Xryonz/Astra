package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.BotPersonaDto
import app.astra.mobile.core.network.dto.BotPersonasWrapper
import app.astra.mobile.core.network.dto.BotPersonaPatch
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

// Aparencia das bots. A rota responde 404 pra quem nao e dono do Astra (e nao 403,
// pra nao confirmar que existe um painel a ser procurado) — entao o cliente usa a
// PROPRIA falha como teste: deu certo, mostra a secao; falhou, ela nem existe.
interface BotPersonaApi {
    @GET("api/bots")
    suspend fun personas(): ApiEnvelope<BotPersonasWrapper>

    @PATCH("api/bots/{chave}")
    suspend fun ajustar(@Path("chave") chave: String, @Body corpo: BotPersonaPatch): ApiEnvelope<BotPersonaDto>
}
