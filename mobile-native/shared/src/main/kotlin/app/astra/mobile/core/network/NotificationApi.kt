package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.NotificationsPageDto
import app.astra.mobile.core.network.dto.UnreadCountDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApi {
    @GET("api/notifications")
    suspend fun list(
        @Query("limit") limit: Int = 30,
        @Query("cursor") cursor: String? = null,
    ): ApiEnvelope<NotificationsPageDto>

    @GET("api/notifications/unread")
    suspend fun unread(): ApiEnvelope<UnreadCountDto>

    @POST("api/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String)

    @POST("api/notifications/read-all")
    suspend fun readAll()
}
