package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.FormaDoKit
import com.composables.ui.theme.borderColor
import com.composables.ui.theme.colors
import com.composables.ui.theme.defaultIndication
import com.composables.ui.theme.indications
import com.composables.ui.theme.mutedColor
import com.composables.ui.theme.primaryColor
import com.composeunstyled.TabList
import com.composeunstyled.TabListScope
import com.composeunstyled.Tab
import com.composeunstyled.UnstyledTabGroup
import com.composeunstyled.theme.Theme

private const val TROCA_DA_ABA_MS = 180

@Composable
internal fun <T> AbasDoKit(
    selecionada: T,
    aoSelecionar: (T) -> Unit,
    abas: List<T>,
    modifier: Modifier = Modifier,
    rotulo: @Composable (aba: T, cor: Color) -> Unit,
) {
    UnstyledTabGroup(selectedTab = selecionada, onSelectedTabChange = aoSelecionar, tabs = abas, modifier = modifier) {
        TabList {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                abas.forEach { aba -> AbaDoKit(aba, aba == selecionada) { cor -> rotulo(aba, cor) } }
            }
        }
    }
}

@Composable
private fun <T> TabListScope<T>.AbaDoKit(aba: T, ativa: Boolean, rotulo: @Composable (cor: Color) -> Unit) {
    val reduzir = LocalReduceMotion.current
    val toque = remember { MutableInteractionSource() }
    val transicao = if (reduzir) snap<Color>() else tween(TROCA_DA_ABA_MS, easing = EaseOutStd)
    val corDoTexto by animateColorAsState(
        if (ativa) Theme[colors][primaryColor] else Theme[colors][mutedColor],
        transicao,
        label = "textoDaAba",
    )
    val corDoTraco by animateColorAsState(
        if (ativa) Theme[colors][primaryColor] else Theme[colors][borderColor],
        transicao,
        label = "tracoDaAba",
    )
    Tab(
        key = aba,
        modifier = Modifier
            .clickScale(toque, formaDoFoco = FormaDoKit)
            .clip(FormaDoKit)
            .pointerHoverIcon(PointerIcon.Hand),
        interactionSource = toque,
        indication = Theme[indications][defaultIndication],
    ) {
        Column {
            Row(
                Modifier.heightIn(min = 32.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rotulo(corDoTexto)
            }
            Box(Modifier.padding(top = 2.dp).fillMaxWidth().height(1.dp).background(corDoTraco))
        }
    }
}
