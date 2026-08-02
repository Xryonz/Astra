package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

// Cronometro da call.
//
// Existe por dois motivos que se somam: saber ha quanto tempo a conversa esta
// rolando e, agora, enxergar o XP de call — que e pago por MINUTO. Sem relogio, "eu
// ganho 8 por minuto" e uma frase sobre nada.
//
// Uma batida por segundo, e so enquanto a tela que usa esta viva. O texto e um
// State proprio: quem chama le dentro do seu Text e recompoe SO esse Text, nao a
// call inteira.
@Composable
fun lembrarTempoDeCall(inicio: Long?): State<String> {
    val texto = remember { mutableStateOf("") }
    LaunchedEffect(inicio) {
        if (inicio == null) {
            texto.value = ""
            return@LaunchedEffect
        }
        while (true) {
            texto.value = formatarDuracao(System.currentTimeMillis() - inicio)
            // Dorme ate a virada do proximo segundo em vez de 1000ms cravado: senao o
            // relogio atrasa um tiquinho a cada volta e, depois de uma hora de call,
            // pula um segundo na cara da pessoa.
            delay(1000L - (System.currentTimeMillis() - inicio) % 1000L)
        }
    }
    return texto
}

// mm:ss ate uma hora, h:mm:ss depois. Sem "00:" na frente: a call de 3 minutos e a
// esmagadora maioria, e ela nao precisa carregar uma casa de hora vazia.
fun formatarDuracao(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val s = total % 60
    val m = (total / 60) % 60
    val h = total / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
