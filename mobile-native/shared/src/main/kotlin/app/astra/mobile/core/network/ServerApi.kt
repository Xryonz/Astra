package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.BanDto
import app.astra.mobile.core.network.dto.BanRequest
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ChannelVisibilityDto
import app.astra.mobile.core.network.dto.ChannelVisibilityRequest
import app.astra.mobile.core.network.dto.CategoryDto
import app.astra.mobile.core.network.dto.CreateCategoryRequest
import app.astra.mobile.core.network.dto.CreateChannelRequest
import app.astra.mobile.core.network.dto.UpdateCategoryRequest
import app.astra.mobile.core.network.dto.MoveChannelRequest
import app.astra.mobile.core.network.dto.UpdateChannelNameRequest
import app.astra.mobile.core.network.dto.CreateServerRequest
import app.astra.mobile.core.network.dto.InviteCodeResponse
import app.astra.mobile.core.network.dto.MemberRoleRequest
import app.astra.mobile.core.network.dto.MemberRoleResponse
import app.astra.mobile.core.network.dto.MyColorRequest
import app.astra.mobile.core.network.dto.MyColorResponse
import app.astra.mobile.core.network.dto.MyPermsDto
import app.astra.mobile.core.network.dto.RoleDto
import app.astra.mobile.core.network.dto.RoleRequest
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import app.astra.mobile.core.network.dto.UpdateServerRequest
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

    @PATCH("api/servers/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateServerRequest): ApiEnvelope<ServerDto>

    @POST("api/servers/{id}/regenerate-invite")
    suspend fun regenerateInvite(@Path("id") serverId: String): ApiEnvelope<InviteCodeResponse>

    @PATCH("api/servers/{id}/my-color")
    suspend fun setMyColor(@Path("id") serverId: String, @Body body: MyColorRequest): ApiEnvelope<MyColorResponse>

    @GET("api/servers/{id}/members")
    suspend fun members(@Path("id") serverId: String): ApiEnvelope<List<ServerMemberDto>>

    @GET("api/servers/{id}/me")
    suspend fun myPerms(@Path("id") serverId: String): ApiEnvelope<MyPermsDto>

    @PATCH("api/servers/{sid}/members/{mid}")
    suspend fun setMemberRole(
        @Path("sid") serverId: String,
        @Path("mid") memberId: String,
        @Body body: MemberRoleRequest,
    ): ApiEnvelope<MemberRoleResponse>

    @DELETE("api/servers/{sid}/members/{mid}")
    suspend fun kickMember(@Path("sid") serverId: String, @Path("mid") memberId: String)

    @POST("api/servers/{id}/bans")
    suspend fun banMember(@Path("id") serverId: String, @Body body: BanRequest)

    @GET("api/servers/{id}/bans")
    suspend fun bans(@Path("id") serverId: String): ApiEnvelope<List<BanDto>>

    @DELETE("api/servers/{sid}/bans/{uid}")
    suspend fun unban(@Path("sid") serverId: String, @Path("uid") userId: String)

    @DELETE("api/servers/{id}/leave")
    suspend fun leaveServer(@Path("id") serverId: String)

    @DELETE("api/servers/{id}")
    suspend fun deleteServer(@Path("id") serverId: String)

    // ---- Cargos ----
    @GET("api/servers/{id}/roles")
    suspend fun roles(@Path("id") serverId: String): ApiEnvelope<List<RoleDto>>

    @POST("api/servers/{id}/roles")
    suspend fun createRole(@Path("id") serverId: String, @Body body: RoleRequest): ApiEnvelope<RoleDto>

    @PATCH("api/servers/{sid}/roles/{rid}")
    suspend fun updateRole(
        @Path("sid") serverId: String,
        @Path("rid") roleId: String,
        @Body body: RoleRequest,
    ): ApiEnvelope<RoleDto>

    @DELETE("api/servers/{sid}/roles/{rid}")
    suspend fun deleteRole(@Path("sid") serverId: String, @Path("rid") roleId: String)

    @POST("api/servers/{sid}/members/{mid}/roles/{rid}")
    suspend fun assignRole(
        @Path("sid") serverId: String,
        @Path("mid") memberId: String,
        @Path("rid") roleId: String,
    )

    @DELETE("api/servers/{sid}/members/{mid}/roles/{rid}")
    suspend fun unassignRole(
        @Path("sid") serverId: String,
        @Path("mid") memberId: String,
        @Path("rid") roleId: String,
    )

    @GET("api/reads/channels")
    suspend fun channelReads(): ApiEnvelope<Map<String, String>>

    @POST("api/servers/{serverId}/channels")
    suspend fun createChannel(
        @Path("serverId") serverId: String,
        @Body body: CreateChannelRequest,
    ): ApiEnvelope<ChannelDto>

    // ---- Gestao de canal (MANAGE_CHANNELS) ----
    @GET("api/servers/{sid}/channels/{cid}/visibility")
    suspend fun channelVisibility(
        @Path("sid") serverId: String,
        @Path("cid") channelId: String,
    ): ApiEnvelope<ChannelVisibilityDto>

    @PATCH("api/servers/{sid}/channels/{cid}/visibility")
    suspend fun setChannelVisibility(
        @Path("sid") serverId: String,
        @Path("cid") channelId: String,
        @Body body: ChannelVisibilityRequest,
    ): ApiEnvelope<ChannelVisibilityDto>

    @PATCH("api/servers/{sid}/channels/{cid}")
    suspend fun renameChannel(
        @Path("sid") serverId: String,
        @Path("cid") channelId: String,
        @Body body: UpdateChannelNameRequest,
    ): ApiEnvelope<ChannelDto>

    @PATCH("api/servers/{sid}/channels/{cid}")
    suspend fun moveChannel(
        @Path("sid") serverId: String,
        @Path("cid") channelId: String,
        @Body body: MoveChannelRequest,
    ): ApiEnvelope<ChannelDto>

    @DELETE("api/servers/{sid}/channels/{cid}")
    suspend fun deleteChannel(@Path("sid") serverId: String, @Path("cid") channelId: String)

    // ---- Categorias (MANAGE_CHANNELS) ----
    @POST("api/servers/{sid}/categories")
    suspend fun createCategory(
        @Path("sid") serverId: String,
        @Body body: CreateCategoryRequest,
    ): ApiEnvelope<CategoryDto>

    @PATCH("api/servers/{sid}/categories/{cid}")
    suspend fun updateCategory(
        @Path("sid") serverId: String,
        @Path("cid") categoryId: String,
        @Body body: UpdateCategoryRequest,
    ): ApiEnvelope<CategoryDto>

    @DELETE("api/servers/{sid}/categories/{cid}")
    suspend fun deleteCategory(@Path("sid") serverId: String, @Path("cid") categoryId: String)
}
