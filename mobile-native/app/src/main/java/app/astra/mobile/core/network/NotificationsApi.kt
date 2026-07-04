package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.ChannelNotifPrefDto
import app.astra.mobile.core.network.dto.FcmTokenRequest
import app.astra.mobile.core.network.dto.NotifModeRequest
import app.astra.mobile.core.network.dto.NotificationPrefsResponse
import app.astra.mobile.core.network.dto.NotificationsPageDto
import app.astra.mobile.core.network.dto.ServerNotifPrefDto
import app.astra.mobile.core.network.dto.UnreadCountDto
import app.astra.mobile.core.network.dto.UpdateNotificationPrefsRequest
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
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

    // ---- Silenciar canal/servidor (modos all/mentions/mute) ----

    @GET("api/channels/notification-prefs")
    suspend fun channelNotifPrefs(): ApiEnvelope<List<ChannelNotifPrefDto>>

    @PUT("api/channels/{id}/notification-pref")
    suspend fun setChannelNotifPref(
        @Path("id") channelId: String,
        @Body body: NotifModeRequest,
    ): ApiEnvelope<ChannelNotifPrefDto>

    @DELETE("api/channels/{id}/notification-pref")
    suspend fun clearChannelNotifPref(@Path("id") channelId: String): ApiEnvelope<ChannelNotifPrefDto>

    @GET("api/servers/notification-prefs")
    suspend fun serverNotifPrefs(): ApiEnvelope<List<ServerNotifPrefDto>>

    @PUT("api/servers/{id}/notification-pref")
    suspend fun setServerNotifPref(
        @Path("id") serverId: String,
        @Body body: NotifModeRequest,
    ): ApiEnvelope<ServerNotifPrefDto>

    @DELETE("api/servers/{id}/notification-pref")
    suspend fun clearServerNotifPref(@Path("id") serverId: String): ApiEnvelope<ServerNotifPrefDto>

    @POST("api/push/test")
    suspend fun pushTest()

    @POST("api/push/fcm-token")
    suspend fun registerFcmToken(@Body body: FcmTokenRequest)
}
