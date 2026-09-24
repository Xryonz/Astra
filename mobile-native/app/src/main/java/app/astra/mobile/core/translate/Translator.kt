package app.astra.mobile.core.translate

import kotlinx.coroutines.CancellationException
import app.astra.mobile.core.ApiException
import app.astra.mobile.core.network.TranslateApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.TranslateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Translator @Inject constructor(
    private val api: TranslateApi,
    private val json: Json,
) {
    suspend fun translate(text: String, target: String = "pt"): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val out = api.translate(TranslateRequest(text, target)).data?.translation
                if (out.isNullOrBlank()) Result.failure(ApiException("Tradução vazia"))
                else Result.success(out)
            } catch (e: HttpException) {
                val msg = e.response()?.errorBody()?.string()?.let {
                    runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull()
                }
                Result.failure(ApiException(msg ?: "Tradução indisponível"))
            } catch (e: IOException) {
                Result.failure(ApiException("Sem conexão com o servidor"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(ApiException("Erro na tradução"))
            }
        }
}
