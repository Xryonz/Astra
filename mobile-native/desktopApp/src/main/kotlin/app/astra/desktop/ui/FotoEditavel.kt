package app.astra.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.Obsidian
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil

class AcoesDoCartao {
    var foto: () -> List<MenuEntry> = { emptyList() }
    var banner: () -> List<MenuEntry> = { emptyList() }
}

@Composable
fun FotoEditavel(
    forma: Shape,
    acoes: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    glifo: Dp = 18.dp,
    rotulo: String = "editar imagem",
    conteudo: @Composable (hover: Boolean) -> Unit,
) {
    val fonte = remember { MutableInteractionSource() }
    val hover by fonte.collectIsHoveredAsState()
    var menuAberto by remember { mutableStateOf(false) }

    val aceso = hover || menuAberto
    val veu = animateFloatAsState(if (aceso) 1f else 0f, tween(140), label = "veuDaFoto")
    val veuNaTela by remember { derivedStateOf { veu.value > 0f } }

    Box(
        modifier
            .clip(forma)
            .hoverable(fonte)
            .clickable(interactionSource = fonte, indication = null, onClickLabel = rotulo) {
                menuAberto = true
            },
        contentAlignment = Alignment.Center,
    ) {
        conteudo(aceso)
        if (veuNaTela) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = veu.value }
                    .background(Obsidian.void.copy(alpha = 0.55f)),
            )
            Box(Modifier.graphicsLayer { alpha = veu.value }) {
                LIcon(Lucide.Pencil, tint = Obsidian.text1, size = glifo)
            }
        }
        MenuDoKit(
            aberto = menuAberto,
            aoMudar = { menuAberto = it },
            modifier = Modifier.matchParentSize(),
            itens = { EntradasDoMenu(acoes(), fechar = { menuAberto = false }) },
        ) {
            Spacer(Modifier.fillMaxSize())
        }
    }
}
