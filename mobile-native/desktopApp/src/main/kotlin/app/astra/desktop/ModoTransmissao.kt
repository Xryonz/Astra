package app.astra.desktop

import app.astra.desktop.prefs.DesktopPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ModoTransmissao {

    private val PROGRAMAS = setOf(
        "obs64.exe", "obs32.exe", "obs.exe",
        "streamlabs obs.exe", "streamlabs desktop.exe",
        "xsplit.core.exe", "xsplit.broadcaster.exe",
        "twitchstudio.exe",
    )

    private const val INTERVALO_MS = 12_000L

    private val _ativo = MutableStateFlow(false)

    val ativo = _ativo.asStateFlow()

    private val _detectado = MutableStateFlow(false)
    val detectado = _detectado.asStateFlow()

    fun vigiar(scope: CoroutineScope, prefs: DesktopPrefs) {
        scope.launch {
            prefs.state.collect { p ->
                if (!p.modoTransmissaoAuto) _detectado.value = false
                _ativo.value = p.modoTransmissao || (p.modoTransmissaoAuto && _detectado.value)
            }
        }
        scope.launch {
            while (isActive) {
                if (prefs.state.value.modoTransmissaoAuto) {
                    val achou = withContext(Dispatchers.IO) { algumProgramaAberto() }
                    if (_detectado.value != achou) {
                        _detectado.value = achou
                        val p = prefs.state.value
                        _ativo.value = p.modoTransmissao || (p.modoTransmissaoAuto && achou)
                    }
                }
                delay(INTERVALO_MS)
            }
        }
    }

    private fun algumProgramaAberto(): Boolean = runCatching {
        ProcessHandle.allProcesses().anyMatch { p ->
            val nome = p.info().command().orElse(null)
                ?.substringAfterLast('\\')
                ?.substringAfterLast('/')
                ?.lowercase()
            nome != null && nome in PROGRAMAS
        }
    }.getOrDefault(false)
}
