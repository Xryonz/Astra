package app.astra.mobile.feature.dm.data

import kotlinx.coroutines.CancellationException
import app.astra.mobile.core.ApiException
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.db.MessageDao
import app.astra.mobile.core.db.MessageEntity
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.model.toDto
import app.astra.mobile.core.model.toModel
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.AttachmentDto
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.OpenDmRequest
import app.astra.mobile.core.network.dto.SendDmRequest
import kotlinx.serialization.encodeToString
import app.astra.mobile.core.realtime.SocketManager
import app.astra.mobile.feature.dm.domain.DmRepository
import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.dm.domain.model.DmMessage
import app.astra.mobile.feature.dm.domain.model.MessagesPage
import app.astra.mobile.feature.dm.domain.model.OpenedConversation
import app.astra.mobile.feature.dm.domain.model.TypingUser
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val messageDao: MessageDao,
    private val json: Json,
) : DmRepository {

    override suspend fun conversations(): Result<List<Conversation>> = try {
        val uid = tokenStore.currentUserId()
        val env = dmApi.conversations()
        Result.success(env.data.orEmpty().mapNotNull { it.toDomain(uid) })
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao carregar conversas"))
    }

    override suspend fun dmReads(): Result<Map<String, String?>> = try {
        Result.success(dmApi.dmReads().data.orEmpty().mapValues { it.value.mine })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao carregar leituras"))
    }

    override suspend fun markRead(conversationId: String): Result<Unit> = try {
        dmApi.markRead(conversationId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao marcar como lido"))
    }

    override suspend fun setMuted(conversationId: String, muted: Boolean): Result<Unit> = try {
        if (muted) dmApi.mute(conversationId) else dmApi.unmute(conversationId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao silenciar conversa"))
    }

    override fun incomingConversations(): Flow<String> = flow {
        val uid = tokenStore.currentUserId()
        socketManager.newDm.collect { raw ->
            val dto = runCatching { json.decodeFromString<DmMessageDto>(raw) }.getOrNull()
            if (dto != null && dto.senderId != uid) emit(dto.conversationId)
        }
    }

    override suspend fun messages(conversationId: String, cursor: String?): Result<MessagesPage> = try {
        val uid = tokenStore.currentUserId()
        val page = dmApi.messages(conversationId, cursor, PAGE_SIZE).data
            ?: return Result.failure(ApiException("Resposta invalida do servidor"))

        messageDao.upsert(page.items.map { it.toEntity(conversationId, json) })
        Result.success(
            MessagesPage(
                messages = page.items.map { it.toDomain(uid) },
                nextCursor = page.nextCursor,
                hasMore = page.hasMore,
            ),
        )
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao carregar mensagens"))
    }

    override suspend fun send(conversationId: String, content: String, replyToId: String?, attachments: List<Attachment>): Result<DmMessage> = try {
        val uid = tokenStore.currentUserId()
        val dto = dmApi.send(conversationId, SendDmRequest(content, replyToId, attachments.map { it.toDto() })).data
            ?: return Result.failure(ApiException("Resposta invalida do servidor"))
        messageDao.upsert(dto.toEntity(conversationId, json))
        Result.success(dto.toDomain(uid))
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao enviar"))
    }

    override suspend fun delete(conversationId: String, messageId: String): Result<Unit> = try {
        dmApi.deleteMessage(conversationId, messageId)
        messageDao.deleteById(messageId)
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao apagar"))
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

        val msg = e.response()?.errorBody()?.string()?.let {
            runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull()
        }
        Result.failure(ApiException(msg ?: "Nao foi possivel abrir a conversa"))
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(ApiException("Nao foi possivel abrir a conversa"))
    }

    override fun joinConversation(conversationId: String) = socketManager.joinDm(conversationId)

    override fun leaveConversation(conversationId: String) = socketManager.leaveDm(conversationId)

    override fun observeMessages(conversationId: String): Flow<List<DmMessage>> = flow {
        val uid = tokenStore.currentUserId()
        coroutineScope {

            launch {
                socketManager.newDm.collect { raw ->
                    val dto = runCatching { json.decodeFromString<DmMessageDto>(raw) }.getOrNull()
                    if (dto != null && dto.conversationId == conversationId) {
                        messageDao.upsert(dto.toEntity(conversationId, json))
                    }
                }
            }

            launch {
                socketManager.dmDeleted.collect { (id, conv) ->
                    if (conv == conversationId) messageDao.deleteById(id)
                }
            }

            emitAll(messageDao.observe(conversationId).map { rows -> rows.map { it.toDm(uid, json) } })
        }
    }

    override fun typingEvents(conversationId: String): Flow<TypingUser> =
        socketManager.dmTyping
            .filter { it.room == conversationId }
            .map { TypingUser(it.userId, it.username, it.typing) }

    override fun startTyping(conversationId: String) = socketManager.startDmTyping(conversationId)

    override fun stopTyping(conversationId: String) = socketManager.stopDmTyping(conversationId)
}

private fun ConversationDto.toDomain(uid: String?): Conversation? {
    val u = otherUser ?: return null
    return Conversation(
        id = id,
        otherUserId = u.id,
        otherName = u.displayName ?: u.username,
        otherAvatarUrl = u.avatarUrl,
        preview = lastMessage?.content?.ifBlank { "Anexo" } ?: "Sem mensagens ainda",
        lastMessageAt = lastMessage?.createdAt,
        lastFromMe = lastMessage?.senderId != null && lastMessage.senderId == uid,
        muted = muted,
    )
}

private fun DmMessageDto.toDomain(currentUserId: String?) = DmMessage(
    id = id,
    content = content,
    authorId = senderId,
    authorName = author?.displayName ?: author?.username ?: "Alguem",
    authorAvatar = author?.avatarUrl,
    authorFont = author?.displayFont,
    createdAt = createdAt,
    mine = senderId == currentUserId,
    replyToAuthor = replyTo?.authorName,
    replyToContent = replyTo?.content,
    attachments = attachments.map { it.toModel() },
)

private fun DmMessageDto.toEntity(conversationId: String, json: Json) = MessageEntity(
    id = id,
    conversationId = conversationId,
    authorId = senderId,
    authorName = author?.displayName ?: author?.username ?: "Alguem",
    authorAvatar = author?.avatarUrl,
    authorFont = author?.displayFont,
    content = content,
    createdAt = createdAt,
    replyToAuthor = replyTo?.authorName,
    replyToContent = replyTo?.content,
    attachmentsJson = if (attachments.isEmpty()) null else json.encodeToString(attachments),
)

private fun MessageEntity.toDm(uid: String?, json: Json): DmMessage {
    val atts = attachmentsJson?.let {
        runCatching { json.decodeFromString<List<AttachmentDto>>(it).map { a -> a.toModel() } }.getOrNull()
    } ?: emptyList()
    return DmMessage(
        id = id,
        content = content,
        authorId = authorId,
        authorName = authorName,
        authorAvatar = authorAvatar,
        authorFont = authorFont,
        createdAt = createdAt,
        mine = authorId != null && authorId == uid,
        replyToAuthor = replyToAuthor,
        replyToContent = replyToContent,
        attachments = atts,
    )
}
