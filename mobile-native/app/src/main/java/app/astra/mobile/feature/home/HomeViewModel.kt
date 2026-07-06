package app.astra.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    fun registerPush() = pushRegistrar.register()

    val socketState: StateFlow<ConnectionState> = socketManager.state

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    private val _opened = MutableSharedFlow<OpenedConversation>(extraBufferCapacity = 1)
    val opened = _opened.asSharedFlow()

    private val _serverCreated = MutableSharedFlow<Server>(extraBufferCapacity = 1)
    val serverCreated = _serverCreated.asSharedFlow()

    // Prefs explicitas por canal (modo por channelId), pro toggle de servidor
    // saber quais canais herdam (sem pref propria) na hora de recalcular local.
    private var channelPrefModes: Map<String, String> = emptyMap()

    init {
        load()
        refreshNotifications()
        observeIncoming()
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
                    // OFFLINE aqui = corrida (me() antes do socket registrar presenca);
                    // pra voce mesmo isso vira ONLINE (nunca offline com o app aberto).
                    myStatus = me?.status?.takeUnless { it == UserStatus.OFFLINE } ?: UserStatus.ONLINE,
                    myCustomStatus = me?.customStatus,
                    needsOnboarding = me != null && me.onboardedAt == null,
                    needsEmailVerify = me != null && me.emailVerifiedAt == null,
                    needsPassword = me != null && !me.hasPassword,
                )
            }

            // Minhas badges: best-effort fora do caminho critico do load.
            if (myId != null) {
                launch {
                    runCatching { badgesApi.userBadges(myId).data?.toUi() }.getOrNull()?.let { b ->
                        _state.update { it.copy(myBadges = b) }
                    }
                }
            }
        }
    }

    // Recado (custom status). Otimista; sucesso re-hidrata o cache do me()
    // pra proxima leitura nao voltar o valor antigo.
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

    // One-shot: a Home consome antes de navegar pro onboarding (sem loop).
    fun consumeOnboarding() = _state.update { it.copy(needsOnboarding = false) }

    fun consumeEmailVerify() = _state.update { it.copy(needsEmailVerify = false) }

    // Cria a primeira senha (conta Google). Validacao espelha o SetPasswordSchema.
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

    fun selectServer(id: String?) = _state.update { it.copy(selectedServerId = id) }

    fun markChannelSeen(channelId: String) = _state.update { it.copy(channelUnread = it.channelUnread - channelId) }

    fun refreshNotifications() {
        viewModelScope.launch {
            try {
                val count = notificationsApi.unread().data?.count ?: 0
                _state.update { it.copy(unreadNotifs = count) }
            } catch (_: Exception) {}
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

    // Silencia/reativa o servidor inteiro (canais COM pref propria nao mudam).
    // Otimista: recalcula os sets locais na hora; erro reverte.
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
