package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.server.domain.ServerRepository
import app.astra.mobile.feature.server.domain.model.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelListUiState(
    val loading: Boolean = true,
    val channels: List<Channel> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ChannelListViewModel @Inject constructor(
    private val repository: ServerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val serverId: String = savedStateHandle["serverId"] ?: ""
    val serverName: String = savedStateHandle["name"] ?: "Servidor"

    private val _state = MutableStateFlow(ChannelListUiState())
    val state = _state.asStateFlow()

    init { load() }

    // GET /api/servers ja traz os canais aninhados — pega o servidor desta tela.
    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.servers()
                .onSuccess { list ->
                    val server = list.find { it.id == serverId }
                    _state.update { it.copy(loading = false, channels = server?.channels ?: emptyList()) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Erro inesperado") } }
        }
    }
}
