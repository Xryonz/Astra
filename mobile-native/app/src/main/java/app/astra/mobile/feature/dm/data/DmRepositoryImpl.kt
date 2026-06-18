package app.astra.mobile.feature.dm.data

import app.astra.mobile.core.ApiException
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.OpenDmRequest
import app.astra.mobile.core.network.dto.SendDmRequest
import app.astra.mobile.core.realtime.SocketManager
import app.astra.mobile.feature.dm.domain.DmRepository
import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.dm.domain.model.DmMessage
import app.astra.mobile.feature.dm.domain.model.MessagesPage
import app.astra.mobile.feature.dm.domain.model.OpenedConversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val PAGE_SIZE = 30

@Singleton
class DmRepositoryImpl @Inject constructor(
    private val dmApi: DmApi,
    private val socketManager: SocketManager,
    private val tokenStore: TokenStore,
    private val json: Json,
) : DmRepository {

    override suspend fun conversations(): Result<List<Conversation>> = try {
        val env = dmApi.conversations()
        Result.success(env.data.orEmpty().mapNotNull { it.toDomain() })
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao carregar conversas"))
    }

    override suspend fun messages(conversationId: String, cursor: String?): Result<MessagesPage> = try {
        val uid = tokenStore.currentUserId()
        val page = dmApi.messages(conversationId, cursor, PAGE_SIZE).data
            ?: return Result.failure(ApiException("Resposta invalida do servidor"))
        Result.success(
            MessagesPage(
                messages = page.items.map { it.toDomain(uid) },
                nextCursor = page.nextCursor,
                hasMore = page.hasMore,
            ),
        )
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao carregar mensagens"))
    }

    override suspend fun send(conversationId: String, content: String): Result<DmMessage> = try {
        val uid = tokenStore.currentUserId()
        val dto = dmApi.send(conversationId, SendDmRequest(content)).data
            ?: return Result.failure(ApiException("Resposta invalida do servidor"))
        Result.success(dto.toDomain(uid))
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao enviar"))
    }

    override suspend fun open(username: String): Result<OpenedConversation> = try {
        val dto = dmApi.open(OpenDmRequest(username.trim().removePrefix("@"))).data
            ?: return Result.failure(ApiException("Resposta invalida do servidor"))
        Result.success(
            OpenedConversation(
                conversationId = dto.conversationId,
                otherName = dto.otherUser?.displayName ?: dto.otherUser?.username ?: username,
                otherAvatar = dto.otherUser?.avatarUrl,
            ),
        )
    } catch (e: HttpException) {
        // 404/400 trazem mensagem amigavel ("Usuario nao encontrado") no body.
        val msg = e.response()?.errorBody()?.string()?.let {
            runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull()
        }
        Result.failure(ApiException(msg ?: "Nao foi possivel abrir a conversa"))
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Nao foi possivel abrir a conversa"))
    }

    override fun joinConversation(conversationId: String) = socketManager.joinDm(conversationId)

    override fun leaveConversation(conversationId: String) = socketManager.leaveDm(conversationId)

    override fun incomingMessages(conversationId: String): Flow<DmMessage> = flow {
        val uid = tokenStore.currentUserId()
        socketManager.newDm.collect { raw ->
            val dto = runCatching { json.decodeFromString<DmMessageDto>(raw) }.getOrNull()
            if (dto != null && dto.conversationId == conversationId) {
                emit(dto.toDomain(uid))
            }
        }
    }

    override fun deletedMessages(conversationId: String): Flow<String> =
        socketManager.dmDeleted
            .filter { it.second == conversationId }
            .map { it.first }
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

private fun DmMessageDto.toDomain(currentUserId: String?) = DmMessage(
    id = id,
    content = content,
    authorName = author?.displayName ?: author?.username ?: "Alguem",
    authorAvatar = author?.avatarUrl,
    createdAt = createdAt,
    mine = senderId == currentUserId,
)
