package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.PostWishRequest
import app.astra.mobile.core.network.dto.WishDto
import app.astra.mobile.core.network.dto.WishPageDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface WishApi {
    @GET("api/wishes")
    suspend fun listar(
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
    ): ApiEnvelope<WishPageDto>

    @POST("api/wishes")
    suspend fun publicar(@Body corpo: PostWishRequest): ApiEnvelope<WishDto>
}
