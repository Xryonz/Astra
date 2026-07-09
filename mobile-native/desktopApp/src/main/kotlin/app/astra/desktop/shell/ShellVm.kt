package app.astra.desktop.shell

import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.ChannelActivityEventDto
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.DmTypingEventDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

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
    val chat: ChatTarget? = null,
    // Ids (canal ou conversa) com mensagem que voce ainda nao viu.
    val unread: Set<String> = emptySet(),
    // Conversas DM com alguem digitando agora (sidebar mostra "digitando…").
    val dmTyping: Set<String> = emptySet(),
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
    private val socket: DesktopSocket,
    private val json: Json,
    private val myId: String?,
) {
    private val _state = MutableStateFlow(ShellUiState())
    val state = _state.asStateFlow()

    // Expiracao do "digitando…" por conversa+user (se o stop se perder).
    private val typingJobs = mutableMapOf<String, Job>()

    init {
        load()
        listenRealtime()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            val meD = async { runCatching { userApi.me().data?.user }.getOrNull() }
            val serversD = async { runCatching { serverApi.servers().data.orEmpty() }.getOrNull() }
            val dmsD = async { runCatching { dmApi.conversations().data.orEmpty() }.getOrDefault(emptyList()) }
            val channelReadsD = async { runCatching { serverApi.channelReads().data.orEmpty() }.getOrDefault(emptyMap()) }
            val dmReadsD = async { runCatching { dmApi.dmReads().data.orEmpty() }.getOrDefault(emptyMap()) }

            val servers = serversD.await()
            if (servers == null) {
                _state.update { it.copy(loading = false, error = "Sem conexao com o servidor") }
                return@launch
            }
            val dms = dmsD.await()

            // Entra na sala de todas as DMs: typing/new_dm so chegam pra quem
            // esta na sala (o rejoin pos-reconnect ja cobre estas tambem).
            dms.forEach { socket.joinDm(it.id) }

            // Nao lida = ultima mensagem depois da ultima leitura (sem leitura
            // registrada tambem conta). DM mutada ou cuja ultima e minha, nao.
            val channelReads = channelReadsD.await()
            val dmReads = dmReadsD.await()
            val unreadChannels = servers.flatMap { it.channels }
                .filter { ch -> ch.lastMessageAt?.let { last -> channelReads[ch.id]?.let { last > it } ?: true } ?: false }
                .map { it.id }
            val unreadDms = dms.filter { c ->
                val lm = c.lastMessage
                !c.muted && lm?.senderId != null && lm.senderId != myId &&
                    (lm.createdAt?.let { last -> dmReads[c.id]?.mine?.let { last > it } ?: true } ?: false)
            }.map { it.id }

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
                    dms = dms,
                    selection = selection,
                    unread = (unreadChannels + unreadDms).toSet(),
                )
            }
            if (selection is Selection.Server) loadMembers(selection.id)
        }
    }

    fun select(selection: Selection) {
        _state.update { it.copy(selection = selection, members = emptyList(), chat = null) }
        store.setUiPref("lastSelection", selection.encode())
        if (selection is Selection.Server) loadMembers(selection.id)
    }

    // Abrir a conversa limpa a nao-lida local (o POST /read fica no ChatVm).
    fun openChat(target: ChatTarget) = _state.update { it.copy(chat = target, unread = it.unread - target.id) }

    fun toggleMembers() = _state.update { it.copy(membersOpen = !it.membersOpen) }

    private fun listenRealtime() {
        scope.launch {
            launch {
                socket.channelActivity.collect { raw ->
                    val ev = decode<ChannelActivityEventDto>(raw) ?: return@collect
                    if (_state.value.chat?.id != ev.channelId) {
                        _state.update { it.copy(unread = it.unread + ev.channelId) }
                    }
                }
            }
            launch {
                socket.newDm.collect { raw ->
                    val msg = decode<DmMessageDto>(raw) ?: return@collect
                    if (msg.senderId == myId) return@collect
                    dmTypingStopped(msg.conversationId, msg.senderId)
                    val st = _state.value
                    val muted = st.dms.any { it.id == msg.conversationId && it.muted }
                    if (!muted && st.chat?.id != msg.conversationId) {
                        _state.update { it.copy(unread = it.unread + msg.conversationId) }
                    }
                }
            }
            launch {
                socket.dmTyping.collect { raw ->
                    val ev = decode<DmTypingEventDto>(raw) ?: return@collect
                    dmTypingStarted(ev.conversationId, ev.userId)
                }
            }
            launch {
                socket.dmTypingStopped.collect { raw ->
                    val ev = decode<DmTypingEventDto>(raw) ?: return@collect
                    dmTypingStopped(ev.conversationId, ev.userId)
                }
            }
        }
    }

    private fun dmTypingStarted(conversationId: String, userId: String) {
        if (userId == myId) return
        _state.update { it.copy(dmTyping = it.dmTyping + conversationId) }
        val key = "$conversationId/$userId"
        typingJobs.remove(key)?.cancel()
        typingJobs[key] = scope.launch {
            delay(8_000)
            dmTypingStopped(conversationId, userId)
        }
    }

    private fun dmTypingStopped(conversationId: String, userId: String) {
        typingJobs.remove("$conversationId/$userId")?.cancel()
        // DM e 1:1 — sem esse user digitando, a conversa sai do set.
        _state.update { it.copy(dmTyping = it.dmTyping - conversationId) }
    }

    private inline fun <reified T> decode(raw: String): T? =
        runCatching { json.decodeFromString<T>(raw) }.getOrNull()

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
