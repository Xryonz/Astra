package app.astra.mobile.feature.channel.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.channel.domain.ChannelRepository
import app.astra.mobile.feature.channel.domain.model.ChannelMessage
import dagger.hilt.android.lifecycle.HiltViewModel
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

    private fun addMessage(msg: ChannelMessage) {
        _state.update { s ->
            if (s.messages.any { it.id == msg.id }) s
            else s.copy(messages = s.messages + msg)
        }
    }

    fun onInput(value: String) = _state.update { it.copy(input = value) }

    // Long-press "Editar" -> entra em modo edicao reusando o input.
    fun startEdit(messageId: String, content: String) =
        _state.update { it.copy(editingId = messageId, input = content, error = null) }

    fun cancelEdit() = _state.update { it.copy(editingId = null, input = "") }

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
        _state.update { it.copy(sending = true, input = "", error = null) }
        viewModelScope.launch {
            repository.send(channelId, text)
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
        repository.leaveChannel(channelId)
    }
}
