package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.composables.icons.lucide.ChevronDown
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.astra.desktop.ui.theme.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.FileImage
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.shell.ChatMessage
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.ChatVm
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import org.koin.core.context.GlobalContext
import app.astra.mobile.core.network.dto.EmojiDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.astra.desktop.AreaDeTransferencia
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import app.astra.desktop.ui.theme.Tipo

private val HHMM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

internal const val FADE_MS = 340
internal const val REALCE_MS = 150
internal const val REALCE_PAUSA_MS = 300L

internal val QUICK_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

internal fun hhmm(iso: String?): String =
    iso?.let { runCatching { HHMM.format(Instant.parse(it)) }.getOrNull() } ?: ""

private val DIA_DO_MES = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("pt", "BR"))
private val DIA_COM_ANO = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

private const val JANELA_DO_GRUPO_MIN = 5L

private val LARGURA_DO_TRACO = 28.dp

private const val KIND_PASSAGEM_DE_TURNO = "passagem"

internal sealed interface LinhaDoChat {
    data class Dia(val chave: String, val rotulo: String) : LinhaDoChat
    data class Fala(val msg: ChatMessage, val agrupada: Boolean) : LinhaDoChat
    data class Passagem(val id: String, val texto: String) : LinhaDoChat
}

private fun rotuloDoDia(dia: LocalDate, hoje: LocalDate): String = when (dia) {
    hoje -> "hoje"
    hoje.minusDays(1) -> "ontem"
    else -> (if (dia.year == hoje.year) DIA_DO_MES else DIA_COM_ANO).format(dia)
}

private fun montarLinhas(mensagens: List<ChatMessage>): List<LinhaDoChat> {
    if (mensagens.isEmpty()) return emptyList()
    val zona = ZoneId.systemDefault()
    val hoje = LocalDate.now(zona)
    val linhas = ArrayList<LinhaDoChat>(mensagens.size + 4)
    var diaAnterior: LocalDate? = null
    var instanteAnterior: Instant? = null
    var anterior: ChatMessage? = null

    for (m in mensagens) {
        val instante = runCatching { Instant.parse(m.createdAt) }.getOrNull()
        val dia = instante?.atZone(zona)?.toLocalDate()
        if (dia != null && dia != diaAnterior) {
            linhas += LinhaDoChat.Dia(dia.toString(), rotuloDoDia(dia, hoje))
            diaAnterior = dia
            anterior = null
            instanteAnterior = null
        }
        if (m.kind == KIND_PASSAGEM_DE_TURNO) {
            linhas += LinhaDoChat.Passagem(m.id, m.content)
            anterior = null
            instanteAnterior = null
            continue
        }
        val mesmaIdentidade = anterior != null &&
            anterior.authorId == m.authorId &&
            anterior.authorName == m.authorName &&
            anterior.authorAvatar == m.authorAvatar
        val agrupada = m.replyTo == null &&
            mesmaIdentidade &&
            instante != null && instanteAnterior != null &&
            Duration.between(instanteAnterior, instante).toMinutes() < JANELA_DO_GRUPO_MIN
        linhas += LinhaDoChat.Fala(m, agrupada)
        anterior = m
        if (instante != null) instanteAnterior = instante
    }
    return linhas
}

private fun ondeEsta(linhas: List<LinhaDoChat>, messageId: String): Int =
    linhas.indexOfFirst {
        (it is LinhaDoChat.Fala && it.msg.id == messageId) || (it is LinhaDoChat.Passagem && it.id == messageId)
    }

private data class AncoraDoHistorico(val chave: String, val deslocamento: Int, val quantasTinha: Int)

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun ChatView(
    target: ChatTarget,
    vm: ChatVm,
    onStartDm: (String, String) -> Unit,
    botAqui: Boolean = true,
    serverId: String? = null,
    membros: List<ServerMemberDto> = emptyList(),
    lidoAte: String? = null,
) {
    val state by vm.state.collectAsState()
    val listState = lembrarListaQueSeAdianta()
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

    var marcaDeLeitura by remember(target.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(target.id, lidoAte) {
        if (marcaDeLeitura == null) marcaDeLeitura = lidoAte
    }
    val primeiraNaoLida = remember(target.id, marcaDeLeitura, state.messages) {
        val marca = marcaDeLeitura
        if (marca == null) null
        else state.messages.firstOrNull { m ->
            m.authorId != vm.myId && (m.createdAt ?: "") > marca
        }?.id
    }

    val linhas = remember(state.messages) { montarLinhas(state.messages) }

    val noPresente by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val ultimo = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            ultimo.index >= info.totalItemsCount - 3
        }
    }

    var vistaNoPresente by remember(target.id) { mutableStateOf<Instant?>(null) }
    LaunchedEffect(target.id, noPresente, state.messages.lastOrNull()?.id) {
        if (noPresente) vistaNoPresente = instanteDaMaisRecente(state.messages)
    }
    val novas = remember(state.messages, vistaNoPresente) { contarNovas(state.messages, vistaNoPresente) }

    var ultimaVista by remember(target.id) { mutableStateOf<String?>(null) }
    var pousouNoFim by remember(target.id) { mutableStateOf(false) }
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        val ultima = state.messages.lastOrNull() ?: return@LaunchedEffect
        if (ultima.id == ultimaVista) return@LaunchedEffect
        val primeiraCarga = ultimaVista == null
        ultimaVista = ultima.id
        if (primeiraCarga || noPresente || vm.foiEnviadaDaqui(ultima)) {
            if (linhas.isNotEmpty()) listState.scrollToItem(linhas.lastIndex)
        }
        pousouNoFim = true
    }

    val pertoDoTopo by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) false
            else (info.visibleItemsInfo.firstOrNull()?.index ?: 0) <= 10
        }
    }

    var ancora by remember(target.id) { mutableStateOf<AncoraDoHistorico?>(null) }
    LaunchedEffect(pertoDoTopo, state.cursorAnterior, pousouNoFim) {
        if (!pousouNoFim || !pertoDoTopo || state.cursorAnterior == null) return@LaunchedEffect
        val alvo = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { (it.key as? String)?.startsWith("dia:") == false } ?: return@LaunchedEffect
        val quantasTinha = state.messages.size
        if (vm.carregarAnteriores()) {
            ancora = AncoraDoHistorico(alvo.key as String, alvo.offset, quantasTinha)
        }
    }
    LaunchedEffect(linhas, state.buscandoAnteriores) {
        val marca = ancora ?: return@LaunchedEffect
        if (state.buscandoAnteriores) return@LaunchedEffect
        ancora = null
        if (state.messages.size <= marca.quantasTinha) return@LaunchedEffect
        val onde = ondeEsta(linhas, marca.chave)
        if (onde >= 0) listState.scrollToItem(onde, -marca.deslocamento)
    }

    fun jumpTo(id: String) {
        val idx = ondeEsta(linhas, id)
        if (idx < 0) return
        scope.launch {
            listState.animateScrollToItem(idx)
            highlightId = id
            delay(REALCE_MS * 2L + REALCE_PAUSA_MS)
            highlightId = null
        }
    }

    var lightboxUrl by remember { mutableStateOf<String?>(null) }
    lightboxUrl?.let { Lightbox(it) { lightboxUrl = null } }

    var mensagemAApagar by remember(target.id) { mutableStateOf<String?>(null) }
    val aindaExiste = mensagemAApagar?.let { ondeEsta(linhas, it) >= 0 } == true
    LaunchedEffect(aindaExiste) { if (!aindaExiste) mensagemAApagar = null }
    mensagemAApagar?.takeIf { aindaExiste }?.let { id ->
        Confirmacao(
            titulo = "apagar mensagem",
            consequencia = "ela some da conversa para todos, e não há como desfazer.",
            acao = "apagar",
            aoConfirmar = {
                mensagemAApagar = null
                vm.delete(id)
            },
            aoFechar = { mensagemAApagar = null },
        )
    }

    val pulo = LocalPuloParaMensagem.current
    SideEffect {
        pulo.estaCarregada = { id -> ondeEsta(linhas, id) >= 0 }
        pulo.pular = { id -> jumpTo(id) }
    }

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
    val meuId = LocalMinhaConta.current.id
    val falas = remember(linhas, primeiraNaoLida, meuId) { falasPorMensagem(linhas, primeiraNaoLida, meuId) }
    val alturas = remember(target.id) { mutableStateMapOf<String, Int>() }
    val larguras = remember(target.id) { mutableStateMapOf<String, Int>() }
    Column(Modifier.fillMaxSize()) {
        EsperaNoTopo(state.loading && !state.acordando)
        Box(Modifier.weight(1f)) {
            when {
                state.loading && state.acordando -> AcordandoOServidor()
                state.loading -> Unit
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
                    linhas.forEachIndexed { i, linha -> when (linha) {
                        is LinhaDoChat.Dia -> item(key = "dia:${linha.chave}", contentType = "dia") {
                            SeparadorDoChat(linha.rotulo)
                        }
                        is LinhaDoChat.Passagem -> item(key = linha.id, contentType = "dia") {
                            SeparadorDoChat(linha.texto)
                        }
                        is LinhaDoChat.Fala -> {
                            val anterior = linhas.getOrNull(i - 1) as? LinhaDoChat.Fala
                            val proxima = linhas.getOrNull(i + 1) as? LinhaDoChat.Fala
                            val abre = abreUmCartao(linha, anterior, primeiraNaoLida, meuId)
                            val fecha = proxima == null || abreUmCartao(proxima, linha, primeiraNaoLida, meuId)
                            item(
                            key = linha.msg.id,
                            contentType = when {
                                linha.msg.poll != null -> "enquete"
                                linha.msg.attachments.isNotEmpty() -> "anexo"
                                else -> "texto"
                            },
                        ) {
                            val msg = linha.msg
                            if (msg.id == primeiraNaoLida) MarcaDeNovasMensagens()
                            MessageRow(
                                msg = msg,
                                abreOCartao = abre,
                                fechaOCartao = fecha,
                                idsDaFala = falas[msg.id].orEmpty(),
                                alturas = alturas,
                                aoMedir = { altura -> if (alturas[msg.id] != altura) alturas[msg.id] = altura },
                                larguras = larguras,
                                aoMedirLargura = { largura -> if (larguras[msg.id] != largura) larguras[msg.id] = largura },
                                isChannel = isChannel,
                                highlighted = msg.id == highlightId,
                                editing = msg.id == editingId,
                                myId = vm.myId,
                                onReply = { vm.startReply(msg) },
                                onReact = { emoji -> vm.react(msg.id, emoji) },
                                onStartEdit = { editingId = msg.id },
                                onSaveEdit = { text -> vm.edit(msg.id, text); editingId = null },
                                onCancelEdit = { editingId = null },
                                onDelete = { mensagemAApagar = msg.id },
                                onPin = { vm.pin(msg.id) },
                                onVote = { opcao -> vm.vote(msg.id, opcao) },
                                onClosePoll = { vm.closePoll(msg.id) },
                                onRetry = { vm.retry(msg) },
                                onJumpTo = { id -> jumpTo(id) },
                                onStartDm = onStartDm,
                            )
                        }
                        }
                    } }
                }
            }

            VoltarAoPresente(
                visivel = !noPresente && state.messages.isNotEmpty(),
                novas = novas,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 12.dp),
            ) {
                scope.launch { if (linhas.isNotEmpty()) listState.animateScrollToItem(linhas.lastIndex) }
            }
        }

        EscopoProprio {
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
                        1 -> "${names[0]} está digitando…"
                        2 -> "${names[0]} e ${names[1]} estão digitando…"
                        else -> "várias pessoas estão digitando…"
                    }
                    Text(label, style = Tipo.apoio)
                }
            }
            if (state.error != null && state.messages.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.error!!, style = Tipo.erro)
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
            state.avisoDoAnexo?.let { aviso ->
                Text(aviso, style = Tipo.erro)
                Spacer(Modifier.height(6.dp))
            }
            if (state.pending.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.pending.forEach { pf ->
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
                                pf.nome,
                                style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            val tamanhoDoArquivo = remember(pf.file) { pf.file.length() }
                            Text(
                                when {
                                    pf.comprimindo == null -> sizeLabel(tamanhoDoArquivo)
                                    pf.comprimindo < 0.01f -> "preparando o vídeo"
                                    else -> "comprimindo ${(pf.comprimindo * 100).toInt()}%"
                                },
                                style = Tipo.nota,
                            )
                            Spacer(Modifier.width(6.dp))
                            HoverGlyph(Lucide.X, "remover anexo") { vm.removePending(pf.id) }
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
                        Text("respondendo a ", style = Tipo.apoio)
                        Text(
                            r?.authorName.orEmpty(),
                            style = TextStyle(color = Obsidian.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(Modifier.weight(1f))
                        HoverGlyph(Lucide.X, "cancelar resposta") { vm.cancelReply() }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            var draft by remember(target.id) { mutableStateOf("") }
            var composerFocused by remember(target.id) { mutableStateOf(false) }
            var envios by remember(target.id) { mutableIntStateOf(0) }
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
            val comprimindo = state.pending.any { it.comprimindo != null }
            fun submit() {
                if (draft.isBlank() && state.pending.isEmpty()) return
                if (comprimindo) return
                val texto = draft.trim()
                val prefixo = prefixosBot.firstOrNull {
                    texto.equals(it, true) || texto.startsWith("$it ", true)
                }
                if (prefixo != null && serverId != null) vm.sendBotCommand(serverId, texto)
                else vm.send(draft)
                draft = ""
                envios++
            }
            val canSend = (draft.isNotBlank() || state.pending.isNotEmpty()) && !comprimindo
            Column(Modifier.fillMaxWidth().then(lugarDoPetNoCampo())) {
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
                val placeholder = if (target is ChatTarget.Dm) "Sussurre para ${target.title}…" else "Escreva em #${target.title}…"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .marcoDoTour(Marco.ESCREVER)
                        .background(Obsidian.raised, RoundedCornerShape(12.dp))
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
                                style = TextStyle(
                                    color = Obsidian.text3,
                                    fontSize = 17.sp,
                                    lineHeight = 22.sp,
                                    fontFamily = DmSerif,
                                    fontStyle = FontStyle.Italic,
                                ),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = {
                                draft = it.take(4000)
                                if (it.isNotBlank()) vm.typing()
                            },
                            textStyle = TextStyle(color = Obsidian.text1, fontSize = 15.sp, lineHeight = 22.sp),
                            cursorBrush = SolidColor(Obsidian.accent),
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { composerFocused = it.isFocused }
                                .onPreviewKeyEvent { e ->
                                    when {
                                        e.type != KeyEventType.KeyDown -> false
                                        e.key == Key.Enter && !e.isShiftPressed -> { submit(); true }
                                        ehColar(e) && AreaDeTransferencia.temAlgoParaAnexar() -> {
                                            scope.launch { vm.addFiles(AreaDeTransferencia.anexosColados()) }
                                            true
                                        }
                                        else -> false
                                    }
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
                    EstrelaDeEnviar(acesa = canSend, envios = envios) { submit() }
                }
            }
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
private fun EscopoProprio(conteudo: @Composable () -> Unit) {
    conteudo()
}

private fun ehColar(e: KeyEvent): Boolean =
    (e.isCtrlPressed && e.key == Key.V) || (e.isShiftPressed && e.key == Key.Insert)

internal fun mencionaVoce(msg: ChatMessage, meuId: String?): Boolean = meuId != null && meuId in msg.mentions

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

@Composable
private fun HoverGlyph(icon: ImageVector, rotulo: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(24.dp)
            .clickScale(src, formaDoFoco = RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(if (hov) Obsidian.hover else Color.Transparent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, tint = Obsidian.text3, size = 12.dp, rotulo = rotulo)
    }
}

@Composable
private fun EstrelaDeEnviar(acesa: Boolean, envios: Int, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val reduzir = LocalReduceMotion.current
    val luz by animateFloatAsState(
        if (acesa) 1f else 0f,
        if (reduzir) snap() else tween(TROCA_DA_ESTRELA_MS, easing = EaseOutStd),
        label = "estrelaDeEnviar",
    )
    val apagada = Obsidian.text3
    val cor = Obsidian.accent
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clickScale(src, formaDoFoco = CircleShape)
                .clip(CircleShape)
                .background(if (hov && acesa) Obsidian.hover else Color.Transparent)
                .hoverable(src)
                .clickable(interactionSource = src, indication = null, enabled = acesa, onClick = onClick)
                .semantics { contentDescription = "enviar" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(18.dp)) {
                val r = size.minDimension / 2f
                if (luz < 1f) {
                    drawPath(
                        estrelaDeQuatroPontas(center, r * 0.86f),
                        apagada.copy(alpha = apagada.alpha * (1f - luz)),
                        style = Stroke(width = 1.4.dp.toPx(), join = StrokeJoin.Round),
                    )
                }
                if (luz > 0f) {
                    drawPath(estrelaDeQuatroPontas(center, r * (0.8f + 0.2f * luz)), cor.copy(alpha = cor.alpha * luz))
                }
            }
        }
        AnimacaoDeArtista(
            arquivo = "brilho-de-envio",
            vez = envios,
            luz = cor,
            modifier = Modifier.requiredSize(40.dp),
            quadros = 0..QUADRO_FINAL_DO_BRILHO,
            velocidade = VELOCIDADE_DO_BRILHO,
        )
    }
}

private const val TROCA_DA_ESTRELA_MS = 180
private const val QUADRO_FINAL_DO_BRILHO = 23
private const val VELOCIDADE_DO_BRILHO = 2.5f

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
    }
}

@Composable
private fun SeparadorDoChat(rotulo: String) {
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
private fun MarcaDeNovasMensagens() {
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
private fun VoltarAoPresente(
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

private fun instanteDaMaisRecente(mensagens: List<ChatMessage>): Instant? =
    mensagens.lastOrNull { !it.pending && !it.failed }?.let(::instanteDe)

private fun contarNovas(mensagens: List<ChatMessage>, vista: Instant?): Int {
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
                "nenhuma mensagem ainda. Comece a conversa.",
                style = Tipo.descricao,
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
                style = Tipo.apoio,
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
