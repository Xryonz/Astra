package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.PainelMissoesDto
import app.astra.mobile.core.network.dto.ResgateDto
import app.astra.mobile.core.network.dto.ResgatesDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MissionApi {
    @GET("api/missions")
    suspend fun painel(): ApiEnvelope<PainelMissoesDto>

    @POST("api/missions/{missionId}/resgatar")
    suspend fun resgatar(@Path("missionId") missionId: String): ApiEnvelope<ResgateDto>

    @POST("api/missions/resgatar")
    suspend fun resgatarTudo(): ApiEnvelope<ResgatesDto>
}
