package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.shell.ChatMessage
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.UnfurlApi
import app.astra.mobile.core.network.dto.AttachmentDto
import app.astra.mobile.core.network.dto.CallLogDto
import app.astra.mobile.core.network.dto.CartaoDeLinkDto
import app.astra.mobile.core.network.dto.ReactionDto
import app.astra.shared.AstraShared
import coil3.compose.AsyncImage
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.PhoneMissed
import com.composables.icons.lucide.Video
import org.koin.core.context.GlobalContext
import java.awt.Desktop
import java.net.URI

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContentBlock(
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
        val corpo = (15 * scale * fator).sp
        val linha = ((if (realce.temPersonalizado) 24 else 22) * scale * fator).sp
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
                Text("(editado)", style = Tipo.nota)
            }
        }
    }
    if (msg.attachments.isEmpty() && !msg.pending) {
        val link = remember(msg.content) { primeiroLink(msg.content) }
        if (link != null) {
            Spacer(Modifier.height(6.dp))
            PreviaDeLink(link)
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
            Text(" · ", style = Tipo.apoio)
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
internal fun LinhaDeChamada(c: CallLogDto, minha: Boolean) {
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
                    Text(sizeLabel(it), style = Tipo.nota)
                }
            }
        }
    }
}

private val REGEX_LINK = Regex("""https?://[^\s<>"']{4,512}""")

private fun primeiroLink(texto: String): String? =
    REGEX_LINK.find(texto)?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')
        ?.takeIf { it.length > 10 }

private const val CARTOES_LEMBRADOS = 120

private val cartoesVistos = object : LinkedHashMap<String, CartaoDeLinkDto?>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, CartaoDeLinkDto?>) =
        size > CARTOES_LEMBRADOS
}

@Composable
private fun PreviaDeLink(url: String) {
    val api = remember { GlobalContext.get().get<UnfurlApi>() }
    val cartao by produceState(cartoesVistos[url], url) {
        if (cartoesVistos.containsKey(url)) return@produceState
        val achado = runCatching { api.cartao(url).data }.getOrNull()
        cartoesVistos[url] = achado
        value = achado
    }
    val c = cartao ?: return
    if (c.titulo == null && c.descricao == null && c.imagem == null) return

    val fonte = remember { MutableInteractionSource() }
    val hover by fonte.collectIsHoveredAsState()
    Row(
        Modifier
            .widthIn(max = 420.dp)
            .clickScale(fonte)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hover) Obsidian.hover else Obsidian.base)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .hoverable(fonte)
            .clickable(interactionSource = fonte, indication = null) { openAttachment(c.url) }
            .padding(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            c.site?.let {
                Text(it, style = Tipo.nota)
                Spacer(Modifier.height(3.dp))
            }
            c.titulo?.let {
                Text(it, style = Tipo.rotulo.copy(color = Obsidian.accent), maxLines = 2)
                Spacer(Modifier.height(3.dp))
            }
            c.descricao?.let { Text(it, style = Tipo.apoio, maxLines = 3) }
        }
        c.imagem?.let {
            Spacer(Modifier.width(10.dp))
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)),
            )
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

internal fun sizeLabel(bytes: Long): String = when {
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
        textStyle = TextStyle(color = Obsidian.text1, fontSize = 15.sp, lineHeight = 22.sp),
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
    Text("enter salva · esc cancela", style = Tipo.nota)
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
