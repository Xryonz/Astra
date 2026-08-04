package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.PainelMissoesDto
import retrofit2.http.GET

interface MissionApi {
    // Lido ao abrir a tela. O avanco depois vem pelo socket (`mission_done`) — poll
    // aqui devolveria a mesma resposta quase sempre.
    @GET("api/missions")
    suspend fun painel(): ApiEnvelope<PainelMissoesDto>
}
