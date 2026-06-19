package app.astra.mobile.feature.channel.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.channel.domain.ChannelRepository
import app.astra.mobile.feature.channel.domain.model.ChannelMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelChatUiState(
    val loading: Boolean = true,
    val messages: List<ChannelMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    // id da mensagem em edicao (null = compondo nova). Reusa o input.
    val editingId: String? = null,
    // alvo de resposta (null = sem responder). author/preview alimentam o banner.
    val replyToId: String? = null,
    val replyToAuthor: String? = null,
    val replyToPreview: String? = null,
    // usernames digitando agora (exclui voce — o server nao ecoa pro emissor).
    val typingUsers: List<String> = emptyList(),
    // mensagens fixadas (carregadas sob demanda pro sheet de Fixadas).
    val pinned: List<ChannelMessage> = emptyList(),
)

@HiltViewModel
class ChannelChatViewModel @Inject constructor(
    private val repository: ChannelRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val channelId: String = savedStateHandle["channelId"] ?: ""
    val channelName: String = savedStateHandle["name"] ?: "canal"

    private val _state = MutableStateFlow(ChannelChatUiState())
    val state = _state.asStateFlow()

    init {
        repository.joinChannel(channelId)
        loadHistory()
        observeIncoming()
        observeDeleted()
        observeEdited()
        observeReactions()
        observeTyping()
        observePinned()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repository.messages(channelId, null)
                .onSuccess { page -> _state.update { it.copy(loading = false, messages = page.messages) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    // new_message chega aqui (inclusive a propria) — dedupe por id.
    private fun observeIncoming() {
        viewModelScope.launch {
            repository.incomingMessages(channelId).collect(::addMessage)
        }
    }

    private fun observeDeleted() {
        viewModelScope.launch {
            repository.deletedMessages(channelId).collect { deletedId ->
                _state.update { s -> s.copy(messages = s.messages.filterNot { it.id == deletedId }) }
            }
        }
    }

    // message_edited (de qualquer autor) -> troca o conteudo + marca edited.
    private fun observeEdited() {
        viewModelScope.launch {
            repository.editedMessages(channelId).collect { (id, content) ->
                _state.update { s ->
                    s.copy(messages = s.messages.map { if (it.id == id) it.copy(content = content, edited = true) else it })
                }
            }
        }
    }

    // reaction_update -> substitui a lista de reacoes da msg.
    private fun observeReactions() {
        viewModelScope.launch {
            repository.reactionUpdates(channelId).collect { (id, reactions) ->
                _state.update { s ->
                    s.copy(messages = s.messages.map { if (it.id == id) it.copy(reactions = reactions) else it })
                }
            }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch { repository.react(channelId, messageId, emoji) }
    }

    // message_pinned -> marca/desmarca pinned na msg da lista.
    private fun observePinned() {
        viewModelScope.launch {
            repository.pinnedUpdates(channelId).collect { (id, pinned) ->
                _state.update { s ->
                    s.copy(messages = s.messages.map { if (it.id == id) it.copy(pinned = pinned) else it })
                }
            }
        }
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

    // ── Digitando ────────────────────────────────────────────────
    // typingNames: userId -> username (stop nao traz username). Expira em 6s
    // caso o evento de stop se perca.
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

    // Emite typing_start na 1a tecla, reinicia o timer de stop (3s sem digitar).
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

    private fun addMessage(msg: ChannelMessage) {
        _state.update { s ->
            if (s.messages.any { it.id == msg.id }) s
            else s.copy(messages = s.messages + msg)
        }
    }

    fun onInput(value: String) {
        _state.update { it.copy(input = value) }
        handleTyping(value)
    }

    // Long-press "Editar" -> entra em modo edicao reusando o input.
    fun startEdit(messageId: String, content: String) =
        _state.update { it.copy(editingId = messageId, input = content, error = null) }

    fun cancelEdit() = _state.update { it.copy(editingId = null, input = "") }

    // Responder: guarda o alvo (cancela edicao se estava editando).
    fun startReply(messageId: String, author: String, preview: String) =
        _state.update {
            it.copy(replyToId = messageId, replyToAuthor = author, replyToPreview = preview, editingId = null)
        }

    fun cancelReply() =
        _state.update { it.copy(replyToId = null, replyToAuthor = null, replyToPreview = null) }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.delete(channelId, messageId)
                .onSuccess {
                    _state.update { s -> s.copy(messages = s.messages.filterNot { it.id == messageId }) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.sending) return
        stopTypingNow()
        val editing = _state.value.editingId
        if (editing != null) {
            _state.update { it.copy(sending = true, input = "", editingId = null, error = null) }
            viewModelScope.launch {
                repository.edit(channelId, editing, text)
                    .onSuccess {
                        _state.update { s ->
                            s.copy(
                                sending = false,
                                messages = s.messages.map { if (it.id == editing) it.copy(content = text, edited = true) else it },
                            )
                        }
                    }
                    .onFailure { e ->
                        _state.update { it.copy(sending = false, error = e.message, input = text, editingId = editing) }
                    }
            }
            return
        }
        val replyId = _state.value.replyToId
        _state.update {
            it.copy(sending = true, input = "", error = null, replyToId = null, replyToAuthor = null, replyToPreview = null)
        }
        viewModelScope.launch {
            repository.send(channelId, text, replyId)
                .onSuccess { msg ->
                    addMessage(msg)
                    _state.update { it.copy(sending = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(sending = false, error = e.message, input = text) }
                }
        }
    }

    override fun onCleared() {
        stopTypingNow()
        repository.leaveChannel(channelId)
    }
}
