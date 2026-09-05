package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.CartaoDeLinkDto
import retrofit2.http.GET
import retrofit2.http.Query

interface UnfurlApi {
    @GET("api/unfurl")
    suspend fun cartao(@Query("url") url: String): ApiEnvelope<CartaoDeLinkDto?>
}
