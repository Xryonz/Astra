package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.dto.ChannelVisibilityRequest
import app.astra.mobile.core.network.dto.UpdateChannelNameRequest
import app.astra.mobile.feature.server.domain.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelMgmtUi(
    val id: String,
    val name: String,
    val isVoice: Boolean,
    val isPrivate: Boolean,
)

data class RoleMini(val id: String, val name: String, val color: String?)

// Estado da folha de gestao do canal aberto (visibility carregada sob demanda).
data class ChannelEditState(
    val channelId: String,
    val name: String,
    val loadingVisibility: Boolean = true,
    val isPrivate: Boolean = false,
    val roleIds: Set<String> = emptySet(),
)

data class ServerChannelsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val channels: List<ChannelMgmtUi> = emptyList(),
    val roles: List<RoleMini> = emptyList(),
    val editing: ChannelEditState? = null,
    val actionError: String? = null,
)

@HiltViewModel
class ServerChannelsViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val serverApi: ServerApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(ServerChannelsUiState())
    val state = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val serversD = async { repository.servers() }
            val rolesD = async { runCatching { serverApi.roles(serverId).data.orEmpty() } }
            val server = serversD.await().getOrNull()?.find { it.id == serverId }
            if (server == null) {
                _state.update { it.copy(loading = false, error = "Constelacao nao encontrada") }
                return@launch
            }
            val roles = rolesD.await().getOrNull().orEmpty().map { RoleMini(it.id, it.name, it.color) }
            _state.update {
                it.copy(
                    loading = false,
                    channels = server.channels.map { c -> ChannelMgmtUi(c.id, c.name, c.isVoice, c.isPrivate) },
                    roles = roles,
                )
            }
        }
    }

    fun open(channel: ChannelMgmtUi) {
        _state.update { it.copy(editing = ChannelEditState(channel.id, channel.name)) }
        viewModelScope.launch {
            try {
                val v = serverApi.channelVisibility(serverId, channel.id).data
                _state.update {
                    val e = it.editing ?: return@update it
                    if (e.channelId != channel.id) return@update it
                    it.copy(editing = e.copy(
                        loadingVisibility = false,
                        isPrivate = v?.isPrivate ?: false,
                        roleIds = v?.roleIds?.toSet() ?: emptySet(),
                    ))
                }
            } catch (e: Exception) {
                _state.update {
                    val ed = it.editing ?: return@update it
                    it.copy(editing = ed.copy(loadingVisibility = false), actionError = "Nao foi possivel carregar a visibilidade")
                }
            }
        }
    }

    fun close() = _state.update { it.copy(editing = null) }

    fun togglePrivate(on: Boolean) = _state.update {
        it.copy(editing = it.editing?.copy(isPrivate = on))
    }

    fun toggleRole(roleId: String) = _state.update {
        val e = it.editing ?: return@update it
        it.copy(editing = e.copy(roleIds = if (roleId in e.roleIds) e.roleIds - roleId else e.roleIds + roleId))
    }

    fun saveVisibility() {
        val e = _state.value.editing ?: return
        viewModelScope.launch {
            try {
                serverApi.setChannelVisibility(
                    serverId, e.channelId,
                    ChannelVisibilityRequest(e.isPrivate, e.roleIds.toList()),
                )
                _state.update {
                    it.copy(
                        channels = it.channels.map { c -> if (c.id == e.channelId) c.copy(isPrivate = e.isPrivate) else c },
                        editing = null,
                    )
                }
            } catch (ex: Exception) {
                _state.update { it.copy(actionError = "Nao foi possivel salvar a visibilidade") }
            }
        }
    }

    fun rename(channelId: String, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val prev = _state.value.channels
        _state.update {
            it.copy(channels = it.channels.map { c -> if (c.id == channelId) c.copy(name = clean) else c })
        }
        viewModelScope.launch {
            try {
                serverApi.renameChannel(serverId, channelId, UpdateChannelNameRequest(clean))
            } catch (e: Exception) {
                _state.update { it.copy(channels = prev, actionError = "Nao foi possivel renomear") }
            }
        }
    }

    fun delete(channelId: String) {
        val prev = _state.value.channels
        _state.update { it.copy(channels = it.channels.filterNot { c -> c.id == channelId }, editing = null) }
        viewModelScope.launch {
            try {
                serverApi.deleteChannel(serverId, channelId)
            } catch (e: Exception) {
                _state.update { it.copy(channels = prev, actionError = "Nao foi possivel apagar") }
            }
        }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }
}
