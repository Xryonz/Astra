package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.RefreshData
import retrofit2.http.Header
import retrofit2.http.POST

interface RefreshApi {
    @POST("api/auth/refresh")
    suspend fun refresh(@Header("Authorization") bearer: String): ApiEnvelope<RefreshData>
}
