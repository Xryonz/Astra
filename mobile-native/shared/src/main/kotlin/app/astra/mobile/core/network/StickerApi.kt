package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.CriarFigurinhaRequest
import app.astra.mobile.core.network.dto.ServerStickerDto
import app.astra.mobile.core.network.dto.StickersResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface StickerApi {
    @GET("api/stickers/{serverId}")
    suspend fun listar(@Path("serverId") serverId: String): StickersResponse

    @POST("api/stickers/{serverId}")
    suspend fun criar(
        @Path("serverId") serverId: String,
        @Body body: CriarFigurinhaRequest,
    ): ServerStickerDto

    @DELETE("api/stickers/{serverId}/{stickerId}")
    suspend fun apagar(
        @Path("serverId") serverId: String,
        @Path("stickerId") stickerId: String,
    )
}
