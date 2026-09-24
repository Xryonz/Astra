package app.astra.mobile.feature.xp.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.astra.mobile.core.network.dto.ProgressoDto
import app.astra.mobile.core.xp.Estrelas
import app.astra.mobile.core.xp.fracaoDoNivel
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.DmMono
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val ESPESSURA = 2.dp
private val FOLGA = 2.5.dp

class VisualDasEstrelas(
    private val fracaoAnim: Animatable<Float, *>,
    private val acesoAnim: Animatable<Float, *>,
    private val varreduraAnim: Animatable<Float, *>,
) {
    val fracao: () -> Float = { fracaoAnim.value }
    val aceso: () -> Float = { acesoAnim.value }
    val varredura: () -> Float = { varreduraAnim.value }
}

@Composable
fun lembrarVisualDasEstrelas(progresso: State<ProgressoDto>, estrelas: Estrelas): VisualDasEstrelas {
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val fracaoAnim = remember { Animatable(0f) }
    val acesoAnim = remember { Animatable(0f) }
    val varreduraAnim = remember { Animatable(1f) }

    LaunchedEffect(progresso.value) {
        val alvo = fracaoDoNivel(progresso.value)
        when {
            semMovimento -> fracaoAnim.snapTo(alvo)
            alvo < fracaoAnim.value -> {
                fracaoAnim.snapTo(0f)
                fracaoAnim.animateTo(alvo, tween(520, easing = EaseOutSoft))
            }
            else -> fracaoAnim.animateTo(alvo, tween(620, easing = EaseOutSoft))
        }
    }

    LaunchedEffect(estrelas, semMovimento) {
        estrelas.ganhos.collectLatest { ganho ->
            if (semMovimento) return@collectLatest
            if (ganho.subiuDeNivel) {
                varreduraAnim.snapTo(0f)
                varreduraAnim.animateTo(1f, tween(900, easing = LinearEasing))
            }
            val forte = ganho.origem == ORIGEM_MISSAO
            acesoAnim.snapTo(1f)
            acesoAnim.animateTo(0f, tween(if (forte) 1_500 else 900, easing = EaseOutSoft))
        }
    }

    return remember { VisualDasEstrelas(fracaoAnim, acesoAnim, varreduraAnim) }
}

fun Modifier.anelDeEstrelas(
    visual: VisualDasEstrelas,
    cor: Color,
    trilho: Color,
    espessura: Dp = ESPESSURA,
    folga: Dp = FOLGA,
): Modifier = drawBehind {
    val esp = espessura.toPx()
    val raio = size.minDimension / 2f + folga.toPx() + esp / 2f
    val canto = Offset(center.x - raio, center.y - raio)
    val caixa = Size(raio * 2f, raio * 2f)
    val traco = Stroke(width = esp, cap = StrokeCap.Round)

    drawArc(
        color = trilho, startAngle = 0f, sweepAngle = 360f, useCenter = false,
        topLeft = canto, size = caixa, style = Stroke(width = esp),
    )

    val fracao = visual.fracao().coerceIn(0f, 1f)
    val aceso = visual.aceso()
    if (fracao > 0.001f) {
        drawArc(
            color = cor.copy(alpha = 0.85f + 0.15f * aceso),
            startAngle = -90f, sweepAngle = 360f * fracao, useCenter = false,
            topLeft = canto, size = caixa, style = traco,
        )
    }
    if (aceso > 0.01f) {
        drawArc(
            color = cor.copy(alpha = 0.22f * aceso),
            startAngle = -90f, sweepAngle = 360f * fracao.coerceAtLeast(0.02f), useCenter = false,
            topLeft = canto, size = caixa, style = Stroke(width = esp * 3.2f, cap = StrokeCap.Round),
        )
    }
    val varredura = visual.varredura()
    if (varredura > 0.001f && varredura < 0.999f) {
        drawArc(
            color = cor.copy(alpha = (1f - varredura) * 0.9f + 0.1f),
            startAngle = -90f + 360f * varredura, sweepAngle = CAUDA_DA_VARREDURA, useCenter = false,
            topLeft = canto, size = caixa, style = Stroke(width = esp * 1.6f, cap = StrokeCap.Round),
        )
    }
}

private class MoedaVoando(val chave: Int, val quanto: Int, val atrasoMs: Long, val forte: Boolean)

@Composable
fun MoedasDeBrilho(estrelas: Estrelas, modifier: Modifier = Modifier) {
    val voando = remember { mutableStateListOf<MoedaVoando>() }
    var proximaChave by remember { mutableIntStateOf(0) }

    LaunchedEffect(estrelas) {
        estrelas.ganhos.collect { ganho ->
            if (ganho.ganho <= 0) return@collect
            if (voando.size >= MAXIMO_NA_TELA) voando.removeAt(0)
            voando.add(
                MoedaVoando(
                    chave = proximaChave++,
                    quanto = ganho.ganho,
                    atrasoMs = voando.size * ATRASO_ENTRE_ELAS_MS,
                    forte = ganho.origem == ORIGEM_MISSAO,
                ),
            )
        }
    }

    if (voando.isEmpty()) return

    Box(
        modifier.width(LARGURA_DA_PISTA).height(ALTURA_DO_VOO),
        contentAlignment = Alignment.BottomCenter,
    ) {
        voando.forEach { moeda ->
            key(moeda.chave) { UmaMoeda(moeda) { voando.remove(moeda) } }
        }
    }
}

@Composable
private fun UmaMoeda(moeda: MoedaVoando, aoSumir: () -> Unit) {
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val subida = remember { Animatable(0f) }
    val opacidade = remember { Animatable(0f) }
    val salto = remember { Animatable(if (semMovimento) 1f else 0.7f) }

    LaunchedEffect(moeda.chave) {
        if (semMovimento) {
            opacidade.snapTo(1f)
            delay(PARADA_SEM_MOVIMENTO_MS)
            opacidade.animateTo(0f, tween(200))
            aoSumir()
            return@LaunchedEffect
        }
        delay(moeda.atrasoMs)
        opacidade.snapTo(1f)
        launch { salto.animateTo(1f, tween(SALTO_MS, easing = EaseOutSoft)) }
        launch {
            delay(COMECO_DO_SUMICO_MS)
            opacidade.animateTo(0f, tween(SUBIDA_MS - COMECO_DO_SUMICO_MS.toInt()))
        }
        subida.animateTo(1f, tween(SUBIDA_MS, easing = EaseOutSoft))
        aoSumir()
    }

    Text(
        "+${moeda.quanto}",
        color = if (moeda.forte) astraColors.accent else astraColors.text1,
        fontSize = if (moeda.forte) 15.sp else 13.sp,
        fontFamily = DmMono,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.graphicsLayer {
            alpha = opacidade.value
            translationY = -subida.value * ALTURA_DO_VOO.toPx()
            scaleX = salto.value
            scaleY = salto.value
        },
    )
}

private const val ORIGEM_MISSAO = "missao"
private const val CAUDA_DA_VARREDURA = 46f
private const val MAXIMO_NA_TELA = 3
private const val SUBIDA_MS = 1_100
private const val SALTO_MS = 190
private const val ATRASO_ENTRE_ELAS_MS = 90L
private const val COMECO_DO_SUMICO_MS = 600L
private const val PARADA_SEM_MOVIMENTO_MS = 900L
private val ALTURA_DO_VOO = 52.dp
private val LARGURA_DA_PISTA = 64.dp
