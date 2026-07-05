package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.feature.server.domain.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerSettingsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val name: String = "",
    val iconUrl: String? = null,
    val isGroup: Boolean = false,
    val isOwner: Boolean = false,
    val canManageServer: Boolean = false,
    val canManageRoles: Boolean = false,
    val working: Boolean = false,
    // sair/excluir concluido -> a tela navega de volta pra Home.
    val closed: Boolean = false,
)

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val serverApi: ServerApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(ServerSettingsUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val serversD = async { repository.servers() }
            val permsD = async { runCatching { serverApi.myPerms(serverId).data } }
            val server = serversD.await().getOrNull()?.find { it.id == serverId }
            val perms = permsD.await().getOrNull()
            if (server == null) {
                _state.update { it.copy(loading = false, error = "Constelacao nao encontrada") }
                return@launch
            }
            _state.update {
                it.copy(
                    loading = false,
                    name = server.name,
                    iconUrl = server.iconUrl,
                    isGroup = server.isGroup,
                    isOwner = perms?.isOwner == true,
                    canManageServer = perms?.isOwner == true ||
                        perms?.isAdmin == true ||
                        perms?.permissions.orEmpty().contains("MANAGE_SERVER"),
                    canManageRoles = perms?.isOwner == true ||
                        perms?.permissions.orEmpty().contains("MANAGE_ROLES"),
                )
            }
        }
    }

    fun leave() {
        if (_state.value.working) return
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            try {
                serverApi.leaveServer(serverId)
                _state.update { it.copy(working = false, closed = true) }
            } catch (e: Exception) {
                _state.update { it.copy(working = false, error = "Nao foi possivel sair") }
            }
        }
    }

    fun deleteServer() {
        if (_state.value.working) return
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            try {
                serverApi.deleteServer(serverId)
                _state.update { it.copy(working = false, closed = true) }
            } catch (e: Exception) {
                _state.update { it.copy(working = false, error = "Nao foi possivel excluir") }
            }
        }
    }
}
