package app.astra.mobile.feature.discover.presentation

import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.DiscoverApi
import app.astra.mobile.core.network.dto.DiscoverServerDto
import app.astra.mobile.feature.server.domain.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class DiscoverUiState(
    val loading: Boolean = true,
    val query: String = "",
    val servers: List<DiscoverServerDto> = emptyList(),
    val error: String? = null,
    val joiningId: String? = null,
    val joined: Pair<String, String>? = null,
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val api: DiscoverApi,
    private val serverRepo: ServerRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverUiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null
    // Ids das constelacoes que o user ja orbita: some da Descoberta (nao faz
    // sentido "descobrir" o que voce ja entrou).
    private var joinedIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            joinedIds = serverRepo.servers().getOrNull()?.mapTo(HashSet()) { it.id } ?: emptySet()
            load(null)
        }
    }

    private fun load(q: String?) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { api.discover(q?.takeIf { s -> s.isNotBlank() }).data.orEmpty() }
                .onSuccess { list ->
                    _state.update { it.copy(loading = false, servers = list.filterNot { s -> s.id in joinedIds }) }
                }
                .onFailure { _state.update { it.copy(loading = false, error = "Falha ao carregar a Descoberta") } }
        }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            load(q)
        }
    }

    fun join(server: DiscoverServerDto) {
        if (_state.value.joiningId != null) return
        _state.update { it.copy(joiningId = server.id, error = null) }
        viewModelScope.launch {
            try {
                api.join(server.id)
                joinedIds = joinedIds + server.id
                _state.update { it.copy(joiningId = null, joined = server.id to server.name, servers = it.servers.filterNot { s -> s.id == server.id }) }
            } catch (e: HttpException) {
                if (e.code() == 409) {
                    joinedIds = joinedIds + server.id
                    _state.update { it.copy(joiningId = null, joined = server.id to server.name, servers = it.servers.filterNot { s -> s.id == server.id }) }
                } else {
                    _state.update { it.copy(joiningId = null, error = "Nao foi possivel entrar") }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(joiningId = null, error = "Sem conexao com o servidor") }
            }
        }
    }

    fun consumeJoined() = _state.update { it.copy(joined = null) }
}
