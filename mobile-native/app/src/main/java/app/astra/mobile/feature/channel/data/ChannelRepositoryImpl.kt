package app.astra.mobile.feature.channel.data

import app.astra.mobile.core.ApiException
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.db.MessageDao
import app.astra.mobile.core.db.MessageEntity
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.model.toDto
import app.astra.mobile.core.model.toModel
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.dto.AttachmentDto
import app.astra.mobile.core.network.dto.ChannelMessageDto
import app.astra.mobile.core.network.dto.EditChannelRequest
import app.astra.mobile.core.network.dto.ReactRequest
import app.astra.mobile.core.network.dto.ReactionDto
import app.astra.mobile.core.network.dto.ReactionUpdateDto
import app.astra.mobile.core.network.dto.SendChannelRequest
import app.astra.mobile.core.realtime.SocketManager
import app.astra.mobile.feature.channel.domain.ChannelRepository
import app.astra.mobile.feature.channel.domain.model.ChannelMessage
import app.astra.mobile.feature.channel.domain.model.ChannelMessagesPage
import app.astra.mobile.feature.channel.domain.model.MessageReaction
import app.astra.mobile.feature.channel.domain.model.TypingUser
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val PAGE_SIZE = 30

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val channelApi: ChannelApi,
    private val socketManager: SocketManager,
    private val tokenStore: TokenStore,
    private val messageDao: MessageDao,
    private val json: Json,
) : ChannelRepository {

    override suspend fun messages(channelId: String, cursor: String?): Result<ChannelMessagesPage> = try {
        val uid = tokenStore.currentUserId()
        val page = channelApi.messages(channelId, cursor, PAGE_SIZE).data
            ?: return Result.failure(ApiException("Resposta invalida do servidor"))

        messageDao.upsert(page.items.map { it.toEntity(channelId, json) })
        Result.success(
            ChannelMessagesPage(
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

    override suspend fun send(channelId: String, content: String, replyToId: String?, attachments: List<Attachment>): Result<ChannelMessage> = try {
        val uid = tokenStore.currentUserId()
        val dto = channelApi.send(channelId, SendChannelRequest(content, replyToId, attachments.map { it.toDto() })).data
            ?: return Result.failure(ApiException("Resposta invalida do servidor"))
        messageDao.upsert(dto.toEntity(channelId, json))
        Result.success(dto.toDomain(uid))
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao enviar"))
    }

    override suspend fun edit(channelId: String, messageId: String, content: String): Result<Unit> = try {
        channelApi.editMessage(channelId, messageId, EditChannelRequest(content))

        messageDao.applyEdit(messageId, content)
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao editar"))
    }

    override suspend fun delete(channelId: String, messageId: String): Result<Unit> = try {
        channelApi.deleteMessage(channelId, messageId)
        messageDao.deleteById(messageId)
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao apagar"))
    }

    override suspend fun react(channelId: String, messageId: String, emoji: String): Result<Unit> = try {
        val result = channelApi.react(channelId, messageId, ReactRequest(emoji)).data
        if (result != null) {
            messageDao.applyReactions(
                messageId,
                if (result.reactions.isEmpty()) null else json.encodeToString(result.reactions),
            )
        }
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao reagir"))
    }

    override fun joinChannel(channelId: String) = socketManager.joinChannel(channelId)

    override fun leaveChannel(channelId: String) = socketManager.leaveChannel(channelId)

    override fun observeMessages(channelId: String): Flow<List<ChannelMessage>> = flow {
        val uid = tokenStore.currentUserId()
        coroutineScope {

            launch {
                socketManager.newChannelMessage.collect { raw ->
                    val dto = runCatching { json.decodeFromString<ChannelMessageDto>(raw) }.getOrNull()
                    if (dto != null && dto.channelId == channelId) messageDao.upsert(dto.toEntity(channelId, json))
                }
            }

            launch {
                socketManager.channelMessageDeleted.collect { (id, ch) ->
                    if (ch == channelId) messageDao.deleteById(id)
                }
            }

            launch {
                socketManager.channelMessageEdited.collect { (id, content, ch) ->
                    if (ch == channelId) messageDao.applyEdit(id, content)
                }
            }

            launch {
                socketManager.channelReactionUpdate.collect { raw ->
                    val dto = runCatching { json.decodeFromString<ReactionUpdateDto>(raw) }.getOrNull()
                    if (dto != null && dto.channelId == channelId) {
                        messageDao.applyReactions(dto.messageId, if (dto.reactions.isEmpty()) null else json.encodeToString(dto.reactions))
                    }
                }
            }

            launch {
                socketManager.channelMessagePinned.collect { (id, ch, pinned) ->
                    if (ch == channelId) messageDao.applyPinned(id, pinned)
                }
            }

            emitAll(messageDao.observe(channelId).map { rows -> rows.map { it.toChannelMessage(uid, json) } })
        }
    }

    override fun typingEvents(channelId: String): Flow<TypingUser> =
        socketManager.channelTyping
            .filter { it.room == channelId }
            .map { TypingUser(it.userId, it.username, it.typing) }

    override fun startTyping(channelId: String) = socketManager.startTyping(channelId)

    override fun stopTyping(channelId: String) = socketManager.stopTyping(channelId)

    override suspend fun pin(channelId: String, messageId: String, pinned: Boolean): Result<Unit> = try {
        if (pinned) channelApi.pin(channelId, messageId) else channelApi.unpin(channelId, messageId)

        messageDao.applyPinned(messageId, pinned)
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Sem permissao pra fixar"))
    }

    override suspend fun pinnedMessages(channelId: String): Result<List<ChannelMessage>> = try {
        val uid = tokenStore.currentUserId()
        val list = channelApi.pinned(channelId).data.orEmpty().map { it.toDomain(uid) }
        Result.success(list)
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao carregar fixadas"))
    }

    override suspend fun markRead(channelId: String): Result<Unit> = try {
        channelApi.markRead(channelId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao marcar como lido"))
    }
}

private fun ChannelMessageDto.toDomain(currentUserId: String?) = ChannelMessage(
    id = id,
    content = content,
    authorName = author?.displayName ?: author?.username ?: "Alguem",
    authorAvatar = author?.avatarUrl,
    createdAt = createdAt,
    mine = authorId == currentUserId,
    edited = edited,
    pinned = pinned,
    reactions = reactions.toDomain(currentUserId),
    replyToAuthor = replyTo?.authorName,
    replyToContent = replyTo?.content,
    attachments = attachments.map { it.toModel() },
)

private fun List<ReactionDto>.toDomain(uid: String?): List<MessageReaction> =
    map { MessageReaction(emoji = it.emoji, count = it.count, mine = uid != null && uid in it.users) }

private fun ChannelMessageDto.toEntity(channelId: String, json: Json) = MessageEntity(
    id = id,
    conversationId = channelId,
    authorId = authorId,
    authorName = author?.displayName ?: author?.username ?: "Alguem",
    authorAvatar = author?.avatarUrl,
    content = content,
    createdAt = createdAt,
    replyToAuthor = replyTo?.authorName,
    replyToContent = replyTo?.content,
    edited = edited,
    pinned = pinned,
    reactionsJson = if (reactions.isEmpty()) null else json.encodeToString(reactions),
    attachmentsJson = if (attachments.isEmpty()) null else json.encodeToString(attachments),
)

private fun MessageEntity.toChannelMessage(uid: String?, json: Json): ChannelMessage {
    val reactions = reactionsJson?.let {
        runCatching { json.decodeFromString<List<ReactionDto>>(it).toDomain(uid) }.getOrNull()
    } ?: emptyList()
    val attachments = attachmentsJson?.let {
        runCatching { json.decodeFromString<List<AttachmentDto>>(it).map { a -> a.toModel() } }.getOrNull()
    } ?: emptyList()
    return ChannelMessage(
        id = id,
        content = content,
        authorName = authorName,
        authorAvatar = authorAvatar,
        createdAt = createdAt,
        mine = authorId != null && authorId == uid,
        edited = edited,
        pinned = pinned,
        reactions = reactions,
        replyToAuthor = replyToAuthor,
        replyToContent = replyToContent,
        attachments = attachments,
    )
}
