package app.astra.desktop.ui

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

// Botao direito DENTRO de campo de texto (Recortar/Copiar/Colar/Selecionar tudo).
//
// Esse menu não era nosso: sem ninguem fornecer o LocalContextMenuRepresentation, o
// Compose usa o `LightDefaultContextMenuRepresentation` — cartao BRANCO de canto
// reto, que num app obsidiana aparece como um retangulo de luz no meio da tela e
// ignora completamente o tema que a pessoa escolheu em Aparencia.
//
// Aqui so trocamos a REPRESENTACAO: os itens, os atalhos e a traducao continuam
// vindo do Compose (por isso saem em portugues junto com o resto do sistema). O
// desenho reusa o MenuCard do EditorialMenu — o mesmo cartao dos menus de canal,
// mensagem e membro. Reusar em vez de copiar e o que garante que os dois nunca
// divirjam, e faz o menu herdar de graca os tokens reativos do Obsidian: mudou o
// accent/fundo em Settings > Aparencia, esse menu muda junto, sem reiniciar.
object AstraTextContextMenu : ContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status
        if (status !is ContextMenuState.Status.Open) return
        val nativos = items()
        if (nativos.isEmpty()) return

        val fechar = { state.status = ContextMenuState.Status.Closed }
        // `rect` vem em coordenadas do proprio campo -> e o ponto do clique dentro
        // da ancora, exatamente o que o AtPointer espera.
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
                    nativos.map { MenuEntry.Item(it.label, onClick = it.onClick) }
                },
                dismiss = fechar,
            )
        }
    }
}
