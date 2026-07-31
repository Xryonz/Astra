package app.astra.desktop.shell

import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.DesktopSocket
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
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.LastMessageDto
import app.astra.mobile.core.network.dto.DmTypingEventDto
import app.astra.mobile.core.network.dto.OpenDmRequest
import app.astra.mobile.core.network.dto.PresenceUpdateDto
import app.astra.mobile.core.network.dto.ServerScopedEventDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.RoleDto
import app.astra.mobile.core.network.dto.RoleRequest
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.MyPermsDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import app.astra.mobile.core.network.dto.VoicePresenceEventDto
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
    // Presenca por userId dos membros da constelação atual (ONLINE/IDLE/DND/OFFLINE).
    // Snapshot no load + patch ao vivo via socket presence_update. Ausente = OFFLINE.
    val memberPresence: Map<String, String> = emptyMap(),
    val membersOpen: Boolean = true,
    val chat: ChatTarget? = null,
    // "Amigos" aberto no palco (area dos sussurros) — some ao abrir uma conversa.
    val friendsOpen: Boolean = false,
    // Sala de voz aberta no palco (sonda V1; persistir em navegacao = V6).
    val voiceChannel: ChannelDto? = null,
    // Ids (canal ou conversa) com mensagem que você ainda não viu.
    val unread: Set<String> = emptySet(),
    // Contagem de não-lidas por canal (badge com numero). So canais; DM usa o
    // booleano acima. Sobe no load (backend) + incrementa ao vivo via socket.
    val unreadCounts: Map<String, Int> = emptyMap(),
    // Canais/constelações silenciados (mode "mute" no backend de notif prefs).
    val mutedChannels: Set<String> = emptySet(),
    val mutedServers: Set<String> = emptySet(),
    // Conversas DM com alguem digitando agora (sidebar mostra "digitando…").
    val dmTyping: Set<String> = emptySet(),
    // Quem esta em cada canal de voz (channelId -> userIds), via poll ~5s do
    // /voice/presence. Alimenta a lista de presenca sob o canal na sidebar.
    val voicePresence: Map<String, List<String>> = emptyMap(),
    // Minhas permissões NA CONSTELACAO SELECIONADA (GET /servers/:id/me). Decide
    // quem ve "configurações" no menu da rail. So da selecionada: buscar de todas
    // seria uma requisicao por constelação no boot, e a API dorme no Render free.
    val myPerms: MyPermsDto? = null,
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
    private val inviteApi: InviteApi,
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

    // Presenca de voz: quem entra/sai AVISA por socket (voice_presence) e aplicamos o
    // delta na hora. O poll continua como fonte AUTORITATIVA — a verdade mora no
    // LiveKit e so ele sabe de fantasma (queda de rede/crash não emite 'leave') —, mas
    // agora bem mais espacado, ja que o caso comum chega pelo evento. Antes era poll de
    // 5s + cache de 5s no servidor = ate ~10s pra ver alguem entrar na call.
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
                delay(20_000)
            }
        }
        scope.launch {
            socket.voicePresence.collect { raw ->
                val ev = runCatching { json.decodeFromString<VoicePresenceEventDto>(raw) }.getOrNull()
                    ?: return@collect
                _state.update { st ->
                    // So mexe em canal que esta na constelação aberta (o resto nem e exibido).
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
            val unreadCountsD = async { runCatching { serverApi.channelUnreadCounts().data.orEmpty() }.getOrDefault(emptyMap()) }
            val dmReadsD = async { runCatching { dmApi.dmReads().data.orEmpty() }.getOrDefault(emptyMap()) }

            val servers = serversD.await()
            if (servers == null) {
                _state.update { it.copy(loading = false, error = "Sem conexão com o servidor") }
                return@launch
            }
            val dms = dmsD.await()

            // Entra na sala de todas as DMs: typing/new_dm so chegam pra quem
            // está na sala (o rejoin pos-reconnect já cobre estas também).
            dms.forEach { socket.joinDm(it.id) }

            // Não lida = última mensagem depois da última leitura (sem leitura
            // registrada também conta). DM mutada ou cuja última e minha, não.
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

            // Restaura a última selecao (se a constelação ainda existe).
            val saved = Selection.decode(store.uiPref("lastSelection"))
            val selection = when (saved) {
                is Selection.Server -> if (servers.any { it.id == saved.id }) saved else Selection.Dms
                Selection.Discover -> Selection.Discover
                Selection.Dms -> Selection.Dms
            }

            // Restaura também o que estava ABERTO ao fechar (canal/DM/amigos), pra
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
                    unreadCounts = unreadCountsD.await() - setOfNotNull(restoredChat?.id),
                )
            }
            store.setUiPref("lastSelection", finalSelection.encode())
            if (finalSelection is Selection.Server) loadMembers(finalSelection.id)
        }
    }

    fun select(selection: Selection) {
        // Ja nesta aba: não reseta chat/membros nem re-dispara a animação de
        // entrada. Re-clicar o mesmo servidor mantem a conversa aberta (as
        // mensagens novas já chegam pelo socket ao vivo — nada pra recarregar).
        if (_state.value.selection == selection) return
        // myPerms zera junto com members: são da constelação ANTERIOR. Deixar o
        // valor velho faria o menu oferecer "configurações" numa constelação onde
        // você não manda nada (o backend recusaria, mas a UI já teria mentido).
        _state.update {
            it.copy(selection = selection, members = emptyList(), memberPresence = emptyMap(), myPerms = null, chat = null, friendsOpen = false)
        }
        store.setUiPref("lastSelection", selection.encode())
        saveLocation()
        if (selection is Selection.Server) loadMembers(selection.id)
    }

    // "Amigos" no topo dos sussurros: ocupa o palco (fecha conversa/voz).
    fun openFriends() {
        _state.update { it.copy(friendsOpen = true, chat = null, voiceChannel = null) }
        saveLocation()
    }

    // Persiste ONDE o usuário esta (canal/DM/amigos) pra restaurar no próximo boot.
    // Voz NAO entra: não auto-reconecta call. Le o estado JA atualizado.
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

    // Abrir a conversa limpa a não-lida local (o POST /read fica no ChatVm).
    // V1 da voz: abrir texto SAI da sala (chamada persistente/mini-dock = V6).
    fun openChat(target: ChatTarget) {
        // Mesma conversa já aberta: não recria o ChatVm (evitaria recarregar tudo
        // + replay do fade). As mensagens novas já chegam pelo socket em tempo real.
        if (_state.value.chat == target) return
        _state.update { it.copy(chat = target, voiceChannel = null, friendsOpen = false, unread = it.unread - target.id, unreadCounts = it.unreadCounts - target.id) }
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

    // "Fechar sussurro": some da MINHA lista (nada e apagado, o outro lado nem
    // fica sabendo, e volta sozinho na proxima mensagem). Otimista: tira da lista
    // na hora e, se a conversa fechada estava aberta no palco, esvazia o palco —
    // ficar olhando pra uma conversa que "não existe mais" seria esquisito.
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

    // Excluir a constelação (so o dono). Mesma limpeza de estado do leave; a UI so
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

    // Expulsar / banir (so o dono na UI; backend exige permissão). Recarrega a
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

    // "Enviar sussurro" do card de perfil: abre/cria a conversa e já cai nela.
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

    // Cria constelação (ou grupo) pelo "+" da rail: cria, recarrega a lista e já
    // cai na nova (vira a selecao ativa).
    fun createServer(name: String, isGroup: Boolean) {
        scope.launch {
            val created = runCatching { serverApi.create(CreateServerRequest(name, isGroup)).data }.getOrNull() ?: return@launch
            socket.joinServer(created.id) // nasceu depois do connect: entra na sala dela
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

    // Entrou numa constelação pela Descoberta: recarrega a lista de servidores e
    // já cai nela (vira a selecao ativa, como se tivesse clicado na rail).
    fun refreshServersAndSelect(serverId: String) {
        scope.launch {
            // Entrei AGORA: o socket so inscreveu nas constelacoes que eu tinha no
            // connect, entao sem isto eu nao receberia aviso de canal novo aqui.
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

    // Gestao de canais/categorias (dono da constelação). Cada ação bate na API e
    // recarrega so a lista de servidores (mantem selecao/chat). Sem otimismo: o
    // reload traz o estado real (posição/id vindos do backend).
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
    // orderedIds = nova ordem dos ids da secao. Preserva os VALORES de position já
    // existentes, so permutando quem fica com qual (não reindexa pra 0-base, pra não
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

    // Mover uma órbita PRA DENTRO de outra categoria (drag cross-categoria). position =
    // fim da categoria alvo. Otimista: troca categoryId+position local; PATCH com categoryId
    // (não-nulo -> serializa); reload reconcilia. So o dono chega aqui (a UI so habilita drag
    // pro dono). Mover pra "solta" (categoryId null explicito) e' bloqueado pelo explicitNulls.
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

    // Reordena as CATEGORIAS entre si (drag no cabecalho). orderedIds = nova ordem; PATCH
    // position dos que mudaram. Otimista + reload reconcilia. So o dono chega aqui.
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

    // Marca todos os canais não-lidos da constelação como lidos (menu da rail / vazio).
    fun markServerRead(serverId: String) {
        val srv = _state.value.servers.find { it.id == serverId } ?: return
        val unread = _state.value.unread
        srv.channels.forEach { if (it.id in unread) markChannelRead(it.id) }
    }

    // ---- Menu de canal (botao direito na órbita) ----
    // Marcar lido: qualquer membro. Renomear/excluir: so o dono (a UI gateia).
    fun markChannelRead(channelId: String) {
        scope.launch { runCatching { channelApi.markRead(channelId) } }
        _state.update { it.copy(unread = it.unread - channelId, unreadCounts = it.unreadCounts - channelId) }
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

    // ---- Configuracoes da constelação ----
    // Salvar devolve o erro REAL do backend pra tela mostrar (nome duplicado,
    // imagem grande demais, sem permissão) em vez de um "não deu" generico.
    // Sucesso -> recarrega a lista: a rail repinta com o ícone/nome novo.
    fun updateServer(serverId: String, body: UpdateServerRequest, onResult: (String?) -> Unit) {
        scope.launch {
            val r = runCatching { serverApi.update(serverId, body) }
            if (r.isSuccess) {
                reloadServers()
                onResult(null)
            } else {
                onResult(apiMessage(r.exceptionOrNull(), "Não deu pra salvar"))
            }
        }
    }

    // Adiciona alguem pelo @usuario. Nao passa por convite nenhum: o backend checa
    // permissao (dono ou MANAGE_SERVER), banimento e se já e membro. Sucesso ->
    // recarrega os membros pra pessoa aparecer no painel na hora.
    fun addMember(serverId: String, username: String, onResult: (String?) -> Unit) {
        val u = username.trim().removePrefix("@")
        if (u.isBlank()) { onResult("Digite o nome de usuário"); return }
        scope.launch {
            val r = runCatching { serverApi.addMember(serverId, u) }
            if (r.isSuccess) {
                loadMembers(serverId)
                onResult(null)
            } else {
                onResult(apiMessage(r.exceptionOrNull(), "Não deu pra adicionar essa pessoa"))
            }
        }
    }

    // Entrar por convite. Aceita o codigo cru OU o link inteiro colado (o usuário
    // vai colar o link, não o codigo) — por isso o parse tolerante.
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

    // Regenerar convite: o código novo vem na resposta, mas quem manda na UI e o
    // ServerDto da lista — entao recarrega pra não ficar mostrando o antigo.
    fun regenerateInvite(serverId: String, onResult: (String?) -> Unit) {
        scope.launch {
            val r = runCatching { serverApi.regenerateInvite(serverId) }
            if (r.isSuccess) {
                reloadServers()
                onResult(null)
            } else {
                onResult(apiMessage(r.exceptionOrNull(), "Não deu pra gerar um convite novo"))
            }
        }
    }

    // ---- Cargos ----
    // A tela e dona da propria lista (não entra no ShellUiState): so ela usa, e
    // manter no estado global obrigaria a invalidar em lugares que não ligam.
    fun loadRoles(serverId: String, onResult: (List<RoleDto>?, String?) -> Unit) {
        scope.launch {
            val r = runCatching { serverApi.roles(serverId).data.orEmpty() }
            r.onSuccess { onResult(it, null) }
                .onFailure { onResult(null, apiMessage(it, "Não deu pra carregar os cargos")) }
        }
    }

    fun saveRole(serverId: String, roleId: String?, body: RoleRequest, onResult: (String?) -> Unit) {
        scope.launch {
            val r = runCatching {
                if (roleId == null) serverApi.createRole(serverId, body)
                else serverApi.updateRole(serverId, roleId, body)
            }
            if (r.isSuccess) {
                // Membros recarregam junto: a cor/hoist do cargo muda como a lista
                // de membros se agrupa e se pinta.
                _state.value.selectedServer?.id?.let { if (it == serverId) loadMembers(it) }
                onResult(null)
            } else {
                onResult(apiMessage(r.exceptionOrNull(), "Não deu pra salvar o cargo"))
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
                onResult(apiMessage(r.exceptionOrNull(), "Não deu pra excluir o cargo"))
            }
        }
    }

    // memberId aqui e o id do MEMBRO (serverMembers.id), não o do usuário.
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
                onResult(apiMessage(r.exceptionOrNull(), "Não deu pra mudar o cargo do membro"))
            }
        }
    }

    // ---- Banimentos ----
    fun loadBans(serverId: String, onResult: (List<BanDto>?, String?) -> Unit) {
        scope.launch {
            val r = runCatching { serverApi.bans(serverId).data.orEmpty() }
            r.onSuccess { onResult(it, null) }
                .onFailure { onResult(null, apiMessage(it, "Não deu pra carregar os banimentos")) }
        }
    }

    // userId (não memberId): quem foi banido já não e membro.
    fun unbanUser(serverId: String, userId: String, onResult: (String?) -> Unit) {
        scope.launch {
            val r = runCatching { serverApi.unban(serverId, userId) }
            onResult(if (r.isSuccess) null else apiMessage(r.exceptionOrNull(), "Não deu pra revogar o banimento"))
        }
    }

    // Mensagem de erro do backend ({ error }) quando houver; senao um texto curto.
    private fun apiMessage(t: Throwable?, fallback: String): String {
        val http = t as? HttpException ?: return "$fallback — sem conexão"
        val parsed = runCatching { http.response()?.errorBody()?.string() }.getOrNull()
            ?.let { runCatching { json.decodeFromString<ApiError>(it) }.getOrNull() }
        return parsed?.error?.takeIf { it.isNotBlank() } ?: "$fallback (erro ${http.code()})"
    }

    // Canal que ANUNCIAMOS por socket estar na call. Nao e o mesmo que voiceChannel:
    // aquele e so a sala aberta NO PALCO, e clicar numa sala abre a antessala (ver
    // quem esta la antes de abrir o microfone) — o que nunca deveria contar como
    // entrar. Era exatamente essa confusao que fazia "clicar ja aparecer na call".
    private var announcedVoice: String? = null

    // Abrir a antessala. So navegacao: nao entra na call nem avisa ninguem.
    fun openVoice(channel: ChannelDto) {
        // Voz não e restaurada no boot: limpa o lastChat (saveLocation le chat=null).
        _state.update { it.copy(voiceChannel = channel, chat = null, friendsOpen = false) }
        saveLocation()
    }

    // Entrei DE VERDADE (botao verde da antessala). Avisa a constelação na hora; o
    // poll do /voice/presence ainda corrige depois.
    fun announceVoiceJoin(channelId: String) {
        if (announcedVoice == channelId) return
        announcedVoice?.let { socket.voiceLeave(it) } // uma call por vez
        announcedVoice = channelId
        socket.voiceJoin(channelId)
    }

    fun leaveVoice() {
        announcedVoice?.let { socket.voiceLeave(it) }
        announcedVoice = null
        _state.update { it.copy(voiceChannel = null) }
    }

    fun toggleMembers() = _state.update { it.copy(membersOpen = !it.membersOpen) }

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
                    // So mexe se o user já e membro visivel da constelação atual —
                    // evita recompor o painel a cada presenca do app inteiro.
                    _state.update {
                        if (it.members.any { m -> m.userId == ev.userId }) {
                            val norm = if (ev.status == "INVISIBLE") "OFFLINE" else ev.status
                            it.copy(memberPresence = it.memberPresence + (ev.userId to norm))
                        } else it
                    }
                }
            }
            launch {
                socket.newDm.collect { raw ->
                    val msg = decode<DmMessageDto>(raw) ?: return@collect
                    dmTypingStopped(msg.conversationId, msg.senderId)
                    // DELTA da barra lateral: aplica a previa ("Você: ..."/texto) e sobe a
                    // conversa pro topo na hora. Antes so marcava não-lida e a previa/ordem
                    // ficavam velhas ate um reload — o classico "chegou mensagem mas a lista
                    // continua igual". Vale pras MINHAS tambem (por isso o filtro de senderId
                    // saiu daqui e virou so a regra do não-lida abaixo).
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
            // Constelacao mexeu. Sao PINGS ("mudou, busca de novo"), nao deltas: canal
            // privado faz cada membro ver uma lista diferente, entao so o backend sabe
            // o que cada um deve enxergar. Uma busca extra num evento raro e barato —
            // o caro era o canal novo so aparecer pros outros no proximo boot do app.
            launch {
                socket.serverChannels.collect { reloadServers() }
            }
            launch {
                socket.serverMembers.collect { raw ->
                    val ev = decode<ServerScopedEventDto>(raw) ?: return@collect
                    // So recarrega se for a constelacao ABERTA (o painel de membros das
                    // outras nem esta na tela).
                    if ((_state.value.selection as? Selection.Server)?.id == ev.serverId) {
                        loadMembers(ev.serverId)
                    }
                }
            }
            launch {
                socket.serverJoined.collect { raw ->
                    val ev = decode<ServerScopedEventDto>(raw) ?: return@collect
                    // Fui adicionado agora: entra na sala dela (o connect nao a incluia)
                    // e traz a constelacao nova pra rail.
                    socket.joinServer(ev.serverId)
                    reloadServers()
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
            // Membros e permissões juntos: são pedidos pelos mesmos gatilhos (trocar
            // de constelação) e não dependem um do outro -> em paralelo.
            val membersD = async { runCatching { serverApi.members(serverId).data.orEmpty() }.getOrDefault(emptyList()) }
            val permsD = async { runCatching { serverApi.myPerms(serverId).data }.getOrNull() }
            val members = membersD.await()
            val perms = permsD.await()
            // Presenca dos membros num único mget (ONLINE colorido / OFFLINE apagado).
            // Fail-safe: se cair, mapa vazio -> todos aparecem offline, sem quebrar.
            val presence = if (members.isNotEmpty()) {
                val ids = members.joinToString(",") { it.userId }
                runCatching { userApi.presence(ids).data.orEmpty() }.getOrDefault(emptyMap())
            } else emptyMap()
            // So aplica se a selecao não mudou enquanto carregava.
            _state.update {
                if ((it.selection as? Selection.Server)?.id == serverId) {
                    it.copy(members = members, memberPresence = presence, myPerms = perms)
                } else it
            }
        }
    }
}
