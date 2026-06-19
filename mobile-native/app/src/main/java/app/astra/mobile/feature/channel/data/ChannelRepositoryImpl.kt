package app.astra.mobile.feature.channel.data

import app.astra.mobile.core.ApiException
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.network.ChannelApi
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
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
    private val json: Json,
) : ChannelRepository {

    override suspend fun messages(channelId: String, cursor: String?): Result<ChannelMessagesPage> = try {
        val uid = tokenStore.currentUserId()
        val page = channelApi.messages(channelId, cursor, PAGE_SIZE).data
            ?: return Result.failure(ApiException("Resposta invalida do servidor"))
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

    override suspend fun send(channelId: String, content: String, replyToId: String?): Result<ChannelMessage> = try {
        val uid = tokenStore.currentUserId()
        val dto = channelApi.send(channelId, SendChannelRequest(content, replyToId)).data
            ?: return Result.failure(ApiException("Resposta invalida do servidor"))
        Result.success(dto.toDomain(uid))
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao enviar"))
    }

    override suspend fun edit(channelId: String, messageId: String, content: String): Result<Unit> = try {
        channelApi.editMessage(channelId, messageId, EditChannelRequest(content))
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao editar"))
    }

    override suspend fun delete(channelId: String, messageId: String): Result<Unit> = try {
        channelApi.deleteMessage(channelId, messageId)
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao apagar"))
    }

    // Toggle no servidor; a UI atualiza pelo socket reaction_update (broadcast
    // pra sala inclui o proprio autor). Fire-and-forget.
    override suspend fun react(channelId: String, messageId: String, emoji: String): Result<Unit> = try {
        channelApi.react(channelId, messageId, ReactRequest(emoji))
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao reagir"))
    }

    override fun joinChannel(channelId: String) = socketManager.joinChannel(channelId)

    override fun leaveChannel(channelId: String) = socketManager.leaveChannel(channelId)

    override fun incomingMessages(channelId: String): Flow<ChannelMessage> = flow {
        val uid = tokenStore.currentUserId()
        socketManager.newChannelMessage.collect { raw ->
            val dto = runCatching { json.decodeFromString<ChannelMessageDto>(raw) }.getOrNull()
            if (dto != null && dto.channelId == channelId) {
                emit(dto.toDomain(uid))
            }
        }
    }

    override fun deletedMessages(channelId: String): Flow<String> =
        socketManager.channelMessageDeleted
            .filter { it.second == channelId }
            .map { it.first }

    // message_edited (messageId, content, channelId) -> (messageId, content) do canal certo.
    override fun editedMessages(channelId: String): Flow<Pair<String, String>> =
        socketManager.channelMessageEdited
            .filter { it.third == channelId }
            .map { it.first to it.second }

    // reaction_update (JSON cru) -> (messageId, lista de reacoes ja com mine).
    override fun reactionUpdates(channelId: String): Flow<Pair<String, List<MessageReaction>>> = flow {
        val uid = tokenStore.currentUserId()
        socketManager.channelReactionUpdate.collect { raw ->
            val dto = runCatching { json.decodeFromString<ReactionUpdateDto>(raw) }.getOrNull()
            if (dto != null && dto.channelId == channelId) {
                emit(dto.messageId to dto.reactions.toDomain(uid))
            }
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

    override fun pinnedUpdates(channelId: String): Flow<Pair<String, Boolean>> =
        socketManager.channelMessagePinned
            .filter { it.second == channelId }
            .map { it.first to it.third }
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
)

private fun List<ReactionDto>.toDomain(uid: String?): List<MessageReaction> =
    map { MessageReaction(emoji = it.emoji, count = it.count, mine = uid != null && uid in it.users) }
