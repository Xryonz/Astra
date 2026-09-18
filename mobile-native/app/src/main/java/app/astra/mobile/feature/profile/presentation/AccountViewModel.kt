package app.astra.mobile.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.auth.domain.AuthRepository
import app.astra.mobile.feature.profile.domain.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.me()
                .onSuccess { p ->
                    _state.update {
                        it.copy(
                            loading = false,
                            displayName = p.displayName, origDisplayName = p.displayName,
                            username = p.username, origUsername = p.username,
                            email = p.email ?: "—", userId = p.id, hasPassword = p.hasPassword,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun onDisplayName(v: String) = _state.update { it.copy(displayName = v, saved = false, error = null) }
    fun onUsername(v: String) = _state.update { it.copy(username = v.lowercase(), saved = false, error = null) }

    fun save() {
        val s = _state.value
        if (s.saving || !s.dirty) return
        _state.update { it.copy(saving = true, error = null, saved = false) }
        viewModelScope.launch {
            userRepository.updateProfile(
                displayName = s.displayName.takeIf { it != s.origDisplayName },
                username = s.username.takeIf { it != s.origUsername },
            )
                .onSuccess { p ->
                    _state.update {
                        it.copy(
                            saving = false, saved = true,
                            displayName = p.displayName, origDisplayName = p.displayName,
                            username = p.username, origUsername = p.username,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }

    fun togglePw() = _state.update {
        it.copy(pwOpen = !it.pwOpen, pwError = null, pwDone = false, curPw = "", newPw = "")
    }

    fun onCurPw(v: String) = _state.update { it.copy(curPw = v, pwError = null) }
    fun onNewPw(v: String) = _state.update { it.copy(newPw = v, pwError = null) }

    fun changePassword() {
        val s = _state.value
        if (s.pwSaving) return
        if (s.curPw.isBlank()) { _state.update { it.copy(pwError = "Informe a senha atual") }; return }
        if (s.newPw.length < 8) { _state.update { it.copy(pwError = "Nova senha: 8+ caracteres") }; return }
        _state.update { it.copy(pwSaving = true, pwError = null) }
        viewModelScope.launch {
            userRepository.changePassword(s.curPw, s.newPw)
                .onSuccess { _state.update { it.copy(pwSaving = false, pwOpen = false, pwDone = true, curPw = "", newPw = "") } }
                .onFailure { e -> _state.update { it.copy(pwSaving = false, pwError = e.message) } }
        }
    }

    fun logout() = viewModelScope.launch { authRepository.logout() }
}
