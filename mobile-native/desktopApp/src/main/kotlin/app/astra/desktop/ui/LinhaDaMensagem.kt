package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.shell.ChatMessage
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.dto.EmojiDto
import app.astra.mobile.core.network.dto.ReplyToDto
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Reply
import com.composables.icons.lucide.SmilePlus
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.delay

internal fun abreUmCartao(
    fala: LinhaDoChat.Fala,
    anterior: LinhaDoChat.Fala?,
    primeiraNaoLida: String?,
    meuId: String?,
): Boolean =
    anterior == null || !fala.agrupada ||
        fala.msg.call != null || anterior.msg.call != null ||
        fala.msg.id == primeiraNaoLida ||
        mencionaVoce(fala.msg, meuId) || mencionaVoce(anterior.msg, meuId)

internal fun falasPorMensagem(
    linhas: List<LinhaDoChat>,
    primeiraNaoLida: String?,
    meuId: String?,
): Map<String, List<String>> {
    val porMensagem = HashMap<String, List<String>>()
    var atual = ArrayList<String>()
    linhas.forEachIndexed { i, linha ->
        if (linha !is LinhaDoChat.Fala) return@forEachIndexed
        if (abreUmCartao(linha, linhas.getOrNull(i - 1) as? LinhaDoChat.Fala, primeiraNaoLida, meuId)) atual = ArrayList()
        atual += linha.msg.id
        porMensagem[linha.msg.id] = atual
    }
    return porMensagem
}

private class MedidaDaFala(val acima: Float, val acimaCompleto: Boolean, val abaixo: Float, val abaixoCompleto: Boolean) {
    val inteira: Boolean get() = acimaCompleto && abaixoCompleto
}

private fun medirFala(ids: List<String>, eu: String, alturas: Map<String, Int>): MedidaDaFala {
    var acima = 0f
    var acimaCompleto = true
    var abaixo = 0f
    var abaixoCompleto = true
    var passou = false
    for (id in ids) {
        if (id == eu) {
            passou = true
            continue
        }
        val altura = alturas[id]
        when {
            altura == null && passou -> abaixoCompleto = false
            altura == null -> acimaCompleto = false
            passou -> abaixo += altura
            else -> acima += altura
        }
    }
    return MedidaDaFala(acima, acimaCompleto, abaixo, abaixoCompleto)
}

private fun maiorLargura(ids: List<String>, larguras: Map<String, Int>): Int =
    ids.maxOfOrNull { larguras[it] ?: 0 } ?: 0

private fun DrawScope.direitaDaFala(ids: List<String>, larguras: Map<String, Int>): Float {
    val conteudo = maiorLargura(ids, larguras)
    if (conteudo == 0) return size.width
    return minOf(size.width, (INICIO_DO_TEXTO + RECUO_DA_LINHA).toPx() + conteudo)
}

private fun DrawScope.semibordas(abre: Boolean, fecha: Boolean, cor: Color, fala: MedidaDaFala, direita: Float) {
    val traco = 1.dp.toPx()
    val meio = traco / 2f
    val raio = CANTO_DA_SEMIBORDA.toPx()
    val alto = size.height
    val deitada = maxOf(direita * PONTA_DEITADA, raio)
    val tetoEmPe = maxOf(raio, PONTA_EM_PE_MAXIMA.toPx())
    val emPeSemTeto = (fala.acima + alto + fala.abaixo) * PONTA_EM_PE
    if (emPeSemTeto < tetoEmPe && !fala.inteira) return
    val emPe = emPeSemTeto.coerceIn(raio, tetoEmPe)
    val estilo = Stroke(width = traco, cap = StrokeCap.Round)
    if (abre) {
        drawPath(
            Path().apply {
                moveTo(meio, minOf(meio + emPe, alto))
                lineTo(meio, meio + raio)
                quadraticTo(meio, meio, meio + raio, meio)
                lineTo(meio + deitada, meio)
            },
            cor,
            style = estilo,
        )
    } else if (fala.acimaCompleto) {
        val de = (raio - fala.acima).coerceAtLeast(0f)
        val ate = (emPe - fala.acima).coerceAtMost(alto)
        if (ate > de) drawLine(cor, Offset(meio, de), Offset(meio, ate), traco)
    }
    val x = direita - meio
    if (fecha) {
        val y = alto - meio
        drawPath(
            Path().apply {
                moveTo(x, maxOf(y - emPe, 0f))
                lineTo(x, y - raio)
                quadraticTo(x, y, x - raio, y)
                lineTo(x - deitada, y)
            },
            cor,
            style = estilo,
        )
    } else if (fala.abaixoCompleto) {
        val fundo = alto + fala.abaixo
        val de = (fundo - emPe).coerceAtLeast(0f)
        val ate = (fundo - raio).coerceAtMost(alto)
        if (ate > de) drawLine(cor, Offset(x, de), Offset(x, ate), traco)
    }
}

private val CANTO_DA_SEMIBORDA = 8.dp
private val MARGEM_DA_FALA = 14.dp
private val RECUO_DA_LINHA = 12.dp
private val LARGURA_DO_AVATAR = 34.dp
private val ESPACO_ATE_O_TEXTO = 10.dp
private val INICIO_DO_TEXTO = RECUO_DA_LINHA + LARGURA_DO_AVATAR + ESPACO_ATE_O_TEXTO
private val PONTA_EM_PE_MAXIMA = 120.dp
private const val PONTA_DEITADA = 0.25f
private const val PONTA_EM_PE = 0.5f
private const val ALFA_DA_MENCAO = 0.7f
private const val RESPIRO_DA_CARTA = 0.75f

@Composable
internal fun MessageRow(
    msg: ChatMessage,
    abreOCartao: Boolean,
    fechaOCartao: Boolean,
    idsDaFala: List<String>,
    alturas: Map<String, Int>,
    aoMedir: (Int) -> Unit,
    larguras: Map<String, Int>,
    aoMedirLargura: (Int) -> Unit,
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
    val clipboard = LocalClipboardManager.current

    val grouped = !abreOCartao
    val meMencionou = mencionaVoce(msg, LocalMinhaConta.current.id)
    val opacidade = if (!msg.deleting) 1f else {
        val sumindo = remember { Animatable(1f) }
        LaunchedEffect(Unit) { sumindo.animateTo(0f, tween(FADE_MS)) }
        sumindo.value
    }
    val realce = if (!highlighted) 0f else {
        val aceso = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            aceso.animateTo(1f, tween(REALCE_MS))
            delay(REALCE_PAUSA_MS)
            aceso.animateTo(0f, tween(REALCE_MS))
        }
        aceso.value
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
            if (isChannel) {
                val rotuloDoAlfinete = if (msg.pinned) "desafixar mensagem" else "fixar mensagem"
                add(MenuEntry.Item(rotuloDoAlfinete, icon = Lucide.Pin) { onPin() })
            }
            if (isChannel && msg.mine && msg.content.isNotBlank()) {
                add(MenuEntry.Item("editar", icon = Lucide.Pencil) { onStartEdit() })
            }
            if (msg.mine) {
                add(MenuEntry.Separator)
                add(MenuEntry.Item("excluir", danger = true, icon = Lucide.Trash2) { onDelete() })
            }
        }
    }) {
    val dens = LocalMsgDensity.current
    val respiroDaCarta = (dens.topDp * RESPIRO_DA_CARTA).dp
    val espacoEntreCartas = (dens.groupedTopDp * 2).dp
    val corDaSemiborda = when {
        meMencionou -> Obsidian.accent.copy(alpha = ALFA_DA_MENCAO)
        msg.mine -> Obsidian.borderMid
        else -> Obsidian.borderDim
    }
    val formaDoRealce = RoundedCornerShape(
        topStart = if (abreOCartao) CANTO_DA_SEMIBORDA else 0.dp,
        topEnd = if (abreOCartao) CANTO_DA_SEMIBORDA else 0.dp,
        bottomStart = if (fechaOCartao) CANTO_DA_SEMIBORDA else 0.dp,
        bottomEnd = if (fechaOCartao) CANTO_DA_SEMIBORDA else 0.dp,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MARGEM_DA_FALA)
            .padding(top = if (abreOCartao) espacoEntreCartas else 0.dp)
            .onSizeChanged { aoMedir(it.height) }
            .graphicsLayer {
                alpha = opacidade * (if (msg.pending) 0.55f else 1f)
            }
            .drawBehind {
                val direita = direitaDaFala(idsDaFala, larguras)
                if (realce > 0f) {
                    val aceso = Obsidian.accentDim
                    drawOutline(
                        formaDoRealce.createOutline(Size(direita, size.height), layoutDirection, this),
                        aceso.copy(alpha = aceso.alpha * realce),
                    )
                }
                semibordas(
                    abreOCartao,
                    fechaOCartao,
                    corDaSemiborda,
                    medirFala(idsDaFala, msg.id, alturas),
                    direita,
                )
            }
            .hoverable(interaction),
    ) {
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
                        onDelete = onDelete,
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
                .padding(horizontal = RECUO_DA_LINHA)
                .padding(
                    top = if (grouped) dens.groupedTopDp.dp else respiroDaCarta,
                    bottom = if (fechaOCartao) respiroDaCarta else 2.dp,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            if (grouped) {
                Box(Modifier.width(LARGURA_DO_AVATAR), contentAlignment = Alignment.CenterEnd) {
                    if (hovered) {
                        Text(hhmm(msg.createdAt), style = Tipo.nota)
                    }
                }
                Spacer(Modifier.width(ESPACO_ATE_O_TEXTO))
                PilulaJuntoDoTexto(
                    Modifier.weight(1f),
                    pilula = pilula,
                    aoMedirConteudo = aoMedirLargura,
                    larguraDaFala = { maiorLargura(idsDaFala, larguras) },
                ) {
                    ContentBlock(msg, editing, myId, onReact, onSaveEdit, onCancelEdit, onVote, onClosePoll, onRetry)
                }
            } else {
                ProfileAnchor(msg.authorId, isMe = msg.mine, onStartDm = onStartDm) {
                    DesktopAvatar(msg.authorAvatar, msg.authorName, LARGURA_DO_AVATAR.value.toInt(), animar = hovered)
                }
                Spacer(Modifier.width(ESPACO_ATE_O_TEXTO))
                PilulaJuntoDoTexto(
                    Modifier.weight(1f),
                    pilula = pilula,
                    aoMedirConteudo = aoMedirLargura,
                    larguraDaFala = { maiorLargura(idsDaFala, larguras) },
                ) {
                    msg.replyTo?.let { ref ->
                        ReplyRef(ref, onJumpTo)
                    }
                    Row {
                        val fonte = profileFontFamily(msg.authorFont)
                        NomeColorido(
                            texto = msg.authorName,
                            cor = LocalCoresDeCargo.current[msg.authorId],
                            padrao = Obsidian.text1,
                            fontSize = if (fonte == DmSerif) 17.sp else 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = fonte,
                            modifier = Modifier.alignByBaseline().weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(hhmm(msg.createdAt), style = Tipo.nota, modifier = Modifier.alignByBaseline())
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

@Composable
private fun ReplyRef(ref: ReplyToDto, onJumpTo: (String) -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .clickable(interactionSource = src, indication = null) { onJumpTo(ref.id) }
            .padding(bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(Lucide.Reply, tint = Obsidian.text3, size = 13.dp)
        Spacer(Modifier.width(5.dp))
        NomeColorido(
            texto = ref.authorName ?: "alguém",
            cor = ref.authorId?.let { LocalCoresDeCargo.current[it] },
            padrao = Obsidian.accent,
            fontSize = 11.sp,
            modifier = Modifier.widthIn(max = 200.dp),
            fontWeight = FontWeight.SemiBold,
            fontFamily = ref.authorFont?.let { profileFontFamily(it) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            ref.content,
            style = Tipo.apoio,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PilulaJuntoDoTexto(
    modifier: Modifier = Modifier,
    pilula: @Composable () -> Unit,
    aoMedirConteudo: (Int) -> Unit,
    larguraDaFala: () -> Int,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val densidade = LocalDensity.current
    val respiro = with(densidade) { 8.dp.roundToPx() }
    val subida = with(densidade) { 10.dp.roundToPx() }
    val recuo = with(densidade) { RECUO_DA_LINHA.roundToPx() }
    Layout(
        contents = listOf({ Column(Modifier.onSizeChanged { aoMedirConteudo(it.width) }, content = conteudo) }, pilula),
        modifier = modifier,
    ) { (medidosConteudo, medidosPilula), constraints ->
        val conteudoMedido = medidosConteudo.first().measure(constraints.copy(minWidth = 0))
        val pilulaMedida = medidosPilula.firstOrNull()?.measure(Constraints())
        val largura = constraints.maxWidth
        layout(largura, conteudoMedido.height) {
            conteudoMedido.place(0, 0)
            if (pilulaMedida != null) {
                val larguraDaPilula = pilulaMedida.width
                val borda = recuo + minOf(maxOf(larguraDaFala(), conteudoMedido.width), largura)
                val aoLado = conteudoMedido.width + respiro
                val encostaNaBorda = aoLado + larguraDaPilula + respiro > borda
                val cabeDepoisDaBorda = borda + respiro + larguraDaPilula <= largura
                val x = if (encostaNaBorda && cabeDepoisDaBorda) borda + respiro else aoLado
                pilulaMedida.place(x.coerceAtMost(largura - larguraDaPilula).coerceAtLeast(0), -subida)
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
        PillButton(Lucide.Reply, "responder", onReply)
        if (canReact) PillButton(Lucide.SmilePlus, "reagir", onReact)
        if (canEdit) PillButton(Lucide.Pencil, "editar", onEdit)
        if (canDelete) PillButton(Lucide.Trash2, "excluir", onDelete, danger = true)
    }
}

@Composable
private fun PillButton(
    icon: ImageVector,
    rotulo: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
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
        LIcon(
            icon,
            tint = if (danger && hov) Obsidian.danger else Obsidian.text2,
            size = 15.dp,
            rotulo = rotulo,
        )
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
            EmojiCell(
                if (expanded) Lucide.Minus else Lucide.Plus,
                if (expanded) "menos emojis" else "mais emojis",
            ) { expanded = !expanded }
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
private fun EmojiCell(icon: ImageVector, rotulo: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(30.dp)
            .clickScale(src, formaDoFoco = RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(if (hov) Obsidian.hover else Color.Transparent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, tint = Obsidian.text2, size = 16.dp, rotulo = rotulo)
    }
}
