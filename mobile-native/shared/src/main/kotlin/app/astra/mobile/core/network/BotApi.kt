package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.BotCatalogoWrapper
import app.astra.mobile.core.network.dto.BotCommandDto
import retrofit2.http.GET

interface BotApi {
    @GET("api/bot/commands")
    suspend fun commands(): ApiEnvelope<List<BotCommandDto>>

    @GET("api/bot/catalog")
    suspend fun catalogo(): ApiEnvelope<BotCatalogoWrapper>
}
