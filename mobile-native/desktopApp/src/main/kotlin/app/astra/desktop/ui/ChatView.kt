package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.shell.ChatMessage
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.ChatVm
import app.astra.desktop.ui.theme.Obsidian
import zed.rainxch.rikkaui.components.ui.input.Input
import zed.rainxch.rikkaui.components.ui.input.InputAnimation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun hhmm(iso: String?): String =
    iso?.let { runCatching { HHMM.format(Instant.parse(it)) }.getOrNull() } ?: ""

// Mensagens do mesmo autor com menos de 5min entre si agrupam (compacto Discord).
private fun grouped(prev: ChatMessage?, cur: ChatMessage): Boolean {
    if (prev == null || prev.authorId != cur.authorId) return false
    val a = runCatching { Instant.parse(prev.createdAt) }.getOrNull() ?: return false
    val b = runCatching { Instant.parse(cur.createdAt) }.getOrNull() ?: return false
    return java.time.Duration.between(a, b).toMinutes() < 5
}

@Composable
fun ChatView(target: ChatTarget, vm: ChatVm) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

    // Cola no fim quando chega mensagem (historico carregado ou live).
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when {
                state.loading -> Center("abrindo a conversa…")
                state.messages.isEmpty() -> Center("nada por aqui ainda — comece a conversa")
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                ) {
                    itemsIndexed(state.messages, key = { _, m -> m.id }) { i, msg ->
                        MessageRow(
                            msg = msg,
                            grouped = grouped(state.messages.getOrNull(i - 1), msg),
                        )
                    }
                }
            }
        }

        // Composer
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (state.error != null) {
                BasicText(state.error!!, style = TextStyle(color = Obsidian.danger, fontSize = 12.sp))
                Spacer(Modifier.height(6.dp))
            }
            var draft by remember(target.id) { mutableStateOf("") }
            fun submit() {
                if (draft.isBlank()) return
                vm.send(draft)
                draft = ""
            }
            Input(
                value = draft,
                onValueChange = { draft = it.take(4000) },
                placeholder = "mensagem em ${target.title}",
                singleLine = false,
                animation = InputAnimation.Glow,
                modifier = Modifier
                    .fillMaxWidth()
                    // Enter envia; Shift+Enter quebra linha (convencao desktop).
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) {
                            submit(); true
                        } else false
                    },
            )
        }
    }
}

@Composable
private fun MessageRow(msg: ChatMessage, grouped: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (hovered) Obsidian.hover.copy(alpha = 0.35f) else Color.Transparent)
            .hoverable(interaction)
            .padding(horizontal = 14.dp, vertical = if (grouped) 1.dp else 4.dp)
            .padding(top = if (grouped) 0.dp else 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (grouped) {
            // Calha do avatar: hora exata aparece no hover.
            Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterEnd) {
                if (hovered) {
                    BasicText(hhmm(msg.createdAt), style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
                }
            }
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = msg.content,
                style = TextStyle(color = Obsidian.text2, fontSize = 13.sp, lineHeight = 19.sp),
                modifier = Modifier.weight(1f),
            )
        } else {
            DesktopAvatar(msg.authorAvatar, msg.authorName, 34)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicText(
                        text = msg.authorName,
                        style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicText(hhmm(msg.createdAt), style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
                }
                Spacer(Modifier.height(2.dp))
                BasicText(
                    text = msg.content,
                    style = TextStyle(color = Obsidian.text2, fontSize = 13.sp, lineHeight = 19.sp),
                )
            }
        }
    }
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(text, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
    }
}
