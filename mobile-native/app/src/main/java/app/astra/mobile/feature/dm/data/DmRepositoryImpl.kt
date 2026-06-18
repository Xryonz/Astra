package app.astra.mobile.feature.dm.data

import app.astra.mobile.core.ApiException
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.feature.dm.domain.DmRepository
import app.astra.mobile.feature.dm.domain.model.Conversation
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DmRepositoryImpl @Inject constructor(
    private val dmApi: DmApi,
) : DmRepository {

    override suspend fun conversations(): Result<List<Conversation>> = try {
        val env = dmApi.conversations()
        Result.success(env.data.orEmpty().mapNotNull { it.toDomain() })
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao carregar conversas"))
    }
}

// Conversa sem otherUser (usuario sumiu) e descartada — nao da pra renderizar.
private fun ConversationDto.toDomain(): Conversation? {
    val u = otherUser ?: return null
    return Conversation(
        id = id,
        otherUserId = u.id,
        otherName = u.displayName ?: u.username,
        otherAvatarUrl = u.avatarUrl,
        preview = lastMessage?.content?.ifBlank { "Anexo" } ?: "Sem mensagens ainda",
    )
}
