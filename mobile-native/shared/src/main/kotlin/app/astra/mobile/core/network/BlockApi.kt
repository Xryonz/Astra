package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.BlockedUserDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface BlockApi {
    @GET("api/blocks")
    suspend fun blocked(): ApiEnvelope<List<BlockedUserDto>>

    @POST("api/blocks/{userId}")
    suspend fun block(@Path("userId") userId: String)

    @DELETE("api/blocks/{userId}")
    suspend fun unblock(@Path("userId") userId: String)
}
