package app.astra.desktop.shell

import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.NotificationApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ChannelActivityEventDto
import app.astra.mobile.core.network.dto.BanRequest
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.CreateCategoryRequest
import app.astra.mobile.core.network.dto.CreateChannelRequest
import app.astra.mobile.core.network.dto.CreateServerRequest
import app.astra.mobile.core.network.dto.MoveChannelRequest
import app.astra.mobile.core.network.dto.NotifModeRequest
import app.astra.mobile.core.network.dto.UpdateChannelNameRequest
import app.astra.mobile.core.network.dto.UpdateCategoryRequest
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.DmTypingEventDto
import app.astra.mobile.core.network.dto.OpenDmRequest
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
    // "Amigos" aberto no palco (area dos sussurros) — some ao abrir uma conversa.
    val friendsOpen: Boolean = false,
    // Sala de voz aberta no palco (sonda V1; persistir em navegacao = V6).
    val voiceChannel: ChannelDto? = null,
    // Ids (canal ou conversa) com mensagem que voce ainda nao viu.
    val unread: Set<String> = emptySet(),
    // Canais/constelacoes silenciados (mode "mute" no backend de notif prefs).
    val mutedChannels: Set<String> = emptySet(),
    val mutedServers: Set<String> = emptySet(),
    // Conversas DM com alguem digitando agora (sidebar mostra "digitando…").
    val dmTyping: Set<String> = emptySet(),
    // Quem esta em cada canal de voz (channelId -> userIds), via poll ~5s do
    // /voice/presence. Alimenta a lista de presenca sob o canal na sidebar.
    val voicePresence: Map<String, List<String>> = emptyMap(),
) {
    val selectedServer: ServerDto?
        get() = (selection as? Selection.Server)?.let { sel -> servers.find { it.id == sel.id } }
}

// Estado do shell. Sem ViewModel no desktop: classe simples presa ao escopo da
// composicao (rememberCoroutineScope).
class ShellVm(
    private val scope: CoroutineScope,
    private val serverApi: ServerApi,
    private val channelApi: ChannelApi,
    private val userApi: UserApi,
    private val dmApi: DmApi,
    private val voiceApi: VoiceApi,
    private val notifApi: NotificationApi,
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
        loadNotifPrefs()
        listenRealtime()
        pollVoicePresence()
    }

    // Presenca de voz: nao ha evento de socket (backend so tem o REST com cache
    // de 5s) — entao poll simples enquanto uma constelacao esta aberta. Pega os
    // canais de voz do servidor selecionado; DMs = limpa. Latencia ~5-10s (cache
    // do servidor + intervalo); aceitavel pro "quem esta na sala".
    private fun pollVoicePresence() {
        scope.launch {
            while (true) {
                val voiceIds = _state.value.selectedServer
                    ?.channels?.filter { it.type == "VOICE" }?.map { it.id }.orEmpty()
                if (voiceIds.isNotEmpty()) {
                    val pres = runCatching { voiceApi.presence(voiceIds.joinToString(",")).data.orEmpty() }
                        .getOrDefault(emptyMap())
                    _state.update { if (it.voicePresence != pres) it.copy(voicePresence = pres) else it }
                } else if (_state.value.voicePresence.isNotEmpty()) {
                    _state.update { it.copy(voicePresence = emptyMap()) }
                }
                delay(5_000)
            }
        }
    }

    // Recarrega so o proprio perfil (pos-edicao no card do rodape).
    fun refreshMe() {
        scope.launch {
            runCatching { userApi.me().data?.user }.getOrNull()?.let { u ->
                _state.update { it.copy(me = u) }
            }
        }
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
                Selection.Discover -> Selection.Discover
                Selection.Dms -> Selection.Dms
            }

            // Restaura tambem o que estava ABERTO ao fechar (canal/DM/amigos), pra
            // cair de volta no que estava fazendo. NAO reconecta voz (entrar numa
            // call sozinho ao abrir seria agressivo). Alvo que sumiu = ignora.
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
                )
            }
            store.setUiPref("lastSelection", finalSelection.encode())
            if (finalSelection is Selection.Server) loadMembers(finalSelection.id)
        }
    }

    fun select(selection: Selection) {
        // Ja nesta aba: nao reseta chat/membros nem re-dispara a animacao de
        // entrada. Re-clicar o mesmo servidor mantem a conversa aberta (as
        // mensagens novas ja chegam pelo socket ao vivo — nada pra recarregar).
        if (_state.value.selection == selection) return
        _state.update { it.copy(selection = selection, members = emptyList(), chat = null, friendsOpen = false) }
        store.setUiPref("lastSelection", selection.encode())
        saveLocation()
        if (selection is Selection.Server) loadMembers(selection.id)
    }

    // "Amigos" no topo dos sussurros: ocupa o palco (fecha conversa/voz).
    fun openFriends() {
        _state.update { it.copy(friendsOpen = true, chat = null, voiceChannel = null) }
        saveLocation()
    }

    // Persiste ONDE o usuario esta (canal/DM/amigos) pra restaurar no proximo boot.
    // Voz NAO entra: nao auto-reconecta call. Le o estado JA atualizado.
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

    // Abrir a conversa limpa a nao-lida local (o POST /read fica no ChatVm).
    // V1 da voz: abrir texto SAI da sala (chamada persistente/mini-dock = V6).
    fun openChat(target: ChatTarget) {
        // Mesma conversa ja aberta: nao recria o ChatVm (evitaria recarregar tudo
        // + replay do fade). As mensagens novas ja chegam pelo socket em tempo real.
        if (_state.value.chat == target) return
        _state.update { it.copy(chat = target, voiceChannel = null, friendsOpen = false, unread = it.unread - target.id) }
        saveLocation()
    }

    // Menu de botao direito (F4) ------------------------------------------------

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

    // Excluir a constelacao (so o dono). Mesma limpeza de estado do leave; a UI so
    // oferece isso quando ownerId == meu id.
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

    // Expulsar / banir (so o dono na UI; backend exige permissao). Recarrega a
    // lista de membros pra sumir com quem saiu.
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

    // "Enviar sussurro" do card de perfil: abre/cria a conversa e ja cai nela.
    fun startDm(username: String, title: String) {
        scope.launch {
            val conv = runCatching { dmApi.open(OpenDmRequest(username)).data }.getOrNull() ?: return@launch
            if (_state.value.dms.none { it.id == conv.conversationId }) {
                // Conversa nova: recarrega a lista e entra na sala dela (typing/new_dm).
                val dms = runCatching { dmApi.conversations().data.orEmpty() }
                    .getOrDefault(_state.value.dms)
                dms.forEach { socket.joinDm(it.id) }
                _state.update { it.copy(dms = dms) }
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

    // Cria constelacao (ou grupo) pelo "+" da rail: cria, recarrega a lista e ja
    // cai na nova (vira a selecao ativa).
    fun createServer(name: String, isGroup: Boolean) {
        scope.launch {
            val created = runCatching { serverApi.create(CreateServerRequest(name, isGroup)).data }.getOrNull() ?: return@launch
            val servers = runCatching { serverApi.servers().data.orEmpty() }.getOrDefault(_state.value.servers)
            _state.update {
                it.copy(
                    servers = servers,
                    selection = Selection.Server(created.id),
                    chat = null,
                    voiceChannel = null,
                    friendsOpen = false,
                    members = emptyList(),
                )
            }
            store.setUiPref("lastSelection", Selection.Server(created.id).encode())
            saveLocation()
            loadMembers(created.id)
        }
    }

    // Entrou numa constelacao pela Descoberta: recarrega a lista de servidores e
    // ja cai nela (vira a selecao ativa, como se tivesse clicado na rail).
    fun refreshServersAndSelect(serverId: String) {
        scope.launch {
            val servers = runCatching { serverApi.servers().data.orEmpty() }.getOrDefault(_state.value.servers)
            _state.update {
                it.copy(
                    servers = servers,
                    selection = Selection.Server(serverId),
                    chat = null,
                    voiceChannel = null,
                    members = emptyList(),
                )
            }
            store.setUiPref("lastSelection", Selection.Server(serverId).encode())
            saveLocation()
            loadMembers(serverId)
        }
    }

    // Gestao de canais/categorias (dono da constelacao). Cada acao bate na API e
    // recarrega so a lista de servidores (mantem selecao/chat). Sem otimismo: o
    // reload traz o estado real (posicao/id vindos do backend).
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

    // Reordena canais DENTRO de uma secao (soltos, ou de uma categoria) via drag.
    // orderedIds = nova ordem dos ids da secao. Preserva os VALORES de position ja
    // existentes, so permutando quem fica com qual (nao reindexa pra 0-base, pra nao
    // colidir com a position das outras secoes). Otimista: reposiciona local na hora;
    // persiste PATCHando so os que mudaram; reload reconcilia. So o dono chega aqui
    // (a UI so habilita o drag pro dono).
    fun reorderChannel(serverId: String, orderedIds: List<String>) {
        scope.launch {
            val srv0 = _state.value.servers.find { it.id == serverId } ?: return@launch
            // Posicao = INDICE na nova ordem (0,1,2...). Robusto mesmo quando os canais
            // tem position igual/0 (nascem sem position distinta): o metodo antigo
            // permutava os VALORES atuais e, com todos = 0, dava sempre "sem mudanca"
            // -> nenhum moveChannel era enviado e o reload voltava a ordem (o bug).
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

    // ---- Silenciar (notif prefs): backend channelNotifPrefs.ts, mode "mute" ----
    private fun loadNotifPrefs() {
        scope.launch {
            val ch = async { runCatching { notifApi.channelNotifPrefs().data.orEmpty() }.getOrDefault(emptyList()) }
            val sv = async { runCatching { notifApi.serverNotifPrefs().data.orEmpty() }.getOrDefault(emptyList()) }
            val mutedCh = ch.await().filter { it.mode == "mute" }.map { it.channelId }.toSet()
            val mutedSv = sv.await().filter { it.mode == "mute" }.map { it.serverId }.toSet()
            _state.update { it.copy(mutedChannels = mutedCh, mutedServers = mutedSv) }
        }
    }

    fun toggleChannelMute(channelId: String) {
        val muted = channelId in _state.value.mutedChannels
        _state.update { it.copy(mutedChannels = if (muted) it.mutedChannels - channelId else it.mutedChannels + channelId) }
        scope.launch {
            runCatching {
                if (muted) notifApi.clearChannelNotifPref(channelId)
                else notifApi.setChannelNotifPref(channelId, NotifModeRequest("mute"))
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

    // Marca todos os canais nao-lidos da constelacao como lidos (menu da rail / vazio).
    fun markServerRead(serverId: String) {
        val srv = _state.value.servers.find { it.id == serverId } ?: return
        val unread = _state.value.unread
        srv.channels.forEach { if (it.id in unread) markChannelRead(it.id) }
    }

    // ---- Menu de canal (botao direito na orbita) ----
    // Marcar lido: qualquer membro. Renomear/excluir: so o dono (a UI gateia).
    fun markChannelRead(channelId: String) {
        scope.launch { runCatching { channelApi.markRead(channelId) } }
        _state.update { it.copy(unread = it.unread - channelId) }
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

    fun openVoice(channel: ChannelDto) {
        // Voz nao e restaurada no boot: limpa o lastChat (saveLocation le chat=null).
        _state.update { it.copy(voiceChannel = channel, chat = null, friendsOpen = false) }
        saveLocation()
    }

    fun leaveVoice() = _state.update { it.copy(voiceChannel = null) }

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
