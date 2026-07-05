package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.dto.RoleRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoleUi(
    val id: String,
    val name: String,
    val color: String?,
    val hoist: Boolean,
    val permissions: List<String>,
)

data class ServerRolesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val roles: List<RoleUi> = emptyList(),
    val actionError: String? = null,
)

// As 7 permissoes do backend (lib/permissions.ts), com rotulo/descricao em PT
// espelhando o pt.json do web.
val PERM_OPTIONS: List<Triple<String, String, String>> = listOf(
    Triple("MANAGE_SERVER", "Gerenciar constelacao", "editar nome, icone, retencao"),
    Triple("MANAGE_ROLES", "Gerenciar cargos", "criar/editar cargos (sempre pertence ao dono)"),
    Triple("MANAGE_CHANNELS", "Gerenciar orbitas", "criar, editar, apagar orbitas e emojis"),
    Triple("KICK_MEMBERS", "Expulsar", "remover estrelas da constelacao"),
    Triple("BAN_MEMBERS", "Banir", "bloqueio permanente"),
    Triple("MANAGE_MESSAGES", "Gerenciar mensagens", "apagar mensagens de outros, fixar"),
    Triple("MENTION_EVERYONE", "Mencionar @everyone", "notifica todo mundo"),
)

@HiltViewModel
class ServerRolesViewModel @Inject constructor(
    private val serverApi: ServerApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(ServerRolesUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val roles = serverApi.roles(serverId).data.orEmpty().map {
                    RoleUi(it.id, it.name, it.color, it.hoist, it.permissions)
                }
                _state.update { it.copy(loading = false, roles = roles) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Sem conexao com o servidor") }
            }
        }
    }

    fun createRole(name: String, color: String?, hoist: Boolean, permissions: List<String>) {
        viewModelScope.launch {
            try {
                val r = serverApi.createRole(serverId, RoleRequest(name.trim(), color, permissions, hoist)).data
                    ?: return@launch
                _state.update {
                    it.copy(roles = it.roles + RoleUi(r.id, r.name, r.color, r.hoist, r.permissions))
                }
            } catch (e: Exception) {
                _state.update { it.copy(actionError = "Nao foi possivel criar o cargo") }
            }
        }
    }

    fun updateRole(id: String, name: String, color: String?, hoist: Boolean, permissions: List<String>) {
        val prev = _state.value.roles
        _state.update {
            it.copy(roles = it.roles.map { r ->
                if (r.id == id) r.copy(name = name.trim(), color = color, hoist = hoist, permissions = permissions) else r
            })
        }
        viewModelScope.launch {
            try {
                serverApi.updateRole(serverId, id, RoleRequest(name.trim(), color, permissions, hoist))
            } catch (e: Exception) {
                _state.update { it.copy(roles = prev, actionError = "Nao foi possivel salvar o cargo") }
            }
        }
    }

    fun deleteRole(id: String) {
        val prev = _state.value.roles
        _state.update { it.copy(roles = it.roles.filterNot { r -> r.id == id }) }
        viewModelScope.launch {
            try {
                serverApi.deleteRole(serverId, id)
            } catch (e: Exception) {
                _state.update { it.copy(roles = prev, actionError = "Nao foi possivel apagar o cargo") }
            }
        }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }
}
