package app.astra.mobile.feature.invite.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.invite.domain.InvitesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JoinServerViewModel @Inject constructor(
    private val repository: InvitesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinServerUiState())
    val state = _state.asStateFlow()

    private val _joined = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val joined = _joined.asSharedFlow()

    fun setCode(value: String) = _state.update {
        it.copy(code = value, previewError = null, joinError = null)
    }

    fun loadPreview() {
        val code = extractCode(_state.value.code)
        if (code.isBlank() || _state.value.loadingPreview) return
        _state.update { it.copy(loadingPreview = true, previewError = null, preview = null) }
        viewModelScope.launch {
            repository.preview(code)
                .onSuccess { p -> _state.update { it.copy(loadingPreview = false, preview = p) } }
                .onFailure { e -> _state.update { it.copy(loadingPreview = false, previewError = e.message ?: "Convite invalido") } }
        }
    }

    fun join() {
        val code = extractCode(_state.value.code)
        if (code.isBlank() || _state.value.joining) return
        _state.update { it.copy(joining = true, joinError = null) }
        viewModelScope.launch {
            repository.join(code)
                .onSuccess { server ->
                    _state.update { it.copy(joining = false) }
                    _joined.tryEmit(server.id to server.name)
                }
                .onFailure { e -> _state.update { it.copy(joining = false, joinError = e.message ?: "Nao foi possivel entrar") } }
        }
    }

    private fun extractCode(raw: String): String {
        var s = raw.trim().substringBefore('?').substringBefore('#').trimEnd('/')
        if (s.contains('/')) s = s.substringAfterLast('/')
        return s
    }
}
