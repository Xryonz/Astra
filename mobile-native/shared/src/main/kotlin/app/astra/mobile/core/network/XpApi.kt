package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.ProgressoDto
import app.astra.mobile.core.network.dto.RegrasXpDto
import retrofit2.http.GET
import retrofit2.http.Path

interface XpApi {
    @GET("api/xp/me")
    suspend fun me(): ApiEnvelope<ProgressoDto>

    @GET("api/xp/regras")
    suspend fun regras(): ApiEnvelope<RegrasXpDto>

    @GET("api/xp/{userId}")
    suspend fun de(@Path("userId") userId: String): ApiEnvelope<ProgressoDto>
}
