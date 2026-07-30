package app.astra.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import app.astra.desktop.ui.theme.Obsidian
import kotlin.math.sin

// "Constelação conectada": decoracao do vao do header quando ha uma constelação
// selecionada. Nos-estrela em alturas variadas, ligados por linhas finas (o idioma do
// mapa do tesouro), piscando fora de fase e derivando devagar. O último no encosta na
// borda direita — onde fica o botao de membros, que FECHA a constelação. Fica ATRAS do
// titulo (alpha baixo, não briga com o texto). Congela ao reduzir-movimento ou janela
// inativa (guardrail de frame: withFrameNanos para de vir quando ocluida).
//
// Layout fixo dos nos: fx (0..1 no trecho direito do vao), fy (0..1 vertical), fase
// (deslocamento do brilho/bob pra cada estrela cintilar no seu tempo).
private val NODES = floatArrayOf(
    0.00f, 0.55f, 0.0f,
    0.17f, 0.30f, 1.3f,
    0.32f, 0.64f, 2.1f,
    0.48f, 0.38f, 3.4f,
    0.63f, 0.68f, 4.2f,
    0.79f, 0.32f, 5.0f,
    1.00f, 0.50f, 5.8f,
)

@Composable
internal fun ConstellationRoute(modifier: Modifier) {
    val reduce = LocalReduceMotion.current
    val active = LocalWindowActive.current
    val accent = Obsidian.accent
    val t = if (reduce || !active) 0f else {
        val inf = rememberInfiniteTransition(label = "constRoute")
        val v by inf.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
            label = "drift",
        )
        v
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val x0 = w * 0.42f // deixa o titulo (a esquerda) respirar
        val span = w - x0
        if (span < 12f || h < 6f) return@Canvas
        val top = h * 0.24f
        val vspan = h * 0.52f
        val bob = (h * 0.06f).coerceIn(1.5f, 5f)
        val n = NODES.size / 3
        val pts = Array(n) { i ->
            val fx = NODES[i * 3]
            val fy = NODES[i * 3 + 1]
            val ph = NODES[i * 3 + 2]
            val x = x0 + span * fx
            val y = top + vspan * fy + bob * sin(t * 6.2832f + ph)
            Offset(x, y)
        }
        // linhas ligando os nos consecutivos = a "constelação"
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, color = accent.copy(alpha = 0.16f), style = Stroke(width = 1f, cap = StrokeCap.Round))
        // nos-estrela piscando (halo suave + nucleo); glint em cruz quando no pico do brilho
        for (i in 0 until n) {
            val ph = NODES[i * 3 + 2]
            val a = (0.32f + 0.24f * sin(t * 6.2832f * 1.6f + ph)).coerceIn(0.12f, 0.62f)
            val p = pts[i]
            drawCircle(accent.copy(alpha = a * 0.35f), radius = 3.2f, center = p)
            drawCircle(accent.copy(alpha = a), radius = 1.5f, center = p)
            if (a > 0.5f) {
                val g = (a - 0.5f) * 1.4f
                val r = 3.6f
                drawLine(accent.copy(alpha = g), Offset(p.x - r, p.y), Offset(p.x + r, p.y), strokeWidth = 0.8f, cap = StrokeCap.Round)
                drawLine(accent.copy(alpha = g), Offset(p.x, p.y - r), Offset(p.x, p.y + r), strokeWidth = 0.8f, cap = StrokeCap.Round)
            }
        }
    }
}
