package app.astra.desktop.ui

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.dto.ChannelMessageDto
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import org.koin.core.context.GlobalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AlfineteDoCanal(channelId: String) {
    var aberto by remember(channelId) { mutableStateOf(false) }
    val api = remember { GlobalContext.get().get<ChannelApi>() }
    val socket = remember { GlobalContext.get().get<DesktopSocket>() }
    var fixadas by remember(channelId) { mutableStateOf<List<ChannelMessageDto>?>(null) }

    LaunchedEffect(channelId, aberto) {
        if (!aberto) return@LaunchedEffect
        fixadas = runCatching { api.pinned(channelId).data.orEmpty() }.getOrNull()
        socket.messagePinned.collect {
            fixadas = runCatching { api.pinned(channelId).data.orEmpty() }.getOrNull()
        }
    }

    Box {
        val interacao = remember { MutableInteractionSource() }
        val hover by interacao.collectIsHoveredAsState()
        Box(
            Modifier
                .size(28.dp)
                .clickScale(interacao)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    1.dp,
                    if (hover || aberto) Obsidian.accentDim else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .hoverable(interacao)
                .clickable(interactionSource = interacao, indication = null) { aberto = !aberto },
            contentAlignment = Alignment.Center,
        ) {
            LIcon(
                Lucide.Pin,
                tint = if (hover || aberto) Obsidian.accent else Obsidian.text3,
                size = 15.dp,
                rotulo = "mensagens fixadas",
            )
        }

        if (aberto) {
            Popup(
                popupPositionProvider = AbaixoPelaDireita,
                onDismissRequest = { aberto = false },
                properties = PopupProperties(focusable = true),
            ) {
                PopupReveal(originX = 1f, originY = 0f) {
                    ListaDeFixadas(fixadas) { aberto = false }
                }
            }
        }
    }
}

@Composable
private fun ListaDeFixadas(fixadas: List<ChannelMessageDto>?, aoEscolher: () -> Unit) {
    val pulo = LocalPuloParaMensagem.current
    Column(
        Modifier
            .widthIn(min = 300.dp, max = 380.dp)
            .heightIn(max = 420.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp),
    ) {
        Text(
            "mensagens fixadas",
            style = Tipo.nota,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
        when {
            fixadas == null -> Aviso("Buscando o que foi fixado por aqui.")
            fixadas.isEmpty() -> Aviso("Nada fixado neste canal ainda.")
            else -> LazyColumn(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                items(fixadas, key = { it.id }) { msg ->
                    LinhaFixada(
                        msg = msg,
                        alcancavel = pulo.estaCarregada(msg.id),
                        aoTocar = { pulo.pular(msg.id); aoEscolher() },
                    )
                }
            }
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        texto,
        style = Tipo.apoio,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun LinhaFixada(msg: ChannelMessageDto, alcancavel: Boolean, aoTocar: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    val hover by interacao.collectIsHoveredAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (alcancavel) Modifier.clickScale(interacao) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hover && alcancavel) Obsidian.hover else Obsidian.overlay)
            .hoverable(interacao, enabled = alcancavel)
            .clickable(
                interactionSource = interacao,
                indication = null,
                enabled = alcancavel,
                onClick = aoTocar,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                msg.author?.displayName ?: msg.author?.username ?: "Alguém",
                style = Tipo.rotulo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(horaCurta(msg.createdAt), style = Tipo.nota)
        }
        if (msg.content.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                msg.content,
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!alcancavel) {
            Spacer(Modifier.height(4.dp))
            Text("role o histórico para alcançar", style = Tipo.nota)
        }
    }
}

private val HORA_CURTA = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun horaCurta(iso: String?): String =
    iso?.let { runCatching { HORA_CURTA.format(Instant.parse(it)) }.getOrNull() } ?: ""

private object AbaixoPelaDireita : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val abaixo = anchorBounds.bottom + 6
        val y = if (abaixo + popupContentSize.height <= windowSize.height) abaixo
        else (anchorBounds.top - popupContentSize.height - 6).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
