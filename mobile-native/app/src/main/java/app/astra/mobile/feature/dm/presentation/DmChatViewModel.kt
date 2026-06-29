package app.astra.mobile.feature.dm.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.model.toModel
import app.astra.mobile.core.translate.Translator
import app.astra.mobile.core.upload.ImageUploader
import app.astra.mobile.core.upload.UploadFile
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
    private val imageUploader: ImageUploader,
    private val translator: Translator,
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

    fun addAttachment(att: Attachment) =
        _state.update { it.copy(pendingAttachments = it.pendingAttachments + att) }

    fun removeAttachment(att: Attachment) =
        _state.update { it.copy(pendingAttachments = it.pendingAttachments - att) }

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
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun send() {
        val text = _state.value.input.trim()
        val pending = _state.value.pendingAttachments
        if ((text.isEmpty() && pending.isEmpty()) || _state.value.sending || _state.value.uploading) return
        stopTypingNow()
        val replyId = _state.value.replyToId
        _state.update {
            it.copy(sending = true, input = "", error = null, replyToId = null, replyToAuthor = null, replyToPreview = null, pendingAttachments = emptyList())
        }
        viewModelScope.launch {

            repository.send(conversationId, text, replyId, pending)
                .onSuccess { _state.update { it.copy(sending = false) } }
                .onFailure { e ->

                    _state.update { it.copy(sending = false, error = e.message, input = text, pendingAttachments = pending) }
                }
        }
    }

    override fun onCleared() {
        stopTypingNow()
        repository.leaveConversation(conversationId)
    }
}
