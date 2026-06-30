package app.astra.mobile.feature.sessions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.network.SessionApi
import app.astra.mobile.core.network.dto.RevokeOthersRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionRow(
    val id: String,
    val label: String,
    val isMobile: Boolean,
    val ip: String?,
    val lastUsed: String?,
    val current: Boolean,
)

data class SessionsUiState(
    val loading: Boolean = true,
    val sessions: List<SessionRow> = emptyList(),
    val error: String? = null,
    val revokingId: String? = null,
    val revokingOthers: Boolean = false,
)

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val api: SessionApi,
    private val tokenStore: TokenStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionsUiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { api.sessions().data?.sessions.orEmpty() }
                .onSuccess { list ->
                    val rows = list.mapIndexed { i, s ->
                        val (label, mobile) = parseUserAgent(s.userAgent)
                        SessionRow(
                            id = s.id,
                            label = label,
                            isMobile = mobile,
                            ip = s.ip,
                            lastUsed = s.lastUsedAt ?: s.createdAt,
                            current = i == 0,
                        )
                    }
                    _state.update { it.copy(loading = false, sessions = rows) }
                }
                .onFailure { _state.update { it.copy(loading = false, error = "Falha ao carregar sessoes") } }
        }
    }

    fun revoke(id: String) {
        if (_state.value.revokingId != null) return
        _state.update { it.copy(revokingId = id, error = null) }
        viewModelScope.launch {
            runCatching { api.revoke(id) }
                .onSuccess { _state.update { it.copy(revokingId = null) }; load() }
                .onFailure { _state.update { it.copy(revokingId = null, error = "Nao foi possivel encerrar") } }
        }
    }

    fun revokeOthers() {
        if (_state.value.revokingOthers) return
        _state.update { it.copy(revokingOthers = true, error = null) }
        viewModelScope.launch {
            val refresh = tokenStore.currentRefresh()
            if (refresh.isNullOrBlank()) {
                _state.update { it.copy(revokingOthers = false, error = "Sessao atual nao encontrada") }
                return@launch
            }
            runCatching { api.revokeOthers(RevokeOthersRequest(refresh)) }
                .onSuccess { _state.update { it.copy(revokingOthers = false) }; load() }
                .onFailure { _state.update { it.copy(revokingOthers = false, error = "Nao foi possivel encerrar as outras") } }
        }
    }
}

/** Best-effort: rotulo do dispositivo + se e movel. */
private fun parseUserAgent(ua: String?): Pair<String, Boolean> {
    if (ua.isNullOrBlank()) return "Dispositivo desconhecido" to false
    val mobile = Regex("Mobile|Android|iPhone|iPad", RegexOption.IGNORE_CASE).containsMatchIn(ua)
    val os = when {
        ua.contains("Android", true) -> "Android"
        ua.contains("iPhone", true) || ua.contains("iPad", true) || ua.contains("iOS", true) -> "iOS"
        ua.contains("Windows", true) -> "Windows"
        ua.contains("Mac OS", true) || ua.contains("Macintosh", true) -> "macOS"
        ua.contains("Linux", true) -> "Linux"
        else -> null
    }
    val browser = when {
        ua.contains("Edg/", true) -> "Edge"
        ua.contains("Chrome", true) && !ua.contains("OPR", true) -> "Chrome"
        ua.contains("Firefox", true) -> "Firefox"
        ua.contains("Safari", true) && !ua.contains("Chrome", true) -> "Safari"
        ua.contains("OPR", true) || ua.contains("Opera", true) -> "Opera"
        ua.contains("okhttp", true) -> "App Astra"
        else -> null
    }
    val label = listOfNotNull(browser, os).joinToString(" · ").ifBlank { "Dispositivo" }
    return label to mobile
}
