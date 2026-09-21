package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.TranslateRequest
import app.astra.mobile.core.network.dto.TranslateResultDto
import retrofit2.http.Body
import retrofit2.http.POST

interface TranslateApi {

    @POST("api/translate")
    suspend fun translate(@Body body: TranslateRequest): ApiEnvelope<TranslateResultDto>
}
