package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.CreateChannelRequest
import app.astra.mobile.core.network.dto.CreateServerRequest
import app.astra.mobile.core.network.dto.MyColorRequest
import app.astra.mobile.core.network.dto.MyColorResponse
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import app.astra.mobile.core.network.dto.UpdateServerRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ServerApi {
    @GET("api/servers")
    suspend fun servers(): ApiEnvelope<List<ServerDto>>

    @POST("api/servers")
    suspend fun create(@Body body: CreateServerRequest): ApiEnvelope<ServerDto>

    @PATCH("api/servers/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateServerRequest): ApiEnvelope<ServerDto>

    @PATCH("api/servers/{id}/my-color")
    suspend fun setMyColor(@Path("id") serverId: String, @Body body: MyColorRequest): ApiEnvelope<MyColorResponse>

    @GET("api/servers/{id}/members")
    suspend fun members(@Path("id") serverId: String): ApiEnvelope<List<ServerMemberDto>>

    @GET("api/reads/channels")
    suspend fun channelReads(): ApiEnvelope<Map<String, String>>

    @POST("api/servers/{serverId}/channels")
    suspend fun createChannel(
        @Path("serverId") serverId: String,
        @Body body: CreateChannelRequest,
    ): ApiEnvelope<ChannelDto>
}
