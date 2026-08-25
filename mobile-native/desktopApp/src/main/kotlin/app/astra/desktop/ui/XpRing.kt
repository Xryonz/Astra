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

private val ESPESSURA = 2.dp
private val FOLGA     = 2.5.dp

fun Modifier.anelDeXp(
    fracao:    () -> Float,
    aceso:     () -> Float,
    varredura: () -> Float,
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

    drawArc(color = trilho, startAngle = 0f, sweepAngle = 360f, useCenter = false,
        topLeft = canto, size = caixa, style = Stroke(width = esp))

    val f = fracao().coerceIn(0f, 1f)
    val brilhoAgora = aceso()
    if (f > 0.001f) {
        drawArc(
            color = cor.copy(alpha = 0.85f + 0.15f * brilhoAgora),
            startAngle = -90f, sweepAngle = 360f * f, useCenter = false,
            topLeft = canto, size = caixa, style = traco,
        )
    }
    if (brilhoAgora > 0.01f) {
        drawArc(
            color = cor.copy(alpha = 0.22f * brilhoAgora),
            startAngle = -90f, sweepAngle = 360f * f.coerceAtLeast(0.02f), useCenter = false,
            topLeft = canto, size = caixa, style = Stroke(width = esp * 3.2f, cap = StrokeCap.Round),
        )
    }
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
    val varreduraAnim = remember { Animatable(1f) }

    val progresso = store.progresso
    LaunchedEffect(store) {
        var primeira = true
        progresso.collect { p ->
            val alvo = fracaoDe(p)
            if (primeira) {
                primeira = false
                fracaoAnim.snapTo(alvo)
            } else if (alvo < fracaoAnim.value) {
                fracaoAnim.snapTo(0f)
                fracaoAnim.animateTo(alvo, tween(520, easing = EaseOutSoft))
            } else {
                fracaoAnim.animateTo(alvo, tween(620, easing = EaseOutSoft))
            }
        }
    }

    LaunchedEffect(store) {
        store.ganhos.collectLatest { g ->
            if (g.subiuDeNivel) {
                varreduraAnim.snapTo(0f)
                varreduraAnim.animateTo(1f, tween(900, easing = LinearEasing))
            }
            val forte = g.origem == "missao"
            acesoAnim.snapTo(1f)
            acesoAnim.animateTo(0f, tween(if (forte) 1500 else 900, easing = EaseOutStd))
        }
    }

    return remember { VisualDeXp(fracaoAnim, acesoAnim, varreduraAnim) }
}

fun fracaoDe(p: ProgressoDto): Float =
    if (p.paraOProximo <= 0) 0f else (p.noNivel.toFloat() / p.paraOProximo).coerceIn(0f, 1f)

val corDoAnel: Color get() = Obsidian.accent
val trilhoDoAnel: Color get() = Obsidian.borderDim.copy(alpha = 0.55f)
