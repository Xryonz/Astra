package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.CreateServerRequest
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ServerApi {
    @GET("api/servers")
    suspend fun servers(): ApiEnvelope<List<ServerDto>>

    @POST("api/servers")
    suspend fun create(@Body body: CreateServerRequest): ApiEnvelope<ServerDto>

    @GET("api/servers/{id}/members")
    suspend fun members(@Path("id") serverId: String): ApiEnvelope<List<ServerMemberDto>>
}
