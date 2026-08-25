package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.PainelMissoesDto
import retrofit2.http.GET

interface MissionApi {
    @GET("api/missions")
    suspend fun painel(): ApiEnvelope<PainelMissoesDto>
}
