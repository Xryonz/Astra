package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.composables.ui.components.Slider
import kotlin.math.roundToInt

@Composable
internal fun DeslizanteDoKit(
    valor: Int,
    aoMudar: (Int) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    minimo: Int = 0,
    maximo: Int = 100,
) {
    Slider(
        value = valor.coerceIn(minimo, maximo).toFloat(),
        onValueChange = { aoMudar(it.roundToInt()) },
        modifier = modifier.semantics { contentDescription = rotulo }.pointerHoverIcon(PointerIcon.Hand),
        valueRange = minimo.toFloat()..maximo.toFloat(),
    )
}
