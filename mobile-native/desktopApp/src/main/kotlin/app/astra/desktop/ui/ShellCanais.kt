package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.ChannelDto
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Volume2

@Composable
internal fun EditorialInputDialog(
    title: String,
    placeholder: String,
    initial: String,
    confirmLabel: String,
    channelType: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var type by remember { mutableStateOf("TEXT") }
    val valid = text.trim().isNotEmpty()
    val confirmar = {
        if (valid) {
            onConfirm(text.trim(), type)
            onDismiss()
        }
    }
    Dialogo(titulo = title, aoFechar = onDismiss) { fechar ->
        val focoInicial = rememberFocoAoAbrir()
        Spacer(Modifier.height(16.dp))
        CampoDeDialogo(
            text,
            { text = it.take(50) },
            dica = placeholder,
            aoEnviar = confirmar,
            foco = focoInicial,
            icone = if (channelType) Lucide.Hash else null,
        )
        if (channelType) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EscolhaDoDialogo("texto", type == "TEXT", { type = "TEXT" }, Lucide.Hash)
                EscolhaDoDialogo("voz", type == "VOICE", { type = "VOICE" }, Lucide.Volume2)
            }
        }
        RodapeDoDialogo {
            BotaoDeDialogo("cancelar", TipoDeBotao.SECUNDARIO, onClick = fechar)
            BotaoDeDialogo(confirmLabel, TipoDeBotao.PRINCIPAL, onClick = confirmar, ligado = valid)
        }
    }
}

@Composable
internal fun OrbitItem(
    ch: ChannelDto,
    active: Boolean,
    unread: Boolean,
    unreadCount: Int,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    dragCtx: ChannelDragCtx? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val isUnread = !active && unread
    val alvo = if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent
    val fundoAnimado by animateColorAsState(alvo, tween(120))
    val itemBg = if (hovered && !active) alvo else fundoAnimado
    val dSt = dragCtx?.state
    val lifted = dSt != null && dSt.dragging && dSt.id == ch.id
    Box(
        Modifier
            .fillMaxWidth()
            .channelDrag(ch, dragCtx)
            .graphicsLayer { alpha = if (lifted) 0.35f else 1f },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(itemBg)
                .hoverable(interaction)
                .clickable {
                    if (ch.type == "VOICE") onOpenVoice(ch)
                    else onOpenChat(ChatTarget.Channel(ch.id, ch.name))
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LIcon(
                if (ch.type == "VOICE") Lucide.Volume2 else Lucide.Hash,
                tint = if (ch.type == "VOICE") Obsidian.accent else Obsidian.text3,
                size = 15.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = ch.name,
                style = TextStyle(
                    color = if (active || hovered || isUnread) Obsidian.text1 else Obsidian.text2,
                    fontSize = 13.sp,
                    fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (ch.isPrivate) {
                Spacer(Modifier.width(6.dp))
                LIcon(Lucide.Lock, tint = Obsidian.text3, size = 11.dp, rotulo = "órbita privada")
            }
            if (!active && unreadCount > 0) {
                Spacer(Modifier.width(6.dp))
                UnreadCountBadge(unreadCount)
            }
        }
        if (isUnread) UnreadPill(ch.id, Modifier.align(Alignment.CenterStart), chave = unreadCount)
        if (dSt != null && dSt.dragging &&
            dSt.section == dragCtx.section && dSt.id != ch.id && dSt.targetIndex == dragCtx.index
        ) {
            Box(
                Modifier
                    .align(if (dSt.targetIndex > dSt.fromIndex) Alignment.BottomCenter else Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Obsidian.accent),
            )
        }
    }
}

@Composable
internal fun UnreadCountBadge(count: Int, destaque: Boolean = true) {
    Box(
        Modifier
            .height(18.dp)
            .widthIn(min = 18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (destaque) Color.White else Obsidian.raised)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = TextStyle(
                color = if (destaque) Color.Black else Obsidian.text2,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
internal fun UnreadPill(id: String, modifier: Modifier = Modifier, chave: Any? = Unit) {
    val glow = pulsoBreve(PREFIXO_DO_NAO_LIDO + id, chave, piso = 0.55f, meiaVoltaMs = 1400)
    Box(
        modifier
            .width(3.dp)
            .height(16.dp)
            .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
            .graphicsLayer { alpha = glow.value }
            .background(Obsidian.accent),
    )
}
