package app.astra.desktop.shell

import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.dto.ChannelMessageDto
import app.astra.mobile.core.network.dto.ChannelTypingEventDto
import app.astra.mobile.core.network.dto.DmDeletedEventDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.DmTypingEventDto
import app.astra.mobile.core.network.dto.EditChannelRequest
import app.astra.mobile.core.network.dto.MessageDeletedEventDto
import app.astra.mobile.core.network.dto.MessageEditedEventDto
import app.astra.mobile.core.network.dto.MsgAuthorDto
import app.astra.mobile.core.network.dto.ReactRequest
import app.astra.mobile.core.network.dto.ReactionDto
import app.astra.mobile.core.network.dto.ReactionUpdateDto
import app.astra.mobile.core.network.dto.ReplyToDto
import app.astra.mobile.core.network.dto.SendChannelRequest
import app.astra.mobile.core.network.dto.SendDmRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val mine: Boolean = false,
    val edited: Boolean = false,
    val reactions: List<ReactionDto> = emptyList(),
    val replyTo: ReplyToDto? = null,
    // Marcada pra sumir: a UI anima o fade-out e o VM tira da lista em seguida.
    val deleting: Boolean = false,
)

data class ChatUiState(
    val loading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val replyingTo: ChatMessage? = null,
    // Quem esta digitando nesta conversa (userId -> nome exibido).
    val typing: Map<String, String> = emptyMap(),
    val error: String? = null,
)

private const val PAGE = 50
private const val FADE_OUT_MS = 340L

// Reenvia typing_start a cada 3s enquanto digita; para apos 3s parado; quem
// recebe expira o typing em 8s caso o stop se perca (socket caiu etc).
private const val TYPING_RESEND_MS = 3_000L
private const val TYPING_IDLE_MS = 3_000L
private const val TYPING_EXPIRY_MS = 8_000L

// Estado de UMA conversa aberta: historico + envio + acoes (responder/reagir/
// editar/apagar) + eventos ao vivo (socket). Recriado por alvo (remember(target)
// na composicao); o listener do socket morre junto do escopo.
class ChatVm(
    private val scope: CoroutineScope,
    private val target: ChatTarget,
    private val channelApi: ChannelApi,
    private val dmApi: DmApi,
    private val socket: DesktopSocket,
    private val json: Json,
    val myId: String?,
) {
    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    private var liveJob: Job? = null
    private var typingIdleJob: Job? = null
    private var lastTypingEmit = 0L
    private val typingExpiry = mutableMapOf<String, Job>()

    init {
        load()
        listenLive()
        // Sala de DM e persistente (ShellVm entra em todas pra typing/unread na
        // sidebar) — aqui so entra/sai de sala de canal.
        if (target is ChatTarget.Channel) socket.joinChannel(target.id)
        markRead()
    }

    fun dispose() {
        liveJob?.cancel()
        stopTypingEmit()
        if (target is ChatTarget.Channel) socket.leaveChannel(target.id)
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
        val replyToId = _state.value.replyingTo?.id
        stopTypingEmit()
        _state.update { it.copy(sending = true, error = null) }
        scope.launch {
            val result = runCatching {
                when (target) {
                    is ChatTarget.Channel ->
                        channelApi.send(target.id, SendChannelRequest(content, replyToId = replyToId)).data?.toChat()
                    is ChatTarget.Dm ->
                        dmApi.send(target.id, SendDmRequest(content, replyToId = replyToId)).data?.toChat()
                }
            }
            result
                .onSuccess { msg ->
                    _state.update {
                        it.copy(
                            sending = false,
                            replyingTo = null,
                            messages = if (msg != null && it.messages.none { m -> m.id == msg.id }) it.messages + msg else it.messages,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(sending = false, error = "Mensagem nao enviada") } }
        }
    }

    // Chamado a cada tecla no composer: emite typing_start com throttle e agenda
    // o typing_stop pra quando parar de digitar.
    fun typing() {
        val now = System.currentTimeMillis()
        if (now - lastTypingEmit > TYPING_RESEND_MS) {
            lastTypingEmit = now
            when (target) {
                is ChatTarget.Channel -> socket.startTyping(target.id)
                is ChatTarget.Dm -> socket.startDmTyping(target.id)
            }
        }
        typingIdleJob?.cancel()
        typingIdleJob = scope.launch {
            delay(TYPING_IDLE_MS)
            stopTypingEmit()
        }
    }

    private fun stopTypingEmit() {
        typingIdleJob?.cancel()
        if (lastTypingEmit == 0L) return
        lastTypingEmit = 0
        when (target) {
            is ChatTarget.Channel -> socket.stopTyping(target.id)
            is ChatTarget.Dm -> socket.stopDmTyping(target.id)
        }
    }

    private fun userTyping(userId: String, username: String?) {
        if (userId == myId) return
        _state.update { it.copy(typing = it.typing + (userId to (username ?: "alguem"))) }
        typingExpiry.remove(userId)?.cancel()
        typingExpiry[userId] = scope.launch {
            delay(TYPING_EXPIRY_MS)
            userStoppedTyping(userId)
        }
    }

    private fun userStoppedTyping(userId: String) {
        typingExpiry.remove(userId)?.cancel()
        _state.update { if (userId in it.typing) it.copy(typing = it.typing - userId) else it }
    }

    // Zera o "nao lida" desta conversa no backend (sidebar limpa localmente).
    private fun markRead() {
        scope.launch {
            runCatching {
                when (target) {
                    is ChatTarget.Channel -> channelApi.markRead(target.id)
                    is ChatTarget.Dm -> dmApi.markRead(target.id)
                }
            }
        }
    }

    fun startReply(msg: ChatMessage) {
        _state.update { it.copy(replyingTo = msg) }
    }

    fun cancelReply() {
        _state.update { it.copy(replyingTo = null) }
    }

    // Toggle no backend: mesma chamada adiciona e remove (so canais tem reacao).
    fun react(messageId: String, emoji: String) {
        val channelId = (target as? ChatTarget.Channel)?.id ?: return
        scope.launch {
            runCatching { channelApi.react(channelId, messageId, ReactRequest(emoji)) }
                .onSuccess { res -> res.data?.let { setReactions(messageId, it.reactions) } }
                .onFailure { _state.update { it.copy(error = "Nao deu pra reagir") } }
        }
    }

    fun edit(messageId: String, newContent: String) {
        val channelId = (target as? ChatTarget.Channel)?.id ?: return
        val content = newContent.trim()
        if (content.isEmpty()) return
        scope.launch {
            runCatching { channelApi.editMessage(channelId, messageId, EditChannelRequest(content)) }
                .onSuccess {
                    _state.update { st ->
                        st.copy(messages = st.messages.map {
                            if (it.id == messageId) it.copy(content = content, edited = true) else it
                        })
                    }
                }
                .onFailure { _state.update { it.copy(error = "Nao deu pra editar") } }
        }
    }

    fun delete(messageId: String) {
        scope.launch {
            val result = runCatching {
                when (target) {
                    is ChatTarget.Channel -> channelApi.deleteMessage(target.id, messageId)
                    is ChatTarget.Dm -> dmApi.deleteMessage(target.id, messageId)
                }
            }
            result
                .onSuccess { fadeOutAndRemove(messageId) }
                .onFailure { _state.update { it.copy(error = "Nao deu pra apagar") } }
        }
    }

    private fun listenLive() {
        liveJob = scope.launch {
            when (target) {
                is ChatTarget.Channel -> {
                    launch {
                        socket.newChannelMessage.collect { raw ->
                            val msg = decode<ChannelMessageDto>(raw) ?: return@collect
                            if (msg.channelId == target.id) append(msg.toChat())
                        }
                    }
                    launch {
                        socket.messageEdited.collect { raw ->
                            val ev = decode<MessageEditedEventDto>(raw) ?: return@collect
                            if (ev.channelId != target.id) return@collect
                            _state.update { st ->
                                st.copy(messages = st.messages.map {
                                    if (it.id == ev.messageId) it.copy(content = ev.content, edited = true) else it
                                })
                            }
                        }
                    }
                    launch {
                        socket.messageDeleted.collect { raw ->
                            val ev = decode<MessageDeletedEventDto>(raw) ?: return@collect
                            if (ev.channelId == target.id) fadeOutAndRemove(ev.messageId)
                        }
                    }
                    launch {
                        socket.reactionUpdate.collect { raw ->
                            val ev = decode<ReactionUpdateDto>(raw) ?: return@collect
                            if (ev.channelId == target.id) setReactions(ev.messageId, ev.reactions)
                        }
                    }
                    launch {
                        socket.channelTyping.collect { raw ->
                            val ev = decode<ChannelTypingEventDto>(raw) ?: return@collect
                            if (ev.channelId == target.id) userTyping(ev.userId, ev.username)
                        }
                    }
                    launch {
                        socket.channelTypingStopped.collect { raw ->
                            val ev = decode<ChannelTypingEventDto>(raw) ?: return@collect
                            if (ev.channelId == target.id) userStoppedTyping(ev.userId)
                        }
                    }
                }
                is ChatTarget.Dm -> {
                    launch {
                        socket.newDm.collect { raw ->
                            val msg = decode<DmMessageDto>(raw) ?: return@collect
                            if (msg.conversationId == target.id) append(msg.toChat())
                        }
                    }
                    launch {
                        socket.dmDeleted.collect { raw ->
                            val ev = decode<DmDeletedEventDto>(raw) ?: return@collect
                            if (ev.conversationId == target.id) fadeOutAndRemove(ev.messageId)
                        }
                    }
                    launch {
                        socket.dmTyping.collect { raw ->
                            val ev = decode<DmTypingEventDto>(raw) ?: return@collect
                            if (ev.conversationId == target.id) userTyping(ev.userId, ev.username)
                        }
                    }
                    launch {
                        socket.dmTypingStopped.collect { raw ->
                            val ev = decode<DmTypingEventDto>(raw) ?: return@collect
                            if (ev.conversationId == target.id) userStoppedTyping(ev.userId)
                        }
                    }
                }
            }
        }
    }

    private fun append(msg: ChatMessage) {
        // Quem mandou mensagem obviamente parou de digitar.
        userStoppedTyping(msg.authorId)
        _state.update {
            if (it.messages.any { m -> m.id == msg.id }) it
            else it.copy(messages = it.messages + msg)
        }
        // Conversa aberta: o que chega ja nasce lido.
        if (!msg.mine) markRead()
    }

    private fun setReactions(messageId: String, reactions: List<ReactionDto>) {
        _state.update { st ->
            st.copy(messages = st.messages.map {
                if (it.id == messageId) it.copy(reactions = reactions) else it
            })
        }
    }

    // Marca deleting (a UI anima o fade-out) e tira da lista quando a animacao
    // acaba. Chega duas vezes pra quem apagou (HTTP ok + evento) — dedupe aqui.
    private fun fadeOutAndRemove(messageId: String) {
        val current = _state.value.messages.firstOrNull { it.id == messageId }
        if (current == null || current.deleting) return
        _state.update { st ->
            st.copy(
                messages = st.messages.map { if (it.id == messageId) it.copy(deleting = true) else it },
                replyingTo = if (st.replyingTo?.id == messageId) null else st.replyingTo,
            )
        }
        scope.launch {
            delay(FADE_OUT_MS)
            _state.update { st -> st.copy(messages = st.messages.filterNot { it.id == messageId }) }
        }
    }

    private inline fun <reified T> decode(raw: String): T? =
        runCatching { json.decodeFromString<T>(raw) }.getOrNull()

    private fun MsgAuthorDto?.name(fallbackId: String): String =
        this?.displayName ?: this?.username ?: if (fallbackId == myId) "voce" else "alguem"

    private fun ChannelMessageDto.toChat() = ChatMessage(
        id = id, content = content, authorId = authorId,
        authorName = author.name(authorId), authorAvatar = author?.avatarUrl,
        createdAt = createdAt,
        mine = authorId == myId, edited = edited,
        reactions = reactions, replyTo = replyTo,
    )

    private fun DmMessageDto.toChat() = ChatMessage(
        id = id, content = content, authorId = senderId,
        authorName = author.name(senderId), authorAvatar = author?.avatarUrl,
        createdAt = createdAt,
        mine = senderId == myId,
        replyTo = replyTo,
    )
}
