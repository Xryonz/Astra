package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.RelatoDto
import retrofit2.http.GET
import retrofit2.http.Query

interface RelatosApi {
    @GET("api/falhas")
    suspend fun relatos(@Query("limit") limite: Int): ApiEnvelope<List<RelatoDto>>
}
