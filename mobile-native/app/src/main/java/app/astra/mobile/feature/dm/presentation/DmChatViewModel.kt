package app.astra.mobile.feature.dm.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.model.toModel
import app.astra.mobile.core.realtime.SocketManager
import app.astra.mobile.core.share.DmShortcuts
import app.astra.mobile.core.translate.Translator
import app.astra.mobile.core.upload.ImageUploader
import app.astra.mobile.core.upload.UploadFile
import app.astra.mobile.feature.dm.domain.DmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DmChatViewModel @Inject constructor(
    private val repository: DmRepository,
    private val imageUploader: ImageUploader,
    private val translator: Translator,
    private val socketManager: SocketManager,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val conversationId: String = savedStateHandle["conversationId"] ?: ""
    val otherName: String = savedStateHandle["name"] ?: "Conversa"

    private val _state = MutableStateFlow(DmChatUiState())
    val state = _state.asStateFlow()

    // Dispara quando o outro lado ACEITA a ligacao -> a tela navega pro CallScreen.
    private val _joinCall = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val joinCall = _joinCall.asSharedFlow()

    private var otherUserId: String? = null
    private var ringTimeout: Job? = null

    init {
        repository.joinConversation(conversationId)
        viewModelScope.launch { repository.markRead(conversationId) }
        observeMessages()
        loadHistory()
        observeTyping()
        observeCallSignals()
        viewModelScope.launch {
            repository.conversations().onSuccess { list ->
                val conv = list.find { it.id == conversationId }
                otherUserId = conv?.otherUserId
                _state.update { it.copy(muted = conv?.muted == true) }
                // Atalho dinamico (launcher + Direct Share) pra conversa aberta.
                conv?.let {
                    launch(Dispatchers.IO) {
                        DmShortcuts.push(appContext, conversationId, it.otherName, it.otherAvatarUrl)
                    }
                }
            }
        }
    }

    // Otimista: UI muda na hora; erro reverte.
    fun toggleMute() {
        val target = !_state.value.muted
        _state.update { it.copy(muted = target) }
        viewModelScope.launch {
            repository.setMuted(conversationId, target)
                .onFailure { _state.update { it.copy(muted = !target) } }
        }
    }

    // Fluxo de quem LIGA (o modal de quem recebe fica global no AstraApp):
    // invite -> tocando (30s) -> aceito = entra na sala | recusado/timeout = para.
    fun startCall() {
        val other = otherUserId ?: return
        if (_state.value.ringing) return
        socketManager.sendDmCallInvite(conversationId, other)
        _state.update { it.copy(ringing = true) }
        ringTimeout?.cancel()
        ringTimeout = viewModelScope.launch {
            delay(30_000)
            _state.update { it.copy(ringing = false) }
        }
    }

    fun cancelCall() {
        val other = otherUserId ?: return
        ringTimeout?.cancel()
        socketManager.sendDmCallReject(conversationId, other)
        _state.update { it.copy(ringing = false) }
    }

    private fun observeCallSignals() {
        viewModelScope.launch {
            socketManager.dmCallAccept.collect { convId ->
                if (convId == conversationId && _state.value.ringing) {
                    ringTimeout?.cancel()
                    _state.update { it.copy(ringing = false) }
                    _joinCall.tryEmit(Unit)
                }
            }
        }
        viewModelScope.launch {
            socketManager.dmCallReject.collect { convId ->
                if (convId == conversationId && _state.value.ringing) {
                    ringTimeout?.cancel()
                    _state.update { it.copy(ringing = false) }
                }
            }
        }
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
