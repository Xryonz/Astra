package app.astra.mobile.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.auth.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    // Sucesso nao navega aqui: salvar o token faz isLoggedIn virar true e o
    // NavHost reage sozinho. So precisamos tratar loading + erro.
    fun submit() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val s = _state.value
            login(s.email, s.password).onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Erro inesperado") }
            }
            // onSuccess: deixa loading=true; a tela ja vai ser trocada pela home.
        }
    }
}
