package app.astra.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private const val ESPERA_ATE_VALER_A_LINHA_MS = 160L
private const val ESPERA_ATE_DIZER_QUE_ACORDA_MS = 4_000L
private const val VOLTA_DA_LINHA_MS = 1_100
private const val FATIA_DO_TRACO = 0.28f

@Composable
fun esperouMaisQue(carregando: Boolean, limiteMs: Long): Boolean {
    var passou by remember { mutableStateOf(false) }
    LaunchedEffect(carregando) {
        passou = false
        if (carregando) {
            delay(limiteMs)
            passou = true
        }
    }
    return carregando && passou
}

@Composable
fun LinhaDeEspera(carregando: Boolean, modifier: Modifier = Modifier) {
    if (!esperouMaisQue(carregando, ESPERA_ATE_VALER_A_LINHA_MS)) return
    val cor = astraColors.accent.copy(alpha = 0.55f)
    if (LocalAppPrefs.current.reduceMotion) {
        Box(modifier.fillMaxWidth().height(2.dp).background(cor.copy(alpha = 0.3f)))
        return
    }
    val avanco by rememberInfiniteTransition(label = "espera").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(VOLTA_DA_LINHA_MS, easing = LinearEasing)),
        label = "avanco",
    )
    Canvas(modifier.fillMaxWidth().height(2.dp).clipToBounds()) {
        val traco = size.width * FATIA_DO_TRACO
        drawRect(
            color = cor,
            topLeft = Offset((size.width + traco) * avanco - traco, 0f),
            size = Size(traco, size.height),
        )
    }
}

@Composable
fun EsperaDaConversa(carregando: Boolean) {
    if (!esperouMaisQue(carregando, ESPERA_ATE_DIZER_QUE_ACORDA_MS)) return
    Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PontosQueRespiram()
            Spacer(Modifier.height(14.dp))
            Text(
                "O servidor está acordando",
                style = MaterialTheme.typography.bodyMedium,
                color = astraColors.text2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Isto pode levar até um minuto depois de um tempo parado.",
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.text3,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PontosQueRespiram() {
    val accent = astraColors.accent
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val fase = if (semMovimento) {
        0f
    } else {
        val f by rememberInfiniteTransition(label = "pontos").animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1_150, easing = LinearEasing)),
            label = "fase",
        )
        f
    }
    Canvas(Modifier.size(width = 62.dp, height = 28.dp)) {
        val r = 4.5.dp.toPx()
        val vao = 16.dp.toPx()
        val baseY = size.height - r
        val inicioX = size.width / 2f - vao
        repeat(3) { i ->
            val subida = sin(fase - i * 0.7f).coerceAtLeast(0f)
            drawCircle(
                color = accent.copy(alpha = 0.38f + 0.34f * subida),
                radius = r * (0.88f + 0.16f * subida),
                center = Offset(inicioX + i * vao, baseY - subida * 11.dp.toPx()),
            )
        }
    }
}
