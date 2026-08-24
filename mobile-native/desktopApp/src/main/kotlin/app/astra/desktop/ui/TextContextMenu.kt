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
                    nativos.map { MenuEntry.Item(it.label, icon = iconeDaAcao(it.label), onClick = it.onClick) }
                },
                dismiss = fechar,
            )
        }
    }
}

// O ÍCONE DE CADA AÇÃO, DESCOBERTO PELO RÓTULO — e é feio, mas é o que dá.
//
// Os itens deste menu não são nossos: vêm do Compose, junto com a tradução e os atalhos
// (é por isso que saem em português sem ninguém traduzir nada aqui). E `ContextMenuItem`
// expõe DUAS coisas, `label` e `onClick` — não há tipo, não há identificador, não há
// enumeração. Casar pelo texto é a única porta que existe.
//
// DAÍ A LISTA COBRIR PORTUGUÊS E INGLÊS: o rótulo sai no idioma do sistema, e uma máquina
// com Windows em inglês mostraria "Cut/Copy/Paste" — que num mapa só em português cairia
// no `null` e voltaria ao menu sem ícone. Nenhum estrago, mas também nenhum ícone, e o
// motivo seria invisível.
//
// O `null` é resposta legítima e não falha: item desconhecido (o Compose pode ganhar
// outros, e campos diferentes oferecem conjuntos diferentes) aparece só com o texto, que
// é exatamente como este menu era antes.
private fun iconeDaAcao(rotulo: String): ImageVector? = when (rotulo.trim().lowercase()) {
    "recortar", "cut" -> Lucide.Scissors
    "copiar", "copy" -> Lucide.Copy
    "colar", "paste" -> Lucide.ClipboardPaste
    "selecionar tudo", "select all" -> Lucide.TextSelect
    else -> null
}
