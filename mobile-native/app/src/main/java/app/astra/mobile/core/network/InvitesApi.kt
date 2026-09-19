package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.InvitePreviewDto
import app.astra.mobile.core.network.dto.ServerDto
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Path

interface InvitesApi {

    @GET("api/invites/{code}")
    suspend fun preview(@Path("code") code: String): ApiEnvelope<InvitePreviewDto>

    @POST("api/invites/{code}/join")
    suspend fun join(@Path("code") code: String): ApiEnvelope<ServerDto>
}
