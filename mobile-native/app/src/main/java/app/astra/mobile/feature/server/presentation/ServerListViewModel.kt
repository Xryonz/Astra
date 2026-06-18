package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.server.domain.ServerRepository
import app.astra.mobile.feature.server.domain.model.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerListUiState(
    val loading: Boolean = true,
    val servers: List<Server> = emptyList(),
    val error: String? = null,
    val creating: Boolean = false,
    val createError: String? = null,
)

@HiltViewModel
class ServerListViewModel @Inject constructor(
    private val repository: ServerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerListUiState())
    val state = _state.asStateFlow()

    // Servidor criado -> a tela fecha o dialog.
    private val _created = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val created = _created.asSharedFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.servers()
                .onSuccess { list -> _state.update { it.copy(loading = false, servers = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Erro inesperado") } }
        }
    }

    fun createServer(name: String) {
        if (_state.value.creating || name.isBlank()) return
        _state.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            repository.createServer(name)
                .onSuccess {
                    _state.update { it.copy(creating = false) }
                    _created.tryEmit(Unit)
                    load()
                }
                .onFailure { e -> _state.update { it.copy(creating = false, createError = e.message ?: "Erro inesperado") } }
        }
    }

    fun clearCreateError() = _state.update { it.copy(createError = null) }
}
