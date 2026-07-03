package app.astra.mobile.feature.verifyemail.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.VerifyEmailRequest
import app.astra.mobile.feature.auth.domain.AuthRepository
import app.astra.mobile.feature.profile.domain.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

data class VerifyEmailUiState(
    val email: String = "",
    val code: String = "",
    val checking: Boolean = false,
    val error: String? = null,
    val resent: Boolean = false,
    val done: Boolean = false,
)

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VerifyEmailUiState())
    val state = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            userRepository.me().onSuccess { p -> _state.update { it.copy(email = p.email ?: "") } }
        }
    }

    fun onCode(v: String) =
        _state.update { it.copy(code = v.filter { c -> c.isDigit() }.take(6), error = null) }

    fun verify() {
        val code = _state.value.code
        if (code.length != 6 || _state.value.checking) return
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                authApi.verifyEmail(VerifyEmailRequest(code))
                // Atualiza o cache do me() (emailVerifiedAt) pro gate nao voltar.
                userRepository.me(forceRefresh = true)
                _state.update { it.copy(checking = false, done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(checking = false, error = apiMessage(e)) }
            }
        }
    }

    fun resend() {
        viewModelScope.launch {
            try {
                authApi.resendEmailCode()
                _state.update { it.copy(resent = true, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = apiMessage(e)) }
            }
        }
    }

    // Escape de conta presa (email fake que nunca vai receber codigo).
    fun logout() = viewModelScope.launch { authRepository.logout() }

    private fun apiMessage(e: Exception): String = when (e) {
        is IOException -> "Sem conexao com o servidor"
        is HttpException -> e.response()?.errorBody()?.string()
            ?.let { runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull() }
            ?: "Nao foi possivel verificar"
        else -> "Erro inesperado"
    }
}
