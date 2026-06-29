package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.VoiceTokenData
import app.astra.mobile.core.network.dto.VoiceTokenRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface VoiceApi {
    @POST("api/voice/token")
    suspend fun token(@Body body: VoiceTokenRequest): ApiEnvelope<VoiceTokenData>

    @GET("api/voice/presence")
    suspend fun presence(@Query("channelIds") channelIds: String): ApiEnvelope<Map<String, List<String>>>
}
