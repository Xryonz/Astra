package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.LoaderCircle
import com.composables.icons.lucide.Lucide
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import kotlinx.coroutines.delay

private const val ESPERA_DICA_MS = 420L

@Composable
fun BotaoIcone(
    icone: ImageVector,
    dica: String,
    accent: Boolean = false,
    danger: Boolean = false,
    ocupado: Boolean = false,
    onClick: () -> Unit,
) {
    val interacao = remember { MutableInteractionSource() }
    val sobHover by interacao.collectIsHoveredAsState()
    var mostrarDica by remember { mutableStateOf(false) }

    LaunchedEffect(sobHover) {
        if (!sobHover) { mostrarDica = false; return@LaunchedEffect }
        delay(ESPERA_DICA_MS)
        mostrarDica = true
    }

    val corBase = when {
        danger -> Obsidian.danger
        accent -> Obsidian.accent
        else -> Obsidian.text2
    }
    val conteudo by animateColorAsState(
        if (sobHover) (if (danger) Obsidian.danger else Obsidian.text1) else corBase,
        tween(120),
    )
    val fundo by animateColorAsState(
        if (sobHover) (if (danger) Obsidian.danger.copy(alpha = 0.10f) else Obsidian.hover)
        else Color.Transparent,
        tween(120),
    )
    val borda = when {
        danger -> Obsidian.danger.copy(alpha = 0.45f)
        accent -> Obsidian.accentDim
        else -> Obsidian.borderDim
    }

    Box {
        Box(
            Modifier
                .size(34.dp)
                .clickScale(interacao)
                .clip(RoundedCornerShape(8.dp))
                .background(fundo)
                .border(1.dp, borda, RoundedCornerShape(8.dp))
                .hoverable(interacao)
                .clickable(interactionSource = interacao, indication = null, enabled = !ocupado, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (ocupado) {
                val giro = rememberInfiniteTransition(label = "ocupado")
                val angulo by giro.animateFloat(
                    0f, 360f,
                    infiniteRepeatable(tween(900, easing = LinearEasing)),
                    label = "angulo",
                )
                LIcon(
                    Lucide.LoaderCircle, tint = conteudo, size = 16.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = angulo },
                )
            } else {
                LIcon(icone, tint = conteudo, size = 16.dp, rotulo = dica)
            }
        }
        if (mostrarDica && !ocupado) {
            val margem = with(LocalDensity.current) { 6.dp.roundToPx() }
            Popup(popupPositionProvider = remember(margem) { AbaixoCentralizado(margem) }) {
                Box(
                    Modifier
                        .popupReveal(originX = 0.5f, originY = 0f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(7.dp)),
                ) {
                    Text(
                        dica,
                        style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

private class AbaixoCentralizado(private val margem: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val abaixo = anchorBounds.bottom + margem
        val y = if (abaixo + popupContentSize.height <= windowSize.height) abaixo
        else (anchorBounds.top - popupContentSize.height - margem).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
