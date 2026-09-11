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
import androidx.compose.foundation.layout.widthIn
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

private const val VELOCIDADE_DO_PROGRESSO = 5f
private const val CHEGADA = 0.002f
private const val ENTRADA_MS = 1100
private const val ESPERA_PARA_CURIOSIDADE_MS = 1_400L
private const val MINIMO_NA_TELA_MS = 2_500L
private const val PASSEIO_DA_ENTRADA = 0.18f

@Composable
fun TelaDeCarregamento(reduceMotion: Boolean, aoTerminar: () -> Unit) {
    var comecouAAparecer by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        comecouAAparecer = System.nanoTime()
    }

    var aquecido by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(aquecido, comecouAAparecer) {
        if (!aquecido || comecouAAparecer == 0L) return@LaunchedEffect
        val falta = MINIMO_NA_TELA_MS - (System.nanoTime() - comecouAAparecer) / 1_000_000
        if (falta > 0) kotlinx.coroutines.delay(falta)
        aoTerminar()
    }

    var alvo by remember { mutableStateOf(0f) }
    var progresso by remember { mutableStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(comecouAAparecer) {
        if (comecouAAparecer != 0L && alvo == 0f) alvo = PASSEIO_DA_ENTRADA
    }
    androidx.compose.runtime.LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            androidx.compose.runtime.snapshotFlow { alvo }.collect { progresso = it }
            return@LaunchedEffect
        }
        var anterior = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { agora ->
                val passo = if (anterior == 0L) 0f else (agora - anterior) / 1_000_000_000f
                anterior = agora
                val falta = alvo - progresso
                val fatia = (passo * VELOCIDADE_DO_PROGRESSO).coerceIn(0f, 1f)
                progresso = if (falta < CHEGADA) alvo else progresso + falta * fatia
            }
        }
    }

    var curiosidade by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(comecouAAparecer) {
        if (comecouAAparecer == 0L) return@LaunchedEffect
        kotlinx.coroutines.delay(ESPERA_PARA_CURIOSIDADE_MS)
        curiosidade = CURIOSIDADES_DO_ESPACO.random()
    }

    val entrada: State<Float>? = if (reduceMotion) null else {
        animateFloatAsState(
            targetValue = if (comecouAAparecer != 0L) 1f else 0f,
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
        val entradaTerminou = (entrada?.value ?: 1f) >= 1f
        if (comecouAAparecer != 0L && entradaTerminou) {
            Aquecimento(aoAvancar = { alvo = PASSEIO_DA_ENTRADA + (1f - PASSEIO_DA_ENTRADA) * it }, aoTerminar = { aquecido = true })
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 26.dp, vertical = 24.dp)
                .fillMaxWidth(0.66f)
                .widthIn(max = 560.dp),
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
                ThinProgress(
                    progress = progresso,
                    rotulo = Rotulo(curiosidade ?: spaceWord(progresso)),
                    percent = null,
                    reduceMotion = reduceMotion,
                    linhas = if (curiosidade != null) 2 else 1,
                )
            }
        }
    }
}
