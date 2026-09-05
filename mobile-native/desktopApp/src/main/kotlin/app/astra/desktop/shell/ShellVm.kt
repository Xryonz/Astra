package app.astra.desktop.shell

import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.net.insistindoOuNulo
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.InviteApi
import app.astra.mobile.core.network.NotificationApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.ChannelActivityEventDto
import app.astra.mobile.core.network.dto.BanDto
import app.astra.mobile.core.network.dto.BanRequest
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.CreateCategoryRequest
import app.astra.mobile.core.network.dto.CreateChannelRequest
import app.astra.mobile.core.network.dto.CreateServerRequest
import app.astra.mobile.core.network.dto.MoveChannelRequest
import app.astra.mobile.core.network.dto.NotifModeRequest
import app.astra.mobile.core.network.dto.UpdateChannelNameRequest
import app.astra.mobile.core.network.dto.UpdateServerRequest
import app.astra.mobile.core.network.dto.UpdateCategoryRequest
import app.astra.mobile.core.network.dto.UpdateChannelBotRequest
import app.astra.mobile.core.network.dto.UpdateChannelKeepRequest
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.LastMessageDto
import app.astra.mobile.core.network.dto.DmTypingEventDto
import app.astra.mobile.core.network.dto.OpenDmRequest
import app.astra.mobile.core.network.dto.ActivityUpdateDto
import app.astra.mobile.core.network.dto.PresenceUpdateDto
import app.astra.mobile.core.network.dto.ServerScopedEventDto
import app.astra.desktop.ui.invalidateProfileCache
import app.astra.mobile.core.network.dto.CanalMudouDto
import app.astra.mobile.core.network.dto.CanalSumiuDto
import app.astra.mobile.core.network.dto.CargosDoMembroDto
import app.astra.mobile.core.network.dto.CategoriaMudouDto
import app.astra.mobile.core.network.dto.CategoriaSumiuDto
import app.astra.mobile.core.network.dto.ConstelacaoMudouDto
import app.astra.mobile.core.network.dto.MembroEntrouDto
import app.astra.mobile.core.network.dto.MembrosRefeitosDto
import app.astra.mobile.core.network.dto.MembroMudouDeCargoDto
import app.astra.mobile.core.network.dto.MembroSaiuDto
import app.astra.mobile.core.network.dto.ProfileUpdatedDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.RoleDto
import app.astra.mobile.core.network.dto.RoleRequest
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.MyPermsDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import app.astra.mobile.core.network.dto.VoicePresenceEventDto
import app.astra.mobile.core.network.dto.ChamadaAtendidaDto
import app.astra.mobile.core.network.dto.ChamadaChegandoDto
import app.astra.mobile.core.network.dto.ChamadaEncerradaDto
import app.astra.desktop.voice.Sfx
import app.astra.desktop.voice.VoiceSession
import app.astra.desktop.voice.VoiceLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.HttpException

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
private const val TETO_HISTORICO = 12

class ShellVm(
    private val scope: CoroutineScope,
    private val serverApi: ServerApi,
    private val channelApi: ChannelApi,
    private val userApi: UserApi,
    private val dmApi: DmApi,
    private val voiceApi: VoiceApi,
    private val notifApi: NotificationApi,
    private val inviteApi: InviteApi,
    private val store: SessionStore,
    private val socket: DesktopSocket,
    private val json: Json,
    private val myId: String?,
) {
    private val _state = MutableStateFlow(ShellUiState())
    val state = _state.asStateFlow()

    private val typingJobs = mutableMapOf<String, Job>()

    init {
        load()
        loadNotifPrefs()
        listenRealtime()
        pollVoicePresence()
    }

    private fun pollVoicePresence() {
        scope.launch {
            while (true) {
                val voiceIds = _state.value.selectedServer
                    ?.channels?.filter { it.type == "VOICE" }?.map { it.id }.orEmpty()
                if (voiceIds.isNotEmpty()) {
                    runCatching { voiceApi.presence(voiceIds.joinToString(",")).data.orEmpty() }
                        .onSuccess { pres ->
                            _state.update { st ->
                                if (st.voicePresence == pres) return@update st
                                val resumo = pres.entries
                                    .filter { it.value.isNotEmpty() }
                                    .joinToString("; ") { (canal, ids) ->
                                        val nomes = ids.joinToString(",") { id ->
                                            st.members.find { m -> m.userId == id }
                                                ?.user?.username ?: "?$id"
                                        }
                                        "$canal=[$nomes]"
                                    }
                                socket.noteLocal(
                                    if (resumo.isBlank()) "· voz: ninguem em call (${voiceIds.size} orbita(s) de voz)"
                                    else "· voz: $resumo",
                                )
                                st.copy(voicePresence = pres)
                            }
                        }
                } else if (_state.value.voicePresence.isNotEmpty()) {
                    _state.update { it.copy(voicePresence = emptyMap()) }
                }
                delay(20_000)
            }
        }
        scope.launch {
            socket.voicePresence.collect { raw ->
                val ev = runCatching { json.decodeFromString<VoicePresenceEventDto>(raw) }.getOrNull()
                    ?: return@collect
                _state.update { st ->
                    val known = st.selectedServer?.channels?.any { it.id == ev.channelId } == true
                    if (!known) return@update st
                    val cur = st.voicePresence[ev.channelId].orEmpty()
                    val next = if (ev.joined) {
                        if (ev.userId in cur) cur else cur + ev.userId
                    } else {
                        cur - ev.userId
                    }
                    if (next == cur) st
                    else st.copy(voicePresence = st.voicePresence + (ev.channelId to next))
                }
            }
        }

        scope.launch {
            socket.chamadaChegando.collect { raw ->
                val ev = runCatching { json.decodeFromString<ChamadaChegandoDto>(raw) }.getOrNull()
                    ?: return@collect
                if (voiceSession?.joined != null || _state.value.chamada != null) return@collect
                _state.update {
                    it.copy(chamada = ChamadaNaTela(
                        conversationId = ev.conversationId,
                        nome = ev.fromDisplayName.ifBlank { ev.fromUsername },
                        avatarUrl = ev.fromAvatarUrl,
                        video = ev.video,
                        euLiguei = false,
                    ))
                }
                Sfx.ringStart(souEuQueLiguei = false)
            }
        }
        scope.launch {
            socket.chamadaAtendida.collect { raw ->
                val ev = runCatching { json.decodeFromString<ChamadaAtendidaDto>(raw) }.getOrNull()
                    ?: return@collect
                val c = _state.value.chamada ?: return@collect
                if (c.conversationId != ev.conversationId) return@collect
                Sfx.ringStop()
                if (c.euLiguei) entrarNaChamada(c)
                _state.update { it.copy(chamada = null) }
            }
        }
        scope.launch {
            socket.chamadaEncerrada.collect { raw ->
                val ev = runCatching { json.decodeFromString<ChamadaEncerradaDto>(raw) }.getOrNull()
                    ?: return@collect
                Sfx.ringStop()
                _state.update {
                    if (it.chamada?.conversationId != ev.conversationId) it
                    else it.copy(chamada = null)
                }
                val sessao = voiceSession
                if (sessao?.emSussurro == true && sessao.joined?.id == ev.conversationId) {
                    sessao.leave()
                    _state.update { it.copy(voiceChannel = null) }
                }
            }
        }
    }

    var voiceSession: VoiceSession? = null

    fun ligarNoSussurro(conversationId: String, titulo: String, avatarUrl: String?, video: Boolean) {
        if (voiceSession?.joined != null || _state.value.chamada != null) return
        socket.ligarNoSussurro(conversationId, video)
        _state.update {
            it.copy(chamada = ChamadaNaTela(conversationId, titulo, avatarUrl, video, euLiguei = true))
        }
        Sfx.ringStart(souEuQueLiguei = true)
    }

    fun atenderChamada() {
        val c = _state.value.chamada ?: return
        Sfx.ringStop()
        socket.atenderSussurro(c.conversationId)
        entrarNaChamada(c)
        _state.update { it.copy(chamada = null) }
    }

    fun recusarChamada() {
        val c = _state.value.chamada ?: return
        Sfx.ringStop()
        socket.desligarSussurro(c.conversationId)
        _state.update { it.copy(chamada = null) }
    }

    private fun entrarNaChamada(c: ChamadaNaTela) {
        val sessao = voiceSession ?: return
        sessao.joinDm(c.conversationId, c.nome)
        _state.update { it.copy(voiceChannel = sessao.joined) }
        if (c.video) {
            VoiceLog.nota("[call] chamada de vídeo entrou só com voz: falta captura de câmera")
        }
    }

    fun desligarSussurro() {
        val sessao = voiceSession ?: return
        val id = sessao.joined?.id
        if (sessao.emSussurro && id != null) socket.desligarSussurro(id)
        sessao.leave()
        _state.update { it.copy(voiceChannel = null) }
    }

    fun refreshMe() {
        scope.launch {
            runCatching { userApi.me().data?.user }.getOrNull()?.let { u ->
                _state.update { it.copy(me = u) }
            }
        }
    }

    private suspend fun <T : Any> insistindo(oQue: String, bloco: suspend () -> T?): T? =
        insistindoOuNulo(oQue, bloco)

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            val meD = async { runCatching { userApi.me().data?.user }.getOrNull() }
            val serversD = async { insistindo("suas constelações") { serverApi.servers().data.orEmpty() } }
            val dmsD = async { insistindo("seus sussurros") { dmApi.conversations().data.orEmpty() } }
            val channelReadsD = async { insistindo("suas leituras") { serverApi.channelReads().data.orEmpty() } }
            val unreadCountsD = async { insistindo("as não lidas") { serverApi.channelUnreadCounts().data.orEmpty() } }
            val dmReadsD = async { insistindo("as leituras dos sussurros") { dmApi.dmReads().data.orEmpty() } }

            val servers = serversD.await()
            if (servers == null) {
                _state.update { it.copy(loading = false, error = "Sem conexão com o servidor") }
                return@launch
            }
            val dms = dmsD.await().orEmpty()

            dms.forEach { socket.joinDm(it.id) }

            val channelReads = channelReadsD.await().orEmpty()
            val dmReads = dmReadsD.await().orEmpty()
            val unreadChannels = servers.flatMap { it.channels }
                .filter { ch -> ch.lastMessageAt?.let { last -> channelReads[ch.id]?.let { last > it } ?: true } ?: false }
                .map { it.id }
            val unreadDms = dms.filter { c ->
                val lm = c.lastMessage
                !c.muted && lm?.senderId != null && lm.senderId != myId &&
                    (lm.createdAt?.let { last -> dmReads[c.id]?.mine?.let { last > it } ?: true } ?: false)
            }.map { it.id }

            val saved = Selection.decode(store.uiPref("lastSelection"))
            val selection = when (saved) {
                is Selection.Server -> if (servers.any { it.id == saved.id }) saved else Selection.Dms
                Selection.Discover -> Selection.Discover
                Selection.Dms -> Selection.Dms
            }

            val savedChat = store.uiPref("lastChat")
            var restoredChat: ChatTarget? = null
            var restoredFriends = false
            var finalSelection = selection
            when {
                savedChat == "friends" -> { restoredFriends = true; finalSelection = Selection.Dms }
                savedChat?.startsWith("channel:") == true -> {
                    val id = savedChat.removePrefix("channel:")
                    val srv = servers.find { s -> s.channels.any { it.id == id } }
                    val ch = srv?.channels?.find { it.id == id }
                    if (srv != null && ch != null && ch.type != "VOICE") {
                        restoredChat = ChatTarget.Channel(ch.id, ch.name)
                        finalSelection = Selection.Server(srv.id)
                    }
                }
                savedChat?.startsWith("dm:") == true -> {
                    val id = savedChat.removePrefix("dm:")
                    val conv = dms.find { it.id == id }
                    if (conv != null) {
                        val title = conv.otherUser?.displayName ?: conv.otherUser?.username ?: "sussurro"
                        restoredChat = ChatTarget.Dm(conv.id, title)
                        finalSelection = Selection.Dms
                    }
                }
            }

            _state.update {
                it.copy(
                    loading = false,
                    me = meD.await(),
                    servers = servers,
                    dms = dms,
                    selection = finalSelection,
                    chat = restoredChat,
                    friendsOpen = restoredFriends,
                    unread = (unreadChannels + unreadDms).toSet() - setOfNotNull(restoredChat?.id),
                    unreadCounts = unreadCountsD.await().orEmpty() - setOfNotNull(restoredChat?.id),
                    leiturasAoEntrar = channelReads +
                        dmReads.mapNotNull { (id, r) -> r.mine?.let { id to it } },
                )
            }
            store.setUiPref("lastSelection", finalSelection.encode())
            if (finalSelection is Selection.Server) loadMembers(finalSelection.id)
            carregarPresencaDosSussurros()
        }
    }

    private fun carregarPresencaDosSussurros() {
        scope.launch {
            val ids = _state.value.dms.mapNotNull { it.otherUser?.id }.distinct()
            if (ids.isEmpty()) return@launch
            val p = runCatching { userApi.presence(ids.joinToString(",")).data.orEmpty() }.getOrNull()
                ?: return@launch
            _state.update { it.copy(dmPresence = it.dmPresence + p) }
        }
    }

    fun select(selection: Selection) {
        if (_state.value.selection == selection) return
        _state.update {
            it.copy(selection = selection, members = emptyList(), memberPresence = emptyMap(), myPerms = null, chat = null, friendsOpen = false)
        }
        store.setUiPref("lastSelection", selection.encode())
        saveLocation()
        if (selection is Selection.Server) loadMembers(selection.id)
    }

    fun openFriends() {
        _state.update { it.copy(friendsOpen = true, chat = null, voiceChannel = null) }
        saveLocation()
    }

    private fun saveLocation() {
        val st = _state.value
        val c = st.chat
        val enc = when {
            st.friendsOpen -> "friends"
            c is ChatTarget.Channel -> "channel:${c.id}"
            c is ChatTarget.Dm -> "dm:${c.id}"
            else -> ""
        }
        store.setUiPref("lastChat", enc)
    }

    fun openChat(target: ChatTarget) {
        if (_state.value.chat == target) return
        registrarDestino(target)
        val deixado = _state.value.chat?.id
        _state.update {
            it.copy(
                chat = target, voiceChannel = null, friendsOpen = false,
                unread = it.unread - target.id, unreadCounts = it.unreadCounts - target.id,
                leiturasAoEntrar =
                    if (deixado == null) it.leiturasAoEntrar else it.leiturasAoEntrar - deixado,
            )
        }
        saveLocation()
    }

    private fun registrarDestino(target: ChatTarget) {
        val entrada = when (target) {
            is ChatTarget.Channel -> {
                val sid = (_state.value.selection as? Selection.Server)?.id ?: return
                listOf("c", sid, target.id, target.title).joinToString(SEP_HISTORICO)
            }
            is ChatTarget.Dm -> listOf("d", target.id, target.title).joinToString(SEP_HISTORICO)
        }
        val chave = entrada.substringBeforeLast(SEP_HISTORICO)
        val atual = store.uiPref(HISTORICO_DESTINOS)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        val nova = (listOf(entrada) + atual.filterNot { it.substringBeforeLast(SEP_HISTORICO) == chave })
            .take(TETO_HISTORICO)
        store.setUiPref(HISTORICO_DESTINOS, nova.joinToString("\n"))
    }

    fun toggleDmMute(conv: ConversationDto) {
        scope.launch {
            val ok = runCatching {
                if (conv.muted) dmApi.unmute(conv.id) else dmApi.mute(conv.id)
            }.isSuccess
            if (ok) {
                _state.update { st ->
                    st.copy(dms = st.dms.map { if (it.id == conv.id) it.copy(muted = !conv.muted) else it })
                }
            }
        }
    }

    fun closeDm(conversationId: String) {
        scope.launch { runCatching { dmApi.close(conversationId) } }
        _state.update { st ->
            st.copy(
                dms = st.dms.filterNot { it.id == conversationId },
                unread = st.unread - conversationId,
                chat = if (st.chat?.id == conversationId) null else st.chat,
            )
        }
    }

    fun markDmRead(conversationId: String) {
        scope.launch { runCatching { dmApi.markRead(conversationId) } }
        _state.update { it.copy(unread = it.unread - conversationId) }
    }

    fun leaveServer(id: String) {
        scope.launch {
            runCatching { serverApi.leaveServer(id) }.onSuccess {
                _state.update { st ->
                    val leaving = (st.selection as? Selection.Server)?.id == id
                    st.copy(
                        servers = st.servers.filterNot { it.id == id },
                        selection = if (leaving) Selection.Dms else st.selection,
                        chat = if (leaving) null else st.chat,
                        voiceChannel = if (leaving) null else st.voiceChannel,
                    )
                }
                saveLocation()
            }
        }
    }

    fun deleteServer(id: String) {
        scope.launch {
            runCatching { serverApi.deleteServer(id) }.onSuccess {
                _state.update { st ->
                    val gone = (st.selection as? Selection.Server)?.id == id
                    st.copy(
                        servers = st.servers.filterNot { it.id == id },
                        selection = if (gone) Selection.Dms else st.selection,
                        chat = if (gone) null else st.chat,
                        voiceChannel = if (gone) null else st.voiceChannel,
                    )
                }
                saveLocation()
            }
        }
    }

    fun kickMember(serverId: String, userId: String) {
        scope.launch {
            runCatching { serverApi.kickMember(serverId, userId) }.onSuccess { loadMembers(serverId) }
        }
    }

    fun banMember(serverId: String, userId: String) {
        scope.launch {
            runCatching { serverApi.banMember(serverId, BanRequest(userId)) }.onSuccess { loadMembers(serverId) }
        }
    }

    fun startDm(username: String, title: String) {
        scope.launch {
            val conv = runCatching { dmApi.open(OpenDmRequest(username)).data }.getOrNull() ?: return@launch
            if (_state.value.dms.none { it.id == conv.conversationId }) {
                val dms = runCatching { dmApi.conversations().data.orEmpty() }
                    .getOrDefault(_state.value.dms)
                dms.forEach { socket.joinDm(it.id) }
                _state.update { it.copy(dms = dms) }
                carregarPresencaDosSussurros()
            }
            _state.update {
                it.copy(
                    selection = Selection.Dms,
                    chat = ChatTarget.Dm(conv.conversationId, title),
                    voiceChannel = null,
                    friendsOpen = false,
                )
            }
            saveLocation()
        }
    }

    fun createServer(name: String) {
        scope.launch {
            val created = runCatching { serverApi.create(CreateServerRequest(name)).data }.getOrNull() ?: return@launch
            socket.joinServer(created.id)
            val servers = runCatching { serverApi.servers().data.orEmpty() }.getOrDefault(_state.value.servers)
            _state.update {
                it.copy(
                    servers = servers,
                    selection = Selection.Server(created.id),
                    chat = null,
                    voiceChannel = null,
                    friendsOpen = false,
                    members = emptyList(),
                    myPerms = null,
                )
            }
            store.setUiPref("lastSelection", Selection.Server(created.id).encode())
            saveLocation()
            loadMembers(created.id)
        }
    }

    fun refreshServersAndSelect(serverId: String) {
        scope.launch {
            socket.joinServer(serverId)
            val servers = runCatching { serverApi.servers().data.orEmpty() }.getOrDefault(_state.value.servers)
            _state.update {
                it.copy(
                    servers = servers,
                    selection = Selection.Server(serverId),
                    chat = null,
                    voiceChannel = null,
                    members = emptyList(),
                    myPerms = null,
                )
            }
            store.setUiPref("lastSelection", Selection.Server(serverId).encode())
            saveLocation()
            loadMembers(serverId)
        }
    }

    fun createChannel(serverId: String, name: String, type: String, categoryId: String?) {
        scope.launch {
            val ok = runCatching {
                serverApi.createChannel(serverId, CreateChannelRequest(name, type, categoryId))
            }.isSuccess
            if (ok) reloadServers()
        }
    }

    fun createCategory(serverId: String, name: String) {
        scope.launch {
            val ok = runCatching { serverApi.createCategory(serverId, CreateCategoryRequest(name)) }.isSuccess
            if (ok) reloadServers()
        }
    }

    fun renameCategory(serverId: String, categoryId: String, name: String) {
        scope.launch {
            val ok = runCatching {
                serverApi.updateCategory(serverId, categoryId, UpdateCategoryRequest(name = name))
            }.isSuccess
            if (ok) reloadServers()
        }
    }

    fun deleteCategory(serverId: String, categoryId: String) {
        scope.launch {
            val ok = runCatching { serverApi.deleteCategory(serverId, categoryId) }.isSuccess
            if (ok) reloadServers()
        }
    }

    fun reorderChannel(serverId: String, orderedIds: List<String>) {
        scope.launch {
            val srv0 = _state.value.servers.find { it.id == serverId } ?: return@launch
            val newPos = orderedIds.mapIndexed { i, id -> id to i }.toMap()
            val oldPos = srv0.channels.associate { it.id to it.position }

            _state.update { st ->
                st.copy(servers = st.servers.map { srv ->
                    if (srv.id != serverId) srv
                    else srv.copy(channels = srv.channels.map { ch -> newPos[ch.id]?.let { ch.copy(position = it) } ?: ch })
                })
            }
            orderedIds.forEach { id ->
                val np = newPos.getValue(id)
                if (oldPos[id] != np) runCatching { serverApi.moveChannel(serverId, id, MoveChannelRequest(np)) }
            }
            reloadServers()
        }
    }

    fun moveChannelToCategory(serverId: String, channelId: String, targetCategoryId: String) {
        scope.launch {
            val srv0 = _state.value.servers.find { it.id == serverId } ?: return@launch
            val ch = srv0.channels.find { it.id == channelId } ?: return@launch
            if (ch.categoryId == targetCategoryId) return@launch
            val np = (srv0.channels.filter { it.categoryId == targetCategoryId }.maxOfOrNull { it.position } ?: -1) + 1
            _state.update { st ->
                st.copy(servers = st.servers.map { s ->
                    if (s.id != serverId) s
                    else s.copy(channels = s.channels.map { c ->
                        if (c.id == channelId) c.copy(categoryId = targetCategoryId, position = np) else c
                    })
                })
            }
            runCatching { serverApi.moveChannel(serverId, channelId, MoveChannelRequest(np, targetCategoryId)) }
            reloadServers()
        }
    }

    fun reorderCategories(serverId: String, orderedIds: List<String>) {
        scope.launch {
            val srv0 = _state.value.servers.find { it.id == serverId } ?: return@launch
            val oldPos = srv0.categories.associate { it.id to it.position }
            val newPos = orderedIds.mapIndexed { i, id -> id to i }.toMap()
            _state.update { st ->
                st.copy(servers = st.servers.map { s ->
                    if (s.id != serverId) s
                    else s.copy(categories = s.categories.map { c -> newPos[c.id]?.let { c.copy(position = it) } ?: c })
                })
            }
            orderedIds.forEach { id ->
                val np = newPos.getValue(id)
                if (oldPos[id] != np) runCatching { serverApi.updateCategory(serverId, id, UpdateCategoryRequest(position = np)) }
            }
            reloadServers()
        }
    }

    private fun loadNotifPrefs() {
        scope.launch {
            val ch = async { runCatching { notifApi.channelNotifPrefs().data.orEmpty() }.getOrDefault(emptyList()) }
            val sv = async { runCatching { notifApi.serverNotifPrefs().data.orEmpty() }.getOrDefault(emptyList()) }
            val doCanal = ch.await()
            val mutedCh = doCanal.filter { it.mode == "mute" }.map { it.channelId }.toSet()
            val forcados = doCanal.filter { it.mode == "all" }.map { it.channelId }.toSet()
            val mutedSv = sv.await().filter { it.mode == "mute" }.map { it.serverId }.toSet()
            _state.update { it.copy(mutedChannels = mutedCh, mutedServers = mutedSv, avisoForcado = forcados) }
        }
    }

    fun toggleChannelMute(channelId: String) {
        val s = _state.value
        val silenciada = s.orbitaSilenciada(channelId)
        val constelacaoCalada = s.servers
            .find { sv -> sv.channels.any { it.id == channelId } }
            ?.let { it.id in s.mutedServers } == true

        if (!silenciada) {
            _state.update {
                it.copy(mutedChannels = it.mutedChannels + channelId, avisoForcado = it.avisoForcado - channelId)
            }
            scope.launch { runCatching { notifApi.setChannelNotifPref(channelId, NotifModeRequest("mute")) } }
            return
        }

        _state.update {
            it.copy(
                mutedChannels = it.mutedChannels - channelId,
                avisoForcado = if (constelacaoCalada) it.avisoForcado + channelId else it.avisoForcado - channelId,
            )
        }
        scope.launch {
            runCatching {
                if (constelacaoCalada) notifApi.setChannelNotifPref(channelId, NotifModeRequest("all"))
                else notifApi.clearChannelNotifPref(channelId)
            }
        }
    }

    fun toggleServerMute(serverId: String) {
        val muted = serverId in _state.value.mutedServers
        _state.update { it.copy(mutedServers = if (muted) it.mutedServers - serverId else it.mutedServers + serverId) }
        scope.launch {
            runCatching {
                if (muted) notifApi.clearServerNotifPref(serverId)
                else notifApi.setServerNotifPref(serverId, NotifModeRequest("mute"))
            }
        }
    }

    fun markServerRead(serverId: String) {
        val srv = _state.value.servers.find { it.id == serverId } ?: return
        val unread = _state.value.unread
        srv.channels.forEach { if (it.id in unread) markChannelRead(it.id) }
    }

    fun markChannelRead(channelId: String) {
        scope.launch { runCatching { channelApi.markRead(channelId) } }
        _state.update { it.copy(unread = it.unread - channelId, unreadCounts = it.unreadCounts - channelId) }
    }

    fun setChannelBot(serverId: String, channelId: String, enabled: Boolean) {
        scope.launch {
            val ok = runCatching {
                serverApi.setChannelBot(serverId, channelId, UpdateChannelBotRequest(enabled))
            }.isSuccess
            if (ok) reloadServers()
        }
    }

    fun setChannelKeepBot(serverId: String, channelId: String, guardar: Boolean) {
        scope.launch {
            val ok = runCatching {
                serverApi.setChannelKeepBot(serverId, channelId, UpdateChannelKeepRequest(guardar))
            }.isSuccess
            if (ok) reloadServers()
        }
    }

    fun setCategoryBot(serverId: String, categoryId: String, enabled: Boolean) {
        scope.launch {
            val ok = runCatching {
                serverApi.updateCategory(serverId, categoryId, UpdateCategoryRequest(botEnabled = enabled))
            }.isSuccess
            if (ok) reloadServers()
        }
    }

    fun renameChannel(serverId: String, channelId: String, name: String) {
        scope.launch {
            val ok = runCatching {
                serverApi.renameChannel(serverId, channelId, UpdateChannelNameRequest(name))
            }.isSuccess
            if (ok) reloadServers()
        }
    }

    fun deleteChannel(serverId: String, channelId: String) {
        scope.launch {
            runCatching { serverApi.deleteChannel(serverId, channelId) }.onSuccess {
                _state.update { st ->
                    val gone = (st.chat as? ChatTarget.Channel)?.id == channelId
                    st.copy(chat = if (gone) null else st.chat)
                }
                reloadServers()
            }
        }
    }

    private fun reloadServers() {
        scope.launch {
            val servers = runCatching { serverApi.servers().data.orEmpty() }.getOrNull() ?: return@launch
            _state.update { it.copy(servers = servers) }
        }
    }

    fun updateServer(serverId: String, body: UpdateServerRequest, onResult: (String?) -> Unit) {
        scope.launch {
            val r = runCatching { serverApi.update(serverId, body) }
            if (r.isSuccess) {
                reloadServers()
                onResult(null)
            } else {
                onResult(apiMessage(r.exceptionOrNull(), "Não foi possível salvar"))
            }
        }
    }

    fun addMember(serverId: String, username: String, onResult: (String?) -> Unit) {
        val u = username.trim().removePrefix("@")
        if (u.isBlank()) { onResult("Digite o nome de usuário"); return }
        scope.launch {
            val r = runCatching { serverApi.addMember(serverId, u) }
            if (r.isSuccess) {
                loadMembers(serverId)
                onResult(null)
            } else {
                onResult(apiMessage(r.exceptionOrNull(), "Não foi possível adicionar essa pessoa"))
            }
        }
    }

    fun joinByInvite(raw: String, onResult: (String?) -> Unit) {
        val code = raw.trim().trimEnd('/').substringAfterLast('/').substringBefore('?')
        if (code.isBlank()) { onResult("Cole o convite ou o código"); return }
        scope.launch {
            val r = runCatching { inviteApi.join(code).data }
            val joined = r.getOrNull()
            if (joined != null) {
                refreshServersAndSelect(joined.id)
                onResult(null)
            } else {
                onResult(apiMessage(r.exceptionOrNull(), "Convite inválido ou expirado"))
            }
        }
    }

    fun regenerateInvite(serverId: String, onResult: (String?) -> Unit) {
        scope.launch {
            val r = runCatching { serverApi.regenerateInvite(serverId) }
            if (r.isSuccess) {
                reloadServers()
                onResult(null)
            } else {
                onResult(apiMessage(r.exceptionOrNull(), "Não foi possível gerar um convite novo"))
            }
        }
    }

    fun loadRoles(serverId: String, onResult: (List<RoleDto>?, String?) -> Unit) {
        scope.launch {
            val r = runCatching { serverApi.roles(serverId).data.orEmpty() }
            r.onSuccess { onResult(it, null) }
                .onFailure { onResult(null, apiMessage(it, "Não foi possível carregar os cargos")) }
        }
    }

    fun saveRole(serverId: String, roleId: String?, body: RoleRequest, onResult: (String?) -> Unit) {
        scope.launch {
            val r = runCatching {
                if (roleId == null) serverApi.createRole(serverId, body)
                else serverApi.updateRole(serverId, roleId, body)
            }
            if (r.isSuccess) {
                _state.value.selectedServer?.id?.let { if (it == serverId) loadMembers(it) }
                onResult(null)
            } else {
                onResult(apiMessage(r.exceptionOrNull(), "Não foi possível salvar o cargo"))
            }
        }
    }

    fun deleteRole(serverId: String, roleId: String, onResult: (String?) -> Unit) {
        scope.launch {
            val r = runCatching { serverApi.deleteRole(serverId, roleId) }
            if (r.isSuccess) {
                loadMembers(serverId)
                onResult(null)
            } else {
                onResult(apiMessage(r.exceptionOrNull(), "Não foi possível excluir o cargo"))
            }
        }
    }

    fun setMemberRole(serverId: String, memberId: String, roleId: String, give: Boolean, onResult: (String?) -> Unit) {
        scope.launch {
            val r = runCatching {
                if (give) serverApi.assignRole(serverId, memberId, roleId)
                else serverApi.unassignRole(serverId, memberId, roleId)
            }
            if (r.isSuccess) {
                loadMembers(serverId)
                onResult(null)
            } else {
                onResult(apiMessage(r.exceptionOrNull(), "Não foi possível mudar o cargo do membro"))
            }
        }
    }

    fun loadBans(serverId: String, onResult: (List<BanDto>?, String?) -> Unit) {
        scope.launch {
            val r = runCatching { serverApi.bans(serverId).data.orEmpty() }
            r.onSuccess { onResult(it, null) }
                .onFailure { onResult(null, apiMessage(it, "Não foi possível carregar os banimentos")) }
        }
    }

    fun unbanUser(serverId: String, userId: String, onResult: (String?) -> Unit) {
        scope.launch {
            val r = runCatching { serverApi.unban(serverId, userId) }
            onResult(if (r.isSuccess) null else apiMessage(r.exceptionOrNull(), "Não foi possível revogar o banimento"))
        }
    }

    private fun apiMessage(t: Throwable?, fallback: String): String {
        val http = t as? HttpException ?: return "$fallback — sem conexão"
        val parsed = runCatching { http.response()?.errorBody()?.string() }.getOrNull()
            ?.let { runCatching { json.decodeFromString<ApiError>(it) }.getOrNull() }
        return parsed?.error?.takeIf { it.isNotBlank() } ?: "$fallback (erro ${http.code()})"
    }

    private var announcedVoice: String? = null

    fun openVoice(channel: ChannelDto) {
        _state.update { it.copy(voiceChannel = channel, chat = null, friendsOpen = false) }
        saveLocation()
    }

    fun announceVoiceJoin(channelId: String) {
        if (announcedVoice == channelId) return
        announcedVoice?.let { socket.voiceLeave(it) }
        announcedVoice = channelId
        socket.voiceJoin(channelId)
    }

    fun leaveVoice() {
        announcedVoice?.let { socket.voiceLeave(it) }
        announcedVoice = null
        _state.update { it.copy(voiceChannel = null) }
    }

    fun toggleMembers() = _state.update { it.copy(membersOpen = !it.membersOpen) }

    fun dispensarPenalidade() = _state.update { it.copy(penalidade = null) }

    private fun listenRealtime() {
        scope.launch {
            launch {
                socket.channelActivity.collect { raw ->
                    val ev = decode<ChannelActivityEventDto>(raw) ?: return@collect
                    if (_state.value.chat?.id != ev.channelId) {
                        _state.update {
                            it.copy(
                                unread = it.unread + ev.channelId,
                                unreadCounts = it.unreadCounts + (ev.channelId to ((it.unreadCounts[ev.channelId] ?: 0) + 1)),
                            )
                        }
                    }
                }
            }
            launch {
                socket.presenceUpdate.collect { raw ->
                    val ev = decode<PresenceUpdateDto>(raw) ?: return@collect
                    _state.update {
                        val norm = if (ev.status == "INVISIBLE") "OFFLINE" else ev.status
                        val ehMembro = it.members.any { m -> m.userId == ev.userId }
                        val ehSussurro = it.dms.any { c -> c.otherUser?.id == ev.userId }
                        when {
                            ehMembro && ehSussurro -> it.copy(
                                memberPresence = it.memberPresence + (ev.userId to norm),
                                dmPresence = it.dmPresence + (ev.userId to norm),
                            )
                            ehMembro -> it.copy(memberPresence = it.memberPresence + (ev.userId to norm))
                            ehSussurro -> it.copy(dmPresence = it.dmPresence + (ev.userId to norm))
                            else -> it
                        }
                    }
                }
            }
            launch {
                socket.activityUpdate.collect { raw ->
                    val ev = decode<ActivityUpdateDto>(raw) ?: return@collect
                    _state.update {
                        if (it.members.none { m -> m.userId == ev.userId }) return@update it
                        val texto = ev.activity?.takeIf { t -> t.isNotBlank() }
                        if (texto == null) it.copy(memberActivity = it.memberActivity - ev.userId)
                        else it.copy(memberActivity = it.memberActivity + (ev.userId to texto))
                    }
                }
            }
            launch {
                socket.newDm.collect { raw ->
                    val msg = decode<DmMessageDto>(raw) ?: return@collect
                    dmTypingStopped(msg.conversationId, msg.senderId)
                    if (_state.value.dms.none { it.id == msg.conversationId }) {
                        val novas = runCatching { dmApi.conversations().data.orEmpty() }.getOrNull()
                        if (!novas.isNullOrEmpty()) {
                            novas.forEach { socket.joinDm(it.id) }
                            _state.update { st ->
                                val naoLida = if (msg.senderId != myId) st.unread + msg.conversationId else st.unread
                                st.copy(dms = novas, unread = naoLida)
                            }
                            carregarPresencaDosSussurros()
                        }
                        return@collect
                    }
                    _state.update { st ->
                        val idx = st.dms.indexOfFirst { it.id == msg.conversationId }
                        val dms = if (idx < 0) st.dms else {
                            val conv = st.dms[idx].copy(
                                lastMessage = LastMessageDto(
                                    content = msg.content,
                                    senderId = msg.senderId,
                                    createdAt = msg.createdAt,
                                ),
                            )
                            listOf(conv) + st.dms.filterIndexed { i, _ -> i != idx }
                        }
                        val mine = msg.senderId == myId
                        val muted = st.dms.any { it.id == msg.conversationId && it.muted }
                        val unread =
                            if (!mine && !muted && st.chat?.id != msg.conversationId) st.unread + msg.conversationId
                            else st.unread
                        st.copy(dms = dms, unread = unread)
                    }
                }
            }
            launch {
                socket.canalMudou.collect { raw ->
                    val ev = decode<CanalMudouDto>(raw) ?: return@collect
                    _state.update { st ->
                        st.copy(
                            servers = st.servers.map { s ->
                                if (s.id != ev.serverId) s
                                else s.copy(
                                    channels = (s.channels.filterNot { it.id == ev.canal.id } + ev.canal)
                                        .sortedBy { it.position },
                                )
                            },
                        )
                    }
                }
            }
            launch {
                socket.canalSumiu.collect { raw ->
                    val ev = decode<CanalSumiuDto>(raw) ?: return@collect
                    tirarCanal(ev.serverId, ev.channelId)
                }
            }
            launch {
                socket.categoriaMudou.collect { raw ->
                    val ev = decode<CategoriaMudouDto>(raw) ?: return@collect
                    _state.update { st ->
                        st.copy(
                            servers = st.servers.map { s ->
                                if (s.id != ev.serverId) s
                                else s.copy(
                                    categories = (s.categories.filterNot { it.id == ev.categoria.id } + ev.categoria)
                                        .sortedBy { it.position },
                                )
                            },
                        )
                    }
                }
            }
            launch {
                socket.categoriaSumiu.collect { raw ->
                    val ev = decode<CategoriaSumiuDto>(raw) ?: return@collect
                    _state.update { st ->
                        st.copy(
                            servers = st.servers.map { s ->
                                if (s.id != ev.serverId) s
                                else s.copy(categories = s.categories.filterNot { it.id == ev.categoryId })
                            },
                        )
                    }
                }
            }
            launch {
                socket.membroEntrou.collect { raw ->
                    val ev = decode<MembroEntrouDto>(raw) ?: return@collect
                    if ((_state.value.selection as? Selection.Server)?.id != ev.serverId) return@collect
                    _state.update { st ->
                        if (st.members.any { it.userId == ev.membro.userId }) st
                        else st.copy(
                            members = st.members + ev.membro,
                            memberPresence = st.memberPresence + (ev.membro.userId to ev.presenca),
                        )
                    }
                }
            }
            launch {
                socket.membroSaiu.collect { raw ->
                    val ev = decode<MembroSaiuDto>(raw) ?: return@collect
                    if ((_state.value.selection as? Selection.Server)?.id != ev.serverId) return@collect
                    _state.update { st ->
                        st.copy(members = st.members.filterNot { it.userId == ev.userId })
                    }
                }
            }
            launch {
                socket.membroMudouDeCargo.collect { raw ->
                    val ev = decode<MembroMudouDeCargoDto>(raw) ?: return@collect
                    if ((_state.value.selection as? Selection.Server)?.id != ev.serverId) return@collect
                    val mexeuComigo = _state.value.members.any { it.id == ev.memberId && it.userId == myId }
                    _state.update { st ->
                        st.copy(
                            members = st.members.map {
                                if (it.id == ev.memberId) it.copy(role = ev.role) else it
                            },
                        )
                    }
                    if (mexeuComigo) refreshMyPerms(ev.serverId)
                }
            }
            launch {
                socket.profileUpdated.collect { raw ->
                    val ev = decode<ProfileUpdatedDto>(raw) ?: return@collect
                    invalidateProfileCache(ev.userId)
                    if (ev.userId == myId) {
                        refreshMe()
                    }
                    val perfil = ev.publico ?: return@collect
                    _state.update { st ->
                        val naLista = st.members.any { it.userId == ev.userId }
                        val noSussurro = st.dms.any { it.otherUser?.id == ev.userId }
                        if (!naLista && !noSussurro) return@update st

                        val dms =
                            if (!noSussurro) st.dms
                            else st.dms.map { conversa ->
                                val outro = conversa.otherUser
                                if (outro?.id != ev.userId) conversa
                                else conversa.copy(
                                    otherUser = outro.copy(
                                        username = perfil.username,
                                        displayName = perfil.displayName,
                                        avatarUrl = perfil.avatarUrl,
                                        displayFont = perfil.displayFont,
                                    ),
                                )
                            }
                        val aberto = st.chat
                        val chat =
                            if (noSussurro && aberto is ChatTarget.Dm &&
                                dms.any { it.id == aberto.id && it.otherUser?.id == ev.userId }
                            ) aberto.copy(title = perfil.displayName ?: perfil.username)
                            else aberto

                        st.copy(
                            members =
                                if (!naLista) st.members
                                else st.members.map {
                                    if (it.userId == ev.userId) it.copy(user = perfil) else it
                                },
                            dms = dms,
                            chat = chat,
                        )
                    }
                }
            }
            launch {
                socket.serverJoined.collect { raw ->
                    val ev = decode<ServerScopedEventDto>(raw) ?: return@collect
                    socket.joinServer(ev.serverId)
                    reloadServers()
                }
            }
            launch {
                socket.constelacaoMudou.collect { raw ->
                    val ev = decode<ConstelacaoMudouDto>(raw) ?: return@collect
                    _state.update { st ->
                        st.copy(
                            servers = st.servers.map { s ->
                                if (s.id != ev.serverId) s
                                else ev.constelacao.copy(
                                    channels = s.channels,
                                    categories = s.categories,
                                    count = s.count,
                                )
                            },
                        )
                    }
                }
            }
            launch {
                socket.serverAccessLost.collect { raw ->
                    val ev = decode<ServerScopedEventDto>(raw) ?: return@collect
                    val st = _state.value
                    val nome = st.servers.firstOrNull { it.id == ev.serverId }?.name

                    val naCallDaqui = st.voiceChannel?.let { canal ->
                        st.servers.firstOrNull { s -> s.id == ev.serverId }
                            ?.channels?.any { c -> c.id == canal.id } == true
                    } == true
                    if (naCallDaqui) leaveVoice()

                    if ((st.selection as? Selection.Server)?.id == ev.serverId) {
                        _state.update {
                            it.copy(
                                selection = Selection.Dms,
                                chat = null,
                                members = emptyList(),
                                friendsOpen = true,
                            )
                        }
                    }
                    if (ev.motivo == "expulso" || ev.motivo == "banido") {
                        _state.update {
                            it.copy(penalidade = Penalidade(ev.motivo, nome, ev.reason))
                        }
                    }
                    _state.update {
                        it.copy(servers = it.servers.filterNot { s -> s.id == ev.serverId })
                    }
                }
            }
            launch {
                socket.membrosRefeitos.collect { raw ->
                    val ev = decode<MembrosRefeitosDto>(raw) ?: return@collect
                    if ((_state.value.selection as? Selection.Server)?.id != ev.serverId) return@collect
                    _state.update { it.copy(members = ev.membros) }
                    refreshMyPerms(ev.serverId)
                }
            }
            launch {
                socket.cargosDoMembro.collect { raw ->
                    val ev = decode<CargosDoMembroDto>(raw) ?: return@collect
                    if ((_state.value.selection as? Selection.Server)?.id != ev.serverId) return@collect
                    val mexeuComigo = _state.value.members.any { it.id == ev.memberId && it.userId == myId }
                    _state.update { st ->
                        st.copy(
                            members = st.members.map {
                                if (it.id == ev.memberId) it.copy(roles = ev.roles, topColor = ev.topColor) else it
                            },
                        )
                    }
                    if (mexeuComigo) refreshMyPerms(ev.serverId)
                }
            }
            launch {
                socket.reconnected.collect {
                    reloadServers()
                    (_state.value.selection as? Selection.Server)?.id?.let { loadMembers(it) }
                    runCatching { dmApi.conversations().data.orEmpty() }.getOrNull()?.let { dms ->
                        dms.forEach { socket.joinDm(it.id) }
                        _state.update { it.copy(dms = dms) }
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
        _state.update { it.copy(dmTyping = it.dmTyping - conversationId) }
    }

    private inline fun <reified T> decode(raw: String): T? =
        runCatching { json.decodeFromString<T>(raw) }.getOrNull()

    private fun tirarCanal(serverId: String, channelId: String) {
        if (_state.value.voiceChannel?.id == channelId) leaveVoice()
        val eraOAberto = (_state.value.chat as? ChatTarget.Channel)?.id == channelId
        _state.update { st ->
            st.copy(
                servers = st.servers.map { s ->
                    if (s.id != serverId) s
                    else s.copy(channels = s.channels.filterNot { it.id == channelId })
                },
            )
        }
        if (!eraOAberto) return
        val proximo = _state.value.servers.find { it.id == serverId }
            ?.channels?.firstOrNull { it.type != "VOICE" }
        if (proximo == null) {
            _state.update { it.copy(chat = null) }
            saveLocation()
        } else {
            openChat(ChatTarget.Channel(proximo.id, proximo.name))
        }
    }

    private fun refreshMyPerms(serverId: String) {
        scope.launch {
            val perms = runCatching { serverApi.myPerms(serverId).data }.getOrNull() ?: return@launch
            _state.update {
                if ((it.selection as? Selection.Server)?.id == serverId) it.copy(myPerms = perms) else it
            }
        }
    }

    private fun loadMembers(serverId: String) {
        scope.launch {
            val membersD = async { runCatching { serverApi.members(serverId).data.orEmpty() }.getOrDefault(emptyList()) }
            val permsD = async { runCatching { serverApi.myPerms(serverId).data }.getOrNull() }
            val members = membersD.await()
            val perms = permsD.await()
            val ids = members.joinToString(",") { it.userId }
            val presence = if (members.isNotEmpty()) {
                runCatching { userApi.presence(ids).data.orEmpty() }.getOrDefault(emptyMap())
            } else emptyMap()
            val atividade = if (members.isNotEmpty()) {
                runCatching { userApi.activity(ids).data.orEmpty().mapValues { it.value.text } }
                    .getOrDefault(emptyMap())
            } else emptyMap()
            _state.update {
                if ((it.selection as? Selection.Server)?.id == serverId) {
                    it.copy(members = members, memberPresence = presence, memberActivity = atividade, myPerms = perms)
                } else it
            }
        }
    }
}
