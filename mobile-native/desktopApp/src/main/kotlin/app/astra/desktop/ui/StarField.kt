package app.astra.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.Obsidian
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Campo de estrelas portado do mobile (StarField.kt do :app): estrelas fixas +
// piscar + meteoros ("estrelas caindo"). Fica entre a aurora e os paineis, sutil.
// DROPADO do mobile: tilt (acelerometro, nao existe no desktop). MANTIDO: o
// relogio com PAUSA sem foco (guardrail de perf) e o respeito ao reduzir
// movimento (LocalReduceMotion) — congela num campo estatico, sem meteoros.

private fun lcg(seed: Int): () -> Float {
    var s = seed
    return {
        s = (s * 1103515245 + 12345) and 0x7fffffff
        s / 0x7fffffff.toFloat()
    }
}

private class BgStar(val x: Float, val y: Float, val r: Float)
private class Twinkle(val x: Float, val y: Float, val r: Float, val freq: Float, val phase: Float)
private class Meteor(val startX: Float, val stagger: Float, val mx: Float)

private val BG_STARS: List<BgStar> = buildList {
    val rnd = lcg(424242)
    repeat(70) { add(BgStar(rnd(), rnd(), if (rnd() > 0.5f) 1.4f else 1.0f)) }
}
private val TWINKLES: List<Twinkle> = buildList {
    val rnd = lcg(99883)
    repeat(14) {
        add(Twinkle(rnd(), rnd(), 1.5f + rnd() * 1.0f, (1 + (rnd() * 3f).toInt()).toFloat(), rnd() * 6.2832f))
    }
}
private val METEORS: List<Meteor> = buildList {
    val rnd = lcg(13579)
    repeat(3) { i -> add(Meteor(0.3f + rnd() * 0.6f, i / 3f, -(0.25f + rnd() * 0.45f))) }
}

// Periodos das camadas (segundos): drift orbital lento, ciclo do piscar, ciclo
// dos meteoros. Um relogio unico alimenta os tres.
private const val DRIFT_PERIOD = 90f
private const val TWINKLE_PERIOD = 5f
private const val METEOR_PERIOD = 24f
private const val STAR_LOOP = 360f // multiplo dos tres periodos: enrola sem salto

@Composable
fun StarField(modifier: Modifier = Modifier, color: Color = Obsidian.accent) {
    val reduceMotion = LocalReduceMotion.current
    val windowInfo = LocalWindowInfo.current
    // Mesmo relogio da aurora: pausa sem foco; reduzir movimento congela em 0
    // (campo estatico, sem drift/piscar/meteoros).
    val time by produceState(0f, windowInfo, reduceMotion) {
        if (reduceMotion) {
            value = 0f
            return@produceState
        }
        var acc = 0f
        while (true) {
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            var last = withFrameNanos { it }
            while (windowInfo.isWindowFocused) {
                withFrameNanos { now ->
                    acc += (now - last) / 1_000_000_000f
                    if (acc >= STAR_LOOP) acc -= STAR_LOOP
                    last = now
                    value = acc
                }
            }
        }
    }

    // Resolucao cheia: estrelas sao pontos nitidos e baratos (~90 ops/frame);
    // meia-res (truque da aurora) so serve pra shader caro por pixel, aqui borra.
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val drift = time * (2f * PI.toFloat() / DRIFT_PERIOD)
        val dx = sin(drift) * 14.dp.toPx()
        val dy = (cos(drift) - 1f) * 9.dp.toPx()

        BG_STARS.forEach { s ->
            drawCircle(color, s.r.dp.toPx(), Offset(s.x * w + dx, s.y * h + dy), alpha = 0.34f)
        }

        val tau = time * (2f * PI.toFloat() / TWINKLE_PERIOD)
        TWINKLES.forEach { t ->
            val a = 0.25f + 0.7f * ((sin(tau * t.freq + t.phase) + 1f) / 2f)
            val c = Offset(t.x * w + dx, t.y * h + dy)
            drawCircle(color, t.r.dp.toPx() * 2.4f, c, alpha = a * 0.16f) // brilho
            drawCircle(color, t.r.dp.toPx(), c, alpha = a)
        }

        // Meteoros so quando ha movimento (reduzir movimento = campo parado).
        if (!reduceMotion) {
            val meteorT = (time % METEOR_PERIOD) / METEOR_PERIOD
            METEORS.forEach { m ->
                val local = (meteorT + m.stagger) % 1f
                if (local < 0.12f) {
                    val p = local / 0.12f
                    val headX = (m.startX + m.mx * p) * w
                    val headY = (-0.1f + 1.2f * p) * h
                    val tailX = headX - m.mx * 0.06f * w
                    val tailY = headY - 0.06f * h
                    val alpha = sin(p * PI).toFloat().coerceIn(0f, 1f)
                    drawLine(color, Offset(tailX, tailY), Offset(headX, headY), 1.5.dp.toPx(), StrokeCap.Round, alpha = alpha * 0.7f)
                    drawCircle(color, 2.2.dp.toPx(), Offset(headX, headY), alpha = alpha)
                }
            }
        }
    }
}
