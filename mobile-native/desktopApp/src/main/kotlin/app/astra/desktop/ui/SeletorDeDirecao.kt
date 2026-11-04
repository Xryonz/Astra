package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.Obsidian
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowDownLeft
import com.composables.icons.lucide.ArrowDownRight
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.ArrowUpLeft
import com.composables.icons.lucide.ArrowUpRight
import com.composables.icons.lucide.Lucide

internal enum class DirecaoDoDegrade(val graus: Int, val icone: ImageVector, val rotulo: String) {
    DIREITA(90, Lucide.ArrowRight, "degradê para a direita"),
    BAIXO_DIREITA(135, Lucide.ArrowDownRight, "degradê para baixo e à direita"),
    BAIXO(180, Lucide.ArrowDown, "degradê para baixo"),
    BAIXO_ESQUERDA(225, Lucide.ArrowDownLeft, "degradê para baixo e à esquerda"),
    ESQUERDA(270, Lucide.ArrowLeft, "degradê para a esquerda"),
    CIMA_ESQUERDA(315, Lucide.ArrowUpLeft, "degradê para cima e à esquerda"),
    CIMA(0, Lucide.ArrowUp, "degradê para cima"),
    CIMA_DIREITA(45, Lucide.ArrowUpRight, "degradê para cima e à direita"),
}

internal val GRAUS_DO_DEGRADE_DE_FABRICA = DirecaoDoDegrade.DIREITA.graus

private val FormaDoBotaoDeDirecao = RoundedCornerShape(8.dp)

@Composable
internal fun SeletorDeDirecao(graus: Int, aoEscolher: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DirecaoDoDegrade.entries.forEach { direcao ->
            BotaoDeDirecao(direcao, selecionada = direcao.graus == graus) { aoEscolher(direcao.graus) }
        }
    }
}

@Composable
private fun BotaoDeDirecao(direcao: DirecaoDoDegrade, selecionada: Boolean, aoTocar: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    val hover by interacao.collectIsHoveredAsState()
    Box(
        Modifier
            .size(28.dp)
            .clickScale(interacao, formaDoFoco = FormaDoBotaoDeDirecao)
            .clip(FormaDoBotaoDeDirecao)
            .background(
                when {
                    selecionada -> Obsidian.active
                    hover -> Obsidian.hover
                    else -> Color.Transparent
                },
            )
            .border(1.dp, if (selecionada) Obsidian.accent else Obsidian.borderDim, FormaDoBotaoDeDirecao)
            .hoverable(interacao)
            .clickable(interactionSource = interacao, indication = null, role = Role.RadioButton, onClick = aoTocar)
            .semantics {
                selected = selecionada
                toggleableState = ToggleableState(selecionada)
            },
        contentAlignment = Alignment.Center,
    ) {
        LIcon(
            direcao.icone,
            tint = if (selecionada) Obsidian.text1 else Obsidian.text3,
            size = 14.dp,
            rotulo = direcao.rotulo,
        )
    }
}
