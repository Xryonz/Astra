package app.astra.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.astraColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── StarField — atmosfera cosmica em 3 camadas (espelha apps/web StarField) ──
//  Layer 1: 70 mini-estrelas com drift global lento (1 offset pra todas).
//  Layer 2: 14 estrelas que piscam (twinkle) seamless via sin(angulo).
//  Layer 3: meteoros raros caindo na diagonal esquerda-baixo.
// Tudo num unico Canvas — ~87 draws/frame, trivial pra GPU.

private fun lcg(seed: Int): () -> Float {
    var s = seed
    return {
        s = (s * 1103515245 + 12345) and 0x7fffffff
        s / 0x7fffffff.toFloat()
    }
}

private data class BgStar(val x: Float, val y: Float, val r: Float)
private data class Twinkle(val x: Float, val y: Float, val r: Float, val freq: Float, val phase: Float)
private data class MeteorDef(val startX: Float, val stagger: Float, val mx: Float)

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
private val METEORS: List<MeteorDef> = buildList {
    val rnd = lcg(13579)
    repeat(3) { i -> add(MeteorDef(0.3f + rnd() * 0.6f, i / 3f, -(0.25f + rnd() * 0.45f))) }
}

@Composable
fun StarField(modifier: Modifier = Modifier, color: Color = astraColors.accent) {
    val inf = rememberInfiniteTransition(label = "starfield")
    // Drift global elptico (90s). angulo 0..2pi -> sin/cos continuos no seam.
    val drift by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "drift",
    )
    // Twinkle: angulo 0..2pi (5s). freq inteiro -> loop sem salto.
    val tau by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(5_000, easing = LinearEasing)), label = "tau",
    )
    // Meteoros: 0..1 (24s). cada um visivel so 12% do proprio ciclo.
    val meteorT by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "meteor",
    )

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val dx = sin(drift) * 14.dp.toPx()
        val dy = (cos(drift) - 1f) * 9.dp.toPx()

        // Layer 1 — mini-stars
        BG_STARS.forEach { s ->
            drawCircle(color, s.r.dp.toPx(), Offset(s.x * w + dx, s.y * h + dy), alpha = 0.34f)
        }

        // Layer 2 — twinkles (glow + nucleo)
        TWINKLES.forEach { t ->
            val a = 0.25f + 0.7f * ((sin(tau * t.freq + t.phase) + 1f) / 2f)
            val c = Offset(t.x * w + dx, t.y * h + dy)
            drawCircle(color, t.r.dp.toPx() * 2.4f, c, alpha = a * 0.16f)
            drawCircle(color, t.r.dp.toPx(), c, alpha = a)
        }

        // Layer 3 — meteoros raros
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

/** Fundo cosmico reutilizavel: void + StarField atras do conteudo. */
@Composable
fun CosmicBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(astraColors.void)) {
        StarField()
        content()
    }
}
