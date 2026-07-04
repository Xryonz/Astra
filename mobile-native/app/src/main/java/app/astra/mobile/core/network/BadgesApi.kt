package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.CreateBadgeRequest
import app.astra.mobile.core.network.dto.GrantBadgeRequest
import app.astra.mobile.core.network.dto.ServerBadgeDto
import app.astra.mobile.core.network.dto.UserBadgesDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface BadgesApi {
    @GET("api/users/{id}/badges")
    suspend fun userBadges(@Path("id") userId: String): ApiEnvelope<UserBadgesDto>

    // ---- Gestao (dono/admin do servidor) ----

    @GET("api/servers/{id}/badges")
    suspend fun serverBadges(@Path("id") serverId: String): ApiEnvelope<List<ServerBadgeDto>>

    @POST("api/servers/{id}/badges")
    suspend fun createBadge(
        @Path("id") serverId: String,
        @Body body: CreateBadgeRequest,
    ): ApiEnvelope<ServerBadgeDto>

    @DELETE("api/servers/{sid}/badges/{bid}")
    suspend fun deleteBadge(
        @Path("sid") serverId: String,
        @Path("bid") badgeId: String,
    )

    @POST("api/servers/{sid}/badges/{bid}/grants")
    suspend fun grantBadge(
        @Path("sid") serverId: String,
        @Path("bid") badgeId: String,
        @Body body: GrantBadgeRequest,
    )

    @DELETE("api/servers/{sid}/badges/{bid}/grants/{uid}")
    suspend fun revokeBadge(
        @Path("sid") serverId: String,
        @Path("bid") badgeId: String,
        @Path("uid") userId: String,
    )
}
