package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.GifEnabledDto
import app.astra.mobile.core.network.dto.GifPageDto
import retrofit2.http.GET
import retrofit2.http.Query

interface GifApi {

    @GET("api/gif/enabled")
    suspend fun enabled(): ApiEnvelope<GifEnabledDto>

    @GET("api/gif/featured")
    suspend fun featured(
        @Query("limit") limit: Int = 24,
        @Query("pos") pos: String? = null,
    ): ApiEnvelope<GifPageDto>

    @GET("api/gif/search")
    suspend fun search(
        @Query("q") q: String,
        @Query("limit") limit: Int = 24,
        @Query("pos") pos: String? = null,
    ): ApiEnvelope<GifPageDto>
}
