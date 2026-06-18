package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.CreateServerRequest
import app.astra.mobile.core.network.dto.ServerDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ServerApi {
    @GET("api/servers")
    suspend fun servers(): ApiEnvelope<List<ServerDto>>

    @POST("api/servers")
    suspend fun create(@Body body: CreateServerRequest): ApiEnvelope<ServerDto>
}
