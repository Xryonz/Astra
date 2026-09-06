package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.ServerDto
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Volume2

private data class QuickResult(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val voice: Boolean,
    val serverId: String?,
)

@Composable
internal fun CommandPalette(
    servers: List<ServerDto>,
    dms: List<ConversationDto>,
    onClose: () -> Unit,
    onOpenChannel: (serverId: String, channelId: String, name: String) -> Unit,
    onOpenDm: (convId: String, title: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(0) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }

    val all = remember(servers, dms) {
        buildList {
            servers.forEach { s ->
                s.channels.sortedBy { it.position }.forEach { c ->
                    add(QuickResult("channel", c.id, c.name, s.name, c.type == "VOICE", s.id))
                }
            }
            dms.forEach { d ->
                val t = d.otherUser?.displayName ?: d.otherUser?.username ?: "sussurro"
                add(QuickResult("dm", d.id, t, "sussurro", false, null))
            }
        }
    }
    val results = remember(all, query) {
        val q = query.trim()
        (if (q.isBlank()) all else all.filter { it.title.contains(q, true) || it.subtitle.contains(q, true) }).take(50)
    }
    LaunchedEffect(results.size) { if (sel >= results.size) sel = 0 }

    fun choose(r: QuickResult) {
        if (r.kind == "channel" && r.serverId != null) onOpenChannel(r.serverId, r.id, r.title)
        else onOpenDm(r.id, r.title)
        onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Obsidian.void.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .semCursorDeClique(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 96.dp)
                .width(520.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Escape -> { onClose(); true }
                        Key.DirectionDown -> { if (results.isNotEmpty()) sel = (sel + 1) % results.size; true }
                        Key.DirectionUp -> { if (results.isNotEmpty()) sel = (sel - 1 + results.size) % results.size; true }
                        Key.Enter -> { results.getOrNull(sel)?.let { choose(it) }; true }
                        else -> false
                    }
                }
                .padding(12.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it; sel = 0 },
                singleLine = true,
                textStyle = TextStyle(color = Obsidian.text1, fontSize = 15.sp),
                cursorBrush = SolidColor(Obsidian.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                        if (query.isEmpty()) {
                            Text(
                                "pular para um canal ou sussurro…",
                                style = TextStyle(color = Obsidian.text3, fontSize = 15.sp),
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            if (results.isEmpty()) {
                Text(
                    "nada encontrado",
                    style = Tipo.descricao,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(results, key = { _, r -> r.kind + r.id }) { i, r ->
                        PaletteRow(r, i == sel) { choose(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteRow(r: QuickResult, active: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Obsidian.active else if (hov) Obsidian.hover else Color.Transparent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (r.kind == "dm") {
            Text("@", style = TextStyle(color = if (active) Obsidian.accent else Obsidian.text3, fontSize = 14.sp))
        } else {
            LIcon(
                if (r.voice) Lucide.Volume2 else Lucide.Hash,
                tint = if (active) Obsidian.accent else Obsidian.text3,
                size = 15.dp,
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            r.title,
            style = Tipo.corpo,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(r.subtitle, style = Tipo.apoio, maxLines = 1)
    }
}
