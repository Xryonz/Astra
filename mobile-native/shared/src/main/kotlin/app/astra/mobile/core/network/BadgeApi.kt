package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.UserBadgesDto
import retrofit2.http.GET
import retrofit2.http.Path

// Insignias de uma pessoa. A rota e os DTOs (UserBadgesDto e companhia) ja existiam
// ha tempos; faltava alguem chamar. O desktop e o primeiro.
interface BadgeApi {
    @GET("api/users/{userId}/badges")
    suspend fun de(@Path("userId") userId: String): ApiEnvelope<UserBadgesDto>
}
