package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.CriarSomRequest
import app.astra.mobile.core.network.dto.ServerSoundDto
import app.astra.mobile.core.network.dto.SoundsResponse
import app.astra.mobile.core.network.dto.TocarSomRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SoundApi {
    @GET("api/sounds/{serverId}")
    suspend fun listar(@Path("serverId") serverId: String): SoundsResponse

    @POST("api/sounds/{serverId}")
    suspend fun criar(
        @Path("serverId") serverId: String,
        @Body body: CriarSomRequest,
    ): ServerSoundDto

    @DELETE("api/sounds/{serverId}/{soundId}")
    suspend fun apagar(
        @Path("serverId") serverId: String,
        @Path("soundId") soundId: String,
    )

    @POST("api/sounds/{serverId}/{soundId}/play")
    suspend fun tocar(
        @Path("serverId") serverId: String,
        @Path("soundId") soundId: String,
        @Body body: TocarSomRequest,
    )
}
