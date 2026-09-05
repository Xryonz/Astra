package app.astra.desktop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import app.astra.desktop.ui.theme.Text
import kotlin.math.max

private const val ABERTURA_MS = 260
private const val GIRO_DO_ARCO_MS = 6000
private const val VARREDURA_MS = 2800
private const val PULSO_MS = 2400
private const val PARADAS_DO_ARCO = 7
private const val SATURACAO_MINIMA = 0.42f
private const val CLARIDADE_MINIMA = 0.72f

@Composable
fun NomeColorido(
    texto: String,
    cor: CorDoNome?,
    padrao: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val repouso = cor?.pincel ?: SolidColor(padrao)
    val animada = cor as? CorDoNome.Animada
    if (animada == null || LocalReduceMotion.current) {
        Text(
            text = texto,
            modifier = modifier,
            style = estiloDoNome(repouso, fontSize, fontWeight, fontFamily),
            overflow = overflow,
            maxLines = maxLines,
        )
        return
    }
    val fonte = remember { MutableInteractionSource() }
    val sobOCursor by fonte.collectIsHoveredAsState()
    val abertura by animateFloatAsState(
        targetValue = if (sobOCursor) 1f else 0f,
        animationSpec = tween(ABERTURA_MS),
    )
    Text(
        text = texto,
        modifier = modifier.hoverable(fonte),
        style = estiloDoNome(
            pincel = if (abertura > 0f) pincelVivo(animada, abertura) else repouso,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
        ),
        overflow = overflow,
        maxLines = maxLines,
    )
}

@Composable
private fun pincelVivo(cor: CorDoNome.Animada, abertura: Float): Brush {
    val relogio = rememberInfiniteTransition()
    return when (cor) {
        is CorDoNome.Animada.ArcoIris -> {
            val giro by relogio.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(GIRO_DO_ARCO_MS, easing = LinearEasing)),
            )
            pincelDeArcoIris(cor.solida, giro, abertura)
        }
        is CorDoNome.Animada.Varredura -> {
            val virada by relogio.animateFloat(
                initialValue = 0.26f,
                targetValue = 0.74f,
                animationSpec = infiniteRepeatable(
                    tween(VARREDURA_MS, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                ),
            )
            pincelDeVarredura(cor.solida, cor.fim, virada, abertura)
        }
        is CorDoNome.Animada.Pulso -> {
            val respiro by relogio.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(PULSO_MS, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                ),
            )
            SolidColor(clareada(cor.solida, respiro * abertura))
        }
    }
}

private fun estiloDoNome(
    pincel: Brush,
    fontSize: TextUnit,
    fontWeight: FontWeight?,
    fontFamily: FontFamily?,
) = TextStyle(brush = pincel, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily)

private fun pincelDeArcoIris(base: Color, giro: Float, abertura: Float): Brush {
    val hsv = emHsv(base)
    val saturacao = max(hsv.saturacao, abertura * SATURACAO_MINIMA)
    val valor = max(hsv.valor, abertura * CLARIDADE_MINIMA)
    val paradas = List(PARADAS_DO_ARCO) { i ->
        val volta = i.toFloat() / (PARADAS_DO_ARCO - 1)
        val matiz = hsv.matiz + abertura * 360f * (volta + giro)
        Color.hsv(((matiz % 360f) + 360f) % 360f, saturacao, valor)
    }
    return Brush.linearGradient(paradas)
}

private fun pincelDeVarredura(inicio: Color, fim: Color, virada: Float, abertura: Float): Brush =
    Brush.linearGradient(
        0f to inicio,
        (1f - abertura * (1f - virada)) to fim,
        1f to lerp(fim, inicio, abertura),
    )

private fun clareada(base: Color, quanto: Float): Color {
    val hsv = emHsv(base)
    val aceso = Color.hsv(
        hue = hsv.matiz,
        saturation = hsv.saturacao * 0.72f,
        value = (hsv.valor + (1f - hsv.valor) * 0.55f).coerceAtMost(1f),
    )
    return lerp(base, aceso, quanto)
}
