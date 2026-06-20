package app.astra.mobile.feature.invite.data

import app.astra.mobile.core.ApiException
import app.astra.mobile.core.network.InvitesApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.feature.invite.domain.InvitesRepository
import app.astra.mobile.feature.invite.domain.model.InvitePreview
import app.astra.mobile.feature.server.domain.model.Channel
import app.astra.mobile.feature.server.domain.model.Server
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvitesRepositoryImpl @Inject constructor(
    private val api: InvitesApi,
    private val json: Json,
) : InvitesRepository {

    override suspend fun preview(code: String): Result<InvitePreview> = try {
        val dto = api.preview(code).data
            ?: return Result.failure(ApiException("Convite invalido ou expirado"))
        Result.success(
            InvitePreview(
                id = dto.id,
                name = dto.name,
                iconUrl = dto.iconUrl,
                isGroup = dto.isGroup,
                memberCount = dto.count?.members ?: 0,
            ),
        )
    } catch (e: HttpException) {
        Result.failure(ApiException(errorMsg(e) ?: "Convite invalido ou expirado"))
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao carregar o convite"))
    }

    override suspend fun join(code: String): Result<Server> = try {
        val dto = api.join(code).data
            ?: return Result.failure(ApiException("Resposta invalida do servidor"))
        Result.success(dto.toServer())
    } catch (e: HttpException) {
        // 403 grupo/banido, 409 ja e membro, 404 invalido — backend manda { error }.
        Result.failure(ApiException(errorMsg(e) ?: "Nao foi possivel entrar no servidor"))
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Nao foi possivel entrar no servidor"))
    }

    private fun errorMsg(e: HttpException): String? =
        e.response()?.errorBody()?.string()?.let {
            runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull()
        }
}

private fun ServerDto.toServer() = Server(
    id = id,
    name = name,
    iconUrl = iconUrl,
    memberCount = count?.members ?: 0,
    inviteCode = inviteCode,
    channels = channels.map {
        Channel(id = it.id, name = it.name, isVoice = it.type == "VOICE", lastMessageAt = it.lastMessageAt)
    },
)
