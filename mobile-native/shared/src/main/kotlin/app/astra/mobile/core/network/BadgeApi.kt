package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.UserBadgesDto
import retrofit2.http.GET
import retrofit2.http.Path

interface BadgeApi {
    @GET("api/users/{userId}/badges")
    suspend fun de(@Path("userId") userId: String): ApiEnvelope<UserBadgesDto>
}
