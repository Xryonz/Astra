package app.astra.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.Obsidian
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil

// A FOTO É O BOTÃO — e não três botões ao lado dela.
//
// Antes havia uma fileira de ícones soltos embaixo do avatar (trocar, reenquadrar,
// remover). Três alvos permanentes na tela pra uma coisa que se mexe uma vez por
// mês, e nenhum deles ENCOSTANDO no que opera: era preciso ler os três rótulos pra
// descobrir qual mexia na foto.
//
// O padrão que o dono pediu (o do Discord): passar o mouse escurece a imagem e
// acende um lápis; clicar abre as opções ali mesmo. As ações somem da tela até
// serem necessárias, e quando aparecem estão EM CIMA do que vão mudar — não há o
// que ler pra saber o que o botão faz.
//
// Reaproveita o MenuCard dos menus de botão-direito de propósito: o app já ensinou
// como um menu dele se parece, e um segundo desenho pra mesma função seria uma
// segunda convenção pra ninguém aprender.
// Portador do menu do banner: o FORMULÁRIO monta as ações (ele tem o rascunho e
// hospeda os diálogos) e a PRÉVIA as consome. Os dois são irmãos na tela de
// Configurações, então nenhum pode receber do outro por parâmetro sem mudar de
// lugar. Classe estável com campo mutável — mesmo padrão do MencaoClicavel, pelo
// mesmo motivo: publicar o fechamento mais recente sem forçar recomposição.
class AcoesDoBanner {
    var construir: () -> List<MenuEntry> = { emptyList() }
}

@Composable
fun FotoEditavel(
    forma: Shape,
    acoes: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    // Tamanho do lápis. O avatar é pequeno (64dp) e a faixa do banner é larga —
    // um glifo só serviria mal aos dois.
    glifo: Dp = 18.dp,
    rotulo: String = "editar imagem",
    conteudo: @Composable (hover: Boolean) -> Unit,
) {
    val fonte = remember { MutableInteractionSource() }
    val hover by fonte.collectIsHoveredAsState()
    var menuEm by remember { mutableStateOf<IntOffset?>(null) }

    // O véu segue o hover OU o menu aberto: com o menu na frente o ponteiro sai da
    // foto, e sem isto a imagem clareava no exato instante em que as opções dela
    // apareciam — a peça perdia o vínculo com o menu que ela abriu.
    val aceso = hover || menuEm != null
    val veu by animateFloatAsState(if (aceso) 1f else 0f, tween(140), label = "veuDaFoto")

    Box(
        modifier
            .clip(forma)
            .hoverable(fonte)
            // Sem indicação: o véu JÁ é o retorno visual, e um brilho por baixo dele
            // seria retorno em cima de retorno. O cursor de mão vem do Clicavel.kt.
            .clickable(interactionSource = fonte, indication = null, onClickLabel = rotulo) {
                // Abre a partir do centro de baixo da peça. Posição fixa e não o
                // ponto do clique: aqui o alvo é UMA coisa (a foto), então o menu
                // sempre no mesmo lugar é previsível — diferente do botão-direito,
                // onde o ponto do clique é o que diz sobre O QUE o menu fala.
                menuEm = IntOffset(0, 0)
            },
        contentAlignment = Alignment.Center,
    ) {
        conteudo(aceso)
        if (veu > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .alpha(veu)
                    .background(Obsidian.void.copy(alpha = 0.55f)),
            )
            Box(Modifier.alpha(veu)) {
                // Sem rótulo no ícone: o alvo clicável já carrega o `onClickLabel`,
                // e nomear os dois faria o leitor de tela dizer tudo duas vezes.
                LIcon(Lucide.Pencil, tint = Obsidian.text1, size = glifo)
            }
        }
        menuEm?.let { em ->
            Popup(
                popupPositionProvider = remember(em) { AbaixoDoAlvo() },
                onDismissRequest = { menuEm = null },
                properties = PopupProperties(focusable = true),
            ) {
                MenuCard(acoes(), dismiss = { menuEm = null })
            }
        }
    }
}

// Menu colado embaixo da peça, alinhado pela esquerda dela. Cai pra CIMA quando não
// há espaço embaixo — o avatar fica no topo do formulário, mas a mesma peça pode
// acabar perto do rodapé numa janela baixa, e menu cortado é menu inutilizável.
private class AbaixoDoAlvo : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): IntOffset {
        val folga = 6
        val abaixo = anchorBounds.bottom + folga
        val y = if (abaixo + popupContentSize.height <= windowSize.height) abaixo
        else (anchorBounds.top - folga - popupContentSize.height).coerceAtLeast(0)
        val x = anchorBounds.left
            .coerceAtMost(windowSize.width - popupContentSize.width)
            .coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
