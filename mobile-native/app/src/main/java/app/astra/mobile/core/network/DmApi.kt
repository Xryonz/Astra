package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.ConversationDto
import retrofit2.http.GET

interface DmApi {
    @GET("api/dm")
    suspend fun conversations(): ApiEnvelope<List<ConversationDto>>
}
