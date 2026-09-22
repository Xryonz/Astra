package app.astra.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.data.ArranjoLocal
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.network.BadgesApi
import app.astra.mobile.core.network.FriendsApi
import app.astra.mobile.core.network.NotificationsApi
import app.astra.mobile.core.network.dto.CustomStatusRequest
import app.astra.mobile.core.network.dto.NotifModeRequest
import app.astra.mobile.core.push.PushRegistrar
import app.astra.mobile.core.realtime.ConnectionState
import app.astra.mobile.core.realtime.SocketManager
import app.astra.mobile.feature.auth.domain.AuthRepository
import app.astra.mobile.feature.dm.domain.DmRepository
import app.astra.mobile.feature.dm.domain.model.OpenedConversation
import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.feature.server.domain.ServerRepository
import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.ui.components.toUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    socketManager: SocketManager,
    private val serverRepository: ServerRepository,
    private val dmRepository: DmRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
    private val notificationsApi: NotificationsApi,
    private val badgesApi: BadgesApi,
    private val friendsApi: FriendsApi,
    private val pushRegistrar: PushRegistrar,
    private val arranjo: ArranjoLocal,
) : ViewModel() {

    fun registerPush() = pushRegistrar.register()

    val socketState: StateFlow<ConnectionState> = socketManager.state

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    private val _opened = MutableSharedFlow<OpenedConversation>(extraBufferCapacity = 1)
    val opened = _opened.asSharedFlow()

    private val _serverCreated = MutableSharedFlow<Server>(extraBufferCapacity = 1)
    val serverCreated = _serverCreated.asSharedFlow()

    private var channelPrefModes: Map<String, String> = emptyMap()

    init {
        load()
        refreshNotifications()
        observeIncoming()
        viewModelScope.launch {
            arranjo.ordemDasConstelacoes.collect { ordem -> _state.update { it.copy(ordemDasOrbitas = ordem) } }
        }
        viewModelScope.launch {
            arranjo.categoriasRecolhidas.collect { ids -> _state.update { it.copy(categoriasRecolhidas = ids) } }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {

            val serversD = async { serverRepository.servers() }
            val convD = async { dmRepository.conversations() }
            val readsD = async { dmRepository.dmReads() }
            val chReadsD = async { serverRepository.channelReads() }
            val meD = async { userRepository.me() }
            val myIdD = async { tokenStore.currentUserId() }
            val chPrefsD = async { runCatching { notificationsApi.channelNotifPrefs().data.orEmpty() }.getOrDefault(emptyList()) }
            val svPrefsD = async { runCatching { notificationsApi.serverNotifPrefs().data.orEmpty() }.getOrDefault(emptyList()) }

            val servers = serversD.await().getOrDefault(emptyList())
            val conversations = convD.await().getOrDefault(emptyList())
            val reads = readsD.await().getOrNull().orEmpty()
            val chReads = chReadsD.await().getOrNull().orEmpty()
            val me = meD.await().getOrNull()
            val myId = myIdD.await()

            channelPrefModes = chPrefsD.await().associate { it.channelId to it.mode }
            val svModes = svPrefsD.await().associate { it.serverId to it.mode }
            val mutedServers = svModes.filterValues { it == "mute" }.keys
            val mutedChannels = servers.flatMap { s -> s.channels.map { ch -> s.id to ch.id } }
                .filter { (sid, cid) -> (channelPrefModes[cid] ?: svModes[sid]) == "mute" }
                .map { it.second }.toSet()
            val mutedConvs = conversations.filter { it.muted }.map { it.id }.toSet()

            val unread = conversations
                .filter { c ->
                    !c.lastFromMe && c.lastMessageAt?.let { last -> reads[c.id]?.let { last > it } ?: true } ?: false
                }
                .map { it.id }.toSet() - mutedConvs

            val channelUnread = servers.flatMap { it.channels }
                .filter { ch -> ch.lastMessageAt?.let { last -> chReads[ch.id]?.let { last > it } ?: true } ?: false }
                .map { it.id }.toSet() - mutedChannels

            val voiceChannels = servers.flatMap { s -> s.channels.filter { it.isVoice }.map { s to it } }
            val activeVoice = if (voiceChannels.isEmpty()) {
                emptyList()
            } else {
                val presence = serverRepository.voicePresence(voiceChannels.map { it.second.id })
                    .getOrDefault(emptyMap())
                voiceChannels.mapNotNull { (s, ch) ->
                    val n = presence[ch.id]?.size ?: 0
                    if (n == 0) null else ActiveVoiceRoom(ch.id, ch.name, s.id, s.name, n)
                }
            }

            _state.update {
                it.copy(
                    loading = false,
                    servers = servers,
                    dms = conversations,
                    unread = unread,
                    channelUnread = channelUnread,
                    mutedServers = mutedServers,
                    mutedChannels = mutedChannels,
                    mutedConvs = mutedConvs,
                    activeVoice = activeVoice,
                    myId = myId,
                    myName = me?.displayName ?: "",
                    myUsername = me?.username ?: "",
                    myAvatar = me?.avatarUrl,
                    myBanner = me?.bannerUrl,
                    myBannerColor = me?.bannerColor,
                    myFont = me?.displayFont ?: "serif",
                    myBio = me?.bio,
                    myPronouns = me?.pronouns,
                    myCreatedAt = me?.createdAt,
                    myStatus = me?.status?.takeUnless { it == UserStatus.OFFLINE } ?: UserStatus.ONLINE,
                    myCustomStatus = me?.customStatus,
                    needsOnboarding = me != null && me.onboardedAt == null,
                    needsEmailVerify = me != null && me.emailVerifiedAt == null,
                    needsPassword = me != null && !me.hasPassword,
                )
            }

            if (myId != null) {
                launch {
                    runCatching { badgesApi.userBadges(myId).data?.toUi() }.getOrNull()?.let { b ->
                        _state.update { it.copy(myBadges = b) }
                    }
                }
            }
        }
    }

    fun setCustomStatus(text: String) {
        val newVal = text.trim().take(100)
        val prev = _state.value.myCustomStatus
        _state.update { it.copy(myCustomStatus = newVal.ifBlank { null }) }
        viewModelScope.launch {
            try {
                friendsApi.setCustomStatus(CustomStatusRequest(newVal))
                userRepository.me(forceRefresh = true)
            } catch (_: Exception) {
                _state.update { it.copy(myCustomStatus = prev) }
            }
        }
    }

    fun consumeOnboarding() = _state.update { it.copy(needsOnboarding = false) }

    fun consumeEmailVerify() = _state.update { it.copy(needsEmailVerify = false) }

    fun setPassword(pw: String, confirm: String) {
        if (_state.value.pwSaving) return
        val error = when {
            pw.length < 8 -> "Minimo 8 caracteres"
            !pw.any { it.isUpperCase() } -> "Precisa de ao menos uma letra maiuscula"
            !pw.any { it.isDigit() } -> "Precisa de ao menos um numero"
            pw != confirm -> "As senhas nao coincidem"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(pwError = error) }
            return
        }
        _state.update { it.copy(pwSaving = true, pwError = null) }
        viewModelScope.launch {
            userRepository.setPassword(pw)
                .onSuccess { _state.update { it.copy(pwSaving = false, needsPassword = false) } }
                .onFailure { e -> _state.update { it.copy(pwSaving = false, pwError = e.message ?: "Nao foi possivel salvar") } }
        }
    }

    fun selectServer(id: String?) {
        _state.update { it.copy(selectedServerId = id, podeArrumar = false) }
        if (id == null) return
        refreshVoicePresence()
        viewModelScope.launch {
            val pode = serverRepository.podeArrumarOrbitas(id)
            _state.update { if (it.selectedServerId == id) it.copy(podeArrumar = pode) else it }
        }
    }

    fun guardarOrdemDasOrbitas(ids: List<String>) {
        _state.update { it.copy(ordemDasOrbitas = ids) }
        viewModelScope.launch { arranjo.guardarOrdem(ids) }
    }

    fun alternarCategoria(categoriaId: String) {
        viewModelScope.launch { arranjo.alternarCategoria(categoriaId) }
    }

    fun reordenarCanais(serverId: String, idsNaOrdem: List<String>) {
        val orbita = _state.value.servers.firstOrNull { it.id == serverId } ?: return
        val novas = idsNaOrdem.withIndex().associate { (i, id) -> id to i }
        val antigas = orbita.channels.associate { it.id to it.position }
        mudarOrbita(serverId) { o ->
            o.copy(channels = o.channels.map { c -> novas[c.id]?.let { c.copy(position = it) } ?: c })
        }
        viewModelScope.launch {
            val falhas = idsNaOrdem.filter { antigas[it] != novas[it] }.mapNotNull { id ->
                serverRepository.moverCanal(serverId, id, novas.getValue(id)).exceptionOrNull()
            }
            falhas.firstOrNull()?.let { e -> _state.update { it.copy(manageError = e.message) } }
            reloadServers()
        }
    }

    fun moverCanalParaCategoria(serverId: String, channelId: String, categoriaId: String) {
        val orbita = _state.value.servers.firstOrNull { it.id == serverId } ?: return
        if (orbita.channels.firstOrNull { it.id == channelId }?.categoryId == categoriaId) return
        val posicao = (orbita.channels.filter { it.categoryId == categoriaId }.maxOfOrNull { it.position } ?: -1) + 1
        mudarOrbita(serverId) { o ->
            o.copy(channels = o.channels.map { c ->
                if (c.id == channelId) c.copy(categoryId = categoriaId, position = posicao) else c
            })
        }
        viewModelScope.launch {
            serverRepository.moverCanal(serverId, channelId, posicao, categoriaId)
                .onFailure { e -> _state.update { it.copy(manageError = e.message) } }
            reloadServers()
        }
    }

    fun reordenarCategorias(serverId: String, idsNaOrdem: List<String>) {
        val orbita = _state.value.servers.firstOrNull { it.id == serverId } ?: return
        val novas = idsNaOrdem.withIndex().associate { (i, id) -> id to i }
        val antigas = orbita.categories.associate { it.id to it.position }
        mudarOrbita(serverId) { o ->
            o.copy(categories = o.categories.map { c -> novas[c.id]?.let { c.copy(position = it) } ?: c })
        }
        viewModelScope.launch {
            val falhas = idsNaOrdem.filter { antigas[it] != novas[it] }.mapNotNull { id ->
                serverRepository.moverCategoria(serverId, id, novas.getValue(id)).exceptionOrNull()
            }
            falhas.firstOrNull()?.let { e -> _state.update { it.copy(manageError = e.message) } }
            reloadServers()
        }
    }

    private fun mudarOrbita(serverId: String, mudanca: (Server) -> Server) {
        _state.update { st -> st.copy(servers = st.servers.map { if (it.id == serverId) mudanca(it) else it }) }
    }

    private fun refreshVoicePresence() {
        viewModelScope.launch {
            val voiceChannels = _state.value.servers.flatMap { s -> s.channels.filter { it.isVoice }.map { s to it } }
            if (voiceChannels.isEmpty()) return@launch
            val presence = serverRepository.voicePresence(voiceChannels.map { it.second.id })
                .getOrNull() ?: return@launch
            val activeVoice = voiceChannels.mapNotNull { (s, ch) ->
                val n = presence[ch.id]?.size ?: 0
                if (n == 0) null else ActiveVoiceRoom(ch.id, ch.name, s.id, s.name, n)
            }
            _state.update { it.copy(activeVoice = activeVoice) }
        }
    }

    fun markChannelSeen(channelId: String) = _state.update { it.copy(channelUnread = it.channelUnread - channelId) }

    fun setStatus(status: UserStatus) {
        _state.update { it.copy(myStatus = status) }
        viewModelScope.launch { userRepository.setStatus(status) }
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            try {
                val count = notificationsApi.unread().data?.count ?: 0
                _state.update { it.copy(unreadNotifs = count) }
            } catch (_: Exception) {}
            try {
                val pedidos = friendsApi.incoming().data.orEmpty().size
                _state.update { it.copy(pedidosDeAmizade = pedidos) }
            } catch (_: Exception) {}
        }
    }

    fun silenciarConversa(conversationId: String, silenciada: Boolean) {
        _state.update {
            it.copy(
                mutedConvs = if (silenciada) it.mutedConvs + conversationId else it.mutedConvs - conversationId,
            )
        }
        viewModelScope.launch { dmRepository.setMuted(conversationId, silenciada) }
    }

    fun fecharConversa(conversationId: String) {
        _state.update { it.copy(dms = it.dms.filterNot { conversa -> conversa.id == conversationId }) }
        viewModelScope.launch {
            dmRepository.close(conversationId)
                .onFailure { reloadConversas() }
        }
    }

    private suspend fun reloadConversas() {
        dmRepository.conversations().onSuccess { conversas ->
            _state.update { it.copy(dms = conversas) }
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            userRepository.me().onSuccess { me ->
                _state.update {
                    it.copy(
                        myName = me.displayName,
                        myUsername = me.username,
                        myAvatar = me.avatarUrl,
                        myBanner = me.bannerUrl,
                        myBannerColor = me.bannerColor,
                        myFont = me.displayFont,
                        myBio = me.bio,
                        myPronouns = me.pronouns,
                        myCreatedAt = me.createdAt,
                        myStatus = me.status.takeUnless { it == UserStatus.OFFLINE } ?: UserStatus.ONLINE,
                        myCustomStatus = me.customStatus,
                    )
                }
            }
        }
    }

    fun refreshServers() {
        viewModelScope.launch {
            serverRepository.servers().onSuccess { servers ->
                _state.update { it.copy(servers = servers) }
            }
        }
    }

    private suspend fun reloadServers() {
        serverRepository.servers().onSuccess { servers -> _state.update { it.copy(servers = servers) } }
    }
    private fun manage(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            block()
                .onSuccess { reloadServers() }
                .onFailure { e -> _state.update { it.copy(manageError = e.message ?: "Acao falhou") } }
        }
    }
    fun createChannel(serverId: String, name: String, isVoice: Boolean) =
        manage { serverRepository.createChannel(serverId, name, isVoice) }
    fun clearManageError() = _state.update { it.copy(manageError = null) }

    fun leaveServer(serverId: String) {
        viewModelScope.launch {
            serverRepository.leaveServer(serverId)
                .onSuccess {
                    _state.update {
                        it.copy(selectedServerId = if (it.selectedServerId == serverId) null else it.selectedServerId)
                    }
                    reloadServers()
                }
                .onFailure { e -> _state.update { it.copy(manageError = e.message ?: "Nao foi possivel sair") } }
        }
    }

    fun markSeen(conversationId: String) = _state.update { it.copy(unread = it.unread - conversationId) }

    fun createServer(name: String, isGroup: Boolean) {
        if (_state.value.creating || name.isBlank()) return
        _state.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            serverRepository.createServer(name.trim(), isGroup)
                .onSuccess { srv ->
                    val servers = serverRepository.servers().getOrDefault(_state.value.servers)
                    _state.update { it.copy(creating = false, servers = servers) }
                    _serverCreated.tryEmit(srv)
                }
                .onFailure { e -> _state.update { it.copy(creating = false, createError = e.message ?: "Erro inesperado") } }
        }
    }

    fun clearCreateError() = _state.update { it.copy(createError = null) }

    fun logout() = viewModelScope.launch { authRepository.logout() }

    private fun observeIncoming() {
        viewModelScope.launch {
            dmRepository.incomingConversations().collect { convId ->
                _state.update {
                    if (convId in it.mutedConvs) it else it.copy(unread = it.unread + convId)
                }
            }
        }
    }

    fun setServerMuted(serverId: String, muted: Boolean) {
        val prev = _state.value
        val srvChannelIds = prev.servers.firstOrNull { it.id == serverId }?.channels?.map { it.id }.orEmpty()
        val inheriting = srvChannelIds.filter { channelPrefModes[it] == null }.toSet()
        _state.update {
            val mutedChannels = if (muted) it.mutedChannels + inheriting else it.mutedChannels - inheriting
            it.copy(
                mutedServers = if (muted) it.mutedServers + serverId else it.mutedServers - serverId,
                mutedChannels = mutedChannels,
                channelUnread = if (muted) it.channelUnread - mutedChannels else it.channelUnread,
            )
        }
        viewModelScope.launch {
            try {
                if (muted) notificationsApi.setServerNotifPref(serverId, NotifModeRequest("mute"))
                else notificationsApi.clearServerNotifPref(serverId)
            } catch (_: Exception) {
                _state.update { it.copy(mutedServers = prev.mutedServers, mutedChannels = prev.mutedChannels) }
            }
        }
    }

    fun openConversation(username: String) {
        if (_state.value.opening || username.isBlank()) return
        _state.update { it.copy(opening = true, openError = null) }
        viewModelScope.launch {
            dmRepository.open(username)
                .onSuccess { conv ->
                    _state.update { it.copy(opening = false) }
                    _opened.tryEmit(conv)
                }
                .onFailure { e ->
                    _state.update { it.copy(opening = false, openError = e.message ?: "Erro inesperado") }
                }
        }
    }

    fun clearOpenError() = _state.update { it.copy(openError = null) }
}
