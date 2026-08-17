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

// EMOJIS DA CONSTELACAO.
//
// Diferente de som e figurinha, que sobem pelo /api/upload e depois se registram:
// aqui a imagem vai MULTIPART pra propria rota. O motivo esta no servidor — ele
// redimensiona pra 128px e re-encoda em WebP antes de guardar. Passar pelo
// /api/upload guardaria o original inteiro (que e o que figurinha QUER) e um emoji
// de 512KB seria baixado inteiro pra ser desenhado com 20 pixels de lado, em toda
// linha da conversa onde aparecesse.
interface EmojiApi {
    @GET("api/servers/{serverId}/emojis")
    suspend fun listar(@Path("serverId") serverId: String): ApiEnvelope<List<EmojiDto>>

    @Multipart
    @POST("api/servers/{serverId}/emojis")
    suspend fun criar(
        @Path("serverId") serverId: String,
        @Part("name") name: RequestBody,
        @Part file: MultipartBody.Part,
    ): ApiEnvelope<EmojiDto>

    // O nome E o emoji: e ele que se digita entre dois-pontos. Por isso renomear
    // existe como rota propria — sem ela, errar o nome obrigaria a apagar e subir
    // de novo, e as mensagens antigas que ja citavam o nome velho ficariam orfas.
    @PATCH("api/servers/{serverId}/emojis/{emojiId}")
    suspend fun renomear(
        @Path("serverId") serverId: String,
        @Path("emojiId") emojiId: String,
        @Body body: RenameEmojiRequest,
    ): ApiEnvelope<EmojiDto>

    @DELETE("api/servers/{serverId}/emojis/{emojiId}")
    suspend fun apagar(
        @Path("serverId") serverId: String,
        @Path("emojiId") emojiId: String,
    )
}
