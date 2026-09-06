package app.astra.desktop.shell

import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.MyPermsDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto

sealed interface Selection {
    data object Dms : Selection
    data object Discover : Selection
    data class Server(val id: String) : Selection

    fun encode(): String = when (this) {
        is Dms -> "dms"
        is Discover -> "discover"
        is Server -> "server:$id"
    }

    companion object {
        fun decode(raw: String?): Selection = when {
            raw == "discover" -> Discover
            raw != null && raw.startsWith("server:") -> Server(raw.removePrefix("server:"))
            else -> Dms
        }
    }
}

data class Penalidade(
    val tipo: String,
    val constelacao: String?,
    val motivo: String?,
)

data class ShellUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val me: ProfileUserDto? = null,
    val servers: List<ServerDto> = emptyList(),
    val dms: List<ConversationDto> = emptyList(),
    val selection: Selection = Selection.Dms,
    val members: List<ServerMemberDto> = emptyList(),
    val memberPresence: Map<String, String> = emptyMap(),
    val memberActivity: Map<String, String> = emptyMap(),
    val dmPresence: Map<String, String> = emptyMap(),
    val membersOpen: Boolean = true,
    val chat: ChatTarget? = null,
    val friendsOpen: Boolean = false,
    val voiceChannel: ChannelDto? = null,
    val penalidade: Penalidade? = null,
    val unread: Set<String> = emptySet(),
    val unreadCounts: Map<String, Int> = emptyMap(),
    val leiturasAoEntrar: Map<String, String> = emptyMap(),
    val mutedChannels: Set<String> = emptySet(),
    val mutedServers: Set<String> = emptySet(),
    val avisoForcado: Set<String> = emptySet(),
    val dmTyping: Set<String> = emptySet(),
    val voicePresence: Map<String, List<String>> = emptyMap(),
    val myPerms: MyPermsDto? = null,
    val chamada: ChamadaNaTela? = null,
) {
    val selectedServer: ServerDto?
        get() = (selection as? Selection.Server)?.let { sel -> servers.find { it.id == sel.id } }

    fun orbitaSilenciada(channelId: String): Boolean {
        if (channelId in mutedChannels) return true
        if (channelId in avisoForcado) return false
        val srv = servers.find { s -> s.channels.any { it.id == channelId } } ?: return false
        return srv.id in mutedServers
    }
}

data class ChamadaNaTela(
    val conversationId: String,
    val nome: String,
    val avatarUrl: String?,
    val video: Boolean,
    val euLiguei: Boolean,
    val tocando: Boolean = true,
)

const val HISTORICO_DESTINOS = "historicoDestinos"
const val SEP_HISTORICO = "\u0001"
