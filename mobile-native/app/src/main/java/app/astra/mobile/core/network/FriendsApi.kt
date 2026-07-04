package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.CustomStatusRequest
import app.astra.mobile.core.network.dto.FriendDto
import app.astra.mobile.core.network.dto.FriendRequestDto
import app.astra.mobile.core.network.dto.SendFriendRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface FriendsApi {
    @GET("api/friends")
    suspend fun friends(): ApiEnvelope<List<FriendDto>>

    @GET("api/friends/requests")
    suspend fun incoming(): ApiEnvelope<List<FriendRequestDto>>

    @GET("api/friends/outgoing")
    suspend fun outgoing(): ApiEnvelope<List<FriendRequestDto>>

    @POST("api/friends/request")
    suspend fun sendRequest(@Body body: SendFriendRequest)

    @POST("api/friends/{id}/accept")
    suspend fun accept(@Path("id") id: String)

    @DELETE("api/friends/{id}")
    suspend fun remove(@Path("id") id: String)

    // Recado (custom status). Limpar = mandar "" (backend faz trim() || null).
    @PATCH("api/friends/custom-status")
    suspend fun setCustomStatus(@Body body: CustomStatusRequest)
}
