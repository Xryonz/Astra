package app.astra.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.realtime.ConnectionState
import app.astra.mobile.core.realtime.SocketManager
import app.astra.mobile.feature.auth.domain.AuthRepository
import app.astra.mobile.feature.dm.domain.DmRepository
import app.astra.mobile.feature.dm.domain.model.OpenedConversation
import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.feature.server.domain.ServerRepository
import app.astra.mobile.feature.server.domain.model.Server
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
) : ViewModel() {

    val socketState: StateFlow<ConnectionState> = socketManager.state

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    // Evento one-shot: DM aberta pelo FAB -> a tela navega pro chat.
    private val _opened = MutableSharedFlow<OpenedConversation>(extraBufferCapacity = 1)
    val opened = _opened.asSharedFlow()

    // Evento one-shot: constelacao criada -> a tela entra nela.
    private val _serverCreated = MutableSharedFlow<Server>(extraBufferCapacity = 1)
    val serverCreated = _serverCreated.asSharedFlow()

    init {
        load()
        observeIncoming()
    }

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            // Servidores + conversas + leituras (DM + canal) + perfil, em paralelo.
            val serversD = async { serverRepository.servers() }
            val convD = async { dmRepository.conversations() }
            val readsD = async { dmRepository.dmReads() }
            val chReadsD = async { serverRepository.channelReads() }
            val meD = async { userRepository.me() }
            val myIdD = async { tokenStore.currentUserId() }

            val servers = serversD.await().getOrDefault(emptyList())
            val conversations = convD.await().getOrDefault(emptyList())
            val reads = readsD.await().getOrNull().orEmpty()
            val chReads = chReadsD.await().getOrNull().orEmpty()
            val me = meD.await().getOrNull()
            val myId = myIdD.await()

            // Nao-lido: ultima msg nao foi minha E e mais nova que minha ultima leitura.
            val unread = conversations
                .filter { c ->
                    !c.lastFromMe && c.lastMessageAt?.let { last -> reads[c.id]?.let { last > it } ?: true } ?: false
                }
                .map { it.id }.toSet()

            // Canais nao-lidos (todas as Constelacoes) -> dot no painel inline.
            val channelUnread = servers.flatMap { it.channels }
                .filter { ch -> ch.lastMessageAt?.let { last -> chReads[ch.id]?.let { last > it } ?: true } ?: false }
                .map { it.id }.toSet()

            // Canais de voz (vem aninhados em servers) -> 1 chamada de presence -> salas com gente.
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
                    activeVoice = activeVoice,
                    myId = myId,
                    myName = me?.displayName ?: "",
                    myUsername = me?.username ?: "",
                    myAvatar = me?.avatarUrl,
                    myBanner = me?.bannerUrl,
                    myBannerColor = me?.bannerColor,
                    myBio = me?.bio,
                    myPronouns = me?.pronouns,
                    myCreatedAt = me?.createdAt,
                    myStatus = me?.status ?: UserStatus.ONLINE,
                )
            }
        }
    }

    // Rail: seleciona a Constelacao (mostra canais no painel) ou null (volta aos Sussurros).
    fun selectServer(id: String?) = _state.update { it.copy(selectedServerId = id) }

    // Tap numa orbita -> some o dot na hora.
    fun markChannelSeen(channelId: String) = _state.update { it.copy(channelUnread = it.channelUnread - channelId) }

    // Voltou pra Home (ON_RESUME) -> reflete edicoes de perfil no bottom bar.
    // me() le o cache, que o updateProfile ja atualizou no SALVAR -> sem rede.
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
                        myBio = me.bio,
                        myPronouns = me.pronouns,
                        myCreatedAt = me.createdAt,
                        myStatus = me.status,
                    )
                }
            }
        }
    }

    // Voltou pra Home -> recarrega o rail (pega icone/nome de servidor editado).
    fun refreshServers() {
        viewModelScope.launch {
            serverRepository.servers().onSuccess { servers ->
                _state.update { it.copy(servers = servers) }
            }
        }
    }

    // Tap na DM -> some o dot na hora.
    fun markSeen(conversationId: String) = _state.update { it.copy(unread = it.unread - conversationId) }

    // Forja constelacao (isGroup=false) ou aglomerado (true). Ao criar,
    // recarrega so a lista de servidores (rail aparece na hora) e entra nela.
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

    // logout flipa isLoggedIn -> AstraApp volta pro login sozinho.
    fun logout() = viewModelScope.launch { authRepository.logout() }

    // new_dm de outra pessoa ao vivo -> marca nao-lido.
    private fun observeIncoming() {
        viewModelScope.launch {
            dmRepository.incomingConversations().collect { convId ->
                _state.update { it.copy(unread = it.unread + convId) }
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
