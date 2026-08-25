package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.AvisosDaContaRequest
import app.astra.mobile.core.network.dto.AvisosDaContaResposta
import app.astra.mobile.core.network.dto.ChannelNotifPrefDto
import app.astra.mobile.core.network.dto.NotifModeRequest
import app.astra.mobile.core.network.dto.NotificationsPageDto
import app.astra.mobile.core.network.dto.ServerNotifPrefDto
import app.astra.mobile.core.network.dto.UnreadCountDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
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

    @DELETE("api/notifications")
    suspend fun clearAll()

    @GET("api/channels/notification-prefs")
    suspend fun channelNotifPrefs(): ApiEnvelope<List<ChannelNotifPrefDto>>

    @PUT("api/channels/{id}/notification-pref")
    suspend fun setChannelNotifPref(@Path("id") channelId: String, @Body body: NotifModeRequest): ApiEnvelope<ChannelNotifPrefDto>

    @DELETE("api/channels/{id}/notification-pref")
    suspend fun clearChannelNotifPref(@Path("id") channelId: String): ApiEnvelope<ChannelNotifPrefDto>

    @GET("api/servers/notification-prefs")
    suspend fun serverNotifPrefs(): ApiEnvelope<List<ServerNotifPrefDto>>

    @PUT("api/servers/{id}/notification-pref")
    suspend fun setServerNotifPref(@Path("id") serverId: String, @Body body: NotifModeRequest): ApiEnvelope<ServerNotifPrefDto>

    @DELETE("api/servers/{id}/notification-pref")
    suspend fun clearServerNotifPref(@Path("id") serverId: String): ApiEnvelope<ServerNotifPrefDto>

    @GET("api/notifications/prefs")
    suspend fun avisosDaConta(): ApiEnvelope<AvisosDaContaResposta>

    @PATCH("api/notifications/prefs")
    suspend fun salvarAvisosDaConta(@Body body: AvisosDaContaRequest): ApiEnvelope<AvisosDaContaResposta>
}
