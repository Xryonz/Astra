package app.astra.mobile.feature.dm.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.dm.domain.DmRepository
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
        observeMessages()
        loadHistory()
        observeTyping()
    }

    // SSOT: a tela so observa o cache (Room). new_dm/dm_deleted o repo dreno pro
    // banco; o historico (loadHistory) tambem grava no banco. Sem mutar lista na mao.
    private fun observeMessages() {
        viewModelScope.launch {
            repository.observeMessages(conversationId).collect { msgs ->
                _state.update { it.copy(messages = msgs) }
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repository.messages(conversationId, null)
                .onSuccess { _state.update { it.copy(loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
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
            // Sucesso: o repo ja removeu do Room -> a lista atualiza pelo observeMessages.
            repository.delete(conversationId, messageId)
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
            // Sucesso: o repo grava a msg no Room -> aparece pelo observeMessages.
            repository.send(conversationId, text, replyId)
                .onSuccess { _state.update { it.copy(sending = false) } }
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
