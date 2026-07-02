package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.FcmTokenRequest
import app.astra.mobile.core.network.dto.NotificationPrefsResponse
import app.astra.mobile.core.network.dto.NotificationsPageDto
import app.astra.mobile.core.network.dto.UnreadCountDto
import app.astra.mobile.core.network.dto.UpdateNotificationPrefsRequest
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationsApi {
    @GET("api/notifications/prefs")
    suspend fun prefs(): ApiEnvelope<NotificationPrefsResponse>

    @PATCH("api/notifications/prefs")
    suspend fun updatePrefs(@Body body: UpdateNotificationPrefsRequest): ApiEnvelope<NotificationPrefsResponse>

    // Pra limpar quiet hours: JsonObject com JsonNull (null explicito no JSON,
    // que o explicitNulls=false do DTO normal omitiria).
    @PATCH("api/notifications/prefs")
    suspend fun updatePrefsRaw(@Body body: JsonObject): ApiEnvelope<NotificationPrefsResponse>

    @GET("api/notifications")
    suspend fun feed(
        @Query("limit") limit: Int = 30,
        @Query("cursor") cursor: String? = null,
    ): ApiEnvelope<NotificationsPageDto>

    @GET("api/notifications/unread")
    suspend fun unread(): ApiEnvelope<UnreadCountDto>

    @POST("api/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String)

    @POST("api/notifications/read-all")
    suspend fun markAllRead()

    @POST("api/push/test")
    suspend fun pushTest()

    @POST("api/push/fcm-token")
    suspend fun registerFcmToken(@Body body: FcmTokenRequest)
}
