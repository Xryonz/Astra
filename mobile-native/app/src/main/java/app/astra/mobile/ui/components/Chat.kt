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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.Reply
import com.composables.icons.lucide.Smile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.ui.LocalAppPrefs
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
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val QuickReactions = listOf("👍", "❤️", "😂", "🔥", "🎉", "😮")

data class ReactionChip(val emoji: String, val count: Int, val mine: Boolean)

private val MentionRegex = Regex("@[a-z0-9_]+", RegexOption.IGNORE_CASE)

fun mentionAnnotated(text: String, accent: Color) = buildAnnotatedString {
    var last = 0
    for (m in MentionRegex.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) { append(m.value) }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

private val CodeBlockRegex = Regex("```[a-zA-Z0-9]*\\n?([\\s\\S]*?)```")
private val InlineTokenRegex = Regex("(`[^`\\n]+`)|(@[a-z0-9_]+)", RegexOption.IGNORE_CASE)

private sealed interface MsgSeg {
    data class Plain(val value: String) : MsgSeg
    data class Code(val value: String) : MsgSeg
}

private fun splitCodeSegments(text: String): List<MsgSeg> = buildList {
    var last = 0
    for (m in CodeBlockRegex.findAll(text)) {
        if (m.range.first > last) add(MsgSeg.Plain(text.substring(last, m.range.first).trim('\n')))
        add(MsgSeg.Code(m.groupValues[1].trim('\n')))
        last = m.range.last + 1
    }
    if (last < text.length) add(MsgSeg.Plain(text.substring(last).trim('\n')))
}

private fun inlineAnnotated(text: String, accent: Color, codeBg: Color) = buildAnnotatedString {
    var last = 0
    for (m in InlineTokenRegex.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        if (m.value.startsWith("`")) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                append(m.value.trim('`'))
            }
        } else {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) { append(m.value) }
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

@Composable
private fun ConteudoDaMensagem(content: String, editada: Boolean, corpoSp: Float) {
    val accent = astraColors.accent
    val codeBg = astraColors.raised
    val nota = astraColors.text3
    val segments = remember(content) { splitCodeSegments(content) }
    val estilo = MaterialTheme.typography.bodyLarge.copy(
        fontSize = corpoSp.sp,
        lineHeight = (corpoSp * 1.4f).sp,
        color = astraColors.text2,
    )
    val soTexto = segments.singleOrNull() as? MsgSeg.Plain
    if (soTexto != null || segments.isEmpty()) {
        val texto = soTexto?.value.orEmpty()
        Text(
            text = remember(texto, editada, accent, codeBg, nota) {
                buildAnnotatedString {
                    append(inlineAnnotated(texto, accent, codeBg))
                    if (editada) withStyle(SpanStyle(color = nota, fontSize = 11.sp)) { append("  (editado)") }
                }
            },
            style = estilo,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { seg ->
            when (seg) {
                is MsgSeg.Plain -> if (seg.value.isNotBlank()) Text(
                    text = remember(seg.value, accent, codeBg) { inlineAnnotated(seg.value, accent, codeBg) },
                    style = estilo,
                )
                is MsgSeg.Code -> Text(
                    text = seg.value,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (corpoSp * 0.82f).sp,
                    color = astraColors.text1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(astraColors.raised)
                        .border(1.dp, astraColors.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        if (editada) Text("(editado)", fontSize = 11.sp, color = nota)
    }
}

private val LADO_DA_FOTO = 38.dp
private val RECUO_DO_TEXTO = 12.dp
private val MARGEM_LATERAL = 14.dp
private const val ENTRADA_MS = 240

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MensagemDoChat(
    authorName: String,
    authorAvatar: String?,
    content: String,
    entrar: Boolean,
    authorColor: String? = null,
    authorFont: String? = null,
    agrupada: Boolean = false,
    hora: String? = null,
    mencionaVoce: Boolean = false,
    edited: Boolean = false,
    pinned: Boolean = false,
    reactions: List<ReactionChip> = emptyList(),
    replyAuthor: String? = null,
    replyContent: String? = null,
    attachments: List<Attachment> = emptyList(),
    translation: String? = null,
    poll: PollUi? = null,
    canClosePoll: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onToggleReaction: ((String) -> Unit)? = null,
    onMoreReactions: (() -> Unit)? = null,
    onTranslate: (() -> Unit)? = null,
    onOpenImage: ((List<Attachment>, Int) -> Unit)? = null,
    onVotePoll: ((String) -> Unit)? = null,
    onClosePoll: (() -> Unit)? = null,
    onAuthorClick: (() -> Unit)? = null,
) {
    val prefs = LocalAppPrefs.current
    val animar = entrar && !prefs.reduceMotion
    val entrada = remember { Animatable(if (animar) 0f else 1f) }
    val brilho = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (animar) {
            launch { entrada.animateTo(1f, tween(ENTRADA_MS, easing = EaseSnappy)) }
            brilho.animateTo(1f, tween(900, delayMillis = 120, easing = EaseOutSoft))
        }
    }
    val glow = astraColors.accentGlow
    val accent = astraColors.accent
    val fundoDaMencao = astraColors.accentDim

    val hasMenu = onEdit != null || onDelete != null || onReply != null ||
        onTogglePin != null || onToggleReaction != null || onTranslate != null
    var menuOpen by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val maxDragPx = with(density) { 56.dp.toPx() }
    val thresholdPx = with(density) { 40.dp.toPx() }
    val subida = with(density) { 10.dp.toPx() }
    val swipeX = remember { Animatable(0f) }
    val larguraDoConteudo = (LocalConfiguration.current.screenWidthDp.dp -
        MARGEM_LATERAL * 2 - LADO_DA_FOTO - RECUO_DO_TEXTO).coerceAtLeast(120.dp)
    val corpoSp = 16f * prefs.fontSize.scale

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entrada.value
                translationY = (1f - entrada.value) * subida
            }
            .then(
                if (mencionaVoce) {
                    Modifier.drawBehind {
                        drawRect(fundoDaMencao.copy(alpha = fundoDaMencao.alpha * 0.5f))
                        drawRect(accent, size = Size(2.dp.toPx(), size.height))
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MARGEM_LATERAL,
                    end = MARGEM_LATERAL,
                    top = if (agrupada) prefs.density.groupedTopDp.dp else prefs.density.topDp.dp,
                    bottom = 2.dp,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            if (agrupada) {
                Spacer(Modifier.width(LADO_DA_FOTO))
            } else {
                val avatarMod = if (onAuthorClick != null) {
                    Modifier.clip(CircleShape).clickable(onClick = onAuthorClick)
                } else Modifier
                Box(avatarMod) { AstraAvatar(authorAvatar, authorName, size = LADO_DA_FOTO.value.toInt()) }
            }
            Spacer(Modifier.width(RECUO_DO_TEXTO))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (onReply != null) {
                    Icon(
                        Lucide.Reply,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                val p = (abs(swipeX.value) / thresholdPx).coerceIn(0f, 1f)
                                alpha = p
                                scaleX = 0.5f + 0.5f * p
                                scaleY = 0.5f + 0.5f * p
                            },
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = swipeX.value }
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
                                                if (prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                            val p = brilho.value
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
                        },
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (!agrupada) {
                        if (replyContent != null) ReferenciaDaResposta(replyAuthor, replyContent)
                        CabecalhoDaMensagem(
                            nome = authorName,
                            cor = authorColor,
                            fonte = authorFont,
                            hora = hora,
                            fixada = pinned,
                            aoTocarNoNome = onAuthorClick,
                        )
                    }
                    if (poll != null) {
                        PollCard(
                            poll = poll,
                            maxWidth = larguraDoConteudo,
                            canClose = canClosePoll,
                            onVote = { optionId -> onVotePoll?.invoke(optionId) },
                            onClose = { onClosePoll?.invoke() },
                        )
                    } else if (content.isNotBlank() || edited) {
                        ConteudoDaMensagem(content, edited, corpoSp)
                    }
                    if (attachments.isNotEmpty()) {
                        MessageAttachments(
                            attachments = attachments,
                            maxWidth = larguraDoConteudo,
                            onOpenImage = { imgs, idx -> onOpenImage?.invoke(imgs, idx) },
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (translation != null) Traducao(translation, corpoSp)
                    ReacoesDaMensagem(reactions, onToggleReaction, Modifier.padding(top = 3.dp))
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
                        onMoreReactions = onMoreReactions,
                        onTranslate = onTranslate,
                    )
                }
            }
        }
    }
}

@Composable
private fun CabecalhoDaMensagem(
    nome: String,
    cor: String?,
    fonte: String?,
    hora: String?,
    fixada: Boolean,
    aoTocarNoNome: (() -> Unit)?,
) {
    val corDoNome = remember(cor) { parseNameColor(cor) }
    val base = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = nome,
            style = if (corDoNome is NameColor.Gradient) base.copy(brush = corDoNome.brush) else base,
            fontFamily = fonte?.let(::displayFontFamily),
            color = when (corDoNome) {
                is NameColor.Solid -> corDoNome.color
                is NameColor.Gradient -> Color.Unspecified
                null -> astraColors.text1
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (aoTocarNoNome != null) Modifier.clickable(onClick = aoTocarNoNome) else Modifier),
        )
        if (hora != null) {
            Spacer(Modifier.width(8.dp))
            Text(hora, style = MaterialTheme.typography.labelSmall, color = astraColors.text3)
        }
        if (fixada) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Lucide.Pin,
                contentDescription = "Fixada",
                tint = astraColors.text3,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun ReferenciaDaResposta(autor: String?, conteudo: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.Reply, contentDescription = null, tint = astraColors.text3, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = autor ?: "mensagem",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = astraColors.accent,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = conteudo,
            style = MaterialTheme.typography.bodySmall,
            color = astraColors.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Traducao(texto: String, corpoSp: Float) {
    Column(Modifier.padding(top = 2.dp)) {
        Text(
            text = "tradução",
            style = MaterialTheme.typography.labelSmall,
            color = astraColors.accent,
        )
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = (corpoSp * 0.94f).sp,
            color = astraColors.text2,
        )
    }
}

@Composable
private fun ReacoesDaMensagem(
    reactions: List<ReactionChip>,
    onToggleReaction: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) return
    FlowRowCompat(modifier = modifier, horizontalSpacing = 4.dp, verticalSpacing = 4.dp) {
        reactions.forEach { r -> PilulaDeReacao(r, onToggleReaction) }
    }
}

@Composable
private fun PilulaDeReacao(r: ReactionChip, onToggleReaction: ((String) -> Unit)?) {
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val escala = remember { Animatable(1f) }
    var contagemAnterior by remember { mutableStateOf(r.count) }
    LaunchedEffect(r.count) {
        if (r.count != contagemAnterior && !semMovimento) {
            escala.snapTo(0.75f)
            escala.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
        contagemAnterior = r.count
    }
    val forma = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = escala.value; scaleY = escala.value }
            .clip(forma)
            .background(if (r.mine) astraColors.active else astraColors.raised)
            .border(1.dp, if (r.mine) astraColors.borderMid else astraColors.border, forma)
            .then(if (onToggleReaction != null) Modifier.clickable { onToggleReaction(r.emoji) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.emoji, fontSize = 14.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${r.count}",
            style = MaterialTheme.typography.labelSmall,
            color = if (r.mine) astraColors.accent else astraColors.text3,
        )
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
    onMoreReactions: (() -> Unit)? = null,
    onTranslate: (() -> Unit)? = null,
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
                if (onMoreReactions != null) {
                    Text(
                        text = "+",
                        fontSize = 20.sp,
                        color = astraColors.accent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onDismiss(); onMoreReactions() }
                            .padding(6.dp),
                    )
                }
            }
        }
        if (onReply != null) {
            ItemDeMenu(
                text = { Text("Responder", color = astraColors.text1) },
                onClick = { onDismiss(); onReply() },
            )
        }
        if (onTranslate != null) {
            ItemDeMenu(
                text = { Text("Traduzir", color = astraColors.text1) },
                onClick = { onDismiss(); onTranslate() },
            )
        }
        if (onTogglePin != null) {
            ItemDeMenu(
                text = { Text(if (pinned) "Desafixar" else "Fixar", color = astraColors.text1) },
                onClick = { onDismiss(); onTogglePin() },
            )
        }
        if (onEdit != null) {
            ItemDeMenu(
                text = { Text("Editar", color = astraColors.text1) },
                onClick = { onDismiss(); onEdit() },
            )
        }
        if (onDelete != null) {
            ItemDeMenu(
                text = { Text("Apagar", color = astraColors.danger) },
                onClick = { onDismiss(); onDelete() },
            )
        }
    }
}

@Immutable
data class ChatRow(
    val id: String,
    val mine: Boolean,
    val authorId: String? = null,
    val authorName: String,
    val authorAvatar: String? = null,
    val authorColor: String? = null,
    val authorFont: String? = null,
    val content: String,
    val edited: Boolean = false,
    val pinned: Boolean = false,
    val reactions: List<ReactionChip> = emptyList(),
    val replyAuthor: String? = null,
    val replyContent: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val translation: String? = null,
    val poll: PollUi? = null,
    val kind: String? = null,
    val criadaEm: String? = null,
    val mencionaVoce: Boolean = false,
)

private const val PAUSA_QUE_QUEBRA_O_BLOCO_MIN = 7L
private const val MAIS_QUE_ISSO_E_HISTORICO = 2

private val PORTUGUES = Locale.forLanguageTag("pt-BR")
private val DIA_DO_MES = DateTimeFormatter.ofPattern("d 'de' MMMM", PORTUGUES)
private val DIA_COM_ANO = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PORTUGUES)

private fun instanteDe(texto: String?): OffsetDateTime? =
    texto?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

private fun rotuloDoDia(dia: LocalDate, hoje: LocalDate): String = when (dia) {
    hoje -> "hoje"
    hoje.minusDays(1) -> "ontem"
    else -> (if (dia.year == hoje.year) DIA_DO_MES else DIA_COM_ANO).format(dia)
}

private sealed interface ItemDaConversa {
    val chave: String

    data class Dia(override val chave: String, val rotulo: String) : ItemDaConversa
    data class Passagem(val row: ChatRow) : ItemDaConversa {
        override val chave: String get() = row.id
    }
    data class Fala(val row: ChatRow, val agrupada: Boolean, val hora: String?) : ItemDaConversa {
        override val chave: String get() = row.id
    }
}

private fun montarItens(rows: List<ChatRow>): List<ItemDaConversa> {
    if (rows.isEmpty()) return emptyList()
    val zona = ZoneId.systemDefault()
    val hoje = LocalDate.now(zona)
    val itens = ArrayList<ItemDaConversa>(rows.size + 4)
    var diaAnterior: LocalDate? = null
    var anterior: ChatRow? = null
    var instanteAnterior: OffsetDateTime? = null
    for (row in rows) {
        val instante = instanteDe(row.criadaEm)
        val local = instante?.atZoneSameInstant(zona)
        val dia = local?.toLocalDate()
        if (dia != null && dia != diaAnterior) {
            itens += ItemDaConversa.Dia("dia-$dia", rotuloDoDia(dia, hoje))
            diaAnterior = dia
            anterior = null
            instanteAnterior = null
        }
        if (row.kind != null) {
            itens += ItemDaConversa.Passagem(row)
            anterior = null
            instanteAnterior = null
            continue
        }
        val mesmaPessoa = anterior != null &&
            anterior.mine == row.mine &&
            anterior.authorId == row.authorId &&
            anterior.authorName == row.authorName &&
            anterior.authorAvatar == row.authorAvatar
        val semPausa = instante != null && instanteAnterior != null &&
            Duration.between(instanteAnterior, instante).toMinutes() < PAUSA_QUE_QUEBRA_O_BLOCO_MIN
        val agrupada = mesmaPessoa && semPausa && row.replyContent == null
        val hora = local?.let { "%02d:%02d".format(it.hour, it.minute) }
        itens += ItemDaConversa.Fala(row, agrupada, hora)
        anterior = row
        if (instante != null) instanteAnterior = instante
    }
    return itens
}

private class ChegadaDeMensagens {
    private var ultima: String? = null
    private var aoVivo = false
    private val aAnimar = HashSet<String>()

    fun observar(rows: List<ChatRow>, vivo: Boolean) {
        val novaUltima = rows.lastOrNull()?.id
        if (aoVivo && ultima != null && novaUltima != ultima) {
            val onde = rows.indexOfLast { it.id == ultima }
            val novas = if (onde >= 0) rows.size - 1 - onde else 0
            if (novas in 1..MAIS_QUE_ISSO_E_HISTORICO) {
                for (j in onde + 1 until rows.size) aAnimar += rows[j].id
            }
        }
        ultima = novaUltima
        if (vivo) aoVivo = true
    }

    fun consumir(id: String): Boolean = aAnimar.remove(id)
}

@Composable
fun ChatMessageList(
    rows: List<ChatRow>,
    vivo: Boolean,
    modifier: Modifier = Modifier,
    canEdit: Boolean = true,
    canReact: Boolean = false,
    canPin: Boolean = false,
    onEdit: (ChatRow) -> Unit = {},
    onDelete: (ChatRow) -> Unit = {},
    onReply: (ChatRow) -> Unit = {},
    onTogglePin: (ChatRow) -> Unit = {},
    onToggleReaction: (ChatRow, String) -> Unit = { _, _ -> },
    onMoreReactions: ((ChatRow) -> Unit)? = null,
    onTranslate: (ChatRow) -> Unit = {},
    onVotePoll: (ChatRow, String) -> Unit = { _, _ -> },
    onClosePoll: (ChatRow) -> Unit = {},
    onOpenProfile: ((String, String) -> Unit)? = null,
) {
    val chegada = remember { ChegadaDeMensagens() }
    remember(rows, vivo) { chegada.observar(rows, vivo) }

    val listState = rememberLazyListState()
    val newest = rows.lastOrNull()
    LaunchedEffect(newest?.id) {
        if (newest != null && (newest.mine || listState.firstVisibleItemIndex <= 2)) {
            listState.animateScrollToItem(0)
        }
    }

    val itens = remember(rows) { montarItens(rows).asReversed() }

    var lightbox by remember { mutableStateOf<Pair<List<Attachment>, Int>?>(null) }

    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            items(
                itens,
                key = { it.chave },
                contentType = {
                    when (it) {
                        is ItemDaConversa.Dia -> "dia"
                        is ItemDaConversa.Passagem -> "passagem"
                        is ItemDaConversa.Fala -> "mensagem"
                    }
                },
            ) { item ->
                when (item) {
                    is ItemDaConversa.Dia -> SeparadorDoDia(item.rotulo)
                    is ItemDaConversa.Passagem -> LinhaDePassagem(item.row.content)
                    is ItemDaConversa.Fala -> {
                        val row = item.row
                        val entrar = remember(row.id) { chegada.consumir(row.id) }
                        MensagemDoChat(
                            authorName = row.authorName,
                            authorAvatar = row.authorAvatar,
                            content = row.content,
                            entrar = entrar,
                            authorColor = row.authorColor,
                            authorFont = row.authorFont,
                            agrupada = item.agrupada,
                            hora = item.hora,
                            mencionaVoce = row.mencionaVoce,
                            edited = row.edited,
                            pinned = row.pinned,
                            reactions = row.reactions,
                            replyAuthor = row.replyAuthor,
                            replyContent = row.replyContent,
                            attachments = row.attachments,
                            translation = row.translation,
                            poll = row.poll,
                            canClosePoll = row.poll != null && row.mine,
                            onEdit = if (row.mine && canEdit && row.poll == null) ({ onEdit(row) }) else null,
                            onDelete = if (row.mine) ({ onDelete(row) }) else null,
                            onReply = { onReply(row) },
                            onTogglePin = if (canPin) ({ onTogglePin(row) }) else null,
                            onToggleReaction = if (canReact) ({ emoji: String -> onToggleReaction(row, emoji) }) else null,
                            onMoreReactions = if (canReact && onMoreReactions != null) ({ onMoreReactions(row) }) else null,
                            onTranslate = if (row.content.isNotBlank() && row.poll == null) ({ onTranslate(row) }) else null,
                            onOpenImage = { imgs, idx -> lightbox = imgs to idx },
                            onVotePoll = if (row.poll != null) ({ optionId: String -> onVotePoll(row, optionId) }) else null,
                            onClosePoll = if (row.poll != null && row.mine) ({ onClosePoll(row) }) else null,
                            onAuthorClick = row.authorId?.let { aid ->
                                onOpenProfile?.let { open -> ({ open(aid, row.authorName) }) }
                            },
                        )
                    }
                }
            }
        }

        lightbox?.let { (imgs, idx) ->
            Lightbox(images = imgs, startIndex = idx, onDismiss = { lightbox = null })
        }
    }
}

@Composable
private fun SeparadorDoDia(rotulo: String) {
    val traco = astraColors.border
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(28.dp).height(1.dp).background(traco))
        Text(
            text = rotulo,
            style = MaterialTheme.typography.labelSmall,
            color = astraColors.text3,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.width(28.dp).height(1.dp).background(traco))
    }
}

@Composable
private fun LinhaDePassagem(texto: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(astraColors.border))
        Text(
            text = texto,
            style = MaterialTheme.typography.bodySmall,
            color = astraColors.text3,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(astraColors.border))
    }
}

@Composable
private fun ComposerOption(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ItemDeMenu(
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = astraColors.accent, modifier = Modifier.size(18.dp))
        },
        text = { Text(label, color = astraColors.text1) },
        enabled = enabled,
        onClick = onClick,
    )
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
    rascunhoExterno: String,
    sending: Boolean,
    onInput: (String) -> Unit,
    onSend: (String) -> Unit,
    onAttach: (() -> Unit)? = null,
    onGif: (() -> Unit)? = null,
    onPoll: (() -> Unit)? = null,
    onEmoji: (() -> Unit)? = null,
    uploading: Boolean = false,
    hasAttachments: Boolean = false,
    emojiPendente: String? = null,
    aoUsarEmoji: () -> Unit = {},
) {
    var texto by rememberSaveable { mutableStateOf(rascunhoExterno) }
    LaunchedEffect(rascunhoExterno) {
        if (rascunhoExterno != texto) texto = rascunhoExterno
    }
    LaunchedEffect(emojiPendente) {
        val emoji = emojiPendente ?: return@LaunchedEffect
        texto += emoji
        onInput(texto)
        aoUsarEmoji()
    }

    val canSend = (texto.isNotBlank() || hasAttachments) && !sending && !uploading
    val hapticsOn = LocalAppPrefs.current.haptics
    val haptic = LocalHapticFeedback.current
    val send = {
        if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        onSend(texto)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        val hasOptions = onAttach != null || onGif != null || onPoll != null || onEmoji != null
        if (hasOptions) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (menuOpen) astraColors.accentDim else astraColors.raised)
                        .border(1.dp, astraColors.borderMid, CircleShape)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (uploading) "…" else "+",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (menuOpen) astraColors.accent else astraColors.text2,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onAttach != null) {
                        ComposerOption(Lucide.Image, "Fotos", enabled = !uploading) { menuOpen = false; onAttach() }
                    }
                    if (onGif != null) {
                        ComposerOption(Lucide.Film, "GIF") { menuOpen = false; onGif() }
                    }
                    if (onPoll != null) {
                        ComposerOption(Lucide.ChartColumn, "Enquete") { menuOpen = false; onPoll() }
                    }
                    if (onEmoji != null) {
                        ComposerOption(Lucide.Smile, "Emoji") { menuOpen = false; onEmoji() }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Input(
            value = texto,
            onValueChange = { novo -> texto = novo; onInput(novo) },
            modifier = Modifier
                .weight(1f)

                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) {
                        send(); true
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
                .clickable(enabled = canSend, onClick = send),
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
