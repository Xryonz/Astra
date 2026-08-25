package app.astra.desktop.ui

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scissors
import com.composables.icons.lucide.TextSelect
import kotlin.math.roundToInt

object AstraTextContextMenu : ContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status
        if (status !is ContextMenuState.Status.Open) return
        val nativos = items()
        if (nativos.isEmpty()) return

        val fechar = { state.status = ContextMenuState.Status.Closed }
        val at = remember(status) {
            IntOffset(status.rect.left.roundToInt(), status.rect.top.roundToInt())
        }
        Popup(
            popupPositionProvider = remember(at) { AtPointer(at) },
            onDismissRequest = fechar,
            properties = PopupProperties(focusable = true),
        ) {
            MenuCard(
                entries = remember(nativos) {
                    nativos.map { MenuEntry.Item(it.label, icon = iconeDaAcao(it.label), onClick = it.onClick) }
                },
                dismiss = fechar,
            )
        }
    }
}

private fun iconeDaAcao(rotulo: String): ImageVector? = when (rotulo.trim().lowercase()) {
    "recortar", "cut" -> Lucide.Scissors
    "copiar", "copy" -> Lucide.Copy
    "colar", "paste" -> Lucide.ClipboardPaste
    "selecionar tudo", "select all" -> Lucide.TextSelect
    else -> null
}
