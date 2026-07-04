package app.astra.mobile.feature.dm.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.dm.domain.DmRepository
import app.astra.mobile.feature.dm.domain.model.OpenedConversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DmListViewModel @Inject constructor(
    private val repository: DmRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DmListUiState())
    val state = _state.asStateFlow()

    private val _opened = MutableSharedFlow<OpenedConversation>(extraBufferCapacity = 1)
    val opened = _opened.asSharedFlow()

    init {
        load()
        observeIncoming()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val reads = repository.dmReads().getOrNull().orEmpty()
            repository.conversations()
                .onSuccess { list ->
                    val muted = list.filter { it.muted }.map { it.id }.toSet()
                    val unread = list
                        .filter { c ->
                            !c.lastFromMe && c.lastMessageAt?.let { last -> reads[c.id]?.let { last > it } ?: true } ?: false
                        }
                        .map { it.id }.toSet() - muted
                    _state.update { it.copy(loading = false, conversations = list, unread = unread) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Erro inesperado") } }
        }
    }

    fun markSeen(conversationId: String) = _state.update { it.copy(unread = it.unread - conversationId) }

    private fun observeIncoming() {
        viewModelScope.launch {
            repository.incomingConversations().collect { convId ->
                _state.update { s ->
                    val isMuted = s.conversations.any { it.id == convId && it.muted }
                    if (isMuted) s else s.copy(unread = s.unread + convId)
                }
            }
        }
    }

    fun openConversation(username: String) {
        if (_state.value.opening || username.isBlank()) return
        _state.update { it.copy(opening = true, openError = null) }
        viewModelScope.launch {
            repository.open(username)
                .onSuccess { conv ->
                    _state.update { it.copy(opening = false) }
                    _opened.tryEmit(conv)
                }
                .onFailure { e ->
                    _state.update { it.copy(opening = false, openError = e.message ?: "Erro inesperado") }
                }
        }
    }

    fun clearOpenError() = _state.update { it.copy(openError = null) }
}
