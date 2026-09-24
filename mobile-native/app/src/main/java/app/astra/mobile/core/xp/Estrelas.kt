package app.astra.mobile.core.xp

import app.astra.mobile.core.network.XpApi
import app.astra.mobile.core.network.dto.GanhoXpDto
import app.astra.mobile.core.network.dto.ProgressoDto
import app.astra.mobile.core.realtime.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Estrelas @Inject constructor(
    private val api: XpApi,
    socketManager: SocketManager,
) {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val _progresso = MutableStateFlow(ProgressoDto())
    val progresso: StateFlow<ProgressoDto> = _progresso.asStateFlow()

    private val _ganhos = MutableSharedFlow<GanhoXpDto>(extraBufferCapacity = 8)
    val ganhos: SharedFlow<GanhoXpDto> = _ganhos.asSharedFlow()

    init {
        escopo.launch {
            socketManager.xpGain.collect { cru ->
                val ganho = runCatching { json.decodeFromString<GanhoXpDto>(cru) }.getOrNull() ?: return@collect
                _progresso.value = ganho.progresso
                _ganhos.tryEmit(ganho)
            }
        }
    }

    suspend fun recarregar() {
        val lido = runCatching { api.me().data }.getOrNull() ?: return
        _progresso.value = lido
    }
}

fun fracaoDoNivel(p: ProgressoDto): Float =
    if (p.paraOProximo <= 0) 0f else (p.noNivel.toFloat() / p.paraOProximo).coerceIn(0f, 1f)
