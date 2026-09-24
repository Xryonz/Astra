package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.BotApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.dto.BotComandoDto
import app.astra.mobile.core.network.dto.UpdateServerRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BotDaOrbitaUiState(
    val carregando: Boolean = true,
    val erro: String? = null,
    val comandos: List<BotComandoDto> = emptyList(),
    val desligados: Set<String> = emptySet(),
    val aviso: String? = null,
)

@HiltViewModel
class BotDaOrbitaViewModel @Inject constructor(
    private val botApi: BotApi,
    private val serverApi: ServerApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val orbitaId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(BotDaOrbitaUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val catalogo = async { runCatching { botApi.catalogo().data?.comandos.orEmpty() } }
            val orbita = async {
                runCatching { serverApi.servers().data.orEmpty().firstOrNull { it.id == orbitaId } }
            }
            val comandos = catalogo.await().getOrDefault(emptyList())
            val desligados = orbita.await().getOrNull()?.botDisabledCommands
                .orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            _state.update {
                if (comandos.isEmpty()) it.copy(carregando = false, erro = "Não foi possível carregar os comandos")
                else it.copy(carregando = false, comandos = comandos, desligados = desligados)
            }
        }
    }

    fun alternar(chave: String, ligado: Boolean) {
        val antes = _state.value.desligados
        val agora = if (ligado) antes - chave else antes + chave
        _state.update { it.copy(desligados = agora) }
        viewModelScope.launch {
            runCatching { serverApi.update(orbitaId, UpdateServerRequest(botDisabledCommands = agora.toList())) }
                .onFailure { _state.update { e -> e.copy(desligados = antes, aviso = "Não foi possível salvar") } }
        }
    }

    fun esquecerAviso() = _state.update { it.copy(aviso = null) }
}
