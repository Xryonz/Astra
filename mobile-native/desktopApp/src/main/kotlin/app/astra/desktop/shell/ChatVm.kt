package app.astra.desktop.shell

import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.dto.ChannelMessageDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.MsgAuthorDto
import app.astra.mobile.core.network.dto.SendChannelRequest
import app.astra.mobile.core.network.dto.SendDmRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

// Alvo do chat aberto no palco.
sealed interface ChatTarget {
    val id: String
    val title: String

    data class Channel(override val id: String, override val title: String) : ChatTarget
    data class Dm(override val id: String, override val title: String) : ChatTarget
}

// Mensagem normalizada pro palco (canal e DM viram a mesma coisa na UI).
data class ChatMessage(
    val id: String,
    val content: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String?,
    val createdAt: String?,
)

data class ChatUiState(
    val loading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
)

private const val PAGE = 50

// Estado de UMA conversa aberta: historico + envio + append ao vivo (socket).
// Recriado por alvo (remember(target) na composicao); o listener do socket morre
// junto do escopo.
class ChatVm(
    private val scope: CoroutineScope,
    private val target: ChatTarget,
    private val channelApi: ChannelApi,
    private val dmApi: DmApi,
    private val socket: DesktopSocket,
    private val json: Json,
    private val myId: String?,
) {
    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    private var liveJob: Job? = null

    init {
        load()
        listenLive()
        when (target) {
            is ChatTarget.Channel -> socket.joinChannel(target.id)
            is ChatTarget.Dm -> socket.joinDm(target.id)
        }
    }

    fun dispose() {
        liveJob?.cancel()
        when (target) {
            is ChatTarget.Channel -> socket.leaveChannel(target.id)
            is ChatTarget.Dm -> socket.leaveDm(target.id)
        }
    }

    private fun load() {
        scope.launch {
            val result = runCatching {
                when (target) {
                    is ChatTarget.Channel ->
                        channelApi.messages(target.id, null, PAGE).data?.items.orEmpty().map { it.toChat() }
                    is ChatTarget.Dm ->
                        dmApi.messages(target.id, null, PAGE).data?.items.orEmpty().map { it.toChat() }
                }
            }
            result
                .onSuccess { list ->
                    // Backend pagina do mais novo pro mais velho; o palco mostra
                    // do mais velho (topo) pro mais novo (base).
                    _state.update { it.copy(loading = false, messages = list.sortedBy { m -> m.createdAt ?: "" }) }
                }
                .onFailure { _state.update { it.copy(loading = false, error = "Nao deu pra carregar a conversa") } }
        }
    }

    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty() || _state.value.sending) return
        _state.update { it.copy(sending = true, error = null) }
        scope.launch {
            val result = runCatching {
                when (target) {
                    is ChatTarget.Channel -> channelApi.send(target.id, SendChannelRequest(content)).data?.toChat()
                    is ChatTarget.Dm -> dmApi.send(target.id, SendDmRequest(content)).data?.toChat()
                }
            }
            result
                .onSuccess { msg ->
                    _state.update {
                        it.copy(
                            sending = false,
                            messages = if (msg != null && it.messages.none { m -> m.id == msg.id }) it.messages + msg else it.messages,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(sending = false, error = "Mensagem nao enviada") } }
        }
    }

    private fun listenLive() {
        liveJob = scope.launch {
            when (target) {
                is ChatTarget.Channel -> socket.newChannelMessage.collect { raw ->
                    val msg = runCatching { json.decodeFromString<ChannelMessageDto>(raw) }.getOrNull() ?: return@collect
                    if (msg.channelId == target.id) append(msg.toChat())
                }
                is ChatTarget.Dm -> socket.newDm.collect { raw ->
                    val msg = runCatching { json.decodeFromString<DmMessageDto>(raw) }.getOrNull() ?: return@collect
                    if (msg.conversationId == target.id) append(msg.toChat())
                }
            }
        }
    }

    private fun append(msg: ChatMessage) {
        _state.update {
            if (it.messages.any { m -> m.id == msg.id }) it
            else it.copy(messages = it.messages + msg)
        }
    }

    private fun MsgAuthorDto?.name(fallbackId: String): String =
        this?.displayName ?: this?.username ?: if (fallbackId == myId) "voce" else "alguem"

    private fun ChannelMessageDto.toChat() = ChatMessage(
        id = id, content = content, authorId = authorId,
        authorName = author.name(authorId), authorAvatar = author?.avatarUrl,
        createdAt = createdAt,
    )

    private fun DmMessageDto.toChat() = ChatMessage(
        id = id, content = content, authorId = senderId,
        authorName = author.name(senderId), authorAvatar = author?.avatarUrl,
        createdAt = createdAt,
    )
}
