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
        viewModelScope.launch { repository.markRead(channelId) }
        observeMessages()
        loadHistory()
        observeTyping()
    }

    // SSOT: a tela so observa o cache (Room). new/deleted/edited/reaction/pinned
    // o repo dreno pro banco; o historico (loadHistory) tambem grava nele.
    private fun observeMessages() {
        viewModelScope.launch {
            repository.observeMessages(channelId).collect { msgs ->
                _state.update { it.copy(messages = msgs) }
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repository.messages(channelId, null)
                .onSuccess { _state.update { it.copy(loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
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
            // Sucesso: o repo ja removeu do Room -> a lista atualiza pelo observeMessages.
            repository.delete(channelId, messageId)
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
                // Sucesso: o socket message_edited aplica no Room -> a lista atualiza sozinha.
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
            it.copy(sending = true, input = "", error = null, replyToId = null, replyToAuthor = null, replyToPreview = null)
        }
        viewModelScope.launch {
            // Sucesso: o repo grava no Room -> aparece pelo observeMessages.
            repository.send(channelId, text, replyId)
                .onSuccess { _state.update { it.copy(sending = false) } }
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
