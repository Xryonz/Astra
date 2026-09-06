package app.astra.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Cinzel
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text

private const val SUAVIDADE_MS = 260
private const val ENTRADA_MS = 1100

@Composable
fun TelaDeCarregamento(reduceMotion: Boolean, aoTerminar: () -> Unit) {
    var alvo by remember { mutableStateOf(0f) }
    val progresso by animateFloatAsState(
        targetValue = alvo,
        animationSpec = tween(if (reduceMotion) 0 else SUAVIDADE_MS, easing = LinearEasing),
        label = "carregamento",
    )

    val entrada: State<Float>? = if (reduceMotion) null else {
        var comecou by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) { comecou = true }
        animateFloatAsState(
            targetValue = if (comecou) 1f else 0f,
            animationSpec = tween(ENTRADA_MS, easing = LinearEasing),
            label = "entradaDoCarregamento",
        )
    }

    Box(
        Modifier.fillMaxSize().background(Obsidian.void),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            Obsidian.accent.copy(alpha = 0.10f),
                            Obsidian.accent.copy(alpha = 0.03f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height * 0.34f),
                        radius = size.minDimension * 0.62f,
                    ),
                )
            },
        )
        Aquecimento(aoAvancar = { alvo = it }, aoTerminar = aoTerminar)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 24.dp).fillMaxWidth(0.5f),
        ) {
            RotatingStarsLogo(reduceMotion, entrance = entrada, planetRes = "astra-glyph.png")
            Spacer(Modifier.height(18.dp))
            Text(
                "ASTRA",
                style = TextStyle(
                    color = Obsidian.text1,
                    fontSize = 22.sp,
                    fontFamily = Cinzel,
                    letterSpacing = 3.5.sp,
                ),
                modifier = Modifier.graphicsLayer {
                    val ms = (entrada?.value ?: 1f) * 2000f
                    alpha = ((ms - 1500f) / 500f).coerceIn(0f, 1f)
                },
            )
            Spacer(Modifier.height(16.dp))
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                ThinProgress(progresso, Rotulo(spaceWord(progresso)), null, reduceMotion)
            }
        }
    }
}
