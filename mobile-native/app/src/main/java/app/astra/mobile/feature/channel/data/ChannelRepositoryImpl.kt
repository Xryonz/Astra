package app.astra.mobile.feature.channel.data

import app.astra.mobile.core.ApiException
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.dto.ChannelMessageDto
import app.astra.mobile.core.network.dto.SendChannelRequest
import app.astra.mobile.core.realtime.SocketManager
import app.astra.mobile.feature.channel.domain.ChannelRepository
import app.astra.mobile.feature.channel.domain.model.ChannelMessage
import app.astra.mobile.feature.channel.domain.model.ChannelMessagesPage
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

    override suspend fun send(channelId: String, content: String): Result<ChannelMessage> = try {
        val uid = tokenStore.currentUserId()
        val dto = channelApi.send(channelId, SendChannelRequest(content)).data
            ?: return Result.failure(ApiException("Resposta invalida do servidor"))
        Result.success(dto.toDomain(uid))
    } catch (e: IOException) {
        Result.failure(ApiException("Sem conexao com o servidor"))
    } catch (e: Exception) {
        Result.failure(ApiException("Falha ao enviar"))
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
}

private fun ChannelMessageDto.toDomain(currentUserId: String?) = ChannelMessage(
    id = id,
    content = content,
    authorName = author?.displayName ?: author?.username ?: "Alguem",
    authorAvatar = author?.avatarUrl,
    createdAt = createdAt,
    mine = authorId == currentUserId,
)
