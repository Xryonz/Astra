package app.astra.desktop.shell

import app.astra.desktop.auth.SessionStore
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Selecao persistida como string ("dms" | "server:<id>") no ui.properties.
sealed interface Selection {
    data object Dms : Selection
    data class Server(val id: String) : Selection

    fun encode(): String = when (this) {
        is Dms -> "dms"
        is Server -> "server:$id"
    }

    companion object {
        fun decode(raw: String?): Selection =
            if (raw != null && raw.startsWith("server:")) Server(raw.removePrefix("server:")) else Dms
    }
}

data class ShellUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val me: ProfileUserDto? = null,
    val servers: List<ServerDto> = emptyList(),
    val dms: List<ConversationDto> = emptyList(),
    val selection: Selection = Selection.Dms,
    val members: List<ServerMemberDto> = emptyList(),
    val membersOpen: Boolean = true,
) {
    val selectedServer: ServerDto?
        get() = (selection as? Selection.Server)?.let { sel -> servers.find { it.id == sel.id } }
}

// Estado do shell. Sem ViewModel no desktop: classe simples presa ao escopo da
// composicao (rememberCoroutineScope).
class ShellVm(
    private val scope: CoroutineScope,
    private val serverApi: ServerApi,
    private val userApi: UserApi,
    private val dmApi: DmApi,
    private val store: SessionStore,
) {
    private val _state = MutableStateFlow(ShellUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            val meD = async { runCatching { userApi.me().data?.user }.getOrNull() }
            val serversD = async { runCatching { serverApi.servers().data.orEmpty() }.getOrNull() }
            val dmsD = async { runCatching { dmApi.conversations().data.orEmpty() }.getOrDefault(emptyList()) }

            val servers = serversD.await()
            if (servers == null) {
                _state.update { it.copy(loading = false, error = "Sem conexao com o servidor") }
                return@launch
            }

            // Restaura a ultima selecao (se a constelacao ainda existe).
            val saved = Selection.decode(store.uiPref("lastSelection"))
            val selection = when (saved) {
                is Selection.Server -> if (servers.any { it.id == saved.id }) saved else Selection.Dms
                Selection.Dms -> Selection.Dms
            }

            _state.update {
                it.copy(
                    loading = false,
                    me = meD.await(),
                    servers = servers,
                    dms = dmsD.await(),
                    selection = selection,
                )
            }
            if (selection is Selection.Server) loadMembers(selection.id)
        }
    }

    fun select(selection: Selection) {
        _state.update { it.copy(selection = selection, members = emptyList()) }
        store.setUiPref("lastSelection", selection.encode())
        if (selection is Selection.Server) loadMembers(selection.id)
    }

    fun toggleMembers() = _state.update { it.copy(membersOpen = !it.membersOpen) }

    private fun loadMembers(serverId: String) {
        scope.launch {
            val members = runCatching { serverApi.members(serverId).data.orEmpty() }.getOrDefault(emptyList())
            // So aplica se a selecao nao mudou enquanto carregava.
            _state.update {
                if ((it.selection as? Selection.Server)?.id == serverId) it.copy(members = members) else it
            }
        }
    }
}
