package app.astra.mobile.feature.profile.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.network.BadgesApi
import app.astra.mobile.core.network.XpApi
import app.astra.mobile.feature.dm.domain.DmRepository
import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.ui.components.toUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val badgesApi: BadgesApi,
    private val xpApi: XpApi,
    private val dmRepository: DmRepository,
    private val tokenStore: TokenStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userId: String = savedStateHandle["userId"] ?: ""
    val initialName: String = savedStateHandle["name"] ?: "Perfil"

    private val _state = MutableStateFlow(UserProfileUiState())
    val state = _state.asStateFlow()

    private val _conversa = MutableSharedFlow<ConversaAberta>(extraBufferCapacity = 1)
    val conversa = _conversa.asSharedFlow()

    init {
        load()
        viewModelScope.launch {
            val eu = tokenStore.currentUserId()
            _state.update { it.copy(souEu = eu != null && eu == userId) }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val badgesD = async {
                runCatching { badgesApi.userBadges(userId).data?.toUi() }.getOrNull().orEmpty()
            }
            userRepository.profile(userId)
                .onSuccess { v -> _state.update { it.copy(loading = false, view = v, badges = badgesD.await()) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun carregarProgresso() {
        if (_state.value.progresso != null) return
        viewModelScope.launch {
            val p = runCatching { xpApi.de(userId).data }.getOrNull() ?: return@launch
            _state.update { it.copy(progresso = p) }
        }
    }

    fun abrirConversa(chamar: Boolean) {
        val usuario = _state.value.view?.profile?.username ?: return
        if (_state.value.abrindoConversa) return
        _state.update { it.copy(abrindoConversa = true, erroDaConversa = null) }
        viewModelScope.launch {
            dmRepository.open(usuario)
                .onSuccess { c ->
                    _state.update { it.copy(abrindoConversa = false) }
                    _conversa.tryEmit(ConversaAberta(c.conversationId, c.otherName, chamar))
                }
                .onFailure { e -> _state.update { it.copy(abrindoConversa = false, erroDaConversa = e.message) } }
        }
    }
}
