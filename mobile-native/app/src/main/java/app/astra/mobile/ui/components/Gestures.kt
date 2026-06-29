package app.astra.mobile.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

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
