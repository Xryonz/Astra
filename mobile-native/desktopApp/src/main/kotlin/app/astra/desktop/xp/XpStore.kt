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

// Progressao do usuario, do lado do app.
//
// SEM POLL, de proposito. Le uma vez ao entrar e depois vive do evento `xp_gain`
// do socket, que ja chega com o progresso inteiro. Perguntar de tempos em tempos
// custaria mais requisicao do que o proprio ganho: a trava do servidor deixa passar
// no maximo um ganho por minuto.
class XpStore(
    private val api: XpApi,
    private val socket: DesktopSocket,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _progresso = MutableStateFlow(ProgressoDto())
    val progresso: StateFlow<ProgressoDto> = _progresso.asStateFlow()

    // Evento, nao estado: "ganhei 12 de XP" ACONTECE, e quem desenha o anel usa isso
    // pra disparar a animacao. Num StateFlow, dois ganhos iguais seguidos seriam o
    // mesmo valor e a segunda animacao nunca rodaria.
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

    // Chamado ao entrar e depois de trocar de conta. Falha em silencio: progressao
    // nunca pode impedir o app de abrir — sem ela o anel so fica vazio.
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
