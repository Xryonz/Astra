package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.UploadResponse
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface UploadApi {
    // multipart, campo "files" (a rota aceita ate 10; mandamos 1 por vez).
    @Multipart
    @POST("api/upload")
    suspend fun upload(@Part file: MultipartBody.Part): ApiEnvelope<UploadResponse>
}
