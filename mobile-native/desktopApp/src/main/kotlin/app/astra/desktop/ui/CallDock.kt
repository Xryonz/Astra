package app.astra.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.voice.VoiceEngine
import app.astra.desktop.voice.VoiceStatus
import app.astra.mobile.core.network.dto.ChannelDto
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize2
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.MicOff
import com.composables.icons.lucide.PhoneOff
import kotlin.math.roundToInt

// Card flutuante da call — aparece quando voce esta conectado mas navegou pra
// outra tela. Espelha o VoiceCallPanel do web (arrastavel, avatares de quem
// fala, mic/desligar, botao de voltar pro palco) com tres cortes de custo que
// pesavam la:
//
//  1. O web roda uma animacao infinita POR PARTICIPANTE (o anel de quem fala) e
//     mais uma no ponto pulsante. Aqui existe UMA transicao infinita so, e o
//     valor dela e lido dentro do drawBehind — muda o desenho, nao a composicao.
//  2. O anel de "falando" e pintado (drawCircle no draw scope), nao um layout a
//     mais por avatar entrando e saindo da arvore.
//  3. Parado (ninguem falando) NENHUM frame e pedido: a transicao infinita so e
//     criada quando ha alguem falando, e some junto.
//
// Arrastar guarda a posicao em memoria (nao persiste entre sessoes de propósito:
// menos I/O e o card sempre volta pro canto conhecido ao reabrir o app).
@Composable
fun BoxScope.CallDock(
    channel: ChannelDto,
    engine: VoiceEngine,
    onExpand: () -> Unit,
    onLeave: () -> Unit,
) {
    val status by engine.status.collectAsState()
    val micOn by engine.micOn.collectAsState()

    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }

    val connected = status as? VoiceStatus.Connected
    val speakers = connected?.others?.filter { it.speaking }.orEmpty()
    val anySpeaking = speakers.isNotEmpty() || connected?.mySpeaking == true
    val count = (connected?.others?.size ?: 0) + if (connected != null) 1 else 0

    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 18.dp, bottom = 18.dp)
            .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
            .width(232.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Obsidian.overlay.copy(alpha = 0.94f))
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    dx += drag.x
                    dy += drag.y
                }
            },
    ) {
        Column {
            // Cabecalho: ponto vivo + de onde vem o audio + quantos estao na sala.
            Row(
                Modifier.fillMaxWidth().padding(start = 11.dp, end = 7.dp, top = 9.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveDot(anySpeaking)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            status is VoiceStatus.Connecting -> "conectando…"
                            status is VoiceStatus.Failed -> "sinal caiu"
                            speakers.isNotEmpty() -> "${speakers.first().label} falando"
                            else -> channel.name
                        },
                        style = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (connected != null) "$count na sala" else "…",
                        style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(6.dp))
                DockIcon(Lucide.Maximize2, tint = Obsidian.text2, onClick = onExpand)
            }
            HairRule()
            // Acoes: calar/abrir mic e desligar. Desligar e a UNICA saida da call
            // agora que navegar nao desconecta mais.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockAction(
                    icon = if (micOn) Lucide.Mic else Lucide.MicOff,
                    tint = if (micOn) Obsidian.text1 else Obsidian.danger,
                    bg = if (micOn) Obsidian.raised else Obsidian.danger.copy(alpha = 0.16f),
                    onClick = engine::toggleMic,
                )
                Spacer(Modifier.width(10.dp))
                DockAction(
                    icon = Lucide.PhoneOff,
                    tint = Obsidian.textInv,
                    bg = Obsidian.danger,
                    onClick = onLeave,
                )
            }
        }
    }
}

// Ponto de "ao vivo". Pulsa SO enquanto alguem fala; em silencio e um circulo
// estatico e nenhum frame e pedido (o web pulsa pra sempre).
@Composable
private fun LiveDot(active: Boolean) {
    if (!active) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Obsidian.success))
        return
    }
    val t = rememberInfiniteTransition(label = "dock-live")
    val p by t.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
        label = "dock-pulse",
    )
    // Le `p` dentro do draw: pulsa sem recompor a linha inteira do cabecalho.
    Box(
        Modifier.size(12.dp).drawBehind {
            drawCircle(Obsidian.success.copy(alpha = 0.22f * p), radius = size.minDimension / 2f * p)
            drawCircle(Obsidian.success, radius = 4.dp.toPx())
        },
    )
}

@Composable
private fun DockIcon(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) Obsidian.hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, tint = if (hovered) Obsidian.text1 else tint, size = 13.dp)
    }
}

@Composable
private fun DockAction(icon: ImageVector, tint: Color, bg: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val s by animateFloatAsState(if (hovered) 1.08f else 1f, tween(120), label = "dockAct")
    Box(
        Modifier
            .scale(s)
            .size(34.dp)
            .clip(CircleShape)
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, tint = tint, size = 15.dp)
    }
}
