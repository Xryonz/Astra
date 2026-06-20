package app.astra.mobile.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.auth.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val register: RegisterUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state = _state.asStateFlow()

    fun onDisplayName(v: String) = _state.update { it.copy(displayName = v, error = null) }
    fun onUsername(v: String) = _state.update { it.copy(username = v.lowercase(), error = null) }
    fun onEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }

    // Sucesso nao navega aqui: salvar o token faz isLoggedIn virar true e o
    // NavHost reage sozinho. So tratamos loading + erro.
    fun submit() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val s = _state.value
            register(s.displayName, s.username, s.email, s.password).onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Erro inesperado") }
            }
        }
    }
}
