package app.astra.mobile.feature.channel.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.model.comoAnexo
import app.astra.mobile.core.model.toModel
import app.astra.mobile.core.network.dto.ServerStickerDto
import app.astra.mobile.core.network.NotificationsApi
import app.astra.mobile.core.network.dto.NotifModeRequest
import app.astra.mobile.core.translate.Translator
import android.net.Uri
import app.astra.mobile.core.upload.AndamentoDoVideo
import app.astra.mobile.core.upload.FilaDeVideos
import app.astra.mobile.core.upload.ImageUploader
import app.astra.mobile.core.upload.PreparadorDeVideo
import app.astra.mobile.core.upload.UploadFile
import app.astra.mobile.feature.channel.domain.ChannelRepository
import app.astra.mobile.feature.channel.domain.model.ChannelMessage
import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.ui.components.ChatRow
import app.astra.mobile.ui.components.ConversaMontada
import app.astra.mobile.ui.components.MensagemPendente
import app.astra.mobile.ui.components.PollOptionUi
import app.astra.mobile.ui.components.PollUi
import app.astra.mobile.ui.components.ReactionChip
import app.astra.mobile.ui.components.Remetente
import app.astra.mobile.ui.components.linhasPendentes
import app.astra.mobile.ui.components.montarConversa
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

private const val ESPERA_PELO_BANCO_MS = 2_000L

data class ChannelChatUiState(
    val loading: Boolean = true,
    val messages: List<ChannelMessage> = emptyList(),
    val cursorAnterior: String? = null,
    val carregandoAntigas: Boolean = false,
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,

    val editingId: String? = null,

    val replyToId: String? = null,
    val replyToAuthor: String? = null,
    val replyToPreview: String? = null,

    val typingUsers: List<String> = emptyList(),

    val pinned: List<ChannelMessage> = emptyList(),

    val pendingAttachments: List<Attachment> = emptyList(),
    val uploading: Boolean = false,
    val videoEmPreparo: AndamentoDoVideo? = null,

    val translations: Map<String, String> = emptyMap(),
    val translatingIds: Set<String> = emptySet(),


    val notifMode: String? = null,

    val pendentes: List<MensagemPendente> = emptyList(),
    val eu: Remetente? = null,
) {
    fun comMensagens(msgs: List<ChannelMessage>, chegaram: Set<String>): ChannelChatUiState {
        val restantes = pendentes.filterNot { it.nonce in chegaram }
        if (restantes.size == pendentes.size) return copy(messages = msgs)
        val falhaResolvida = pendentes.any { it.falhou && it.nonce in chegaram }
        return copy(messages = msgs, pendentes = restantes, error = if (falhaResolvida) null else error)
    }
}

private fun ChannelMessage.comoLinha(traducoes: Map<String, String>) = ChatRow(
    id = id,
    mine = mine,
    authorId = authorId,
    authorName = authorName,
    authorAvatar = authorAvatar,
    authorColor = authorColor,
    authorFont = authorFont,
    content = content,
    edited = edited,
    pinned = pinned,
    reactions = reactions.map { ReactionChip(it.emoji, it.count, it.mine) },
    replyAuthor = replyToAuthor,
    replyContent = replyToContent,
    attachments = attachments,
    translation = traducoes[id],
    kind = kind,
    criadaEm = createdAt,
    mencionaVoce = mencionaVoce,
    poll = poll?.let { p ->
        PollUi(
            question = p.question,
            options = p.options.map { o -> PollOptionUi(o.id, o.text, o.votes, o.mine) },
            allowMultiple = p.allowMultiple,
            expiresAt = p.expiresAt,
            closed = p.closed,
        )
    },
    nonce = clientNonce.takeIf { mine },
)

@HiltViewModel
class ChannelChatViewModel @Inject constructor(
    private val repository: ChannelRepository,
    private val imageUploader: ImageUploader,
    private val preparadorDeVideo: PreparadorDeVideo,
    private val translator: Translator,
    private val notificationsApi: NotificationsApi,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val channelId: String = savedStateHandle["channelId"] ?: ""
    val channelName: String = savedStateHandle["name"] ?: "canal"
    val orbitaId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(ChannelChatUiState())
    val state = _state.asStateFlow()

    private val filaDeEnvio = Mutex()

    val conversa: StateFlow<ConversaMontada> = _state
        .distinctUntilChanged { antes, depois ->
            antes.messages === depois.messages && antes.translations === depois.translations &&
                antes.pendentes === depois.pendentes && antes.eu === depois.eu
        }
        .map { st ->
            val confirmadas = st.messages.mapNotNullTo(HashSet()) { it.clientNonce }
            val minhaCor = st.messages.lastOrNull { it.mine }?.authorColor
            montarConversa(st.messages.map { it.comoLinha(st.translations) } + linhasPendentes(st.pendentes, confirmadas, st.eu, minhaCor))
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConversaMontada.VAZIA)

    init {
        repository.joinChannel(channelId)
        viewModelScope.launch { repository.markRead(channelId) }
        observeMessages()
        loadHistory()
        observeTyping()
        loadNotifPref()
        viewModelScope.launch {
            userRepository.me().onSuccess { p ->
                _state.update { it.copy(eu = Remetente(p.id, p.displayName, p.avatarUrl, p.displayFont)) }
            }
        }
    }

    private fun loadNotifPref() {
        viewModelScope.launch {
            try {
                val mode = notificationsApi.channelNotifPrefs().data
                    ?.firstOrNull { it.channelId == channelId }?.mode
                _state.update { it.copy(notifMode = mode) }
            } catch (_: Exception) {}
        }
    }

    fun setNotifMode(mode: String?) {
        val previous = _state.value.notifMode
        _state.update { it.copy(notifMode = mode) }
        viewModelScope.launch {
            try {
                if (mode == null) notificationsApi.clearChannelNotifPref(channelId)
                else notificationsApi.setChannelNotifPref(channelId, NotifModeRequest(mode))
            } catch (_: Exception) {
                _state.update { it.copy(notifMode = previous) }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            repository.observeMessages(channelId).collect { msgs ->
                val chegaram = msgs.mapNotNullTo(HashSet()) { it.clientNonce }
                _state.update { st -> st.comMensagens(msgs, chegaram) }
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repository.messages(channelId, null)
                .onSuccess { pagina ->
                    if (pagina.messages.isNotEmpty()) {
                        withTimeoutOrNull(ESPERA_PELO_BANCO_MS) { _state.first { it.messages.isNotEmpty() } }
                    }
                    _state.update { it.copy(loading = false, cursorAnterior = pagina.nextCursor.takeIf { pagina.hasMore }) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun carregarAntigas() {
        val cursor = _state.value.cursorAnterior ?: return
        if (_state.value.carregandoAntigas) return
        _state.update { it.copy(carregandoAntigas = true) }
        viewModelScope.launch {
            repository.messages(channelId, cursor)
                .onSuccess { pagina ->
                    val umaDaPagina = pagina.messages.firstOrNull()?.id
                    if (umaDaPagina != null) {
                        withTimeoutOrNull(ESPERA_PELO_BANCO_MS) { conversa.first { c -> c.rows.any { it.id == umaDaPagina } } }
                    }
                    val proximo = pagina.nextCursor.takeIf { pagina.hasMore && pagina.messages.isNotEmpty() }
                    _state.update { it.copy(carregandoAntigas = false, cursorAnterior = proximo) }
                }
                .onFailure { _state.update { it.copy(carregandoAntigas = false) } }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch { repository.react(channelId, messageId, emoji) }
    }

    fun togglePin(messageId: String, pinned: Boolean) {
        viewModelScope.launch {
            repository.pin(channelId, messageId, pinned)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun loadPinned() {
        viewModelScope.launch {
            repository.pinnedMessages(channelId)
                .onSuccess { list -> _state.update { it.copy(pinned = list) } }
        }
    }

    private val typingNames = linkedMapOf<String, String>()
    private val typingExpiry = mutableMapOf<String, Job>()

    private fun observeTyping() {
        viewModelScope.launch {
            repository.typingEvents(channelId).collect { ev ->
                if (ev.typing) {
                    typingNames[ev.userId] = ev.username
                    typingExpiry[ev.userId]?.cancel()
                    typingExpiry[ev.userId] = viewModelScope.launch {
                        delay(6_000)
                        typingNames.remove(ev.userId); typingExpiry.remove(ev.userId); pushTyping()
                    }
                } else {
                    typingNames.remove(ev.userId)
                    typingExpiry.remove(ev.userId)?.cancel()
                }
                pushTyping()
            }
        }
    }

    private fun pushTyping() = _state.update { it.copy(typingUsers = typingNames.values.toList()) }

    private var typingSent = false
    private var typingStopJob: Job? = null
    private fun handleTyping(value: String) {
        if (value.isBlank()) { stopTypingNow(); return }
        if (!typingSent) { typingSent = true; repository.startTyping(channelId) }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch { delay(3_000); stopTypingNow() }
    }

    private fun stopTypingNow() {
        typingStopJob?.cancel(); typingStopJob = null
        if (typingSent) { typingSent = false; repository.stopTyping(channelId) }
    }

    fun onInput(value: String) {
        handleTyping(value)
    }

    fun attachImages(files: List<UploadFile>) {
        if (files.isEmpty()) return
        _state.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            imageUploader.uploadMany(files)
                .onSuccess { dtos ->
                    _state.update { it.copy(uploading = false, pendingAttachments = it.pendingAttachments + dtos.map { d -> d.toModel() }) }
                }
                .onFailure { e -> _state.update { it.copy(uploading = false, error = e.message) } }
        }
    }

    private val filaDeVideos = FilaDeVideos(
        escopo = viewModelScope,
        preparador = preparadorDeVideo,
        uploader = imageUploader,
        aoAndar = { andamento -> _state.update { it.copy(videoEmPreparo = andamento) } },
        aoAnexar = { anexos -> _state.update { it.copy(pendingAttachments = it.pendingAttachments + anexos) } },
        aoFalhar = { motivo -> _state.update { it.copy(error = motivo) } },
    )

    fun attachVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(error = null) }
        filaDeVideos.adicionar(uris)
    }

    fun cancelarVideo(posicao: Int) = filaDeVideos.cancelar(posicao)

    fun translate(messageId: String, content: String) {
        val st = _state.value
        if (st.translations.containsKey(messageId)) {
            _state.update { it.copy(translations = it.translations - messageId) }
            return
        }
        if (messageId in st.translatingIds || content.isBlank()) return
        _state.update { it.copy(translatingIds = it.translatingIds + messageId) }
        viewModelScope.launch {
            translator.translate(content)
                .onSuccess { t -> _state.update { it.copy(translations = it.translations + (messageId to t), translatingIds = it.translatingIds - messageId) } }
                .onFailure { e -> _state.update { it.copy(translatingIds = it.translatingIds - messageId, error = e.message) } }
        }
    }

    fun votePoll(messageId: String, optionId: String) {
        viewModelScope.launch {
            repository.votePoll(channelId, messageId, optionId)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun closePoll(messageId: String) {
        viewModelScope.launch {
            repository.closePoll(channelId, messageId)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun createPoll(question: String, options: List<String>, allowMultiple: Boolean, durationHours: Int?) {
        viewModelScope.launch {
            repository.createPoll(channelId, question, options, allowMultiple, durationHours)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun addAttachment(att: Attachment) =
        _state.update { it.copy(pendingAttachments = it.pendingAttachments + att) }

    fun removeAttachment(att: Attachment) =
        _state.update { it.copy(pendingAttachments = it.pendingAttachments - att) }

    fun startEdit(messageId: String, content: String) =
        _state.update { it.copy(editingId = messageId, input = content, error = null) }

    fun cancelEdit() = _state.update { it.copy(editingId = null, input = "") }

    fun startReply(messageId: String, author: String, preview: String) =
        _state.update {
            it.copy(replyToId = messageId, replyToAuthor = author, replyToPreview = preview, editingId = null)
        }

    fun cancelReply() =
        _state.update { it.copy(replyToId = null, replyToAuthor = null, replyToPreview = null) }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {

            repository.delete(channelId, messageId)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun enviarFigurinha(figurinha: ServerStickerDto) {
        if (_state.value.sending) return
        val replyId = _state.value.replyToId
        _state.update {
            it.copy(sending = true, error = null, replyToId = null, replyToAuthor = null, replyToPreview = null)
        }
        viewModelScope.launch {
            repository.send(channelId, "", replyId, listOf(figurinha.comoAnexo()))
                .onSuccess { _state.update { it.copy(sending = false) } }
                .onFailure { e -> _state.update { it.copy(sending = false, error = e.message) } }
        }
    }

    fun send(rascunho: String) {
        val text = rascunho.trim()
        val pending = _state.value.pendingAttachments
        if (text.isNotEmpty() && pending.isEmpty() && _state.value.editingId == null) {
            enviarNaHora(text)
            return
        }
        if ((text.isEmpty() && pending.isEmpty()) || _state.value.sending || _state.value.uploading) return
        stopTypingNow()
        val editing = _state.value.editingId
        if (editing != null) {
            _state.update { it.copy(sending = true, input = "", editingId = null, error = null) }
            viewModelScope.launch {

                repository.edit(channelId, editing, text)
                    .onSuccess { _state.update { it.copy(sending = false) } }
                    .onFailure { e ->
                        _state.update { it.copy(sending = false, error = e.message, input = text, editingId = editing) }
                    }
            }
            return
        }
        val replyId = _state.value.replyToId
        _state.update {
            it.copy(sending = true, input = "", error = null, replyToId = null, replyToAuthor = null, replyToPreview = null, pendingAttachments = emptyList())
        }
        viewModelScope.launch {

            repository.send(channelId, text, replyId, pending)
                .onSuccess { _state.update { it.copy(sending = false) } }
                .onFailure { e ->
                    _state.update { it.copy(sending = false, error = e.message, input = text, pendingAttachments = pending + it.pendingAttachments) }
                }
        }
    }

    private fun enviarNaHora(text: String) {
        stopTypingNow()
        val atual = _state.value
        val pendente = MensagemPendente(
            nonce = UUID.randomUUID().toString(),
            conteudo = text,
            criadaEm = Instant.now().toString(),
            respostaId = atual.replyToId,
            respostaAutor = atual.replyToAuthor,
            respostaConteudo = atual.replyToPreview,
        )
        _state.update {
            it.copy(input = "", error = null, replyToId = null, replyToAuthor = null, replyToPreview = null, pendentes = it.pendentes + pendente)
        }
        enviarPendente(pendente)
    }

    private fun enviarPendente(pendente: MensagemPendente) {
        viewModelScope.launch {
            filaDeEnvio.withLock {
                repository.send(channelId, pendente.conteudo, pendente.respostaId, clientNonce = pendente.nonce)
            }
                .onSuccess {
                    withTimeoutOrNull(ESPERA_PELO_BANCO_MS) {
                        _state.first { st -> st.messages.any { it.clientNonce == pendente.nonce } }
                    }
                    _state.update { st -> st.copy(pendentes = st.pendentes.filterNot { it.nonce == pendente.nonce }) }
                }
                .onFailure { e ->
                    _state.update { st ->
                        st.copy(
                            error = e.message,
                            pendentes = st.pendentes.map { if (it.nonce == pendente.nonce) it.copy(falhou = true) else it },
                        )
                    }
                }
        }
    }

    fun tentarDeNovo(nonce: String?) {
        val antiga = _state.value.pendentes.firstOrNull { it.nonce == nonce && it.falhou } ?: return
        val pendente = antiga.copy(falhou = false, criadaEm = Instant.now().toString())
        _state.update { st ->
            st.copy(error = null, pendentes = st.pendentes.map { if (it.nonce == nonce) pendente else it })
        }
        enviarPendente(pendente)
    }

    fun descartarPendente(nonce: String?) {
        _state.update { st -> st.copy(pendentes = st.pendentes.filterNot { it.nonce == nonce }) }
    }

    override fun onCleared() {
        stopTypingNow()
        repository.leaveChannel(channelId)
    }
}
