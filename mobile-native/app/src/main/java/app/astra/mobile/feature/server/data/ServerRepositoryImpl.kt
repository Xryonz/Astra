package app.astra.mobile.feature.server.data

import app.astra.mobile.core.ApiException
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.dto.CreateServerRequest
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.feature.server.domain.ServerRepository
import app.astra.mobile.feature.server.domain.model.Channel
import app.astra.mobile.feature.server.domain.model.Server
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepositoryImpl @Inject constructor(
    private val serverApi: ServerApi,
    private val json: Json,
) : ServerRepository {

    override suspend fun servers(): Result<List<Server>> = try {
        val env = serverApi.servers()
        Result.success(env.data.orEmpty().map { it.toDomain() })
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao carregar servidores"))
    }

    override suspend fun createServer(name: String): Result<Server> = try {
        val dto = serverApi.create(CreateServerRequest(name.trim())).data
            ?: return Result.failure(ApiException("Resposta invalida do servidor"))
        Result.success(dto.toDomain())
    } catch (e: HttpException) {
        val msg = e.response()?.errorBody()?.string()?.let {
            runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull()
        }
        Result.failure(ApiException(msg ?: "Nao foi possivel criar o servidor"))
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Nao foi possivel criar o servidor"))
    }
}

private fun ServerDto.toDomain() = Server(
    id = id,
    name = name,
    iconUrl = iconUrl,
    memberCount = count?.members ?: 0,
    channels = channels.map { Channel(id = it.id, name = it.name, isVoice = it.type == "VOICE") },
)
