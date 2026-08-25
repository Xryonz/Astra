package app.astra.desktop.net

import app.astra.desktop.AtividadeDoSistema
import app.astra.desktop.prefs.DesktopPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ESPIA_MS = 5_000L

private const val LEITURAS_PRA_VALER = 2

private const val RENOVA_MS = 45_000L

class AtividadePublicador(
    private val socket: DesktopSocket,
    private val prefs: DesktopPrefs,
) {
    fun iniciar(escopo: CoroutineScope) {
        escopo.launch {
            var candidato: String? = null
            var repeticoes = 0
            var publicado: String? = null
            var ultimoEnvio = 0L

            while (true) {
                delay(ESPIA_MS)

                if (!prefs.state.value.atividadeVisivel) {
                    if (publicado != null) {
                        socket.enviarAtividade("")
                        publicado = null
                    }
                    candidato = null
                    repeticoes = 0
                    continue
                }

                val agora = AtividadeDoSistema.emPrimeiroPlano()
                if (agora == null) continue

                if (agora == candidato) repeticoes++ else { candidato = agora; repeticoes = 1 }
                if (repeticoes < LEITURAS_PRA_VALER) continue

                val tempo = System.currentTimeMillis()
                val mudou = agora != publicado
                if (mudou || tempo - ultimoEnvio >= RENOVA_MS) {
                    socket.enviarAtividade(agora)
                    publicado = agora
                    ultimoEnvio = tempo
                }
            }
        }
    }
}
