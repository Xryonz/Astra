package app.astra.desktop.xp

import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.XpApi
import app.astra.mobile.core.network.dto.GanhoXpDto
import app.astra.mobile.core.network.dto.ProgressoDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class XpStore(
    private val api: XpApi,
    private val socket: DesktopSocket,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _progresso = MutableStateFlow(ProgressoDto())
    val progresso: StateFlow<ProgressoDto> = _progresso.asStateFlow()

    private val _ganhos = MutableSharedFlow<GanhoXpDto>(extraBufferCapacity = 8)
    val ganhos: SharedFlow<GanhoXpDto> = _ganhos.asSharedFlow()

    fun iniciar(escopo: CoroutineScope) {
        escopo.launch { recarregar() }
        escopo.launch {
            socket.xpGain.collect { cru ->
                val g = runCatching { json.decodeFromString<GanhoXpDto>(cru) }.getOrNull() ?: return@collect
                _progresso.value = g.progresso
                _ganhos.tryEmit(g)
            }
        }
    }

    suspend fun recarregar() {
        val p = withContext(Dispatchers.IO) {
            runCatching { api.me().data }.getOrNull()
        } ?: return
        _progresso.value = p
    }

    fun limpar() {
        _progresso.value = ProgressoDto()
    }
}
