package app.astra.desktop.prefs

import app.astra.mobile.core.network.NotificationApi
import app.astra.mobile.core.network.dto.AvisosDaContaDto
import app.astra.mobile.core.network.dto.AvisosDaContaRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AvisosDaConta(private val api: NotificationApi) {

    private val _estado = MutableStateFlow(AvisosDaContaDto())
    val estado: StateFlow<AvisosDaContaDto> = _estado.asStateFlow()

    suspend fun carregar() {
        val prefs = runCatching { api.avisosDaConta().data?.prefs }.getOrNull() ?: return
        _estado.value = prefs
        if (prefs.quietStart != null || prefs.quietEnd != null) {
            salvar(prefs.copy(quietStart = null, quietEnd = null))
        }
    }

    suspend fun salvar(novo: AvisosDaContaDto): Result<Unit> {
        val anterior = _estado.value
        _estado.value = novo
        return runCatching {
            val resp = api.salvarAvisosDaConta(AvisosDaContaRequest.de(novo))
            _estado.value = resp.data?.prefs ?: novo
        }.onFailure { _estado.value = anterior }
    }

    fun devoCalar(status: String?): Boolean = status == "DND"
}
