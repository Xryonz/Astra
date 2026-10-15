package app.astra.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import io.github.alexzhirkevich.compottie.ExperimentalCompottieApi
import io.github.alexzhirkevich.compottie.LottieClipSpec
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.dynamic.LottieDynamicProperties
import io.github.alexzhirkevich.compottie.dynamic.rememberLottieDynamicProperties
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

fun tudoNaLuz(origem: Color, luz: Color, fundo: Color): Color = luz

fun claroViraFundo(origem: Color, luz: Color, fundo: Color): Color =
    if (origem.luminance() > LIMIAR_DO_CLARO) fundo else luz

fun escuroViraFundo(origem: Color, luz: Color, fundo: Color): Color =
    if (origem.luminance() < LIMIAR_DO_ESCURO) fundo else luz

@OptIn(ExperimentalCompottieApi::class)
@Composable
fun AnimacaoDeArtista(
    arquivo: String,
    vez: Int,
    luz: Color,
    modifier: Modifier = Modifier,
    apagaJunto: Modifier = Modifier,
    fundo: Color = Color.Transparent,
    cores: (Color, Color, Color) -> Color = ::tudoNaLuz,
    quadros: IntRange? = null,
    velocidade: Float = 1f,
    ficaNoFim: Boolean = false,
    aoTerminar: () -> Unit = {},
) {
    val reduzir = LocalReduceMotion.current
    if (vez == 0 || (reduzir && !ficaNoFim)) return
    val composicao by rememberLottieComposition(arquivo) {
        LottieCompositionSpec.JsonString(AnimacoesDoAstra.texto(arquivo))
    }
    val propriedades = rememberLottieDynamicProperties(luz, fundo, cores) {
        shapeLayer("**") {
            fill("**") { color { origem -> cores(origem, luz, fundo) } }
            stroke("**") { color { origem -> cores(origem, luz, fundo) } }
        }
    }
    if (reduzir) {
        Quadro(composicao, propriedades, modifier.then(apagaJunto)) {
            val c = composicao ?: return@Quadro 0f
            ((quadros?.last?.toFloat() ?: c.endFrame) / c.endFrame).coerceIn(0f, 1f)
        }
        return
    }
    val trecho = remember(quadros) { quadros?.let { LottieClipSpec.Frame(it.first, it.last) } }
    key(vez) {
        val estado = animateLottieCompositionAsState(
            composicao,
            clipSpec = trecho,
            speed = velocidade,
            iterations = 1,
        )
        if (ficaNoFim) {
            Quadro(composicao, propriedades, modifier.then(apagaJunto)) { estado.progress }
        } else if (!estado.isAtEnd) {
            Quadro(
                composicao,
                propriedades,
                modifier.graphicsLayer {
                    val c = composicao ?: return@graphicsLayer
                    val inicio = (quadros?.first?.toFloat() ?: 0f) / c.endFrame
                    val fim = (quadros?.last?.toFloat() ?: c.endFrame) / c.endFrame
                    val andado = ((estado.progress - inicio) / (fim - inicio).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
                    alpha = ((1f - andado) / TRECHO_DO_APAGAR).coerceIn(0f, 1f)
                }.then(apagaJunto),
            ) { estado.progress }
        }
        if (!ficaNoFim && estado.composition != null && estado.isAtEnd) {
            LaunchedEffect(Unit) { aoTerminar() }
        }
    }
}

@Composable
private fun Quadro(
    composicao: LottieComposition?,
    propriedades: LottieDynamicProperties,
    modifier: Modifier,
    progresso: () -> Float,
) {
    Image(
        painter = rememberLottiePainter(composicao, progress = progresso, dynamicProperties = propriedades),
        contentDescription = null,
        modifier = modifier,
    )
}

private object AnimacoesDoAstra {
    fun texto(arquivo: String): String =
        checkNotNull(javaClass.getResourceAsStream("/animacoes/$arquivo.json")) { "animação $arquivo ausente" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
}

private const val TRECHO_DO_APAGAR = 0.25f
private const val LIMIAR_DO_CLARO = 0.85f
private const val LIMIAR_DO_ESCURO = 0.08f
