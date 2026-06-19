package app.astra.mobile.feature.dm.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.dm.domain.DmRepository
import app.astra.mobile.feature.dm.domain.model.DmMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DmChatViewModel @Inject constructor(
    private val repository: DmRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val conversationId: String = savedStateHandle["conversationId"] ?: ""
    val otherName: String = savedStateHandle["name"] ?: "Conversa"

    private val _state = MutableStateFlow(DmChatUiState())
    val state = _state.asStateFlow()

    init {
        repository.joinConversation(conversationId)
        viewModelScope.launch { repository.markRead(conversationId) }
        loadHistory()
        observeIncoming()
        observeDeleted()
        observeTyping()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repository.messages(conversationId, null)
                .onSuccess { page -> _state.update { it.copy(loading = false, messages = page.messages) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    // new_dm chega aqui (inclusive a propria msg que mandei) — dedupe por id.
    private fun observeIncoming() {
        viewModelScope.launch {
            repository.incomingMessages(conversationId).collect(::addMessage)
        }
    }

    private fun observeDeleted() {
        viewModelScope.launch {
            repository.deletedMessages(conversationId).collect { deletedId ->
                _state.update { s -> s.copy(messages = s.messages.filterNot { it.id == deletedId }) }
            }
        }
    }

    private fun addMessage(msg: DmMessage) {
        _state.update { s ->
            if (s.messages.any { it.id == msg.id }) s
            else s.copy(messages = s.messages + msg)
        }
    }

    fun onInput(value: String) {
        _state.update { it.copy(input = value) }
        handleTyping(value)
    }

    // ── Digitando (espelha o do canal, com dm_typing_*) ──────────
    private val typingNames = linkedMapOf<String, String>()
    private val typingExpiry = mutableMapOf<String, Job>()

    private fun observeTyping() {
        viewModelScope.launch {
            repository.typingEvents(conversationId).collect { ev ->
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
        if (!typingSent) { typingSent = true; repository.startTyping(conversationId) }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch { delay(3_000); stopTypingNow() }
    }

    private fun stopTypingNow() {
        typingStopJob?.cancel(); typingStopJob = null
        if (typingSent) { typingSent = false; repository.stopTyping(conversationId) }
    }

    fun startReply(messageId: String, author: String, preview: String) =
        _state.update { it.copy(replyToId = messageId, replyToAuthor = author, replyToPreview = preview) }

    fun cancelReply() =
        _state.update { it.copy(replyToId = null, replyToAuthor = null, replyToPreview = null) }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.delete(conversationId, messageId)
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
        val replyId = _state.value.replyToId
        _state.update {
            it.copy(sending = true, input = "", error = null, replyToId = null, replyToAuthor = null, replyToPreview = null)
        }
        viewModelScope.launch {
            repository.send(conversationId, text, replyId)
                .onSuccess { msg ->
                    addMessage(msg)
                    _state.update { it.copy(sending = false) }
                }
                .onFailure { e ->
                    // devolve o texto pro campo pra nao perder o que foi digitado
                    _state.update { it.copy(sending = false, error = e.message, input = text) }
                }
        }
    }

    override fun onCleared() {
        stopTypingNow()
        repository.leaveConversation(conversationId)
    }
}
