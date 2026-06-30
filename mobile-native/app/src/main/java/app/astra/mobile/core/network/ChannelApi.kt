package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.ChannelMessageDto
import app.astra.mobile.core.network.dto.ChannelMessagesPageDto
import app.astra.mobile.core.network.dto.CreatePollRequest
import app.astra.mobile.core.network.dto.EditChannelRequest
import app.astra.mobile.core.network.dto.MessageEditDto
import app.astra.mobile.core.network.dto.PollUpdateDto
import app.astra.mobile.core.network.dto.ReactRequest
import app.astra.mobile.core.network.dto.ReactResultDto
import app.astra.mobile.core.network.dto.SendChannelRequest
import app.astra.mobile.core.network.dto.VoteRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
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

    @PATCH("api/channels/{cid}/messages/{mid}")
    suspend fun editMessage(
        @Path("cid") channelId: String,
        @Path("mid") messageId: String,
        @Body body: EditChannelRequest,
    )

    @DELETE("api/channels/{cid}/messages/{mid}")
    suspend fun deleteMessage(
        @Path("cid") channelId: String,
        @Path("mid") messageId: String,
    )

    @POST("api/channels/{cid}/messages/{mid}/react")
    suspend fun react(
        @Path("cid") channelId: String,
        @Path("mid") messageId: String,
        @Body body: ReactRequest,
    ): ApiEnvelope<ReactResultDto>

    @POST("api/channels/{cid}/messages/{mid}/pin")
    suspend fun pin(@Path("cid") channelId: String, @Path("mid") messageId: String)

    @DELETE("api/channels/{cid}/messages/{mid}/pin")
    suspend fun unpin(@Path("cid") channelId: String, @Path("mid") messageId: String)

    @GET("api/channels/{cid}/messages/pinned")
    suspend fun pinned(@Path("cid") channelId: String): ApiEnvelope<List<ChannelMessageDto>>

    @GET("api/channels/{cid}/messages/{mid}/edits")
    suspend fun edits(
        @Path("cid") channelId: String,
        @Path("mid") messageId: String,
    ): ApiEnvelope<List<MessageEditDto>>

    @POST("api/channels/{cid}/read")
    suspend fun markRead(@Path("cid") channelId: String)

    @POST("api/channels/{cid}/polls")
    suspend fun createPoll(
        @Path("cid") channelId: String,
        @Body body: CreatePollRequest,
    ): ApiEnvelope<ChannelMessageDto>

    @POST("api/channels/{cid}/polls/{mid}/vote")
    suspend fun votePoll(
        @Path("cid") channelId: String,
        @Path("mid") messageId: String,
        @Body body: VoteRequest,
    ): ApiEnvelope<PollUpdateDto>

    @POST("api/channels/{cid}/polls/{mid}/close")
    suspend fun closePoll(
        @Path("cid") channelId: String,
        @Path("mid") messageId: String,
    ): ApiEnvelope<PollUpdateDto>
}
