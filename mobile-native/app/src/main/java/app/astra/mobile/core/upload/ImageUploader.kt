package app.astra.mobile.core.upload

import app.astra.mobile.core.ApiException
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.AttachmentDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageUploader @Inject constructor(
    private val api: UploadApi,
    private val json: Json,
) {
    suspend fun upload(bytes: ByteArray, mime: String, filename: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("files", filename, body)
                val url = api.upload(part).data?.attachments?.firstOrNull()?.url
                    ?: return@withContext Result.failure(ApiException("Upload sem resposta"))
                Result.success(url)
            } catch (e: HttpException) {
                val msg = e.response()?.errorBody()?.string()?.let {
                    runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull()
                }
                Result.failure(ApiException(msg ?: "Falha no upload"))
            } catch (e: IOException) {
                Result.failure(ApiException("Sem conexao com o servidor"))
            } catch (e: Exception) {
                Result.failure(ApiException("Falha no upload"))
            }
        }

    suspend fun uploadMany(files: List<UploadFile>): Result<List<AttachmentDto>> =
        withContext(Dispatchers.IO) {
            try {
                val parts = files.map { f ->
                    val body = f.bytes.toRequestBody(f.mime.toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("files", f.filename, body)
                }
                val attachments = api.uploadMany(parts).data?.attachments
                    ?: return@withContext Result.failure(ApiException("Upload sem resposta"))
                Result.success(attachments)
            } catch (e: HttpException) {
                val msg = e.response()?.errorBody()?.string()?.let {
                    runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull()
                }
                Result.failure(ApiException(msg ?: "Falha no upload"))
            } catch (e: IOException) {
                Result.failure(ApiException("Sem conexao com o servidor"))
            } catch (e: Exception) {
                Result.failure(ApiException("Falha no upload"))
            }
        }
}

data class UploadFile(val bytes: ByteArray, val mime: String, val filename: String)
