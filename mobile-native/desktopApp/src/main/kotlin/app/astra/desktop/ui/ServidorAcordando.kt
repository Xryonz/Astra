package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.net.Servidor
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text

// A FAIXA QUE EXPLICA A ESPERA.
//
// Nao existe pra ser bonita: existe porque um minuto de tela parada sem palavra nenhuma
// e indistinguivel de um app quebrado. Aparece so quando a espera vira espera de
// verdade (segunda tentativa em diante, ver Servidor) e some sozinha quando a API
// responde.
//
// Diz o MOTIVO, nao so o sintoma. "Aguarde" nao informa nada; "a hospedagem gratuita
// desliga depois de quinze minutos parada" informa, e a pessoa deixa de achar que o
// proprio computador esta com problema.
@Composable
fun ServidorAcordandoStrip(modifier: Modifier = Modifier) {
    val estado by Servidor.estado.collectAsState()
    val segundos by Servidor.esperandoHa.collectAsState()
    val reduceMotion = LocalReduceMotion.current

    AnimatedVisibility(
        visible = estado == Servidor.Estado.ACORDANDO,
        // tween explicito, nao mola: a faixa muda de ALTURA, e mola tem duracao
        // proporcional a distancia -- a mesma faixa entraria com tempos diferentes
        // conforme a largura da janela mudasse o numero de linhas do texto.
        enter = fadeIn(tween(220)) + expandVertically(tween(220, easing = EaseOutStd)),
        exit = fadeOut(tween(180)) + shrinkVertically(tween(180, easing = EaseOutStd)),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Obsidian.raised)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ponto(reduceMotion)
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Acordando o servidor",
                    style = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
                )
                Text(
                    "a hospedagem gratuita desliga a instância após quinze minutos parada; " +
                        "religar costuma levar até um minuto",
                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                )
            }
            Spacer(Modifier.width(10.dp))
            // O contador e a prova de vida. Um circulo girando gira igual quando a
            // tentativa morreu; um numero que sobe so sobe enquanto alguem conta.
            Text(
                "há ${segundos}s",
                style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
            )
        }
    }
}

// Respiracao lenta em vez de piscar: e um estado que dura um minuto, e piscar por um
// minuto inteiro cansa. Movimento aqui e sinal de "ainda estou tentando", nada mais.
@Composable
private fun Ponto(reduceMotion: Boolean) {
    val alpha = if (reduceMotion) 1f else {
        val t = rememberInfiniteTransition(label = "acordando")
        val a by t.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = EaseOutSoft), RepeatMode.Reverse),
            label = "pulso",
        )
        a
    }
    Spacer(
        Modifier
            .size(7.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Obsidian.accent),
    )
}
