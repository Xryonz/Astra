package app.astra.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.EaseSnappy
import app.astra.mobile.ui.theme.astraColors

/** Emojis de reacao rapida no long-press (espelha o quick-react do web). */
val QuickReactions = listOf("👍", "❤️", "😂", "🔥", "🎉", "😮")

/** Chip de reacao numa mensagem: emoji + contagem; mine destaca a borda. */
data class ReactionChip(val emoji: String, val count: Int, val mine: Boolean)

/**
 * Bolha de mensagem compartilhada (DM + canal). Entrada com slide direcional
 * (do lado do autor) + overshoot elastico; mensagem nova (sweep=true) ganha um
 * "starlight sweep" prata por cima. Espelha dmMsgIn + msgStarlightSweep do web.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    mine: Boolean,
    authorName: String,
    content: String,
    animateIn: Boolean,
    sweep: Boolean,
    edited: Boolean = false,
    pinned: Boolean = false,
    reactions: List<ReactionChip> = emptyList(),
    replyAuthor: String? = null,
    replyContent: String? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onToggleReaction: ((String) -> Unit)? = null,
) {
    val bg = if (mine) astraColors.accent else astraColors.raised
    val fg = if (mine) astraColors.textInv else astraColors.text1
    // Borda que destaca a bolha. Ponto unico -> futura personalizacao troca so aqui.
    val borderColor = if (mine) Color.Black.copy(alpha = 0.18f) else astraColors.borderMid
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (mine) 16.dp else 5.dp,
        bottomEnd = if (mine) 5.dp else 16.dp,
    )
    val enter = remember { Animatable(if (animateIn) 0f else 1f) }
    val sweepA = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (animateIn) enter.animateTo(1f, tween(360, easing = EaseSnappy))
    }
    LaunchedEffect(sweep) {
        if (sweep) {
            sweepA.snapTo(0f)
            sweepA.animateTo(1f, tween(900, delayMillis = 120, easing = EaseOutSoft))
        }
    }
    val fromX = if (mine) 26f else -26f
    val glow = astraColors.accentGlow

    // Long-press abre o menu (reagir / responder / fixar / editar / apagar).
    val hasMenu = onEdit != null || onDelete != null || onReply != null ||
        onTogglePin != null || onToggleReaction != null
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = enter.value
                        translationX = (1f - enter.value) * fromX.dp.toPx()
                    }
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .background(bg)
                    .border(1.dp, borderColor, shape)
                    .then(
                        if (hasMenu) {
                            Modifier.combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                        } else {
                            Modifier
                        },
                    )
                    .drawWithContent {
                        drawContent()
                        val p = sweepA.value
                        if (p > 0f && p < 1f) {
                            val x = (p * 2f - 1f) * size.width
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color.Transparent, glow, Color.Transparent),
                                    start = Offset(x - size.width * 0.5f, 0f),
                                    end = Offset(x + size.width * 0.5f, size.height),
                                ),
                                blendMode = BlendMode.Screen,
                            )
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                if (pinned) {
                    Text(
                        text = "📌 fixado",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (mine) fg.copy(alpha = 0.7f) else astraColors.accent,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                // Quote da mensagem respondida (caixinha no topo da bolha).
                if (replyContent != null) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (mine) Color.Black.copy(alpha = 0.13f) else astraColors.base)
                            .widthIn(max = 272.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "↩ ${replyAuthor ?: "mensagem"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mine) fg.copy(alpha = 0.8f) else astraColors.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = replyContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = fg.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (!mine) {
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.labelSmall,
                        color = astraColors.accent,
                    )
                }
                Text(text = content, style = MaterialTheme.typography.bodyLarge, color = fg)
                if (edited) {
                    Text(
                        text = "editado",
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = fg.copy(alpha = 0.55f),
                    )
                }
                MessageReactions(reactions, onToggleReaction, Modifier.padding(top = 6.dp))
            }

            if (hasMenu) {
                MessageActionsMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    pinned = pinned,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onReply = onReply,
                    onTogglePin = onTogglePin,
                    onToggleReaction = onToggleReaction,
                )
            }
        }
    }
}

/** Chips de reacao reutilizados pela bolha (DM) e pela linha plana (canal). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageReactions(
    reactions: List<ReactionChip>,
    onToggleReaction: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { r ->
            val chipShape = RoundedCornerShape(10.dp)
            Row(
                modifier = Modifier
                    .clip(chipShape)
                    .background(astraColors.base)
                    .border(1.dp, if (r.mine) astraColors.accent else astraColors.border, chipShape)
                    .then(
                        if (onToggleReaction != null) Modifier.clickable { onToggleReaction(r.emoji) } else Modifier,
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.emoji, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${r.count}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (r.mine) astraColors.accent else astraColors.text2,
                )
            }
        }
    }
}

/** Menu de long-press (reagir/responder/fixar/editar/apagar). Bolha + linha plana. */
@Composable
private fun MessageActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    pinned: Boolean,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onReply: (() -> Unit)?,
    onTogglePin: (() -> Unit)?,
    onToggleReaction: ((String) -> Unit)?,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (onToggleReaction != null) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                QuickReactions.forEach { e ->
                    Text(
                        text = e,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onDismiss(); onToggleReaction(e) }
                            .padding(6.dp),
                    )
                }
            }
        }
        if (onReply != null) {
            DropdownMenuItem(
                text = { Text("Responder", color = astraColors.text1) },
                onClick = { onDismiss(); onReply() },
            )
        }
        if (onTogglePin != null) {
            DropdownMenuItem(
                text = { Text(if (pinned) "Desafixar" else "Fixar", color = astraColors.text1) },
                onClick = { onDismiss(); onTogglePin() },
            )
        }
        if (onEdit != null) {
            DropdownMenuItem(
                text = { Text("Editar", color = astraColors.text1) },
                onClick = { onDismiss(); onEdit() },
            )
        }
        if (onDelete != null) {
            DropdownMenuItem(
                text = { Text("Apagar", color = astraColors.danger) },
                onClick = { onDismiss(); onDelete() },
            )
        }
    }
}

/** Linha de chat agnostica de modelo (DM e canal mapeiam pra isto). */
data class ChatRow(
    val id: String,
    val mine: Boolean,
    val authorName: String,
    val content: String,
    val edited: Boolean = false,
    val pinned: Boolean = false,
    val reactions: List<ReactionChip> = emptyList(),
    val replyAuthor: String? = null,
    val replyContent: String? = null,
)

/**
 * Lista de mensagens (reverseLayout, mais nova embaixo). Cada msg desliza ao
 * aparecer pela 1a vez; o "sweep" prata so dispara em msg que chega DEPOIS da
 * carga inicial (shownOnce), pra nao varrer o historico inteiro de uma vez.
 */
@Composable
fun ChatMessageList(
    rows: List<ChatRow>,
    modifier: Modifier = Modifier,
    canEdit: Boolean = true,
    canReact: Boolean = false,
    canPin: Boolean = false,
    onEdit: (ChatRow) -> Unit = {},
    onDelete: (ChatRow) -> Unit = {},
    onReply: (ChatRow) -> Unit = {},
    onTogglePin: (ChatRow) -> Unit = {},
    onToggleReaction: (ChatRow, String) -> Unit = { _, _ -> },
) {
    val animated = remember { mutableSetOf<String>() }
    var shownOnce by remember { mutableStateOf(false) }
    LaunchedEffect(rows.isNotEmpty()) { if (rows.isNotEmpty()) shownOnce = true }

    LazyColumn(
        modifier = modifier,
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        items(rows.asReversed(), key = { it.id }) { row ->
            val isNew = remember(row.id) {
                val n = row.id !in animated
                animated.add(row.id)
                n
            }
            // Editar/apagar so na propria msg; reagir vale pra qualquer uma (canal).
            MessageBubble(
                mine = row.mine,
                authorName = row.authorName,
                content = row.content,
                animateIn = isNew,
                sweep = isNew && shownOnce,
                edited = row.edited,
                pinned = row.pinned,
                reactions = row.reactions,
                replyAuthor = row.replyAuthor,
                replyContent = row.replyContent,
                onEdit = if (row.mine && canEdit) ({ onEdit(row) }) else null,
                onDelete = if (row.mine) ({ onDelete(row) }) else null,
                onReply = { onReply(row) },
                onTogglePin = if (canPin) ({ onTogglePin(row) }) else null,
                onToggleReaction = if (canReact) ({ emoji: String -> onToggleReaction(row, emoji) }) else null,
            )
        }
    }
}

/** Banner acima do composer enquanto edita uma mensagem (input vira o texto dela). */
@Composable
fun EditingBanner(onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarginaliaLabel("editando mensagem", color = astraColors.accent)
        Spacer(Modifier.weight(1f))
        Text(
            text = "cancelar",
            style = MaterialTheme.typography.labelMedium,
            color = astraColors.text2,
            modifier = Modifier.clickable(onClick = onCancel),
        )
    }
}

/** Banner acima do composer enquanto responde a uma mensagem. */
@Composable
fun ReplyBanner(author: String, preview: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            MarginaliaLabel("respondendo a $author", color = astraColors.accent)
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "cancelar",
            style = MaterialTheme.typography.labelMedium,
            color = astraColors.text2,
            modifier = Modifier.clickable(onClick = onCancel),
        )
    }
}

/** Linha "fulano esta digitando..." acima do composer. Vazio = nao renderiza. */
@Composable
fun TypingIndicator(names: List<String>) {
    if (names.isEmpty()) return
    val text = when (names.size) {
        1 -> "${names[0]} esta digitando…"
        2 -> "${names[0]} e ${names[1]} estao digitando…"
        else -> "varias pessoas estao digitando…"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontStyle = FontStyle.Italic,
        color = astraColors.text3,
        modifier = Modifier.padding(start = 18.dp, top = 2.dp, bottom = 2.dp),
    )
}

/** Lista de mensagens fixadas (autor + conteudo). items vazio = "nada fixado". */
@Composable
fun PinnedMessagesDialog(items: List<Pair<String, String>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = astraColors.overlay,
        title = { Text("Mensagens fixadas", style = MaterialTheme.typography.titleLarge, color = astraColors.text1) },
        text = {
            if (items.isEmpty()) {
                Text("Nada fixado nesta orbita.", style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEach { (author, content) ->
                        Column {
                            Text(author, style = MaterialTheme.typography.labelSmall, color = astraColors.accent)
                            Text(
                                content,
                                style = MaterialTheme.typography.bodySmall,
                                color = astraColors.text1,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = astraColors.accent) } },
    )
}

/** Confirmacao de apagar mensagem (compartilhada DM + canal). */
@Composable
fun DeleteMessageDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = astraColors.overlay,
        title = { Text("Apagar mensagem?", style = MaterialTheme.typography.titleLarge, color = astraColors.text1) },
        text = { Text("Isso remove a mensagem pra todo mundo.", style = MaterialTheme.typography.bodyMedium, color = astraColors.text2) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Apagar", color = astraColors.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = astraColors.text2) } },
    )
}

/** Composer cosmico: pill input + botao enviar circular. Enter envia, Shift+Enter quebra linha. */
@Composable
fun ChatInputBar(
    text: String,
    sending: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = text.isNotBlank() && !sending
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(astraColors.raised)
                .border(1.dp, astraColors.borderMid, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = onInput,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = astraColors.text1),
                cursorBrush = SolidColor(astraColors.accent),
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    // Enter (teclado fisico) envia; Shift+Enter quebra linha.
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) {
                            onSend(); true
                        } else false
                    },
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            text = "Mensagem",
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = FontStyle.Italic,
                            color = astraColors.text3,
                        )
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (canSend) astraColors.accent else astraColors.raised)
                .border(1.dp, if (canSend) Color.Transparent else astraColors.borderMid, CircleShape)
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "↑",
                style = MaterialTheme.typography.titleLarge,
                color = if (canSend) astraColors.textInv else astraColors.text3,
            )
        }
    }
}
