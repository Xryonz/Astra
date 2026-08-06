package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.astra.desktop.ui.theme.EaseOutStd

// TROCA DE PAGINA EM DOIS TEMPOS.
//
// O AnimatedContent nao servia aqui, e o motivo importa: ele compoe o conteudo
// NOVO ja no primeiro frame da saida. Durante a transicao existiam duas paginas
// vivas ao mesmo tempo — duas listas de mensagem desenhando, o ChatVm novo abrindo
// socket e rede, as imagens decodificando — tudo espremido nos mesmos ~11 frames.
// Era isso que engasgava, nao o custo da animacao.
//
// Atrasar o fadeIn nao resolveria: adia o DESENHO, nao a composicao.
//
// Aqui a pagina antiga apaga primeiro, e so entao a nova e composta. O frame caro
// cai numa janela em que a tela esta apagada e nada esta se movendo — um frame
// perdido ali e literalmente invisivel. A entrada comeca depois disso, com o
// trabalho pesado ja feito.
// Tempos apertados ate o limite do que ainda le como transicao e nao como piscada.
//
// A saida e MAIS CURTA que a entrada de proposito: sumir e lido como instantaneo
// pelo olho, aparecer nao — cortar a entrada pro mesmo tempo da saida faz a pagina
// "estalar" na tela. 80 + 150 com dois frames de respiro no meio da ~265ms porta a
// porta, contra os ~300ms do fade cruzado antigo — e sem os frames perdidos, que
// eram o que fazia parecer lento mesmo sendo curto.
private const val SAIDA_MS = 80
private const val ENTRADA_MS = 150

@Composable
fun <T> TrocaDePagina(
    alvo: T,
    modifier: Modifier = Modifier,
    saidaMs: Int = SAIDA_MS,
    entradaMs: Int = ENTRADA_MS,
    conteudo: @Composable (T) -> Unit,
) {
    val reduzir = LocalReduceMotion.current
    var mostrado by remember { mutableStateOf(alvo) }
    val opacidade = remember { Animatable(1f) }

    LaunchedEffect(alvo, reduzir) {
        if (alvo == mostrado) return@LaunchedEffect
        if (reduzir) {
            mostrado = alvo
            opacidade.snapTo(1f)
            return@LaunchedEffect
        }
        opacidade.animateTo(0f, tween(saidaMs, easing = EaseOutStd))
        mostrado = alvo
        // Dois frames de respiro com a tela apagada: um pra composicao da pagina
        // nova acontecer, outro pro primeiro layout/desenho dela assentar. Sem
        // isto o frame caro vira o PRIMEIRO frame da entrada — e o engasgo so
        // troca de lado.
        withFrameNanos {}
        withFrameNanos {}
        opacidade.animateTo(1f, tween(entradaMs, easing = EaseOutStd))
    }

    // graphicsLayer e nao alpha(): a leitura da opacidade fica na fase de DESENHO,
    // entao animar nao recompoe a pagina inteira 60x por segundo.
    Box(modifier.graphicsLayer { alpha = opacidade.value }) { conteudo(mostrado) }
}
