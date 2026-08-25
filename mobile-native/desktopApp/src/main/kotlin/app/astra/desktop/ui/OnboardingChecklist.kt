package app.astra.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text

@Composable
fun AvisoDeMaquinaEconomica(motivo: String, aoDispensar: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.overlay.copy(alpha = 0.96f))
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "✦ ajustado a este computador",
                style = TextStyle(color = Obsidian.accent, fontSize = 14.sp, fontFamily = DmSerif),
                modifier = Modifier.weight(1f),
            )
            val src = remember { MutableInteractionSource() }
            Text(
                "entendi",
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                modifier = Modifier.clickable(interactionSource = src, indication = null, onClick = aoDispensar),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Encontrei $motivo, então o Astra começou no modo econômico: fundo parado e " +
                "menos animação. Para ligar tudo, vá em Configurações › Desempenho.",
            style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp),
        )
    }
}

@Composable
fun FirstStepsCard(
    hasServer: Boolean,
    hasDm: Boolean,
    hasAvatar: Boolean,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.overlay.copy(alpha = 0.96f))
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "✦ primeiros passos",
                style = TextStyle(color = Obsidian.accent, fontSize = 14.sp, fontFamily = DmSerif),
                modifier = Modifier.weight(1f),
            )
            val src = remember { MutableInteractionSource() }
            Text(
                "pular",
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                modifier = Modifier.clickable(interactionSource = src, indication = null, onClick = onDismiss),
            )
        }
        Spacer(Modifier.height(13.dp))
        StepRow(hasAvatar, "escolha sua foto de perfil")
        Spacer(Modifier.height(10.dp))
        StepRow(hasServer, "crie ou entre numa constelação — no + da lateral")
        Spacer(Modifier.height(10.dp))
        StepRow(hasDm, "mande seu primeiro sussurro")
    }
}

@Composable
private fun StepRow(done: Boolean, label: String) {
    val fill by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = tween(if (LocalReduceMotion.current) 0 else 260),
        label = "stepFill",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(18.dp)) {
                val r = size.minDimension / 2f
                drawCircle(color = Obsidian.borderMid, radius = r, style = Stroke(1.4.dp.toPx()))
                if (fill > 0f) drawCircle(color = Obsidian.success.copy(alpha = fill), radius = r * fill)
            }
            if (fill > 0.6f) Text("✓", style = TextStyle(color = Obsidian.void, fontSize = 10.sp))
        }
        Spacer(Modifier.width(11.dp))
        Text(
            label,
            style = TextStyle(color = if (done) Obsidian.text3 else Obsidian.text2, fontSize = 12.sp),
        )
    }
}
