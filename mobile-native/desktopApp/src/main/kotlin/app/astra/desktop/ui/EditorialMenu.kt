package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import app.astra.desktop.ui.theme.Text
import kotlin.math.roundToInt

// Menu de botao direito editorial (F4) — obsidiana, hover animado, submenu de
// emojis. Mesma ideia do EditorialContextMenu do web, em Compose.

sealed interface MenuEntry {
    data class Item(
        val label: String,
        val danger: Boolean = false,
        val onClick: () -> Unit,
    ) : MenuEntry

    data object Separator : MenuEntry

    // "reagir ▸" — abre fileira de emojis ao lado (hover ou clique).
    data class EmojiSub(
        val label: String,
        val emojis: List<String>,
        val onPick: (String) -> Unit,
    ) : MenuEntry
}

// Popup na posicao do clique (offset dentro da ancora), clampado na janela.
private class AtPointer(private val at: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = (anchorBounds.left + at.x).coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0),
        y = (anchorBounds.top + at.y).coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0),
    )
}

// Envolve o alvo: botao direito abre o menu no ponto do clique. So OBSERVA os
// eventos (nao consome) — cliques normais seguem funcionando por baixo.
@Composable
fun EditorialContextMenu(
    entries: () -> List<MenuEntry>,
    content: @Composable () -> Unit,
) {
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    Box(
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        val pos = event.changes.first().position
                        menuAt = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                    }
                }
            }
        },
    ) {
        content()
        menuAt?.let { at ->
            Popup(
                popupPositionProvider = remember(at) { AtPointer(at) },
                onDismissRequest = { menuAt = null },
                properties = PopupProperties(focusable = true),
            ) {
                MenuCard(entries(), dismiss = { menuAt = null })
            }
        }
    }
}

@Composable
private fun MenuCard(entries: List<MenuEntry>, dismiss: () -> Unit) {
    val entered = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = entered,
        enter = fadeIn(tween(110, easing = EaseOutStd)) +
            scaleIn(tween(130, easing = EaseOutStd), initialScale = 0.94f, transformOrigin = TransformOrigin(0f, 0f)),
    ) {
        Column(
            Modifier
                // Abraca o conteudo (min menor + teto): item curto nao vira barra
                // esticada. Textos do menu sao de 1 linha (< max), sem quebra.
                .widthIn(min = 150.dp, max = 260.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                .padding(4.dp),
        ) {
            entries.forEach { entry ->
                when (entry) {
                    is MenuEntry.Separator ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .height(1.dp)
                                .background(Obsidian.borderDim.copy(alpha = 0.6f)),
                        )
                    is MenuEntry.Item -> MenuRow(entry, dismiss)
                    is MenuEntry.EmojiSub -> EmojiSubRow(entry, dismiss)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuEntry.Item, dismiss: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            hovered && item.danger -> Obsidian.danger.copy(alpha = 0.14f)
            hovered -> Obsidian.hover
            else -> Color.Transparent
        },
        tween(100),
    )
    val fg by animateColorAsState(
        when {
            item.danger -> Obsidian.danger
            hovered -> Obsidian.text1
            else -> Obsidian.text2
        },
        tween(100),
    )
    Text(
        item.label,
        style = TextStyle(color = fg, fontSize = 13.sp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable {
                dismiss()
                item.onClick()
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun EmojiSubRow(sub: MenuEntry.EmojiSub, dismiss: () -> Unit) {
    val rowInteraction = remember { MutableInteractionSource() }
    val rowHovered by rowInteraction.collectIsHoveredAsState()
    val popupInteraction = remember { MutableInteractionSource() }
    val popupHovered by popupInteraction.collectIsHoveredAsState()
    // Mesmo truque da pill do chat: o submenu vive enquanto o mouse estiver na
    // linha OU nele proprio.
    val open = rowHovered || popupHovered

    val bg by animateColorAsState(if (rowHovered) Obsidian.hover else Color.Transparent, tween(100))
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .hoverable(rowInteraction)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                sub.label,
                style = TextStyle(color = if (rowHovered) Obsidian.text1 else Obsidian.text2, fontSize = 13.sp),
                modifier = Modifier.weight(1f),
            )
            LIcon(Lucide.ChevronRight, tint = Obsidian.text3, size = 13.dp)
        }
        if (open) {
            Popup(popupPositionProvider = SubmenuBeside) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(999.dp))
                        .hoverable(popupInteraction)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    sub.emojis.forEach { e ->
                        val ei = remember { MutableInteractionSource() }
                        val eh by ei.collectIsHoveredAsState()
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (eh) Obsidian.hover else Color.Transparent)
                                .hoverable(ei)
                                .clickable {
                                    dismiss()
                                    sub.onPick(e)
                                }
                                .padding(5.dp),
                        ) {
                            Text(e, style = TextStyle(fontSize = 15.sp))
                        }
                    }
                }
            }
        }
    }
}

private object SubmenuBeside : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val right = anchorBounds.right + 4
        val x = if (right + popupContentSize.width <= windowSize.width) right
        else (anchorBounds.left - popupContentSize.width - 4).coerceAtLeast(0)
        val y = anchorBounds.top.coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
