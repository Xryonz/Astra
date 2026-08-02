package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.xp.XpStore
import app.astra.mobile.core.network.dto.ProgressoDto
import kotlinx.coroutines.flow.collectLatest

// O ANEL DE XP em volta do avatar do rodape.
//
// Anel e nao barra porque nao ha onde por barra: o cartao do rodape tem ~48dp e ja
// carrega avatar, bolinha de presenca, nome, status e dois botoes. O anel nao ocupa
// altura nenhuma — e desenhado PRA FORA da caixa do avatar, dentro da folga que o
// cartao ja tinha. E porque a linguagem do app ja e orbita: o halo do DesktopAvatar
// e o anel de quem fala na call sao a mesma familia.
//
// REGRA DE OURO DESTE ARQUIVO: nada que muda por frame pode ser lido na composicao.
// Todos os valores animados entram como lambda e sao lidos dentro do drawBehind, ou
// seja, na fase de DESENHO. Ler direto recomporia a barra lateral inteira a cada
// ganho de XP — e o ganho pode chegar enquanto a pessoa esta rolando o chat.

private val ESPESSURA = 2.dp
private val FOLGA     = 2.5.dp   // respiro entre a foto e o anel

fun Modifier.anelDeXp(
    fracao:    () -> Float,   // 0..1 — quanto do nivel atual ja andou
    aceso:     () -> Float,   // 0..1 — brilho momentaneo depois de um ganho
    varredura: () -> Float,   // 0..1 — a volta completa de luz ao subir de nivel
    cor:       Color,
    trilho:    Color,
    espessura: Dp = ESPESSURA,
    folga:     Dp = FOLGA,
): Modifier = drawBehind {
    val esp = espessura.toPx()
    val raio = size.minDimension / 2f + folga.toPx() + esp / 2f
    val canto = Offset(center.x - raio, center.y - raio)
    val caixa = Size(raio * 2f, raio * 2f)
    val traco = Stroke(width = esp, cap = StrokeCap.Round)

    // Trilho inteiro: sem ele o anel some quando o nivel acabou de virar, e um anel
    // que aparece e desaparece parece defeito, nao progresso.
    drawArc(color = trilho, startAngle = 0f, sweepAngle = 360f, useCenter = false,
        topLeft = canto, size = caixa, style = Stroke(width = esp))

    val f = fracao().coerceIn(0f, 1f)
    val brilhoAgora = aceso()
    if (f > 0.001f) {
        // Comeca no topo (-90) e anda no sentido horario, como qualquer relogio.
        drawArc(
            color = cor.copy(alpha = 0.85f + 0.15f * brilhoAgora),
            startAngle = -90f, sweepAngle = 360f * f, useCenter = false,
            topLeft = canto, size = caixa, style = traco,
        )
    }
    // Halo do ganho: um segundo anel mais largo e quase transparente por cima. E o
    // "respirar" depois de ganhar XP, sem numero voando nem som.
    if (brilhoAgora > 0.01f) {
        drawArc(
            color = cor.copy(alpha = 0.22f * brilhoAgora),
            startAngle = -90f, sweepAngle = 360f * f.coerceAtLeast(0.02f), useCenter = false,
            topLeft = canto, size = caixa, style = Stroke(width = esp * 3.2f, cap = StrokeCap.Round),
        )
    }
    // Subiu de nivel: uma cabeca de luz da a volta inteira uma vez. E o momento que
    // a pessoa printa — por isso e a unica animacao do conjunto que se permite ser
    // notada.
    val v = varredura()
    if (v > 0.001f && v < 0.999f) {
        val cauda = 46f
        drawArc(
            color = cor.copy(alpha = (1f - v) * 0.9f + 0.1f),
            startAngle = -90f + 360f * v, sweepAngle = cauda, useCenter = false,
            topLeft = canto, size = caixa, style = Stroke(width = esp * 1.6f, cap = StrokeCap.Round),
        )
    }
}

// Estado animado do anel. Devolve lambdas (nao valores) de proposito: quem recebe
// passa direto pro Modifier.anelDeXp e nada disso e lido na composicao.
class VisualDeXp(
    private val fracaoAnim: Animatable<Float, *>,
    private val acesoAnim: Animatable<Float, *>,
    private val varreduraAnim: Animatable<Float, *>,
) {
    val fracao: () -> Float = { fracaoAnim.value }
    val aceso: () -> Float = { acesoAnim.value }
    val varredura: () -> Float = { varreduraAnim.value }
}

@Composable
fun rememberVisualDeXp(store: XpStore): VisualDeXp {
    val fracaoAnim = remember { Animatable(0f) }
    val acesoAnim = remember { Animatable(0f) }
    val varreduraAnim = remember { Animatable(1f) } // 1 = parado (nada desenhado)

    // A fracao segue o progresso. Primeira leitura ASSENTA sem animar: ver a barra
    // encher do zero toda vez que se abre o app transformaria um dado em espetaculo.
    val progresso = store.progresso
    LaunchedEffect(store) {
        var primeira = true
        progresso.collect { p ->
            val alvo = fracaoDe(p)
            if (primeira) {
                primeira = false
                fracaoAnim.snapTo(alvo)
            } else if (alvo < fracaoAnim.value) {
                // Virou o nivel: o anel nao volta girando pra tras. Zera na hora e
                // sobe dali — a volta de luz da varredura e que conta a virada.
                fracaoAnim.snapTo(0f)
                fracaoAnim.animateTo(alvo, tween(520, easing = EaseOutSoft))
            } else {
                fracaoAnim.animateTo(alvo, tween(620, easing = EaseOutSoft))
            }
        }
    }

    // collectLatest: dois ganhos seguidos nao empilham animacao, o novo manda.
    LaunchedEffect(store) {
        store.ganhos.collectLatest { g ->
            if (g.subiuDeNivel) {
                varreduraAnim.snapTo(0f)
                varreduraAnim.animateTo(1f, tween(900, easing = LinearEasing))
            }
            acesoAnim.snapTo(1f)
            acesoAnim.animateTo(0f, tween(900, easing = EaseOutStd))
        }
    }

    return remember { VisualDeXp(fracaoAnim, acesoAnim, varreduraAnim) }
}

fun fracaoDe(p: ProgressoDto): Float =
    if (p.paraOProximo <= 0) 0f else (p.noNivel.toFloat() / p.paraOProximo).coerceIn(0f, 1f)

// Cor do anel: o accent do usuario. Trilho quase invisivel — presente o bastante
// pra dar forma, apagado o bastante pra nao competir com a foto.
val corDoAnel: Color get() = Obsidian.accent
val trilhoDoAnel: Color get() = Obsidian.borderDim.copy(alpha = 0.55f)
