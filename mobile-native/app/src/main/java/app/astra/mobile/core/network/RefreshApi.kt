package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.RefreshData
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Separado do AuthApi de proposito: roda num OkHttpClient "pelado" (sem o
 * AuthInterceptor nem o TokenAuthenticator), senao o refresh dispararia
 * outro 401 -> refresh -> loop infinito.
 *
 * O /refresh do backend le o refresh token do header Authorization (nao do body).
 */
interface RefreshApi {
    @POST("api/auth/refresh")
    suspend fun refresh(@Header("Authorization") bearer: String): ApiEnvelope<RefreshData>
}
