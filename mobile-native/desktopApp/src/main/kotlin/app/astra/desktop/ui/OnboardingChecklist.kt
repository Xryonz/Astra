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

// O ASTRA SE AJUSTOU À MÁQUINA — e este cartão é a metade "e diz que fez".
//
// Ajustar em silêncio seria mais limpo de programar e pior de usar: a pessoa com um
// computador apertado ganharia um app econômico sem saber que existe um bonito
// esperando por ela, e concluiria que o Astra é feio de fábrica. Pior ainda no sentido
// contrário — quem tem 4 GB porque o pente queimou não entenderia por que o fundo
// mudou.
//
// O cartão traz A MEDIDA ("3,9 GB de memória"), não a conclusão. Mostrar o que foi
// visto na máquina se defende sozinho; só afirmar "achamos melhor" vira desconfiança.
//
// Some no "entendi" — e some SÓ o cartão: o modo econômico continua ligado, porque
// dispensar um aviso não é discordar dele. Desligar de verdade é em Desempenho, e o
// texto diz onde.
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

// Metade "checklist" do onboarding (combo): cartao flutuante no rodape do palco
// vazio, so pra quem acabou de passar pelo takeover (Main liga a pref
// "checklist:<userId>"). Risca sozinho conforme o usuário cumpre cada passo — os
// estados vem da state do shell (servers/dms/avatar), não ha rastreio proprio.
// Some ao completar os dois passos-nucleo (constelação + sussurro) ou no "pular".
@Composable
fun FirstStepsCard(
    hasServer: Boolean,
    hasDm: Boolean,
    hasAvatar: Boolean,
    onDismiss: () -> Unit,
) {
    // Largura livre: mora na barra lateral (260dp), não mais solto sobre o palco.
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
    // O anel se preenche (verde) quando o passo e cumprido — mesma linguagem das
    // regras do login. Animado, respeita reduzir movimento.
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
