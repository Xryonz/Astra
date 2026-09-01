package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import app.astra.mobile.core.network.dto.CallLogDto
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.PhoneMissed
import com.composables.icons.lucide.Video
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.FileImage
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Reply
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.SmilePlus
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.shell.ChatMessage
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.ChatVm
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import org.koin.core.context.GlobalContext
import app.astra.mobile.core.network.dto.AttachmentDto
import app.astra.mobile.core.network.dto.EmojiDto
import app.astra.mobile.core.network.dto.ReactionDto
import app.astra.mobile.core.network.dto.ReplyToDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import app.astra.shared.AstraShared
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val HHMM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private const val FADE_MS = 340

private val QUICK_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

private fun hhmm(iso: String?): String =
    iso?.let { runCatching { HHMM.format(Instant.parse(it)) }.getOrNull() } ?: ""

private fun grouped(prev: ChatMessage?, cur: ChatMessage): Boolean {
    if (prev == null || prev.authorId != cur.authorId) return false
    val a = runCatching { Instant.parse(prev.createdAt) }.getOrNull() ?: return false
    val b = runCatching { Instant.parse(cur.createdAt) }.getOrNull() ?: return false
    return java.time.Duration.between(a, b).toMinutes() < 5
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun ChatView(
    target: ChatTarget,
    vm: ChatVm,
    onStartDm: (String, String) -> Unit,
    botAqui: Boolean = true,
    serverId: String? = null,
    membros: List<ServerMemberDto> = emptyList(),
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isChannel = target is ChatTarget.Channel
    val prefs = remember { GlobalContext.get().get<DesktopPrefs>() }
    val prefState by prefs.state.collectAsState()

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

    var enqueteAberta by remember(target.id) { mutableStateOf(false) }

    var editingId by remember(target.id) { mutableStateOf<String?>(null) }
    var highlightId by remember(target.id) { mutableStateOf<String?>(null) }

    var prevCount by remember(target.id) { mutableStateOf(0) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.size > prevCount && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
        }
        prevCount = state.messages.size
    }

    val animatedIds = remember(target.id) { mutableSetOf<String>() }
    var baselineDone by remember(target.id) { mutableStateOf(false) }
    LaunchedEffect(state.loading) {
        if (!state.loading) {
            state.messages.forEach { animatedIds.add(it.id) }
            baselineDone = true
        }
    }

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

    var lightboxUrl by remember { mutableStateOf<String?>(null) }
    lightboxUrl?.let { Lightbox(it) { lightboxUrl = null } }

    val mencao = remember { MencaoClicavel() }
    var perfilDaMencao by remember(target.id) { mutableStateOf<Pair<String, IntOffset>?>(null) }
    val ponteiro = remember { intArrayOf(0, 0) }
    SideEffect {
        mencao.abrir = { usuario ->
            membros.firstOrNull { it.user.username.equals(usuario, ignoreCase = true) }?.let { m ->
                perfilDaMencao = m.userId to IntOffset(ponteiro[0], ponteiro[1])
            }
        }
    }

    val emojisDaSala = rememberEmojisDaSala(serverId)

    androidx.compose.runtime.CompositionLocalProvider(
        LocalOpenImage provides { url -> lightboxUrl = url },
        LocalMsgFontScale provides prefState.fontSize.scale,
        LocalMsgDensity provides MsgDensity(prefState.density.topDp, prefState.density.groupedTopDp),
        LocalMencaoClicavel provides mencao,
        LocalEmojisDaSala provides emojisDaSala,
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val p = awaitPointerEvent(PointerEventPass.Initial)
                            .changes.firstOrNull()?.position ?: continue
                        ponteiro[0] = p.x.roundToInt()
                        ponteiro[1] = p.y.roundToInt()
                    }
                }
            }
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dndTarget),
    ) {
        perfilDaMencao?.let { (uid, onde) ->
            ProfileCardNoPonto(
                userId = uid,
                at = onde,
                isMe = uid == vm.myId,
                onStartDm = { u, t -> perfilDaMencao = null; onStartDm(u, t) },
                onClose = { perfilDaMencao = null },
            )
        }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when {
                state.loading && state.acordando -> AcordandoOServidor()
                state.loading -> ChatSkeleton()
                state.error != null && state.messages.isEmpty() -> PalcoQueFalhou(
                    motivo = state.error!!,
                    podeTentar = !state.errorPermanente,
                    onRetry = vm::tentarDeNovo,
                )
                state.messages.isEmpty() -> EmptyChat()
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                ) {
                    itemsIndexed(
                        state.messages,
                        key = { _, m -> m.id },
                        contentType = { _, m ->
                            when {
                                m.poll != null -> "enquete"
                                m.attachments.isNotEmpty() -> "anexo"
                                else -> "texto"
                            }
                        },
                    ) { i, msg ->
                        val enterAnim = remember(msg.id) {
                            val fresh = animatedIds.add(msg.id)
                            baselineDone && fresh
                        }
                        MessageRow(
                            msg = msg,
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
                            onPin = { vm.pin(msg.id) },
                            onVote = { opcao -> vm.vote(msg.id, opcao) },
                            onClosePoll = { vm.closePoll(msg.id) },
                            onRetry = { vm.retry(msg) },
                            onJumpTo = { id -> jumpTo(id) },
                            onStartDm = onStartDm,
                        )
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            val reduzirMovimento = LocalReduceMotion.current
            AnimatedVisibility(
                visible = state.typing.isNotEmpty(),
                enter = expandVertically(tween(if (reduzirMovimento) 0 else 140, easing = EaseOutStd)) +
                    fadeIn(tween(if (reduzirMovimento) 0 else 120)),
                exit = shrinkVertically(tween(if (reduzirMovimento) 0 else 120, easing = EaseOutStd)) +
                    fadeOut(tween(if (reduzirMovimento) 0 else 80)),
            ) {
                Row(Modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypingDots(Obsidian.accent)
                    Spacer(Modifier.width(6.dp))
                    val names = state.typing.values.toList()
                    val label = when (names.size) {
                        1 -> "${names[0]} esta digitando…"
                        2 -> "${names[0]} e ${names[1]} estão digitando…"
                        else -> "varias pessoas estão digitando…"
                    }
                    Text(label, style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
                }
            }
            if (state.error != null && state.messages.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.error!!, style = TextStyle(color = Obsidian.danger, fontSize = 12.sp))
                    if (!state.errorPermanente) {
                        Spacer(Modifier.width(10.dp))
                        val src = remember { MutableInteractionSource() }
                        Text(
                            "Tentar novamente",
                            style = TextStyle(color = Obsidian.accent, fontSize = 12.sp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(interactionSource = src, indication = null) { vm.tentarDeNovo() }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
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
                            LIcon(
                                if (pf.mime.startsWith("image/")) Lucide.FileImage else Lucide.FileText,
                                tint = Obsidian.text3,
                                size = 14.dp,
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
                            HoverGlyph(Lucide.X) { vm.removePending(i) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            val respondendo = state.replyingTo
            var ultimaResposta by remember(target.id) { mutableStateOf(respondendo) }
            if (respondendo != null) ultimaResposta = respondendo
            val semMovimento = LocalReduceMotion.current
            AnimatedVisibility(
                visible = respondendo != null,
                enter = expandVertically(tween(if (semMovimento) 0 else 180, easing = EaseOutStd)) +
                    fadeIn(tween(if (semMovimento) 0 else 140, delayMillis = if (semMovimento) 0 else 40)),
                exit = shrinkVertically(tween(if (semMovimento) 0 else 140, easing = EaseOutStd)) +
                    fadeOut(tween(if (semMovimento) 0 else 90)),
            ) {
                val r = ultimaResposta
                Column {
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
                            r?.authorName.orEmpty(),
                            style = TextStyle(color = Obsidian.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(Modifier.weight(1f))
                        HoverGlyph(Lucide.X) { vm.cancelReply() }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            var draft by remember(target.id) { mutableStateOf("") }
            var composerFocused by remember(target.id) { mutableStateOf(false) }
            val allCommands = if (botAqui) rememberBotCommands() else emptyList()
            val matches = remember(draft, allCommands) { matchCommands(draft, allCommands) }
            val mencaoAlvo = remember(draft, membros) {
                if (membros.isEmpty()) null else REGEX_MENCAO_ABERTA.find(draft)
            }
            val candidatos = remember(mencaoAlvo, membros) {
                val q = mencaoAlvo?.groupValues?.get(1)?.lowercase() ?: return@remember emptyList()
                membros.asSequence()
                    .filter { m ->
                        q.isEmpty() ||
                            m.user.username.startsWith(q, ignoreCase = true) ||
                            m.user.displayName.orEmpty().startsWith(q, ignoreCase = true)
                    }
                    .take(8)
                    .toList()
            }
            val emojiAlvo = remember(draft, emojisDaSala) {
                if (emojisDaSala.lista.isEmpty()) null else REGEX_EMOJI_ABERTO.find(draft)
            }
            val emojiCandidatos = remember(emojiAlvo, emojisDaSala) {
                val q = emojiAlvo?.groupValues?.get(1)?.lowercase() ?: return@remember emptyList()
                emojisDaSala.lista.asSequence()
                    .filter { it.name.contains(q, ignoreCase = true) }
                    .take(8)
                    .toList()
            }
            val prefixosBot = remember(allCommands) {
                allCommands.map { it.name.substringBefore(' ') }.toSet()
            }
            fun submit() {
                if (draft.isBlank() && state.pending.isEmpty()) return
                val texto = draft.trim()
                val prefixo = prefixosBot.firstOrNull {
                    texto.equals(it, true) || texto.startsWith("$it ", true)
                }
                if (prefixo != null && serverId != null) vm.sendBotCommand(serverId, texto)
                else vm.send(draft)
                draft = ""
            }
            val canSend = draft.isNotBlank() || state.pending.isNotEmpty()
            if (candidatos.isNotEmpty() && matches.isEmpty()) {
                MencaoPalette(candidatos) { escolhido ->
                    val inicio = mencaoAlvo?.range?.first ?: return@MencaoPalette
                    draft = draft.substring(0, inicio) + "@" + escolhido.user.username + " "
                }
                Spacer(Modifier.height(6.dp))
            } else if (emojiCandidatos.isNotEmpty() && matches.isEmpty()) {
                EmojiPalette(emojiCandidatos) { escolhido ->
                    val inicio = emojiAlvo?.range?.first ?: return@EmojiPalette
                    draft = draft.substring(0, inicio) + ":" + escolhido.name + ": "
                }
                Spacer(Modifier.height(6.dp))
            }
            CommandPalette(matches) { picked ->
                draft = picked.name.substringBefore(" <") + " "
            }
            if (matches.isNotEmpty()) Spacer(Modifier.height(6.dp))
            val placeholder = if (target is ChatTarget.Dm) "Mensagem para ${target.title}" else "mensagem em ${target.title}"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Obsidian.raised)
                    .border(
                        1.dp,
                        if (composerFocused) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComposerPlusButton(
                    onPickFiles = vm::addFiles,
                    onCriarEnquete = if (isChannel) ({ enqueteAberta = true }) else null,
                )
                Spacer(Modifier.width(4.dp))
                Box(Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 7.dp)) {
                    if (draft.isEmpty()) {
                        Text(
                            placeholder,
                            style = TextStyle(color = Obsidian.text3, fontSize = 14.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = {
                            draft = it.take(4000)
                            if (it.isNotBlank()) vm.typing()
                        },
                        textStyle = TextStyle(color = Obsidian.text1, fontSize = 14.sp, lineHeight = 20.sp),
                        cursorBrush = SolidColor(Obsidian.accent),
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { composerFocused = it.isFocused }
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) {
                                    submit(); true
                                } else false
                            },
                    )
                }
                if (draft.length > 3600) {
                    Text(
                        "${4000 - draft.length}",
                        style = TextStyle(
                            color = if (draft.length >= 4000) Obsidian.danger else Obsidian.text3,
                            fontSize = 11.sp,
                        ),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                ComposerPickerButton(Seletor.GIF, onPickGif = vm::sendGif)
                Spacer(Modifier.width(4.dp))
                if (serverId != null) {
                    ComposerPickerButton(
                        Seletor.FIGURINHA,
                        serverId = serverId,
                        onPickSticker = vm::sendSticker,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                ComposerPickerButton(
                    Seletor.EMOJI,
                    onPickEmoji = { draft = (draft + it).take(4000) },
                    emojisDaSala = emojisDaSala.lista,
                )
                Spacer(Modifier.width(4.dp))
                SendButton(enabled = canSend) { submit() }
            }
        }
    }

    if (enqueteAberta) {
        CriarEnqueteDialog(
            canalNome = target.title,
            onCriar = { pergunta, opcoes, multipla, prazo ->
                vm.createPoll(pergunta, opcoes, multipla, prazo)
            },
            onClose = { enqueteAberta = false },
        )
    }

    if (dragOver) {
        Box(
            Modifier.fillMaxSize().background(Obsidian.void.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LIcon(Lucide.Download, tint = Obsidian.accent, size = 34.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "solte para anexar em ${target.title}",
                    style = TextStyle(color = Obsidian.text1, fontSize = 14.sp),
                )
            }
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
    onPin: () -> Unit,
    onVote: (String) -> Unit,
    onClosePoll: () -> Unit,
    onRetry: () -> Unit,
    onJumpTo: (String) -> Unit,
    onStartDm: (String, String) -> Unit,
) {
    msg.call?.let { c ->
        LinhaDeChamada(c, minha = msg.mine)
        return
    }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pillInteraction = remember { MutableInteractionSource() }
    val pillHovered by pillInteraction.collectIsHoveredAsState()
    var pickerOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val meMencionou = LocalMinhaConta.current.id?.let { it in msg.mentions } == true
    val rowAlpha = animateFloatAsState(if (msg.deleting) 0f else 1f, tween(FADE_MS), label = "rowAlpha")
    val bg = animateColorAsState(
        when {
            highlighted -> Obsidian.accentDim
            else -> Color.Transparent
        },
        tween(150),
        label = "rowBg",
    )
    val enter = remember { Animatable(if (enterAnim) 0f else 1f) }
    val glow = remember { Animatable(if (enterAnim) 0.16f else 0f) }
    LaunchedEffect(Unit) {
        if (enterAnim) {
            launch { enter.animateTo(1f, tween(150)) }
            glow.animateTo(0f, tween(900, easing = EaseOutSoft))
        }
    }

    EditorialContextMenu(entries = {
        buildList {
            add(MenuEntry.Item("responder", icon = Lucide.Reply) { onReply() })
            if (isChannel) add(MenuEntry.EmojiSub("reagir", QUICK_EMOJIS) { onReact(it) })
            add(MenuEntry.Separator)
            if (msg.content.isNotBlank()) {
                add(MenuEntry.Item("copiar texto", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(msg.content)) })
            }
            add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(msg.id)) })
            if (isChannel) add(MenuEntry.Item("fixar mensagem", icon = Lucide.Pin) { onPin() })
            if (isChannel && msg.mine && msg.content.isNotBlank()) {
                add(MenuEntry.Item("editar", icon = Lucide.Pencil) { onStartEdit() })
            }
            if (msg.mine) {
                add(MenuEntry.Separator)
                add(MenuEntry.Item("excluir", danger = true, icon = Lucide.Trash2) { confirmDelete = true })
            }
        }
    }) {
    if (confirmDelete) {
        ConfirmPopup(
            message = "apagar esta mensagem? não há como desfazer.",
            confirmLabel = "apagar",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value * rowAlpha.value * (if (msg.pending) 0.55f else 1f)
                translationY = (1f - enter.value) * 6.dp.toPx()
            }
            .drawBehind {
                drawRect(bg.value)
                if (glow.value > 0f) drawRect(Obsidian.accent.copy(alpha = glow.value))
                if (meMencionou) {
                    drawRect(Obsidian.accent, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
                }
            }
            .hoverable(interaction),
    ) {
        val dens = LocalMsgDensity.current
        val showPill = (hovered || pillHovered || pickerOpen) && !msg.deleting && !editing
        val pilula: @Composable () -> Unit = {
            if (showPill) {
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 2 },
                ) {
                    ActionPill(
                        canReact = isChannel,
                        canEdit = isChannel && msg.mine && msg.content.isNotBlank(),
                        canDelete = msg.mine,
                        onReply = onReply,
                        onReact = { pickerOpen = true },
                        onEdit = onStartEdit,
                        onDelete = { confirmDelete = true },
                        modifier = Modifier.hoverable(pillInteraction),
                    )
                    if (pickerOpen) {
                        Popup(
                            popupPositionProvider = SobOAlvo,
                            onDismissRequest = { pickerOpen = false },
                            properties = PopupProperties(focusable = true),
                        ) {
                            PopupReveal(originX = 0f, originY = 0f) {
                                ReactionPicker(onPick = { emoji -> onReact(emoji); pickerOpen = false })
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = if (grouped) dens.groupedTopDp.dp else dens.topDp.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (grouped) {
                Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterEnd) {
                    if (hovered) {
                        Text(hhmm(msg.createdAt), style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                PilulaJuntoDoTexto(Modifier.weight(1f), pilula = pilula) {
                    ContentBlock(msg, editing, myId, onReact, onSaveEdit, onCancelEdit, onVote, onClosePoll, onRetry)
                }
            } else {
                ProfileAnchor(msg.authorId, isMe = msg.mine, onStartDm = onStartDm) {
                    DesktopAvatar(msg.authorAvatar, msg.authorName, 34)
                }
                Spacer(Modifier.width(10.dp))
                PilulaJuntoDoTexto(Modifier.weight(1f), pilula = pilula) {
                    msg.replyTo?.let { ref ->
                        ReplyRef(ref, onJumpTo)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = msg.authorName,
                            style = TextStyle(
                                color = LocalCoresDeCargo.current[msg.authorId] ?: Obsidian.text1,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                fontFamily = msg.authorFont?.let { profileFontFamily(it) },
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(hhmm(msg.createdAt), style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
                    }
                    Spacer(Modifier.height(2.dp))
                    ContentBlock(msg, editing, myId, onReact, onSaveEdit, onCancelEdit, onVote, onClosePoll, onRetry)
                }
            }
        }

    }
    }
}

private object SobOAlvo : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val abaixo = anchorBounds.bottom + 6
        val y = if (abaixo + popupContentSize.height <= windowSize.height) abaixo
        else (anchorBounds.top - popupContentSize.height - 6).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentBlock(
    msg: ChatMessage,
    editing: Boolean,
    myId: String?,
    onReact: (String) -> Unit,
    onSaveEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onVote: (String) -> Unit = {},
    onClosePoll: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val scale = LocalMsgFontScale.current
    msg.poll?.let { poll ->
        PollBlock(
            poll = poll,
            myId = myId,
            podeEncerrar = msg.mine,
            onVote = onVote,
            onClose = onClosePoll,
        )
        return
    }
    if (editing) {
        EditField(msg.content, onSaveEdit, onCancelEdit)
    } else if (msg.content.isNotBlank() || msg.edited) {
        val segments = remember(msg.content) { parseSegments(msg.content) }
        val meuUsuario = LocalMinhaConta.current.usuario
        val aoClicarNaMencao = LocalMencaoClicavel.current
        val emojis = LocalEmojisDaSala.current
        val realce = remember(msg.content, emojis) { emojis.realce(msg.content) }
        val fator = if (realce.soEmoji) 2f else 1f
        val corpo = (13 * scale * fator).sp
        val linha = ((if (realce.temPersonalizado) 21 else 19) * scale * fator).sp
        if (segments.size == 1 && segments[0] is Seg.Txt) {
            Text(
                text = remember(msg.content, msg.edited, meuUsuario, emojis) {
                    buildAnnotatedString {
                        appendInlineCoded(msg.content, meuUsuario, aoClicarNaMencao, emojis)
                        if (msg.edited) {
                            withStyle(SpanStyle(color = Obsidian.text3, fontSize = 10.sp)) { append("  (editado)") }
                        }
                    }
                },
                style = TextStyle(color = Obsidian.text2, fontSize = corpo, lineHeight = linha),
                inlineContent = emojis.inline,
            )
        } else {
            segments.forEachIndexed { i, seg ->
                if (i > 0) Spacer(Modifier.height(4.dp))
                when (seg) {
                    is Seg.Txt -> Text(
                        text = remember(seg.s, meuUsuario, emojis) {
                            buildAnnotatedString { appendInlineCoded(seg.s, meuUsuario, aoClicarNaMencao, emojis) }
                        },
                        style = TextStyle(color = Obsidian.text2, fontSize = corpo, lineHeight = linha),
                        inlineContent = emojis.inline,
                    )
                    is Seg.Code -> CodeBox(seg)
                }
            }
            if (msg.edited) {
                Text("(editado)", style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
            }
        }
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
    if (msg.failed) {
        Spacer(Modifier.height(3.dp))
        val src = remember { MutableInteractionSource() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LIcon(Lucide.CircleAlert, tint = Obsidian.danger, size = 12.dp)
            Spacer(Modifier.width(5.dp))
            Text("não enviada", style = TextStyle(color = Obsidian.danger, fontSize = 11.sp))
            Text(" · ", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
            Text(
                "tentar de novo",
                style = TextStyle(color = Obsidian.accent, fontSize = 11.sp),
                modifier = Modifier
                    .clickable(interactionSource = src, indication = null, onClick = onRetry),
            )
        }
    }
}

@Composable
private fun LinhaDeChamada(c: CallLogDto, minha: Boolean) {
    val perdida = c.status != "ended"
    val cor = if (perdida) Obsidian.danger else Obsidian.text3
    val texto = when {
        !perdida -> "chamada de " + duracaoCurta(c.durationSec)
        minha -> "ninguém atendeu"
        else -> "chamada perdida"
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Obsidian.borderDim))
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LIcon(
                if (c.video) Lucide.Video else if (perdida) Lucide.PhoneMissed else Lucide.Phone,
                tint = cor, size = 12.dp,
            )
            Spacer(Modifier.width(7.dp))
            Text(texto, style = TextStyle(color = cor, fontSize = 11.sp, fontFamily = DmMono))
        }
        Box(Modifier.weight(1f).height(1.dp).background(Obsidian.borderDim))
    }
}

private fun duracaoCurta(seg: Int): String = when {
    seg < 60 -> "${seg}s"
    seg < 3600 -> "${seg / 60} min"
    else -> "${seg / 3600}h ${(seg % 3600) / 60}min"
}

private val FIGURINHA_DP = 150.dp

@Composable
private fun AttachmentBlock(att: AttachmentDto) {
    if (att.sticker == true) {
        val w = att.width ?: 0
        val h = att.height ?: 0
        val proporcao = if (w > 0 && h > 0) w.toFloat() / h else null
        AstraImage(
            url = att.url,
            contentDescription = att.name,
            modifier = if (proporcao != null) {
                Modifier.sizeIn(maxWidth = FIGURINHA_DP, maxHeight = FIGURINHA_DP).aspectRatio(proporcao)
            } else {
                Modifier.size(FIGURINHA_DP)
            },
            contentScale = ContentScale.Fit,
        )
        return
    }
    if (att.type?.startsWith("image/") == true) {
        val openImage = LocalOpenImage.current
        val w = att.width ?: 0
        val h = att.height ?: 0
        val proporcao = if (w > 0 && h > 0) w.toFloat() / h else null
        AstraImage(
            url = att.thumbUrl ?: att.url,
            contentDescription = att.name,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .heightIn(max = 240.dp)
                .then(if (proporcao != null) Modifier.aspectRatio(proporcao) else Modifier)
                .clip(RoundedCornerShape(8.dp))
                .clickable { openImage(att.url) },
            contentScale = ContentScale.Fit,
            blurhash = att.blurhash,
            proporcaoBlur = proporcao ?: 1.5f,
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
            LIcon(Lucide.FileText, tint = Obsidian.text2, size = 15.dp)
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

private fun openAttachment(url: String) {
    val abs = if (url.startsWith("/")) AstraShared.BASE_URL.trimEnd('/') + url else url
    runCatching { Desktop.getDesktop().browse(URI(abs)) }
}

private sealed interface Seg {
    data class Txt(val s: String) : Seg
    data class Code(val lang: String?, val s: String) : Seg
}

private fun parseSegments(content: String): List<Seg> {
    if ("```" !in content) return listOf(Seg.Txt(content))
    val out = mutableListOf<Seg>()
    var i = 0
    while (true) {
        val start = content.indexOf("```", i)
        if (start < 0) {
            if (i < content.length) out += Seg.Txt(content.substring(i))
            break
        }
        val end = content.indexOf("```", start + 3)
        if (end < 0) {
            out += Seg.Txt(content.substring(i))
            break
        }
        if (start > i) out += Seg.Txt(content.substring(i, start).trim('\n'))
        val inner = content.substring(start + 3, end)
        val nl = inner.indexOf('\n')
        val lang = if (nl > 0) {
            inner.substring(0, nl).trim().takeIf { it.isNotBlank() && it.length <= 20 && ' ' !in it }
        } else {
            null
        }
        val code = if (lang != null) inner.substring(nl + 1) else inner
        out += Seg.Code(lang, code.trim('\n'))
        i = end + 3
    }
    return out.filter { it !is Seg.Txt || it.s.isNotBlank() }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineCoded(
    s: String,
    meuUsuario: String? = null,
    aoClicar: MencaoClicavel,
    emojis: EmojisDaSala,
) {
    var i = 0
    while (true) {
        val a = s.indexOf('`', i)
        val b = if (a >= 0) s.indexOf('`', a + 1) else -1
        if (a < 0 || b < 0) {
            appendComMencoes(s.substring(i), meuUsuario, aoClicar, emojis)
            return
        }
        appendComMencoes(s.substring(i, a), meuUsuario, aoClicar, emojis)
        withStyle(SpanStyle(fontFamily = DmMono, background = Obsidian.base, fontSize = 12.sp)) {
            append(s.substring(a + 1, b))
        }
        i = b + 1
    }
}

private val REGEX_MENCAO_ABERTA = Regex("@([A-Za-z0-9_]*)$")

@Composable
private fun MencaoPalette(itens: List<ServerMemberDto>, onPick: (ServerMemberDto) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(4.dp),
    ) {
        itens.forEach { m ->
            val src = remember(m.userId) { MutableInteractionSource() }
            val hov by src.collectIsHoveredAsState()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (hov) Obsidian.hover else Color.Transparent)
                    .hoverable(src)
                    .clickable(interactionSource = src, indication = null) { onPick(m) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopAvatar(m.user.avatarUrl, m.user.displayName ?: m.user.username, 22)
                Spacer(Modifier.width(9.dp))
                Text(
                    m.user.displayName ?: m.user.username,
                    style = TextStyle(color = if (hov) Obsidian.text1 else Obsidian.text2, fontSize = 13.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "@" + m.user.username,
                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val REGEX_EMOJI_ABERTO = Regex(":([A-Za-z0-9_]{2,32})$")

@Composable
private fun EmojiPalette(itens: List<EmojiDto>, onPick: (EmojiDto) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(4.dp),
    ) {
        itens.forEach { e ->
            val src = remember(e.id) { MutableInteractionSource() }
            val hov by src.collectIsHoveredAsState()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (hov) Obsidian.hover else Color.Transparent)
                    .hoverable(src)
                    .clickable(interactionSource = src, indication = null) { onPick(e) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AstraImage(
                    url = e.url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    ":${e.name}:",
                    style = TextStyle(
                        color = if (hov) Obsidian.text1 else Obsidian.text2,
                        fontSize = 12.sp,
                        fontFamily = DmMono,
                    ),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val REGEX_MENCAO = Regex("@([A-Za-z0-9_]+)")

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendComMencoes(
    s: String,
    meuUsuario: String?,
    aoClicar: MencaoClicavel,
    emojis: EmojisDaSala,
) {
    var i = 0
    for (m in REGEX_MENCAO.findAll(s)) {
        appendComEmojis(s.substring(i, m.range.first), emojis)
        val usuario = m.groupValues[1]
        val minha = !meuUsuario.isNullOrBlank() && usuario.equals(meuUsuario, ignoreCase = true)
        val repouso = SpanStyle(
            color = Obsidian.accent,
            fontWeight = FontWeight.Medium,
            background = if (minha) Obsidian.accent.copy(alpha = 0.16f) else Color.Transparent,
        )
        val aceso = repouso.copy(background = Obsidian.accent.copy(alpha = if (minha) 0.30f else 0.14f))
        withLink(
            LinkAnnotation.Clickable(
                tag = usuario,
                styles = TextLinkStyles(style = repouso, hoveredStyle = aceso, pressedStyle = aceso),
            ) { aoClicar.abrir(usuario) },
        ) { append(m.value) }
        i = m.range.last + 1
    }
    appendComEmojis(s.substring(i), emojis)
}

@Composable
private fun CodeBox(code: Seg.Code) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.base)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp)),
    ) {
        SelectionContainer {
            Text(
                code.s,
                style = TextStyle(
                    color = Obsidian.text2,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = DmMono,
                ),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .padding(top = if (code.lang != null) 8.dp else 0.dp),
            )
        }
        code.lang?.let {
            Text(
                it,
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
                modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
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

@Composable
private fun ReplyRef(ref: ReplyToDto, onJumpTo: (String) -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clickable(interactionSource = src, indication = null) { onJumpTo(ref.id) }
            .padding(bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(Lucide.Reply, tint = Obsidian.text3, size = 13.dp)
        Spacer(Modifier.width(5.dp))
        Text(
            ref.authorName ?: "alguem",
            style = TextStyle(
                color = ref.authorId?.let { LocalCoresDeCargo.current[it] } ?: Obsidian.accent,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            ),
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
private fun PilulaJuntoDoTexto(
    modifier: Modifier = Modifier,
    pilula: @Composable () -> Unit,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val densidade = LocalDensity.current
    val respiro = with(densidade) { 8.dp.roundToPx() }
    val subida = with(densidade) { 10.dp.roundToPx() }
    Layout(
        contents = listOf({ Column(content = conteudo) }, pilula),
        modifier = modifier,
    ) { (medidosConteudo, medidosPilula), constraints ->
        val conteudoMedido = medidosConteudo.first().measure(constraints.copy(minWidth = 0))
        val pilulaMedida = medidosPilula.firstOrNull()?.measure(Constraints())
        val largura = constraints.maxWidth
        layout(largura, conteudoMedido.height) {
            conteudoMedido.place(0, 0)
            if (pilulaMedida != null) {
                val x = (conteudoMedido.width + respiro)
                    .coerceAtMost(largura - pilulaMedida.width)
                    .coerceAtLeast(0)
                pilulaMedida.place(x, -subida)
            }
        }
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
        PillButton(Lucide.Reply, onReply)
        if (canReact) PillButton(Lucide.SmilePlus, onReact)
        if (canEdit) PillButton(Lucide.Pencil, onEdit)
        if (canDelete) PillButton(Lucide.Trash2, onDelete, danger = true)
    }
}

@Composable
private fun PillButton(icon: ImageVector, onClick: () -> Unit, danger: Boolean = false) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(26.dp)
            .clickScale(src)
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
        LIcon(icon, tint = if (danger && hov) Obsidian.danger else Obsidian.text2, size = 15.dp)
    }
}

@Composable
internal fun ReactionPicker(onPick: (String) -> Unit, personalizados: List<EmojiDto> = emptyList()) {
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
            EmojiCell(if (expanded) Lucide.Minus else Lucide.Plus) { expanded = !expanded }
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            EmojiPicker(onPick = onPick, personalizados = personalizados)
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

@Composable
private fun EmojiCell(icon: ImageVector, onClick: () -> Unit) {
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
        LIcon(icon, tint = Obsidian.text2, size = 16.dp)
    }
}

@Composable
private fun HoverGlyph(icon: ImageVector, onClick: () -> Unit) {
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
        LIcon(icon, tint = Obsidian.text3, size = 12.dp)
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (enabled) Obsidian.text1 else Color.Transparent,
        tween(170), label = "sendBg",
    )
    val fg by animateColorAsState(
        if (enabled) Obsidian.void else Obsidian.text3,
        tween(170), label = "sendFg",
    )
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (enabled) bg else if (hov) Obsidian.hover else Color.Transparent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(Lucide.ArrowUp, tint = fg, size = 17.dp)
    }
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
    }
}

@Composable
private fun EmptyChat() {
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
                "nada por aqui ainda — comece a conversa",
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun AcordandoOServidor() {
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
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun PalcoQueFalhou(motivo: String, podeTentar: Boolean, onRetry: () -> Unit) {
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
