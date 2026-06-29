package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.DiscoverJoinDto
import app.astra.mobile.core.network.dto.DiscoverServerDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DiscoverApi {

    @GET("api/discover")
    suspend fun discover(@Query("q") q: String? = null): ApiEnvelope<List<DiscoverServerDto>>

    @POST("api/discover/{serverId}/join")
    suspend fun join(@Path("serverId") serverId: String): ApiEnvelope<DiscoverJoinDto>
}
