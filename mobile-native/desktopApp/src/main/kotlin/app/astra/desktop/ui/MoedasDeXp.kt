package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.xp.XpStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAXIMO_NA_TELA = 3
private const val SUBIDA_MS = 1100
private const val SALTO_MS = 190
private const val ATRASO_ENTRE_ELAS_MS = 90L
private const val COMECO_DO_SUMICO_MS = 600L
private const val PARADA_SEM_MOVIMENTO_MS = 900L

private val ALTURA_DO_VOO = 52.dp
private val LARGURA_DA_PISTA = 64.dp

private class MoedaDeXp(val chave: Int, val quanto: Int, val atrasoMs: Long, val forte: Boolean)

private object AcimaDaAncora : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centrado = anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2
        val limite = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        return IntOffset(
            centrado.coerceIn(0, limite),
            (anchorBounds.top - popupContentSize.height).coerceAtLeast(0),
        )
    }
}

@Composable
fun MoedasDeXp(store: XpStore) {
    val voando = remember { mutableStateListOf<MoedaDeXp>() }
    var proximaChave by remember { mutableIntStateOf(0) }

    LaunchedEffect(store) {
        store.ganhos.collect { g ->
            if (g.ganho <= 0) return@collect
            if (voando.size >= MAXIMO_NA_TELA) voando.removeAt(0)
            voando.add(
                MoedaDeXp(
                    chave = proximaChave++,
                    quanto = g.ganho,
                    atrasoMs = voando.size * ATRASO_ENTRE_ELAS_MS,
                    forte = g.origem == "missao",
                ),
            )
        }
    }

    if (voando.isEmpty()) return

    Popup(popupPositionProvider = AcimaDaAncora, properties = PopupProperties(focusable = false)) {
        Box(
            Modifier.width(LARGURA_DA_PISTA).height(ALTURA_DO_VOO),
            contentAlignment = Alignment.BottomCenter,
        ) {
            voando.forEach { moeda ->
                key(moeda.chave) {
                    UmaMoeda(moeda) { voando.remove(moeda) }
                }
            }
        }
    }
}

@Composable
private fun UmaMoeda(moeda: MoedaDeXp, aoSumir: () -> Unit) {
    val reduzir = LocalReduceMotion.current
    val subida = remember { Animatable(0f) }
    val opacidade = remember { Animatable(0f) }
    val salto = remember { Animatable(if (reduzir) 1f else 0.7f) }

    LaunchedEffect(moeda.chave) {
        if (reduzir) {
            opacidade.snapTo(1f)
            delay(PARADA_SEM_MOVIMENTO_MS)
            opacidade.animateTo(0f, tween(200))
            aoSumir()
            return@LaunchedEffect
        }
        delay(moeda.atrasoMs)
        opacidade.snapTo(1f)
        launch { salto.animateTo(1f, tween(SALTO_MS, easing = EaseOutStd)) }
        launch {
            delay(COMECO_DO_SUMICO_MS)
            opacidade.animateTo(0f, tween(SUBIDA_MS - COMECO_DO_SUMICO_MS.toInt()))
        }
        subida.animateTo(1f, tween(SUBIDA_MS, easing = EaseOutStd))
        aoSumir()
    }

    Text(
        "+${moeda.quanto}",
        style = TextStyle(
            color = if (moeda.forte) Obsidian.accent else Obsidian.text1,
            fontSize = if (moeda.forte) 15.sp else 13.sp,
            fontFamily = DmMono,
            fontWeight = FontWeight.Medium,
        ),
        modifier = Modifier.graphicsLayer {
            alpha = opacidade.value
            translationY = -subida.value * ALTURA_DO_VOO.toPx()
            scaleX = salto.value
            scaleY = salto.value
        },
    )
}
