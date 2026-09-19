package app.astra.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.Obsidian
import kotlinx.coroutines.delay

private val ALTURA = 2.dp
private const val FATIA_DO_TRACO = 0.28f
private const val VOLTA_MS = 1_100
private const val ESPERA_ATE_VALER_A_LINHA_MS = 160L

@Composable
private fun esperaLongaOBastante(carregando: Boolean): Boolean {
    var passouDoLimiar by remember { mutableStateOf(false) }
    LaunchedEffect(carregando) {
        if (!carregando) {
            passouDoLimiar = false
        } else {
            delay(ESPERA_ATE_VALER_A_LINHA_MS)
            passouDoLimiar = true
        }
    }
    return carregando && passouDoLimiar
}

@Composable
fun LinhaDeProgresso(modifier: Modifier = Modifier) {
    val cor = Obsidian.accent.copy(alpha = 0.55f)

    if (LocalReduceMotion.current) {
        Box(modifier.fillMaxWidth().height(ALTURA).background(cor.copy(alpha = 0.3f)))
        return
    }

    val avanco by rememberInfiniteTransition(label = "linha").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(VOLTA_MS, easing = LinearEasing)),
        label = "avanco",
    )

    Canvas(modifier.fillMaxWidth().height(ALTURA).clipToBounds()) {
        val traco = size.width * FATIA_DO_TRACO
        drawRect(
            color = cor,
            topLeft = Offset((size.width + traco) * avanco - traco, 0f),
            size = Size(traco, size.height),
        )
    }
}

@Composable
fun EsperaNoTopo(carregando: Boolean, modifier: Modifier = Modifier) {
    if (esperaLongaOBastante(carregando)) LinhaDeProgresso(modifier)
}
