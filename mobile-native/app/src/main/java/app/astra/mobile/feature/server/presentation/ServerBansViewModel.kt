package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.ServerApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BanUi(
    val userId: String,
    val name: String,
    val username: String,
    val avatarUrl: String?,
    val reason: String?,
)

data class ServerBansUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val bans: List<BanUi> = emptyList(),
    val actionError: String? = null,
)

@HiltViewModel
class ServerBansViewModel @Inject constructor(
    private val serverApi: ServerApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(ServerBansUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val bans = serverApi.bans(serverId).data.orEmpty().map {
                    BanUi(it.userId, it.user.displayName ?: it.user.username, it.user.username, it.user.avatarUrl, it.reason)
                }
                _state.update { it.copy(loading = false, bans = bans) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Sem conexao com o servidor") }
            }
        }
    }

    fun unban(userId: String) {
        val prev = _state.value.bans
        _state.update { it.copy(bans = it.bans.filterNot { b -> b.userId == userId }) }
        viewModelScope.launch {
            try {
                serverApi.unban(serverId, userId)
            } catch (e: Exception) {
                _state.update { it.copy(bans = prev, actionError = "Nao foi possivel desbanir") }
            }
        }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }
}
