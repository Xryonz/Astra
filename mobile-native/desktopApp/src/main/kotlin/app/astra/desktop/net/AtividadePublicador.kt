package app.astra.desktop.net

import app.astra.desktop.AtividadeDoSistema
import app.astra.desktop.prefs.DesktopPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// "O que a pessoa esta usando agora" — a metade que decide o que sai daqui.
//
// DESLIGADO, ELE NEM OLHA. O laco confere a preferencia antes de tocar no Win32:
// nao existe caminho em que o app leia o programa em primeiro plano e descarte
// depois. "Desligado" tem que significar que a leitura nao aconteceu, senao a
// promessa vale s o enquanto ninguem le o codigo.
private const val ESPIA_MS = 5_000L

// SO PUBLICA O QUE FICOU. Um alt-tab rapido pelo Discord, pelo explorador de
// arquivos e de volta pro jogo nao e uma mudanca de atividade — e o caminho ate
// ela. Sem esta espera, a sua linha piscaria tres vezes em cinco segundos pra
// todo mundo que estivesse olhando, e o que ela dissesse seria falso nas tres.
//
// Duas leituras iguais = 10s parado no mesmo programa.
private const val LEITURAS_PRA_VALER = 2

// O servidor guarda com 60s de vida. Reenviar a cada 45s renova antes de expirar,
// com folga pra uma rodada perdida — se o envio falhar uma vez, a proxima chega
// antes de a linha sumir. Renovar a cada 5s (junto com a espiada) seria doze vezes
// mais trafego pra dizer a mesma coisa.
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
                    // Acabou de desligar: apaga o que ja estava no ar. Sem isto, a
                    // ultima atividade ficaria ate o TTL vencer — desligar tem que
                    // valer AGORA, nao "em ate um minuto".
                    if (publicado != null) {
                        socket.enviarAtividade("")
                        publicado = null
                    }
                    candidato = null
                    repeticoes = 0
                    continue
                }

                val agora = AtividadeDoSistema.emPrimeiroPlano()
                // null = a janela da frente e o proprio Astra ou coisa do sistema.
                // Isso NAO apaga a atividade: voce olhar o chat no meio do jogo nao
                // significa que parou de jogar. So um outro programa firme troca.
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
