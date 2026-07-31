package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.BotCommandDto
import retrofit2.http.GET

// Catalogo de comandos do bot. Vem do backend de proposito: a lista que alimenta
// a caixinha do "/" e a MESMA que alimenta o `/astra ajuda`, entao um comando
// novo aparece nos dois lugares sem ninguem precisar lembrar de copiar.
interface BotApi {
    @GET("api/bot/commands")
    suspend fun commands(): ApiEnvelope<List<BotCommandDto>>
}
