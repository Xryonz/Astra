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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import app.astra.desktop.ui.theme.Obsidian
import kotlin.math.sin

// "Rota estelar": decoracao do vao do header do palco. Trilha senoidal fininha,
// tracejada (o offset do dash anima = "rastejando"), com estrelinhas piscando ao
// longo. Desenhada so na METADE DIREITA do vao e encostando na borda direita, onde
// fica o botao de membros — ELE faz o papel da estrela do fim, entao NAO ha ✦ aqui
// (pedido do dono). Fica ATRAS do titulo (alpha baixo, nao briga com o texto).
// Para quando reduz-movimento ou a janela nao esta ativa (guardrail de frame).
@Composable
internal fun StarRoute(modifier: Modifier) {
    val reduce = LocalReduceMotion.current
    val active = LocalWindowActive.current
    val accent = Obsidian.accent
    val t = if (reduce || !active) 0f else {
        val inf = rememberInfiniteTransition(label = "starRoute")
        val v by inf.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
            label = "drift",
        )
        v
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val x0 = w * 0.42f // deixa o titulo (a esquerda) respirar
        val x1 = w          // encosta no botao de membros
        if (x1 - x0 < 8f || h < 4f) return@Canvas
        val midY = h / 2f
        val amp = (h * 0.14f).coerceIn(2f, 6f)
        val steps = 44
        val path = Path()
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val x = x0 + (x1 - x0) * f
            val y = midY + amp * sin(f * 6.2832f * 1.4f + t * 6.2832f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // dash com fase animada (negativa) = a trilha "rasteja" em direcao ao botao.
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.5f, 6.5f), phase = -t * 40f)
        drawPath(
            path,
            color = accent.copy(alpha = 0.30f),
            style = Stroke(width = 1.3f, pathEffect = dash, cap = StrokeCap.Round),
        )
        // estrelinhas ao longo da rota, piscando fora de fase entre si.
        val stars = 4
        for (i in 1..stars) {
            val f = i / (stars + 1f)
            val x = x0 + (x1 - x0) * f
            val y = midY + amp * sin(f * 6.2832f * 1.4f + t * 6.2832f)
            val a = (0.30f + 0.22f * sin(t * 6.2832f * 2f + i * 1.7f)).coerceIn(0.1f, 0.6f)
            drawCircle(accent.copy(alpha = a), radius = 1.3f, center = Offset(x, y))
        }
    }
}
