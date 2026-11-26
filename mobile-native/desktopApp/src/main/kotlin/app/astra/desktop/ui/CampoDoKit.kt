package app.astra.desktop.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import com.composables.ui.components.TextField
import com.composeunstyled.LocalContentColor

@Composable
internal fun CampoDoKit(
    valor: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier,
    dica: String,
    aoEnviar: () -> Unit,
    ligado: Boolean,
    umaLinha: Boolean,
    linhasNoMaximo: Int,
    icone: ImageVector?,
    estilo: TextStyle,
) {
    val estado = rememberEstadoEspelhado(valor, aoMudar)
    TextField(
        state = estado,
        modifier = modifier,
        readOnly = !ligado,
        accessibilityLabel = dica.ifEmpty { null },
        placeholder = dica.ifEmpty { null }?.let { { DicaDoCampo(it, estilo) } },
        leading = icone?.let { { LIcon(it, tint = Obsidian.text3, size = 14.dp) } },
        textStyle = estilo,
        cursorBrush = SolidColor(Obsidian.accent),
        lineLimits = if (umaLinha) {
            TextFieldLineLimits.SingleLine
        } else {
            TextFieldLineLimits.MultiLine(maxHeightInLines = linhasNoMaximo)
        },
        keyboardOptions = KeyboardOptions(imeAction = if (umaLinha) ImeAction.Done else ImeAction.Default),
        onKeyboardAction = if (umaLinha) KeyboardActionHandler { aoEnviar() } else null,
    )
}

@Composable
private fun DicaDoCampo(dica: String, estilo: TextStyle) {
    Text(dica, style = estilo.copy(color = LocalContentColor.current))
}

@Composable
private fun rememberEstadoEspelhado(valor: String, aoMudar: (String) -> Unit): TextFieldState {
    val estado = rememberTextFieldState(valor)
    val valorAtual by rememberUpdatedState(valor)
    val aoMudarAtual by rememberUpdatedState(aoMudar)
    var enviado by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(estado) {
        snapshotFlow { estado.text.toString() }.collect { texto ->
            if (texto != valorAtual) {
                enviado = texto
                aoMudarAtual(texto)
            }
        }
    }
    LaunchedEffect(valor, enviado) {
        val naTela = estado.text.toString()
        if (naTela != valor && (enviado == null || naTela == enviado)) estado.setTextAndPlaceCursorAtEnd(valor)
    }
    return estado
}
