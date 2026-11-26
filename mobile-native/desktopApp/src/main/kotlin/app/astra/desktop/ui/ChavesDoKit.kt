package app.astra.desktop.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.FormaDoKit
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.ui.theme.borderColor
import com.composables.ui.theme.colors
import com.composables.ui.theme.controlColor
import com.composables.ui.theme.defaultIndication
import com.composables.ui.theme.indications
import com.composables.ui.theme.inverseIndication
import com.composables.ui.theme.onPrimaryColor
import com.composables.ui.theme.primaryColor
import com.composables.ui.theme.switchSelectedTrackColor
import com.composables.ui.theme.switchThumbColor
import com.composables.ui.theme.switchTrackColor
import com.composeunstyled.CheckboxScope
import com.composeunstyled.CheckedIndicator
import com.composeunstyled.Thumb
import com.composeunstyled.Track
import com.composeunstyled.UnstyledCheckbox
import com.composeunstyled.UnstyledSwitch
import com.composeunstyled.theme.Theme

private const val VIRADA_DA_CHAVE_MS = 160
private const val MARCA_DA_CAIXA_MS = 120
private val FormaDaAlca = RoundedCornerShape(3.dp)
private val ALTURA_MINIMA_DA_CAIXA = 28.dp

@Composable
internal fun ChaveDoKit(ligada: Boolean, rotulo: String, aoMudar: (Boolean) -> Unit) {
    val reduzir = LocalReduceMotion.current
    val toque = remember { MutableInteractionSource() }
    val trilho by animateColorAsState(
        if (ligada) Theme[colors][switchSelectedTrackColor] else Theme[colors][switchTrackColor],
        if (reduzir) snap() else tween(VIRADA_DA_CHAVE_MS, easing = EaseOutStd),
        label = "trilhoDaChave",
    )
    UnstyledSwitch(
        checked = ligada,
        onCheckedChange = aoMudar,
        modifier = Modifier.anelDeFoco(FormaDoKit, folga = 2.dp).pointerHoverIcon(PointerIcon.Hand),
        interactionSource = toque,
        accessibilityLabel = rotulo,
    ) {
        Track(
            Modifier
                .background(trilho, FormaDoKit)
                .border(Dp.Hairline, Theme[colors][borderColor], FormaDoKit)
                .size(width = 44.dp, height = 24.dp)
                .padding(2.dp),
        ) {
            Thumb(animationSpec = if (reduzir) snap() else tween(VIRADA_DA_CHAVE_MS, easing = EaseOutStd)) {
                Box(Modifier.size(20.dp).background(Theme[colors][switchThumbColor], FormaDaAlca))
            }
        }
    }
}

@Composable
internal fun CaixaDoKit(
    marcada: Boolean,
    aoMudar: (Boolean) -> Unit,
    rotulo: String? = null,
    rotuloVisivel: (@Composable RowScope.() -> Unit)? = null,
) {
    val toque = remember { MutableInteractionSource() }
    UnstyledCheckbox(
        checked = marcada,
        onCheckedChange = aoMudar,
        modifier = Modifier
            .clickScale(toque, formaDoFoco = FormaDoKit)
            .defaultMinSize(minHeight = ALTURA_MINIMA_DA_CAIXA)
            .pointerHoverIcon(PointerIcon.Hand),
        interactionSource = toque,
        accessibilityLabel = rotulo,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            QuadradoDaCaixa(marcada)
            rotuloVisivel?.invoke(this)
        }
    }
}

@Composable
private fun CheckboxScope.QuadradoDaCaixa(marcada: Boolean) {
    val reduzir = LocalReduceMotion.current
    val cor = if (marcada) Theme[colors][primaryColor] else Theme[colors][controlColor]
    CheckedIndicator(
        indication = if (marcada) Theme[indications][inverseIndication] else Theme[indications][defaultIndication],
        modifier = Modifier
            .clip(FormaDoKit)
            .background(cor, FormaDoKit)
            .border(1.dp, if (marcada) cor else Theme[colors][borderColor], FormaDoKit)
            .size(20.dp),
        enter = if (reduzir) EnterTransition.None else fadeIn(tween(MARCA_DA_CAIXA_MS)),
        exit = if (reduzir) ExitTransition.None else fadeOut(tween(MARCA_DA_CAIXA_MS)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LIcon(Lucide.Check, tint = Theme[colors][onPrimaryColor], size = 13.dp)
        }
    }
}
