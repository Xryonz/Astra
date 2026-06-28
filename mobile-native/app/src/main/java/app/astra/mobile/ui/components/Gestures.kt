package app.astra.mobile.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Swipe-back de borda: arrastar da borda esquerda pra direita passando do limite
 * dispara [onBack]. So conta gestos que COMECAM na faixa de borda (edgeWidth) ->
 * nao rouba o swipe-to-reply das bolhas (que ficam no centro, e por serem filhas
 * consomem o gesto antes via dispatch inner-first) nem o scroll vertical
 * (detectHorizontalDrag so reage a arraste horizontal). Sem "seguir o dedo": ao
 * soltar, deixa a transicao de pop padrao do NavHost cuidar do slide.
 */
fun Modifier.edgeSwipeBack(onBack: () -> Unit): Modifier = this.pointerInput(Unit) {
    val edgePx = 20.dp.toPx()
    val thresholdPx = 90.dp.toPx()
    var fromEdge = false
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { start -> fromEdge = start.x <= edgePx; total = 0f },
        onHorizontalDrag = { _, dx -> if (fromEdge) total += dx },
        onDragEnd = {
            if (fromEdge && total >= thresholdPx) onBack()
            fromEdge = false
            total = 0f
        },
        onDragCancel = { fromEdge = false; total = 0f },
    )
}
