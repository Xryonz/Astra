package app.astra.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.realtime.ConnectionState
import app.astra.mobile.core.realtime.SocketManager
import app.astra.mobile.feature.dm.domain.DmRepository
import app.astra.mobile.feature.dm.domain.model.OpenedConversation
import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.feature.server.domain.ServerRepository
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
) : ViewModel() {

    val socketState: StateFlow<ConnectionState> = socketManager.state

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    // Evento one-shot: DM aberta pelo FAB -> a tela navega pro chat.
    private val _opened = MutableSharedFlow<OpenedConversation>(extraBufferCapacity = 1)
    val opened = _opened.asSharedFlow()

    init {
        load()
        observeIncoming()
    }

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            // Servidores + conversas + leituras + perfil, tudo em paralelo.
            val serversD = async { serverRepository.servers() }
            val convD = async { dmRepository.conversations() }
            val readsD = async { dmRepository.dmReads() }
            val meD = async { userRepository.me() }

            val servers = serversD.await().getOrDefault(emptyList())
            val conversations = convD.await().getOrDefault(emptyList())
            val reads = readsD.await().getOrNull().orEmpty()
            val me = meD.await().getOrNull()

            // Nao-lido: ultima msg nao foi minha E e mais nova que minha ultima leitura.
            val unread = conversations
                .filter { c ->
                    !c.lastFromMe && c.lastMessageAt?.let { last -> reads[c.id]?.let { last > it } ?: true } ?: false
                }
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
                    activeVoice = activeVoice,
                    myName = me?.displayName ?: "",
                    myAvatar = me?.avatarUrl,
                )
            }
        }
    }

    // Tap na DM -> some o dot na hora.
    fun markSeen(conversationId: String) = _state.update { it.copy(unread = it.unread - conversationId) }

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
