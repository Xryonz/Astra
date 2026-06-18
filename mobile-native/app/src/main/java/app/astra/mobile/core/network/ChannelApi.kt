package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.ChannelMessageDto
import app.astra.mobile.core.network.dto.ChannelMessagesPageDto
import app.astra.mobile.core.network.dto.SendChannelRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChannelApi {
    @GET("api/channels/{id}/messages")
    suspend fun messages(
        @Path("id") channelId: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): ApiEnvelope<ChannelMessagesPageDto>

    @POST("api/channels/{id}/messages")
    suspend fun send(
        @Path("id") channelId: String,
        @Body body: SendChannelRequest,
    ): ApiEnvelope<ChannelMessageDto>
}
