package app.astra.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Estado vazio do palco (nada selecionado): glifo celeste com estrelas orbitando
// (calmo, discreto — nao briga com nada) + legenda serifada contextual + dica do
// atalho. Reduzir movimento -> orbita congela num quadro bonito. A fase e lida
// DENTRO do draw (State.value no lambda do Canvas) -> invalida so o desenho, sem
// recompor a arvore a cada frame.
@Composable
fun EmptyStage(isServer: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OrbitGlyph()
            Spacer(Modifier.height(22.dp))
            Text(
                if (isServer) "escolha uma órbita" else "escolha um sussurro",
                style = TextStyle(color = Obsidian.text2, fontSize = 16.sp, fontFamily = DmSerif),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                if (isServer) "pra abrir a conversa" else "ou comece uma nova",
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
            )
            Spacer(Modifier.height(20.dp))
            KbdHint(if (isServer) "pra pular entre órbitas" else "pra pular entre sussurros")
        }
    }
}

@Composable
private fun OrbitGlyph() {
    val accent = Obsidian.accent
    val twoPi = (2.0 * PI).toFloat()
    val count = 12
    val tilt = (-12.0 * PI / 180.0).toFloat()
    val reduceMotion = LocalReduceMotion.current
    // reduceMotion: fase fixa (orbita parada, mas ainda com frente/tras). Senao,
    // giro lento (~13s) — mais calmo que o gate (7s), combina com "idle".
    val phase: State<Float> = if (reduceMotion) {
        remember { mutableStateOf(0.9f) }
    } else {
        rememberInfiniteTransition(label = "emptyOrbit").animateFloat(
            initialValue = 0f,
            targetValue = twoPi,
            animationSpec = infiniteRepeatable(tween(13000, easing = LinearEasing)),
            label = "emptyPhase",
        )
    }

    // Um lado do anel (elipse achatada): sin(theta) > 0 = perto (frente), <= 0 =
    // longe (atras). Paralaxe: perto = maior e mais claro; longe = menor e apagado.
    fun DrawScope.drawOrbit(ph: Float, front: Boolean) {
        val half = size.minDimension / 2f
        val rx = half * 0.90f
        val ry = half * 0.32f
        repeat(count) { i ->
            val theta = ph + i * (twoPi / count)
            val depth = sin(theta)
            if ((depth > 0f) != front) return@repeat
            val ex = rx * cos(theta)
            val ey = ry * depth
            val p = Offset(
                center.x + ex * cos(tilt) - ey * sin(tilt),
                center.y + ex * sin(tilt) + ey * cos(tilt),
            )
            val t01 = (depth + 1f) / 2f
            drawCircle(
                color = accent.copy(alpha = 0.10f + 0.42f * t01),
                radius = (1.0f + 1.7f * t01).dp.toPx(),
                center = p,
            )
        }
    }

    Box(Modifier.size(118.dp), contentAlignment = Alignment.Center) {
        // atras -> glifo central -> frente: as estrelas passam por tras e pela
        // frente do "sol" central, dando profundidade.
        Canvas(Modifier.size(118.dp)) { drawOrbit(phase.value, front = false) }
        Text("✦", style = TextStyle(color = accent.copy(alpha = 0.55f), fontSize = 24.sp))
        Canvas(Modifier.size(118.dp)) { drawOrbit(phase.value, front = true) }
    }
}

// Vazio simples e consistente (mesmo tom do palco, sem animacao): ✦ discreto +
// linha. Pra listas menores (sussurros, amigos, descobrir).
@Composable
fun EmptyHint(text: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("✦", style = TextStyle(color = Obsidian.borderMid, fontSize = 22.sp))
        Spacer(Modifier.height(8.dp))
        Text(text, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
    }
}

// Dica discreta do atalho: chip "Ctrl+K" em mono + legenda.
@Composable
private fun KbdHint(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Ctrl+K",
            style = TextStyle(color = Obsidian.text2, fontSize = 11.sp, fontFamily = DmMono),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Obsidian.raised)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
    }
}
