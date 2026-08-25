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
        withFrameNanos {}
        withFrameNanos {}
        opacidade.animateTo(1f, tween(entradaMs, easing = EaseOutStd))
    }

    Box(modifier.graphicsLayer { alpha = opacidade.value }) { conteudo(mostrado) }
}
