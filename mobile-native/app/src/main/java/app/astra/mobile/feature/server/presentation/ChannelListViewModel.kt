package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.feature.server.domain.ServerRepository
import app.astra.mobile.feature.server.domain.model.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelListUiState(
    val loading: Boolean = true,
    val channels: List<Channel> = emptyList(),
    val error: String? = null,
    // channelIds com mensagem nova nao-lida (dot na lista).
    val unread: Set<String> = emptySet(),
    // codigo do convite deste servidor (pro botao Convidar). null = sem acesso.
    val inviteCode: String? = null,
    // sou o dono? libera a engrenagem de editar a Constelacao.
    val isOwner: Boolean = false,
)

@HiltViewModel
class ChannelListViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val tokenStore: TokenStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val serverId: String = savedStateHandle["serverId"] ?: ""
    val serverName: String = savedStateHandle["name"] ?: "Servidor"

    private val _state = MutableStateFlow(ChannelListUiState())
    val state = _state.asStateFlow()

    init {
        load()
        observeActivity()
    }

    // GET /api/servers ja traz os canais aninhados (com lastMessageAt) — junta
    // com /reads/channels pra marcar o que esta nao-lido.
    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val reads = repository.channelReads().getOrNull().orEmpty()
            val myId = tokenStore.currentUserId()
            repository.servers()
                .onSuccess { list ->
                    val server = list.find { it.id == serverId }
                    val channels = server?.channels ?: emptyList()
                    val unread = channels
                        .filter { ch -> ch.lastMessageAt?.let { last -> reads[ch.id]?.let { last > it } ?: true } ?: false }
                        .map { it.id }.toSet()
                    val isOwner = server?.ownerId != null && server.ownerId == myId
                    _state.update {
                        it.copy(loading = false, channels = channels, unread = unread,
                            inviteCode = server?.inviteCode, isOwner = isOwner)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Erro inesperado") } }
        }
    }

    // Clicou no canal -> some o dot na hora (e o chat marca lido no servidor).
    fun markSeen(channelId: String) = _state.update { it.copy(unread = it.unread - channelId) }

    // channel_activity ao vivo: so marca canais desta tela.
    private fun observeActivity() {
        viewModelScope.launch {
            repository.channelActivity().collect { chId ->
                if (_state.value.channels.any { it.id == chId }) {
                    _state.update { it.copy(unread = it.unread + chId) }
                }
            }
        }
    }
}
