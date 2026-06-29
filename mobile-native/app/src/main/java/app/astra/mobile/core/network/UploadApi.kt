package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.UploadResponse
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface UploadApi {

    @Multipart
    @POST("api/upload")
    suspend fun upload(@Part file: MultipartBody.Part): ApiEnvelope<UploadResponse>

    @Multipart
    @POST("api/upload")
    suspend fun uploadMany(@Part files: List<MultipartBody.Part>): ApiEnvelope<UploadResponse>
}
