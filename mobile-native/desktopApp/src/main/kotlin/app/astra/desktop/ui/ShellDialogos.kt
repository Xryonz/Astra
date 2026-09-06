package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo

@Composable
fun ConfirmPopup(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = "cancelar",
    posicao: PopupPositionProvider? = null,
) {
    val corpo: @Composable () -> Unit = {
        Column(
            Modifier
                .popupReveal()
                .clip(RoundedCornerShape(10.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                .padding(14.dp),
        ) {
            Text(
                message,
                style = Tipo.corpo,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    cancelLabel,
                    style = Tipo.descricao,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(7.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(
                    confirmLabel,
                    style = Tipo.erro,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .border(1.dp, Obsidian.danger, RoundedCornerShape(7.dp))
                        .clickable { onDismiss(); onConfirm() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
    val props = PopupProperties(focusable = true)
    if (posicao != null) {
        Popup(popupPositionProvider = posicao, onDismissRequest = onDismiss, properties = props) { corpo() }
    } else {
        Popup(onDismissRequest = onDismiss, properties = props) { corpo() }
    }
}

object AoLadoDoBotao : PopupPositionProvider {
    private const val FOLGA = 8

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val direita = anchorBounds.right + FOLGA
        val x = if (direita + popupContentSize.width <= windowSize.width) direita
        else (anchorBounds.left - FOLGA - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.center.y - popupContentSize.height / 2)
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

@Composable
fun CenteredConfirmDialog(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String? = "cancelar",
    detalhe: String? = null,
    perigo: Boolean = true,
) {
    val reduce = LocalReduceMotion.current
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium))
    }
    val scrimSrc = remember { MutableInteractionSource() }
    val cardSrc = remember { MutableInteractionSource() }
    val cancelSrc = remember { MutableInteractionSource() }
    val okSrc = remember { MutableInteractionSource() }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value }
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = scrimSrc, indication = null, onClick = onDismiss)
                .semCursorDeClique(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .graphicsLayer {
                        val s = 0.92f + 0.08f * enter.value
                        scaleX = s; scaleY = s
                    }
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Obsidian.overlay)
                    .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = cardSrc, indication = null, onClick = {})
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    message,
                    style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif),
                )
                if (detalhe != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        detalhe,
                        style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                val corBotao = if (perigo) Obsidian.danger else Obsidian.accent
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (cancelLabel != null) {
                        Text(
                            cancelLabel,
                            style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
                            modifier = Modifier
                                .clickScale(cancelSrc)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                                .clickable(interactionSource = cancelSrc, indication = null, onClick = onDismiss)
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                    Text(
                        confirmLabel,
                        style = TextStyle(color = corBotao, fontSize = 13.sp),
                        modifier = Modifier
                            .clickScale(okSrc)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, corBotao, RoundedCornerShape(8.dp))
                            .clickable(interactionSource = okSrc, indication = null) { onDismiss(); onConfirm() }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}
