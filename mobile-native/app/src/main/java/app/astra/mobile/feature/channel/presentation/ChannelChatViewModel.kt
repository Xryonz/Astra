package app.astra.mobile.feature.channel.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.model.toModel
import app.astra.mobile.core.upload.ImageUploader
import app.astra.mobile.core.upload.UploadFile
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

    val editingId: String? = null,

    val replyToId: String? = null,
    val replyToAuthor: String? = null,
    val replyToPreview: String? = null,

    val typingUsers: List<String> = emptyList(),

    val pinned: List<ChannelMessage> = emptyList(),

    val pendingAttachments: List<Attachment> = emptyList(),
    val uploading: Boolean = false,
)

@HiltViewModel
class ChannelChatViewModel @Inject constructor(
    private val repository: ChannelRepository,
    private val imageUploader: ImageUploader,
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
        _state.update { it.copy(input = value) }
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

    fun send() {
        val text = _state.value.input.trim()
        val pending = _state.value.pendingAttachments
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
                    _state.update { it.copy(sending = false, error = e.message, input = text, pendingAttachments = pending) }
                }
        }
    }

    override fun onCleared() {
        stopTypingNow()
        repository.leaveChannel(channelId)
    }
}
