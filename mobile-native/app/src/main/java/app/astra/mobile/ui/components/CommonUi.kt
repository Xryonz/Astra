package app.astra.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Avatar circular com ring sutil; fallback = inicial em prata. */
@Composable
fun AstraAvatar(url: String?, name: String, modifier: Modifier = Modifier, size: Int = 46) {
    val mod = modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(astraColors.raised)
        .border(1.dp, astraColors.borderMid, CircleShape)
    if (!url.isNullOrBlank()) {
        AsyncImage(model = url, contentDescription = null, modifier = mod, contentScale = ContentScale.Crop)
    } else {
        Box(mod, contentAlignment = Alignment.Center) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.accent,
            )
        }
    }
}

// ── Constelacao animada — 7 estrelas ligadas por linha que se "acende" em
//    sequencia (espelha ConstellationEmpty do web). A linha desenha
//    progressivamente via PathMeasure; as estrelas dao pop escalonado. ──
private val ConstellationStars = listOf(
    Offset(20f, 75f), Offset(50f, 40f), Offset(80f, 55f), Offset(110f, 25f),
    Offset(125f, 65f), Offset(95f, 85f), Offset(55f, 80f),
)
private val ConstellationRadii = listOf(2.0f, 2.5f, 1.8f, 2.2f, 1.8f, 2.0f, 1.5f)
// Ordem da polilinha: percorre as estrelas e fecha de volta na primeira.
private val ConstellationPath = listOf(0, 1, 2, 3, 4, 5, 6, 0)

/** Grafico de constelacao (140x100). Anima 1x ao aparecer. */
@Composable
fun ConstellationGraphic(modifier: Modifier = Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1800, easing = EaseOutSoft)) }
    val accent = astraColors.accent
    Canvas(modifier.size(width = 140.dp, height = 100.dp)) {
        val s = size.width / 140f
        fun at(o: Offset) = Offset(o.x * s, o.y * s)

        val path = Path()
        ConstellationPath.forEachIndexed { i, idx ->
            val pt = at(ConstellationStars[idx])
            if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val seg = Path()
        measure.getSegment(0f, measure.length * p.value, seg, true)
        drawPath(seg, accent, alpha = 0.4f, style = Stroke(width = s, cap = StrokeCap.Round))

        ConstellationStars.forEachIndexed { i, o ->
            val local = ((p.value - (0.12f + i * 0.10f)) / 0.20f).coerceIn(0f, 1f)
            if (local > 0f) {
                drawCircle(accent, ConstellationRadii[i] * s * 1.4f * local, at(o), alpha = local)
            }
        }
    }
}

/** Estado vazio editorial: constelacao + linha serif + dica marginalia. */
@Composable
fun EmptyState(line: String, hint: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ConstellationGraphic()
            Spacer(Modifier.height(14.dp))
            Text(
                text = line,
                style = MaterialTheme.typography.titleLarge,
                color = astraColors.text2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            MarginaliaLabel(hint)
        }
    }
}

/**
 * Loader orbital — 3 dots brilhantes em orbita de um centro vazio
 * (espelha o OrbitSpinner do web; substitui o antigo CircularProgressIndicator).
 * Tudo num Canvas: 6 draws/frame, compositor-friendly.
 */
@Composable
fun CosmicSpinner(modifier: Modifier = Modifier, diameter: Int = 30) {
    val inf = rememberInfiniteTransition(label = "orbit")
    val angle by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "angle",
    )
    val accent = astraColors.accent
    Canvas(modifier.size(diameter.dp)) {
        val r = size.minDimension / 2f - 3.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        val dot = (diameter / 13f).dp.toPx().coerceAtLeast(2.dp.toPx())
        for (i in 0..2) {
            val a = angle + i * (2f * PI.toFloat() / 3f)
            val pt = Offset(c.x + r * cos(a), c.y + r * sin(a))
            drawCircle(accent, dot * 2.4f, pt, alpha = 0.18f) // glow
            drawCircle(accent, dot, pt)
        }
    }
}
