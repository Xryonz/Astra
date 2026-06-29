package app.astra.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.EaseSnappy
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import zed.rainxch.rikkaui.components.ui.alertdialog.AlertDialog as RAlertDialog
import zed.rainxch.rikkaui.components.ui.alertdialog.AlertDialogAction
import zed.rainxch.rikkaui.components.ui.alertdialog.AlertDialogActionVariant
import zed.rainxch.rikkaui.components.ui.alertdialog.AlertDialogAnimation
import zed.rainxch.rikkaui.components.ui.alertdialog.AlertDialogCancel
import zed.rainxch.rikkaui.components.ui.alertdialog.AlertDialogFooter
import zed.rainxch.rikkaui.components.ui.alertdialog.AlertDialogHeader
import zed.rainxch.rikkaui.components.ui.input.Input
import zed.rainxch.rikkaui.components.ui.input.InputAnimation

val QuickReactions = listOf("👍", "❤️", "😂", "🔥", "🎉", "😮")

data class ReactionChip(val emoji: String, val count: Int, val mine: Boolean)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    mine: Boolean,
    authorName: String,
    authorAvatar: String?,
    content: String,
    animateIn: Boolean,
    sweep: Boolean,
    grouped: Boolean = false,
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
    val shape = RoundedCornerShape(14.dp)
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
    val glow = astraColors.accentGlow

    val hasMenu = onEdit != null || onDelete != null || onReply != null ||
        onTogglePin != null || onToggleReaction != null
    var menuOpen by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val maxDragPx = with(density) { 56.dp.toPx() }
    val thresholdPx = with(density) { 40.dp.toPx() }
    val swipeX = remember { Animatable(0f) }
    val bubbleMaxDp = (LocalConfiguration.current.screenWidthDp * 0.86f).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = if (grouped) 2.dp else 10.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(46.dp)) {
            if (!grouped) AstraAvatar(authorAvatar, authorName, size = 42)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            if (!grouped) {
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.labelLarge,
                    color = astraColors.accent,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                )
            }
            Box(contentAlignment = Alignment.CenterStart) {
                if (onReply != null) {
                    Text(
                        text = "↩",
                        style = MaterialTheme.typography.titleMedium,
                        color = astraColors.accent,
                        modifier = Modifier
                            .graphicsLayer {
                                val p = (abs(swipeX.value) / thresholdPx).coerceIn(0f, 1f)
                                alpha = p
                                scaleX = 0.5f + 0.5f * p
                                scaleY = 0.5f + 0.5f * p
                            }
                            .padding(horizontal = 10.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = enter.value
                            translationX = (1f - enter.value) * (-22f).dp.toPx() + swipeX.value
                        }
                        .widthIn(max = bubbleMaxDp)
                        .clip(shape)
                        .background(astraColors.raised)
                        .border(1.dp, astraColors.borderMid, shape)
                        .then(
                            if (onReply != null) {
                                Modifier.pointerInput(Unit) {
                                    var triggered = false
                                    detectHorizontalDragGestures(
                                        onDragStart = { triggered = false },
                                        onHorizontalDrag = { change, dx ->
                                            val clamped = (swipeX.value + dx).coerceIn(0f, maxDragPx)
                                            change.consume()
                                            scope.launch { swipeX.snapTo(clamped) }
                                            if (!triggered && abs(clamped) >= thresholdPx) {
                                                triggered = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        },
                                        onDragEnd = {
                                            if (triggered) onReply()
                                            scope.launch {
                                                swipeX.animateTo(
                                                    0f,
                                                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                                                )
                                            }
                                        },
                                        onDragCancel = { scope.launch { swipeX.animateTo(0f, spring()) } },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (pinned) {
                        Text(
                            text = "📌 fixado",
                            style = MaterialTheme.typography.labelSmall,
                            color = astraColors.accent,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    if (replyContent != null) {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(astraColors.base)
                                .widthIn(max = 260.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "↩ ${replyAuthor ?: "mensagem"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = astraColors.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = replyContent,
                                style = MaterialTheme.typography.bodySmall,
                                color = astraColors.text1.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 17.sp,
                        color = astraColors.text1,
                        textAlign = TextAlign.Center,
                    )
                    if (edited) {
                        Text(
                            text = "editado",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = astraColors.text1.copy(alpha = 0.55f),
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
}

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

data class ChatRow(
    val id: String,
    val mine: Boolean,
    val authorName: String,
    val authorAvatar: String? = null,
    val content: String,
    val edited: Boolean = false,
    val pinned: Boolean = false,
    val reactions: List<ReactionChip> = emptyList(),
    val replyAuthor: String? = null,
    val replyContent: String? = null,
)

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

    val listState = rememberLazyListState()
    val newest = rows.lastOrNull()
    LaunchedEffect(newest?.id) {
        if (newest != null && (newest.mine || listState.firstVisibleItemIndex <= 2)) {
            listState.animateScrollToItem(0)
        }
    }

    val groupedIds = remember(rows) {
        buildSet {
            for (i in 1 until rows.size) {
                if (rows[i - 1].mine == rows[i].mine && rows[i - 1].authorName == rows[i].authorName) {
                    add(rows[i].id)
                }
            }
        }
    }

    LazyColumn(
        state = listState,
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

            MessageBubble(
                mine = row.mine,
                authorName = row.authorName,
                authorAvatar = row.authorAvatar,
                content = row.content,
                animateIn = isNew,
                sweep = isNew && shownOnce,
                grouped = row.id in groupedIds,
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

@Composable
fun PinnedMessagesDialog(open: Boolean, items: List<Pair<String, String>>, onDismiss: () -> Unit) {
    AstraDialog(
        open = open,
        onDismiss = onDismiss,
        title = "Mensagens fixadas",
        confirmText = "Fechar",
        onConfirm = onDismiss,
        dismissText = null,
    ) {
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
    }
}

@Composable
fun DeleteMessageDialog(open: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    RAlertDialog(
        open = open,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        animation = AlertDialogAnimation.FadeScale,
    ) {
        AlertDialogHeader(
            title = "Apagar mensagem?",
            description = "Isso remove a mensagem pra todo mundo.",
        )
        AlertDialogFooter {
            AlertDialogCancel(onClick = onDismiss, text = "Cancelar")
            AlertDialogAction(
                text = "Apagar",
                onClick = onConfirm,
                variant = AlertDialogActionVariant.Destructive,
            )
        }
    }
}

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

        Input(
            value = text,
            onValueChange = onInput,
            modifier = Modifier
                .weight(1f)

                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) {
                        onSend(); true
                    } else false
                },
            placeholder = "Mensagem",
            singleLine = false,
            animation = InputAnimation.Glow,
        )
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
