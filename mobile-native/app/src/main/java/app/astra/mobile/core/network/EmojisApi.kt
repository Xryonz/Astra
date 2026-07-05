package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.EmojiDto
import app.astra.mobile.core.network.dto.RenameEmojiRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface EmojisApi {
    @GET("api/servers/{id}/emojis")
    suspend fun emojis(@Path("id") serverId: String): ApiEnvelope<List<EmojiDto>>

    @Multipart
    @POST("api/servers/{id}/emojis")
    suspend fun createEmoji(
        @Path("id") serverId: String,
        @Part("name") name: RequestBody,
        @Part file: MultipartBody.Part,
    ): ApiEnvelope<EmojiDto>

    @PATCH("api/servers/{sid}/emojis/{eid}")
    suspend fun renameEmoji(
        @Path("sid") serverId: String,
        @Path("eid") emojiId: String,
        @Body body: RenameEmojiRequest,
    ): ApiEnvelope<EmojiDto>

    @DELETE("api/servers/{sid}/emojis/{eid}")
    suspend fun deleteEmoji(@Path("sid") serverId: String, @Path("eid") emojiId: String)
}
