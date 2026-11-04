package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.shell.ChatMessage
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Lucide
import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin

private val LARGURA_DO_TRACO = 28.dp

@Composable
internal fun SeparadorDoChat(rotulo: String) {
    val traco = Obsidian.borderDim.copy(alpha = 0.7f)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(LARGURA_DO_TRACO).height(1.dp).background(traco))
        Text(rotulo, style = Tipo.nota, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.width(LARGURA_DO_TRACO).height(1.dp).background(traco))
    }
}

@Composable
internal fun MarcaDeNovasMensagens() {
    val accent = Obsidian.accent
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(28.dp).height(1.dp).background(accent.copy(alpha = 0.45f)))
        Text(
            "novas mensagens",
            style = Tipo.nota.copy(color = accent),
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Box(Modifier.width(28.dp).height(1.dp).background(accent.copy(alpha = 0.45f)))
    }
}

@Composable
internal fun VoltarAoPresente(
    visivel: Boolean,
    novas: Int,
    modifier: Modifier = Modifier,
    aoTocar: () -> Unit,
) {
    val reduzir = LocalReduceMotion.current
    var congelada by remember { mutableIntStateOf(novas) }
    LaunchedEffect(visivel, novas) { if (visivel) congelada = novas }
    val exibidas = if (visivel) novas else congelada
    AnimatedVisibility(
        visible = visivel,
        enter = fadeIn(tween(if (reduzir) 0 else 120)) +
            slideInVertically(tween(if (reduzir) 0 else 140, easing = EaseOutStd)) { it / 2 },
        exit = fadeOut(tween(if (reduzir) 0 else 90)),
        modifier = modifier,
    ) {
        val fonte = remember { MutableInteractionSource() }
        val hover by fonte.collectIsHoveredAsState()
        Row(
            Modifier
                .clickScale(fonte)
                .clip(RoundedCornerShape(8.dp))
                .background(if (hover) Obsidian.hover else Obsidian.overlay)
                .border(1.dp, Obsidian.borderMid.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .hoverable(fonte)
                .clickable(interactionSource = fonte, indication = null, onClick = aoTocar)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LIcon(Lucide.ChevronDown, tint = if (exibidas > 0) Obsidian.accent else Obsidian.text2, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                when (exibidas) {
                    0 -> "voltar ao presente"
                    1 -> "1 nova mensagem"
                    else -> "$exibidas novas mensagens"
                },
                style = Tipo.rotulo,
            )
        }
    }
}

private fun instanteDe(m: ChatMessage): Instant? =
    m.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() }

internal fun instanteDaMaisRecente(mensagens: List<ChatMessage>): Instant? =
    mensagens.lastOrNull { !it.pending && !it.failed }?.let(::instanteDe)

internal fun contarNovas(mensagens: List<ChatMessage>, vista: Instant?): Int {
    if (vista == null) return 0
    var novas = 0
    for (i in mensagens.indices.reversed()) {
        val m = mensagens[i]
        if (m.pending || m.failed) continue
        val quando = instanteDe(m) ?: continue
        if (!quando.isAfter(vista)) break
        if (!m.mine && m.kind != KIND_PASSAGEM_DE_TURNO) novas++
    }
    return novas
}

@Composable
internal fun EmptyChat() {
    val accent = Obsidian.accent
    val phase = if (LocalReduceMotion.current) {
        remember { mutableStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "emptyDots").animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing)),
            label = "dotsPhase",
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(width = 62.dp, height = 28.dp)) {
                val ph = phase.value
                val r = 4.5.dp.toPx()
                val gap = 16.dp.toPx()
                val baseY = size.height - r
                val startX = size.width / 2f - gap
                repeat(3) { i ->
                    val lift = sin(ph - i * 0.7f).coerceAtLeast(0f)
                    drawCircle(
                        color = accent.copy(alpha = 0.38f + 0.34f * lift),
                        radius = r * (0.88f + 0.16f * lift),
                        center = Offset(startX + i * gap, baseY - lift * 11.dp.toPx()),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "nenhuma mensagem ainda. Comece a conversa.",
                style = Tipo.descricao,
            )
        }
    }
}

@Composable
internal fun AcordandoOServidor() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TypingDots(Obsidian.accent)
            Spacer(Modifier.height(14.dp))
            Text(
                "o servidor está acordando",
                style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "isto pode levar até um minuto depois de um tempo parado",
                style = Tipo.apoio,
            )
        }
    }
}

@Composable
internal fun PalcoQueFalhou(motivo: String, podeTentar: Boolean, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.raised)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            LIcon(Lucide.CloudOff, tint = Obsidian.text3, size = 20.dp, rotulo = null)
            Spacer(Modifier.height(12.dp))
            Text(motivo, style = TextStyle(color = Obsidian.text2, fontSize = 13.sp))
            if (podeTentar) {
                Spacer(Modifier.height(14.dp))
                val src = remember { MutableInteractionSource() }
                Text(
                    "tentar de novo",
                    style = TextStyle(color = Obsidian.accent, fontSize = 12.sp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickScale(src)
                        .background(Obsidian.overlay)
                        .clickable(interactionSource = src, indication = null, onClick = onRetry)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}
