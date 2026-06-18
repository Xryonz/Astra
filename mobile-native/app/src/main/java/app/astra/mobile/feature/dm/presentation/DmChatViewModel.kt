package app.astra.mobile.feature.dm.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.dm.domain.DmRepository
import app.astra.mobile.feature.dm.domain.model.DmMessage
import dagger.hilt.android.lifecycle.HiltViewModel
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
        loadHistory()
        observeIncoming()
        observeDeleted()
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

    fun onInput(value: String) = _state.update { it.copy(input = value) }

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
        _state.update { it.copy(sending = true, input = "", error = null) }
        viewModelScope.launch {
            repository.send(conversationId, text)
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
        repository.leaveConversation(conversationId)
    }
}
