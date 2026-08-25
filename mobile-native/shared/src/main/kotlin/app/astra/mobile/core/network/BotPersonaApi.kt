package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.BotPersonaDto
import app.astra.mobile.core.network.dto.BotPersonasWrapper
import app.astra.mobile.core.network.dto.BotPersonaPatch
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface BotPersonaApi {
    @GET("api/bots")
    suspend fun personas(): ApiEnvelope<BotPersonasWrapper>

    @PATCH("api/bots/{chave}")
    suspend fun ajustar(@Path("chave") chave: String, @Body corpo: BotPersonaPatch): ApiEnvelope<BotPersonaDto>
}
