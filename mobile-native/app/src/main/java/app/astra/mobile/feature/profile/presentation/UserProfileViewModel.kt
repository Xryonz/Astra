package app.astra.mobile.feature.profile.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.BadgesApi
import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.ui.components.toUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val badgesApi: BadgesApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userId: String = savedStateHandle["userId"] ?: ""
    val initialName: String = savedStateHandle["name"] ?: "Perfil"

    private val _state = MutableStateFlow(UserProfileUiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // Badges em paralelo e best-effort: perfil abre mesmo se falharem.
            val badgesD = async {
                runCatching { badgesApi.userBadges(userId).data?.toUi() }.getOrNull().orEmpty()
            }
            userRepository.profile(userId)
                .onSuccess { v -> _state.update { it.copy(loading = false, view = v, badges = badgesD.await()) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }
}
