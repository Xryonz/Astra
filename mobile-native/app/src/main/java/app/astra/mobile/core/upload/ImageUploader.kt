package app.astra.mobile.core.upload

import app.astra.mobile.core.ApiException
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.dto.ApiError
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

/**
 * Sobe uma imagem pro POST /api/upload e devolve a URL publica.
 * Generico — usado por avatar, banner e icone de constelacao. GIF/WebP
 * animado e preservado pelo backend (nao transcoda). Limites de tamanho
 * (5/8MB) sao checados no call site, antes de chamar isso.
 */
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
}
