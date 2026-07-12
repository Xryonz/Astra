package app.astra.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
// Twinkle carrega tambem um WANDER proprio (orbita lenta e individual): e o que
// da a sensacao de "fundo vivo" no desktop, ja que o tilt do celular saiu.
// wSpeed em rad/s (periodo ~16..30s), fases desencontram o movimento de cada uma.
private class Twinkle(
    val x: Float, val y: Float, val r: Float, val freq: Float, val phase: Float,
    val wSpeed: Float, val wPhaseX: Float, val wPhaseY: Float,
)
private class Meteor(val startX: Float, val stagger: Float, val mx: Float)

private val BG_STARS: List<BgStar> = buildList {
    val rnd = lcg(424242)
    repeat(70) { add(BgStar(rnd(), rnd(), if (rnd() > 0.5f) 1.4f else 1.0f)) }
}
// Periodos do wander: SO divisores de STAR_LOOP (360) pra orbita fechar sem
// salto no wrap (18|20|24|30|36 -> 360/p inteiro). Lentos (18..36s) = calmo.
private val WANDER_PERIODS = floatArrayOf(18f, 20f, 24f, 30f, 36f)
private val TWINKLES: List<Twinkle> = buildList {
    val rnd = lcg(99883)
    repeat(14) {
        val p = WANDER_PERIODS[(rnd() * WANDER_PERIODS.size).toInt().coerceIn(0, WANDER_PERIODS.size - 1)]
        add(
            Twinkle(
                rnd(), rnd(), 1.5f + rnd() * 1.0f,
                (1 + (rnd() * 3f).toInt()).toFloat(), rnd() * 6.2832f,
                (2f * PI.toFloat()) / p, rnd() * 6.2832f, rnd() * 6.2832f,
            ),
        )
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
private const val STAR_LOOP = 360f // multiplo dos periodos (drift/twinkle/meteoro/wander): enrola sem salto
private const val WANDER_DP = 9f // amplitude da orbita individual das twinkles (perceptivel, calmo)

@Composable
fun StarField(modifier: Modifier = Modifier, color: Color = Obsidian.accent) {
    val reduceMotion = LocalReduceMotion.current
    // Mesmo relogio/gate da aurora: pausa quando minimizada (nao quando so perde
    // o foco pra um popup); reduzir movimento congela em 0 (campo estatico).
    val active = rememberUpdatedState(LocalWindowActive.current)
    // Teto de FPS (Settings > Desempenho): mesmo throttle da aurora — limita a
    // taxa de redesenho sem mexer na velocidade (acc acumula sempre).
    val fpsCap = rememberUpdatedState(LocalRenderPrefs.current.fpsCap)
    val time by produceState(0f, reduceMotion) {
        if (reduceMotion) {
            value = 0f
            return@produceState
        }
        var acc = 0f
        var lastEmit = 0L
        while (true) {
            snapshotFlow { active.value }.first { it }
            var last = withFrameNanos { it }
            while (active.value) {
                withFrameNanos { now ->
                    acc += (now - last) / 1_000_000_000f
                    if (acc >= STAR_LOOP) acc -= STAR_LOOP
                    last = now
                    val cap = fpsCap.value
                    val minInterval = if (cap <= 0) 0L else 1_000_000_000L / cap
                    if (minInterval == 0L || now - lastEmit >= minInterval) {
                        lastEmit = now
                        value = acc
                    }
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
        val wander = WANDER_DP.dp.toPx()
        TWINKLES.forEach { t ->
            val a = 0.25f + 0.7f * ((sin(tau * t.freq + t.phase) + 1f) / 2f)
            // Orbita propria (elipse): X e Y na mesma velocidade angular, fases
            // distintas -> cada estrela vagueia devagar num caminho unico.
            val wx = sin(time * t.wSpeed + t.wPhaseX) * wander
            val wy = cos(time * t.wSpeed + t.wPhaseY) * wander * 0.7f
            val c = Offset(t.x * w + dx + wx, t.y * h + dy + wy)
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
