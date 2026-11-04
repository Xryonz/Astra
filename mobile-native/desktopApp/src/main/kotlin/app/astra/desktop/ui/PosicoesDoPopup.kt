package app.astra.desktop.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

internal object CobrindoAJanela : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

internal enum class AlinhamentoNoAlvo { ESQUERDA, CENTRO, DIREITA }

internal class AbaixoDoAlvo(
    private val alinhamento: AlinhamentoNoAlvo,
    private val folgaPx: Int = 6,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = xAlinhado(anchorBounds, popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val abaixo = anchorBounds.bottom + folgaPx
        val y = if (abaixo + popupContentSize.height <= windowSize.height) abaixo
        else (anchorBounds.top - popupContentSize.height - folgaPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }

    private fun xAlinhado(alvo: IntRect, largura: Int): Int = when (alinhamento) {
        AlinhamentoNoAlvo.ESQUERDA -> alvo.left
        AlinhamentoNoAlvo.CENTRO -> alvo.left + (alvo.width - largura) / 2
        AlinhamentoNoAlvo.DIREITA -> alvo.right - largura
    }
}
