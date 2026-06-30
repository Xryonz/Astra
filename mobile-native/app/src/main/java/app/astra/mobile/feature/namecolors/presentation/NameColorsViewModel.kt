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

val NAME_COLOR_PRESETS = listOf(
    "#c9a96e", "#9b7ac4", "#6aaeca", "#ca7a9b", "#6ec99b",
    "#e07a7a", "#7ac4c4", "#c4c47a", "#c47aaa", "#7ac4a0",
)

private val HEX_RE = Regex("^#[0-9a-fA-F]{6}$")

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

    fun pickPreset(hex: String) = _state.update { it.copy(chosen = hex, customHex = "", error = null) }

    fun onCustom(v: String) = _state.update { it.copy(customHex = v, error = null) }

    fun apply(serverId: String) {
        val st = _state.value
        val color = st.customHex.trim().ifBlank { st.chosen }.ifBlank { null }
        if (color != null && !HEX_RE.matches(color)) {
            _state.update { it.copy(error = "Use #RRGGBB (ex: #c9a96e)") }
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
