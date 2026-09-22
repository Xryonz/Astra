package app.astra.mobile.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.network.BadgesApi
import app.astra.mobile.feature.friends.domain.FriendsRepository
import app.astra.mobile.feature.friends.domain.model.Friend
import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.feature.profile.domain.model.Profile
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.ui.components.BadgeUi
import app.astra.mobile.ui.components.toUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MeuPerfilUiState(
    val perfil: Profile? = null,
    val emblemas: List<BadgeUi> = emptyList(),
    val amigos: List<Friend> = emptyList(),
)

@HiltViewModel
class MeuPerfilViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val friendsRepository: FriendsRepository,
    private val badgesApi: BadgesApi,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(MeuPerfilUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val id = tokenStore.currentUserId() ?: return@launch
            runCatching { badgesApi.userBadges(id).data?.toUi() }.getOrNull()?.let { e ->
                _state.update { it.copy(emblemas = e) }
            }
        }
        viewModelScope.launch {
            friendsRepository.friends().onSuccess { lista -> _state.update { it.copy(amigos = lista) } }
        }
    }

    fun recarregar() {
        viewModelScope.launch {
            userRepository.me(forceRefresh = true).onSuccess { p -> _state.update { it.copy(perfil = p) } }
        }
    }

    fun trocarStatus(status: UserStatus) {
        _state.update { st -> st.copy(perfil = st.perfil?.copy(status = status)) }
        viewModelScope.launch { userRepository.setStatus(status) }
    }
}
