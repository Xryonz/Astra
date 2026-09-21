package app.astra.mobile.feature.namecolors.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.dto.MyColorRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val HEX_RE = Regex("^#[0-9a-fA-F]{6}$")
private val HEX6 = Regex("#[0-9a-fA-F]{6}")
private val DEGRADE_DO_SERVIDOR = Regex("^gradient:\\d+:#[0-9a-fA-F]{6}:#[0-9a-fA-F]{6}$")
private val GRAUS = Regex("(-?\\d{1,3})deg")

internal fun paraFormatoDoServidor(cru: String): String? {
    val v = cru.trim()
    if (HEX_RE.matches(v) || DEGRADE_DO_SERVIDOR.matches(v)) return v
    if (!v.startsWith("linear-gradient")) return null
    val cores = HEX6.findAll(v).map { it.value }.toList()
    if (cores.size < 2) return null
    val graus = GRAUS.find(v)?.groupValues?.get(1)?.toIntOrNull() ?: 135
    return "gradient:${((graus % 360) + 360) % 360}:${cores.first()}:${cores.last()}"
}

data class NameColorServer(val id: String, val name: String, val isGroup: Boolean)

data class NameColorsUiState(
    val loading: Boolean = true,
    val servers: List<NameColorServer> = emptyList(),
    val error: String? = null,
    val expandedId: String? = null,
    val chosen: String = "",
    val customHex: String = "",
    val savingId: String? = null,
    val applied: Map<String, String> = emptyMap(),
)

@HiltViewModel
class NameColorsViewModel @Inject constructor(
    private val api: ServerApi,
) : ViewModel() {
    private val _state = MutableStateFlow(NameColorsUiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { api.servers().data.orEmpty() }
                .onSuccess { list ->
                    _state.update {
                        it.copy(
                            loading = false,
                            servers = list.map { s -> NameColorServer(s.id, s.name, s.isGroup) },
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loading = false, error = "Falha ao carregar servidores") } }
        }
    }

    fun toggleExpand(id: String) = _state.update {
        if (it.expandedId == id) {
            it.copy(expandedId = null, chosen = "", customHex = "", error = null)
        } else {
            it.copy(expandedId = id, chosen = it.applied[id].orEmpty(), customHex = "", error = null)
        }
    }

    fun onCustom(v: String) = _state.update { it.copy(customHex = v, error = null) }

    fun apply(serverId: String) {
        val st = _state.value
        val escolhida = st.customHex.trim().ifBlank { st.chosen }.ifBlank { null }
        if (escolhida == null) {
            save(serverId, null)
            return
        }
        val color = paraFormatoDoServidor(escolhida)
        if (color == null) {
            _state.update { it.copy(error = "Cor invalida — confira o hex") }
            return
        }
        save(serverId, color)
    }

    fun reset(serverId: String) = save(serverId, null)

    private fun save(serverId: String, color: String?) {
        if (_state.value.savingId != null) return
        _state.update { it.copy(savingId = serverId, error = null) }
        viewModelScope.launch {
            runCatching { api.setMyColor(serverId, MyColorRequest(color)) }
                .onSuccess {
                    _state.update {
                        val applied = if (color == null) it.applied - serverId else it.applied + (serverId to color)
                        it.copy(savingId = null, applied = applied, expandedId = null, chosen = "", customHex = "")
                    }
                }
                .onFailure { _state.update { it.copy(savingId = null, error = "Nao foi possivel salvar a cor") } }
        }
    }
}
