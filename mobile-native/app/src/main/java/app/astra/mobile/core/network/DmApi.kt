package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.MessagesPageDto
import app.astra.mobile.core.network.dto.OpenDmDto
import app.astra.mobile.core.network.dto.OpenDmRequest
import app.astra.mobile.core.network.dto.SendDmRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DmApi {
    @GET("api/dm")
    suspend fun conversations(): ApiEnvelope<List<ConversationDto>>

    @GET("api/dm/{id}/messages")
    suspend fun messages(
        @Path("id") conversationId: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): ApiEnvelope<MessagesPageDto>

    @POST("api/dm/{id}/messages")
    suspend fun send(
        @Path("id") conversationId: String,
        @Body body: SendDmRequest,
    ): ApiEnvelope<DmMessageDto>

    @POST("api/dm/open")
    suspend fun open(@Body body: OpenDmRequest): ApiEnvelope<OpenDmDto>

    @DELETE("api/dm/{cid}/messages/{mid}")
    suspend fun deleteMessage(
        @Path("cid") conversationId: String,
        @Path("mid") messageId: String,
    )
}
