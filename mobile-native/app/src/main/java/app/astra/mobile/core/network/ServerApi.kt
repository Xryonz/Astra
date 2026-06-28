package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.CategoryDto
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.CreateCategoryRequest
import app.astra.mobile.core.network.dto.CreateChannelRequest
import app.astra.mobile.core.network.dto.CreateServerRequest
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import app.astra.mobile.core.network.dto.UpdateCategoryRequest
import app.astra.mobile.core.network.dto.UpdateServerRequest
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ServerApi {
    @GET("api/servers")
    suspend fun servers(): ApiEnvelope<List<ServerDto>>

    @POST("api/servers")
    suspend fun create(@Body body: CreateServerRequest): ApiEnvelope<ServerDto>

    // Edita nome/icone (so dono/admin no backend; 403 se nao for).
    @PATCH("api/servers/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateServerRequest): ApiEnvelope<ServerDto>

    @GET("api/servers/{id}/members")
    suspend fun members(@Path("id") serverId: String): ApiEnvelope<List<ServerMemberDto>>

    // { data: { [channelId]: lastReadAtISO } }
    @GET("api/reads/channels")
    suspend fun channelReads(): ApiEnvelope<Map<String, String>>

    // ── Gestao de categorias/canais (owner ou MANAGE_CHANNELS no backend) ──
    @POST("api/servers/{serverId}/categories")
    suspend fun createCategory(
        @Path("serverId") serverId: String,
        @Body body: CreateCategoryRequest,
    ): ApiEnvelope<CategoryDto>

    @PATCH("api/servers/{serverId}/categories/{categoryId}")
    suspend fun patchCategory(
        @Path("serverId") serverId: String,
        @Path("categoryId") categoryId: String,
        @Body body: UpdateCategoryRequest,
    ): ApiEnvelope<CategoryDto>

    @DELETE("api/servers/{serverId}/categories/{categoryId}")
    suspend fun deleteCategory(
        @Path("serverId") serverId: String,
        @Path("categoryId") categoryId: String,
    ): ApiEnvelope<CategoryDto>

    @POST("api/servers/{serverId}/channels")
    suspend fun createChannel(
        @Path("serverId") serverId: String,
        @Body body: CreateChannelRequest,
    ): ApiEnvelope<ChannelDto>

    // Body como JsonObject: mover pra "sem categoria" exige categoryId:null
    // EXPLICITO no JSON, que o Json(explicitNulls=false) omitiria num DTO.
    @PATCH("api/servers/{serverId}/channels/{channelId}")
    suspend fun patchChannel(
        @Path("serverId") serverId: String,
        @Path("channelId") channelId: String,
        @Body body: JsonObject,
    ): ApiEnvelope<ChannelDto>
}
