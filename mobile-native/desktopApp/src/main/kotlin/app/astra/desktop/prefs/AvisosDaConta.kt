package app.astra.desktop.prefs

import app.astra.mobile.core.network.NotificationApi
import app.astra.mobile.core.network.dto.AvisosDaContaDto
import app.astra.mobile.core.network.dto.AvisosDaContaRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

class AvisosDaConta(private val api: NotificationApi) {

    private val _estado = MutableStateFlow(AvisosDaContaDto())
    val estado: StateFlow<AvisosDaContaDto> = _estado.asStateFlow()

    suspend fun carregar() {
        runCatching { api.avisosDaConta().data?.prefs }
            .getOrNull()
            ?.let { _estado.value = it }
    }

    suspend fun salvar(novo: AvisosDaContaDto): Result<Unit> {
        val anterior = _estado.value
        _estado.value = novo
        return runCatching {
            val resp = api.salvarAvisosDaConta(AvisosDaContaRequest.de(novo))
            _estado.value = resp.data?.prefs ?: novo
        }.onFailure { _estado.value = anterior }
    }

    fun emDescanso(agora: LocalTime = LocalTime.now()): Boolean {
        val s = _estado.value.quietStart ?: return false
        val e = _estado.value.quietEnd ?: return false
        val h = agora.hour
        return if (s < e) h in s until e else h >= s || h < e
    }

    fun devoCalar(status: String?): Boolean = status == "DND" || emDescanso()
}
