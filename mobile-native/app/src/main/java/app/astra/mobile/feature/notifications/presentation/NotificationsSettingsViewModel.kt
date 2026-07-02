package app.astra.mobile.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.NotificationsApi
import app.astra.mobile.core.network.dto.NotificationPrefsDto
import app.astra.mobile.core.network.dto.UpdateNotificationPrefsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

data class NotificationsSettingsState(
    val loading: Boolean = true,
    val prefs: NotificationPrefsDto? = null,
    val error: String? = null,
    val testSent: Boolean = false,
)

@HiltViewModel
class NotificationsSettingsViewModel @Inject constructor(
    private val api: NotificationsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsSettingsState())
    val state: StateFlow<NotificationsSettingsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val prefs = api.prefs().data?.prefs
                _state.update { it.copy(loading = false, prefs = prefs) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Sem conexao com o servidor") }
            }
        }
    }

    // Otimista: aplica local na hora, reverte se a rede falhar.
    fun toggle(patch: UpdateNotificationPrefsRequest, apply: (NotificationPrefsDto) -> NotificationPrefsDto) {
        val before = _state.value.prefs ?: return
        _state.update { it.copy(prefs = apply(before)) }
        viewModelScope.launch {
            try {
                val fresh = api.updatePrefs(patch).data?.prefs
                if (fresh != null) _state.update { it.copy(prefs = fresh) }
            } catch (e: Exception) {
                _state.update { it.copy(prefs = before) }
            }
        }
    }

    fun setQuiet(start: Int?, end: Int?) {
        val before = _state.value.prefs ?: return
        _state.update { it.copy(prefs = before.copy(quietStart = start, quietEnd = end)) }
        viewModelScope.launch {
            try {
                // null explicito limpa no backend (o DTO normal omitiria com explicitNulls=false).
                val body = buildJsonObject {
                    if (start != null) put("quietStart", kotlinx.serialization.json.JsonPrimitive(start))
                    else put("quietStart", JsonNull)
                    if (end != null) put("quietEnd", kotlinx.serialization.json.JsonPrimitive(end))
                    else put("quietEnd", JsonNull)
                }
                val fresh = api.updatePrefsRaw(body).data?.prefs
                if (fresh != null) _state.update { it.copy(prefs = fresh) }
            } catch (e: Exception) {
                _state.update { it.copy(prefs = before) }
            }
        }
    }

    fun sendTestPush() {
        viewModelScope.launch {
            try {
                api.pushTest()
                _state.update { it.copy(testSent = true) }
            } catch (_: Exception) {}
        }
    }

    fun clearTestFlag() { _state.update { it.copy(testSent = false) } }
}
