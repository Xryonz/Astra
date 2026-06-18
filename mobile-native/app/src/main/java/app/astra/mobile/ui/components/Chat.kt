package app.astra.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.EaseSnappy
import app.astra.mobile.ui.theme.astraColors

/**
 * Bolha de mensagem compartilhada (DM + canal). Entrada com slide direcional
 * (do lado do autor) + overshoot elastico; mensagem nova (sweep=true) ganha um
 * "starlight sweep" prata por cima. Espelha dmMsgIn + msgStarlightSweep do web.
 */
@Composable
fun MessageBubble(
    mine: Boolean,
    authorName: String,
    content: String,
    animateIn: Boolean,
    sweep: Boolean,
) {
    val bg = if (mine) astraColors.accent else astraColors.raised
    val fg = if (mine) astraColors.textInv else astraColors.text1
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

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    alpha = enter.value
                    translationX = (1f - enter.value) * fromX.dp.toPx()
                }
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bg)
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
            if (!mine) {
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.labelSmall,
                    color = astraColors.accent,
                )
            }
            Text(text = content, style = MaterialTheme.typography.bodyLarge, color = fg)
        }
    }
}

/** Linha de chat agnostica de modelo (DM e canal mapeiam pra isto). */
data class ChatRow(
    val id: String,
    val mine: Boolean,
    val authorName: String,
    val content: String,
)

/**
 * Lista de mensagens (reverseLayout, mais nova embaixo). Cada msg desliza ao
 * aparecer pela 1a vez; o "sweep" prata so dispara em msg que chega DEPOIS da
 * carga inicial (shownOnce), pra nao varrer o historico inteiro de uma vez.
 */
@Composable
fun ChatMessageList(rows: List<ChatRow>, modifier: Modifier = Modifier) {
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
            MessageBubble(
                mine = row.mine,
                authorName = row.authorName,
                content = row.content,
                animateIn = isNew,
                sweep = isNew && shownOnce,
            )
        }
    }
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
