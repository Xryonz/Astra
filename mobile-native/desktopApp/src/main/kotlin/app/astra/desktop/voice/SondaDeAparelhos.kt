package app.astra.desktop.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@androidx.compose.runtime.Stable
interface FonteDeAparelhos {
    val microfones: List<AparelhoDeAudio>
    val saidas: List<AparelhoDeAudio>
    fun listar()
}

class SondaDeAparelhos(private val scope: CoroutineScope) {

    private val _entradas = MutableStateFlow<List<AparelhoDeAudio>>(emptyList())
    val entradas = _entradas.asStateFlow()

    private val _saidas = MutableStateFlow<List<AparelhoDeAudio>>(emptyList())
    val saidas = _saidas.asStateFlow()

    private var sondando: Job? = null

    fun atualizar() {
        if (sondando?.isActive == true) return
        sondando = scope.launch { perguntarAoSidecar() }
    }

    private suspend fun perguntarAoSidecar() {
        val sidecar = SidecarDeVoz(scope)
        try {
            var faltam = 2
            val respondeu = withTimeoutOrNull(PRAZO) {
                sidecar.eventos
                    .onSubscription { sidecar.ligar() }
                    .first { ev ->
                        when (ev.ev) {
                            "pronto" -> sidecar.pedirAparelhos()
                            "aparelhos" -> {
                                val lista = ev.aparelhos.orEmpty()
                                if (ev.tipo == "entrada") _entradas.value = lista
                                else _saidas.value = lista
                                faltam--
                            }
                        }
                        faltam == 0
                    }
                true
            }
            if (respondeu == null) {
                VoiceLog.nota("[aparelhos] o componente de voz não respondeu em ${PRAZO}ms")
            }
        } finally {
            sidecar.parar()
        }
    }

    private companion object {
        const val PRAZO = 6_000L
    }
}
