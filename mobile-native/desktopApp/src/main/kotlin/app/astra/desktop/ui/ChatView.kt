package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import app.astra.desktop.ui.theme.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.shell.ChatMessage
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.ChatVm
import app.astra.desktop.ui.theme.Obsidian
import app.astra.mobile.core.network.dto.AttachmentDto
import app.astra.mobile.core.network.dto.ReactionDto
import app.astra.mobile.core.network.dto.ReplyToDto
import app.astra.shared.AstraShared
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zed.rainxch.rikkaui.components.ui.input.Input
import zed.rainxch.rikkaui.components.ui.input.InputAnimation
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

// Sincronizado com ChatVm.FADE_OUT_MS: o VM remove a mensagem da lista quando
// esta animacao termina.
private const val FADE_MS = 340

private val QUICK_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")
private val GRID_EMOJIS = listOf(
    "😀", "😅", "🤣", "😊", "😍", "😘", "😎",
    "🤔", "😴", "😭", "😡", "🤯", "🥳", "😇",
    "🙏", "👏", "👀", "💀", "✨", "💜", "💔",
    "✅", "❌", "⚡", "🎉", "🎮", "🎧", "🌙",
)

private fun hhmm(iso: String?): String =
    iso?.let { runCatching { HHMM.format(Instant.parse(it)) }.getOrNull() } ?: ""

// Mensagens do mesmo autor com menos de 5min entre si agrupam (compacto Discord).
private fun grouped(prev: ChatMessage?, cur: ChatMessage): Boolean {
    if (prev == null || prev.authorId != cur.authorId) return false
    val a = runCatching { Instant.parse(prev.createdAt) }.getOrNull() ?: return false
    val b = runCatching { Instant.parse(cur.createdAt) }.getOrNull() ?: return false
    return java.time.Duration.between(a, b).toMinutes() < 5
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun ChatView(target: ChatTarget, vm: ChatVm, onStartDm: (String, String) -> Unit) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val isChannel = target is ChatTarget.Channel

    // Drag&drop de arquivo do sistema: solta em qualquer lugar da conversa e o
    // arquivo vira anexo pendente no composer.
    var dragOver by remember { mutableStateOf(false) }
    val dndTarget = remember(target.id) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dragOver = true }
            override fun onExited(event: DragAndDropEvent) { dragOver = false }
            override fun onEnded(event: DragAndDropEvent) { dragOver = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragOver = false
                val files = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>).orEmpty()
                }.getOrDefault(emptyList())
                if (files.isEmpty()) return false
                vm.addFiles(files)
                return true
            }
        }
    }

    // Mensagem sendo editada inline / destacada pelo clique numa reply.
    var editingId by remember(target.id) { mutableStateOf<String?>(null) }
    var highlightId by remember(target.id) { mutableStateOf<String?>(null) }

    // Cola no fim so quando ENTRA mensagem (apagar tambem muda o size e nao
    // pode jogar a lista pro fundo).
    var prevCount by remember(target.id) { mutableStateOf(0) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.size > prevCount && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
        }
        prevCount = state.messages.size
    }

    // Entrada fade+subida so pra mensagem NOVA: o historico entra sem animar e
    // item reciclado pelo scroll nao re-anima (set de ids ja vistos).
    val animatedIds = remember(target.id) { mutableSetOf<String>() }
    var baselineDone by remember(target.id) { mutableStateOf(false) }
    LaunchedEffect(state.loading) {
        if (!state.loading) {
            state.messages.forEach { animatedIds.add(it.id) }
            baselineDone = true
        }
    }

    // Rola ate a origem da reply e da um flash rapido nela.
    fun jumpTo(id: String) {
        val idx = state.messages.indexOfFirst { it.id == id }
        if (idx < 0) return
        scope.launch {
            listState.animateScrollToItem(idx)
            highlightId = id
            delay(300)
            highlightId = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dndTarget),
    ) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when {
                state.loading -> ChatSkeleton()
                state.messages.isEmpty() -> Center("nada por aqui ainda — comece a conversa")
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                ) {
                    itemsIndexed(state.messages, key = { _, m -> m.id }) { i, msg ->
                        val enterAnim = remember(msg.id) {
                            val fresh = animatedIds.add(msg.id)
                            baselineDone && fresh
                        }
                        // Botao direito: mesmas acoes da pill de hover (F4).
                        EditorialContextMenu(entries = {
                            buildList {
                                add(MenuEntry.Item("responder") { vm.startReply(msg) })
                                if (isChannel) {
                                    add(MenuEntry.EmojiSub("reagir", QUICK_EMOJIS) { e -> vm.react(msg.id, e) })
                                }
                                if (msg.content.isNotBlank()) {
                                    add(
                                        MenuEntry.Item("copiar texto") {
                                            clipboard.setText(AnnotatedString(msg.content))
                                        },
                                    )
                                }
                                if (msg.mine) {
                                    add(MenuEntry.Separator)
                                    if (isChannel) add(MenuEntry.Item("editar") { editingId = msg.id })
                                    add(MenuEntry.Item("apagar", danger = true) { vm.delete(msg.id) })
                                }
                            }
                        }) {
                            MessageRow(
                                msg = msg,
                                // Reply quebra o agrupamento (a referencia precisa aparecer).
                                grouped = grouped(state.messages.getOrNull(i - 1), msg) && msg.replyTo == null,
                                enterAnim = enterAnim,
                                isChannel = isChannel,
                                highlighted = msg.id == highlightId,
                                editing = msg.id == editingId,
                                myId = vm.myId,
                                onReply = { vm.startReply(msg) },
                                onReact = { emoji -> vm.react(msg.id, emoji) },
                                onStartEdit = { editingId = msg.id },
                                onSaveEdit = { text -> vm.edit(msg.id, text); editingId = null },
                                onCancelEdit = { editingId = null },
                                onDelete = { vm.delete(msg.id) },
                                onJumpTo = { id -> jumpTo(id) },
                                onStartDm = onStartDm,
                            )
                        }
                    }
                }
            }
        }

        // Composer
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Slot fixo: aparecer/sumir o "digitando…" nao pode pular o layout.
            Box(Modifier.fillMaxWidth().height(16.dp)) {
                if (state.typing.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypingDots(Obsidian.accent)
                        Spacer(Modifier.width(6.dp))
                        val names = state.typing.values.toList()
                        val label = when (names.size) {
                            1 -> "${names[0]} esta digitando…"
                            2 -> "${names[0]} e ${names[1]} estao digitando…"
                            else -> "varias pessoas estao digitando…"
                        }
                        Text(label, style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
                    }
                }
            }
            if (state.error != null) {
                Text(state.error!!, style = TextStyle(color = Obsidian.danger, fontSize = 12.sp))
                Spacer(Modifier.height(6.dp))
            }
            // Anexos pendentes (drag&drop): chips com ✕ pra tirar antes de enviar.
            if (state.pending.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.pending.forEachIndexed { i, pf ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Obsidian.raised)
                                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (pf.mime.startsWith("image/")) "🖼" else "📄",
                                style = TextStyle(fontSize = 12.sp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                pf.file.name,
                                style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(sizeLabel(pf.file.length()), style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
                            Spacer(Modifier.width(6.dp))
                            HoverGlyph("✕") { vm.removePending(i) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            state.replyingTo?.let { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Obsidian.raised)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("respondendo a ", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
                    Text(
                        r.authorName,
                        style = TextStyle(color = Obsidian.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.weight(1f))
                    HoverGlyph("✕") { vm.cancelReply() }
                }
                Spacer(Modifier.height(6.dp))
            }
            var draft by remember(target.id) { mutableStateOf("") }
            fun submit() {
                if (draft.isBlank() && state.pending.isEmpty()) return
                vm.send(draft)
                draft = ""
            }
            Input(
                value = draft,
                onValueChange = {
                    draft = it.take(4000)
                    if (it.isNotBlank()) vm.typing()
                },
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

    // Overlay enquanto o arquivo esta sendo arrastado por cima da conversa.
    if (dragOver) {
        Box(
            Modifier.fillMaxSize().background(Obsidian.void.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⇩", style = TextStyle(color = Obsidian.accent, fontSize = 34.sp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "solte pra anexar em ${target.title}",
                    style = TextStyle(color = Obsidian.text1, fontSize = 14.sp),
                )
            }
        }
    }
    }
}

@Composable
private fun MessageRow(
    msg: ChatMessage,
    grouped: Boolean,
    enterAnim: Boolean,
    isChannel: Boolean,
    highlighted: Boolean,
    editing: Boolean,
    myId: String?,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onStartEdit: () -> Unit,
    onSaveEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: () -> Unit,
    onJumpTo: (String) -> Unit,
    onStartDm: (String, String) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // A pill vive num Popup (camada propria): hover nela tira o hover da linha,
    // entao os dois juntos decidem a visibilidade — senao ela pisca.
    val pillInteraction = remember { MutableInteractionSource() }
    val pillHovered by pillInteraction.collectIsHoveredAsState()
    var pickerOpen by remember { mutableStateOf(false) }

    val rowAlpha by animateFloatAsState(if (msg.deleting) 0f else 1f, tween(FADE_MS))
    val bg by animateColorAsState(
        when {
            highlighted -> Obsidian.accentDim
            hovered -> Obsidian.hover.copy(alpha = 0.35f)
            else -> Color.Transparent
        },
        tween(150),
    )
    // Mensagem nova entra com fade+subida (~150ms); as demais nascem prontas.
    val enter = remember { Animatable(if (enterAnim) 0f else 1f) }
    LaunchedEffect(Unit) { if (enterAnim) enter.animateTo(1f, tween(150)) }

    Box(
        Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 6.dp.toPx()
            }
            .background(bg)
            .hoverable(interaction),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (grouped) 1.dp else 4.dp)
                .padding(top = if (grouped) 0.dp else 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (grouped) {
                // Calha do avatar: hora exata aparece no hover.
                Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterEnd) {
                    if (hovered) {
                        Text(hhmm(msg.createdAt), style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    ContentBlock(msg, editing, myId, onReact, onSaveEdit, onCancelEdit)
                }
            } else {
                // Clique no avatar abre o card de perfil (F3).
                ProfileAnchor(msg.authorId, isMe = msg.mine, onStartDm = onStartDm) {
                    DesktopAvatar(msg.authorAvatar, msg.authorName, 34)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    msg.replyTo?.let { ref ->
                        ReplyRef(ref, onJumpTo)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = msg.authorName,
                            style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(hhmm(msg.createdAt), style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
                    }
                    Spacer(Modifier.height(2.dp))
                    ContentBlock(msg, editing, myId, onReact, onSaveEdit, onCancelEdit)
                }
            }
        }

        val density = LocalDensity.current
        val showPill = (hovered || pillHovered || pickerOpen) && !msg.deleting && !editing
        if (showPill) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = with(density) { IntOffset(-10.dp.roundToPx(), (-12).dp.roundToPx()) },
            ) {
                // Entrada fade+subida; o MutableTransitionState nasce false e vira
                // true na primeira composicao pra animacao rodar.
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 2 },
                ) {
                    ActionPill(
                        canReact = isChannel,
                        canEdit = isChannel && msg.mine,
                        canDelete = msg.mine,
                        onReply = onReply,
                        onReact = { pickerOpen = true },
                        onEdit = onStartEdit,
                        onDelete = onDelete,
                        modifier = Modifier.hoverable(pillInteraction),
                    )
                }
            }
        }
        if (pickerOpen) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = with(density) { IntOffset(-10.dp.roundToPx(), 24.dp.roundToPx()) },
                onDismissRequest = { pickerOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                ReactionPicker(onPick = { emoji -> onReact(emoji); pickerOpen = false })
            }
        }
    }
}

// Conteudo da mensagem: texto (ou campo de edicao inline) + chips de reacao.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentBlock(
    msg: ChatMessage,
    editing: Boolean,
    myId: String?,
    onReact: (String) -> Unit,
    onSaveEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
) {
    if (editing) {
        EditField(msg.content, onSaveEdit, onCancelEdit)
    } else if (msg.content.isNotBlank() || msg.edited) {
        // Mensagem so-anexo tem content vazio — sem linha em branco.
        Text(
            text = buildAnnotatedString {
                append(msg.content)
                if (msg.edited) {
                    withStyle(SpanStyle(color = Obsidian.text3, fontSize = 10.sp)) { append("  (editado)") }
                }
            },
            style = TextStyle(color = Obsidian.text2, fontSize = 13.sp, lineHeight = 19.sp),
        )
    }
    msg.attachments.forEach { att ->
        Spacer(Modifier.height(4.dp))
        AttachmentBlock(att)
    }
    if (msg.reactions.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            msg.reactions.forEach { r ->
                ReactionChip(
                    reaction = r,
                    mine = myId != null && myId in r.users,
                    onClick = { onReact(r.emoji) },
                )
            }
        }
    }
}

// Anexo na mensagem: imagem inline (Coil); resto vira chip que abre no navegador.
@Composable
private fun AttachmentBlock(att: AttachmentDto) {
    if (att.type?.startsWith("image/") == true) {
        AsyncImage(
            model = att.url,
            contentDescription = att.name,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        val src = remember { MutableInteractionSource() }
        val hov by src.collectIsHoveredAsState()
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (hov) Obsidian.hover else Obsidian.raised)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .hoverable(src)
                .clickable(interactionSource = src, indication = null) { openAttachment(att.url) }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📄", style = TextStyle(fontSize = 14.sp))
            Spacer(Modifier.width(7.dp))
            Column {
                Text(
                    att.name ?: "arquivo",
                    style = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
                att.size?.let {
                    Text(sizeLabel(it), style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
                }
            }
        }
    }
}

// Abre o anexo no navegador (URL relativa do backend vira absoluta).
private fun openAttachment(url: String) {
    val abs = if (url.startsWith("/")) AstraShared.BASE_URL.trimEnd('/') + url else url
    runCatching { Desktop.getDesktop().browse(URI(abs)) }
}

private fun sizeLabel(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
private fun EditField(original: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var draft by remember { mutableStateOf(TextFieldValue(original, TextRange(original.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp, lineHeight = 19.sp),
        cursorBrush = SolidColor(Obsidian.accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                when {
                    e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed -> {
                        if (draft.text.isNotBlank()) onSave(draft.text)
                        true
                    }
                    e.type == KeyEventType.KeyDown && e.key == Key.Escape -> {
                        onCancel()
                        true
                    }
                    else -> false
                }
            },
    )
    Spacer(Modifier.height(2.dp))
    Text("enter salva · esc cancela", style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
}

@Composable
private fun ReactionChip(reaction: ReactionDto, mine: Boolean, onClick: () -> Unit) {
    // Pop: entra com overshoot e repete quando o count muda (reacao realtime).
    val scale = remember { Animatable(0f) }
    LaunchedEffect(reaction.count) {
        if (scale.value > 0f) scale.snapTo(0.75f)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    val src = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(999.dp))
            .background(if (mine) Obsidian.active else Obsidian.raised)
            .border(1.dp, if (mine) Obsidian.borderMid else Obsidian.borderDim, RoundedCornerShape(999.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(reaction.emoji, style = TextStyle(fontSize = 12.sp))
        Spacer(Modifier.width(4.dp))
        Text(
            "${reaction.count}",
            style = TextStyle(color = if (mine) Obsidian.accent else Obsidian.text3, fontSize = 11.sp),
        )
    }
}

// Referencia da mensagem-origem acima de uma reply; clique rola ate ela.
@Composable
private fun ReplyRef(ref: ReplyToDto, onJumpTo: (String) -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clickable(interactionSource = src, indication = null) { onJumpTo(ref.id) }
            .padding(bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("↩", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
        Spacer(Modifier.width(5.dp))
        Text(
            ref.authorName ?: "alguem",
            style = TextStyle(color = Obsidian.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            ref.content,
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionPill(
    canReact: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onReply: () -> Unit,
    onReact: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .shadow(6.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(2.dp),
    ) {
        PillButton("↩", onReply)
        if (canReact) PillButton("😊", onReact)
        if (canEdit) PillButton("✏", onEdit)
        if (canDelete) PillButton("🗑", onDelete, danger = true)
    }
}

@Composable
private fun PillButton(glyph: String, onClick: () -> Unit, danger: Boolean = false) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    hov && danger -> Obsidian.danger.copy(alpha = 0.25f)
                    hov -> Obsidian.hover
                    else -> Color.Transparent
                },
            )
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = TextStyle(fontSize = 13.sp, color = Obsidian.text2))
    }
}

// Seletor de reacao: 6 rapidos + grade expansivel.
@Composable
private fun ReactionPicker(onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .shadow(8.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            QUICK_EMOJIS.forEach { e -> EmojiCell(e) { onPick(e) } }
            EmojiCell(if (expanded) "−" else "+") { expanded = !expanded }
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            GRID_EMOJIS.chunked(7).forEach { rowEmojis ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    rowEmojis.forEach { e -> EmojiCell(e) { onPick(e) } }
                }
            }
        }
    }
}

@Composable
private fun EmojiCell(glyph: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hov) Obsidian.hover else Color.Transparent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = TextStyle(fontSize = 15.sp, color = Obsidian.text1))
    }
}

// Botao-glifo pequeno (ex.: fechar a reply bar).
@Composable
private fun HoverGlyph(glyph: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (hov) Obsidian.hover else Color.Transparent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
    }
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
    }
}
