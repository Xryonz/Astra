package app.astra.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text

// Constelacao que SE FORMA conforme o formulario e preenchido, e se DESFAZ quando
// se apaga (pedido do dono). Nao e enfeite solto: e o medidor de preenchimento.
//
// Cada estrela e um marco do progresso; a linha ate a proxima cresce enquanto o
// trecho e percorrido. Apagar uma letra devolve o progresso, entao o traco RETRAI
// — o desenho e funcao pura do que esta digitado, sem estado escondido. Fechado o
// ultimo traco, a mensagem de conclusao acende no meio.
//
// Posicoes normalizadas (0..1) desenhando algo proximo da Cassiopeia (o "W"), que
// fecha bem num painel mais alto que largo.
private val NODES = listOf(
    Offset(0.10f, 0.30f),
    Offset(0.28f, 0.72f),
    Offset(0.46f, 0.34f),
    Offset(0.64f, 0.74f),
    Offset(0.86f, 0.26f),
)

// Quanto de cada campo conta pro progresso. Comprimentos-alvo curtos de proposito:
// a constelacao tem que reagir DESDE a primeira letra, senao o efeito nao se liga
// ao gesto de digitar. O email so completa seu trecho quando tem cara de email —
// assim o desenho fechado significa "da pra enviar", nao so "tem texto".
fun loginProgress(email: String, password: String): Float {
    val e = email.trim()
    val emailShape = e.contains('@') && e.substringAfterLast('@').contains('.')
    val emailPart = (e.length / 14f).coerceIn(0f, 1f) * (if (emailShape) 1f else 0.75f)
    val passPart = (password.length / 8f).coerceIn(0f, 1f)
    return ((emailPart + passPart) / 2f).coerceIn(0f, 1f)
}

@Composable
fun LoginConstellation(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    // Suaviza o avanco/retrocesso: digitar rapido nao faz o traco pular de letra em
    // letra. Com movimento reduzido acompanha o valor cru.
    val p by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(if (reduce) 0 else 260),
        label = "constellation",
    )
    val complete = progress >= 1f
    val msgAlpha by animateFloatAsState(
        targetValue = if (complete) 1f else 0f,
        animationSpec = tween(if (reduce) 0 else 320),
        label = "constellationMsg",
    )

    val accent = Obsidian.accent
    Box(modifier.height(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 14.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            fun at(i: Int) = Offset(pad + NODES[i].x * w, pad + NODES[i].y * h)

            val legs = NODES.size - 1
            // Quanto do caminho inteiro ja foi percorrido, em "pernas".
            val walked = p * legs

            // Linhas: as vencidas inteiras, a atual pela metade exata do progresso.
            for (i in 0 until legs) {
                val done = (walked - i).coerceIn(0f, 1f)
                if (done <= 0f) continue
                val a = at(i)
                val b = at(i + 1)
                drawLine(
                    color = accent.copy(alpha = 0.22f + 0.30f * done),
                    start = a,
                    end = Offset(a.x + (b.x - a.x) * done, a.y + (b.y - a.y) * done),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Estrelas: acendem na ordem. A do inicio ja nasce visivel (a constelacao
            // precisa existir apagada pra a formacao ter pra onde ir).
            for (i in NODES.indices) {
                val lit = (walked - (i - 1)).coerceIn(0f, 1f)
                val c = at(i)
                val alpha = 0.16f + 0.84f * lit
                if (lit > 0.35f) {
                    // Halo so na estrela ja acesa — evita mancha nas apagadas.
                    drawCircle(accent.copy(alpha = 0.14f * lit), radius = 7.dp.toPx() * lit, center = c)
                }
                drawCircle(accent.copy(alpha = alpha), radius = (1.6f + 1.4f * lit).dp.toPx(), center = c)
            }
        }
        if (msgAlpha > 0f) {
            Text(
                "constelacao completa",
                style = TextStyle(
                    color = accent.copy(alpha = msgAlpha),
                    fontSize = 12.sp,
                    fontFamily = DmSerif,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}
