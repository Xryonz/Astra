package app.astra.desktop.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.ui.theme.ColorScheme
import com.composables.ui.theme.InteractionMode
import com.composables.ui.theme.LocalColorScheme
import com.composables.ui.theme.LocalInteractionMode
import com.composables.ui.theme.alphas
import com.composables.ui.theme.backgroundColor
import com.composables.ui.theme.borderColor
import com.composables.ui.theme.buttonShape
import com.composables.ui.theme.colors
import com.composables.ui.theme.controlColor
import com.composables.ui.theme.defaultIndication as indicacaoPadraoDoKit
import com.composables.ui.theme.defaultTextSelectionColors as selecaoPadraoDoKit
import com.composables.ui.theme.destructiveColor
import com.composables.ui.theme.dialogShape
import com.composables.ui.theme.disabledAlpha
import com.composables.ui.theme.fieldColor
import com.composables.ui.theme.fieldShape
import com.composables.ui.theme.indications
import com.composables.ui.theme.inverseIndication
import com.composables.ui.theme.largeShape
import com.composables.ui.theme.mediumShape
import com.composables.ui.theme.menuShape
import com.composables.ui.theme.mutedColor
import com.composables.ui.theme.onBackgroundColor
import com.composables.ui.theme.onControlColor
import com.composables.ui.theme.onDestructiveColor
import com.composables.ui.theme.onFieldColor
import com.composables.ui.theme.onPanelColor
import com.composables.ui.theme.onPrimaryColor
import com.composables.ui.theme.onSecondaryColor
import com.composables.ui.theme.onSelectedControlColor
import com.composables.ui.theme.overlayShadow
import com.composables.ui.theme.panelColor
import com.composables.ui.theme.primaryColor
import com.composables.ui.theme.raisedShadow
import com.composables.ui.theme.ringColor
import com.composables.ui.theme.scrimColor
import com.composables.ui.theme.secondaryColor
import com.composables.ui.theme.selectedControlColor
import com.composables.ui.theme.shadows
import com.composables.ui.theme.shapes
import com.composables.ui.theme.sheetShape
import com.composables.ui.theme.smallShape
import com.composables.ui.theme.switchSelectedTrackColor
import com.composables.ui.theme.switchThumbColor
import com.composables.ui.theme.switchTrackColor
import com.composables.ui.theme.textSelectionColors
import com.composables.ui.theme.thumbColor
import com.composeunstyled.FocusVisibilityProvider
import com.composeunstyled.theme.ThemeComposable
import com.composeunstyled.theme.buildTheme
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch

val RaioDoKit = 4.dp

val FormaDoKit = RoundedCornerShape(RaioDoKit)

private const val VEU_SOBRE_O_ACENTO = 0.10f
private const val VEU_DO_APERTO_NO_ACENTO = 0.18f
private const val ESCURO_ATRAS_DO_DIALOGO = 0.72f
private const val TRILHO_DA_CHAVE_LIGADA = 0.55f
private const val SELECAO_DE_TEXTO = 0.3f
private const val ALFA_DESLIGADO = 0.4f

val DegrauNaSuperficie: IndicationNodeFactory = DegrauDeSuperficie(
    corSobre = { Obsidian.hover },
    corApertado = { Obsidian.active },
)

val VeuNoAcento: IndicationNodeFactory = DegrauDeSuperficie(
    corSobre = { Color.Black.copy(alpha = VEU_SOBRE_O_ACENTO) },
    corApertado = { Color.Black.copy(alpha = VEU_DO_APERTO_NO_ACENTO) },
)

val TemaDoAstraNoKit: ThemeComposable = buildTheme {
    name = "Astra"
    defaultIndication = LocalIndication.current
    defaultTextSelectionColors = LocalTextSelectionColors.current
    defaultTextStyle = TextStyle(fontFamily = DmSans, fontSize = 13.sp, color = Obsidian.text1)
    defaultContentColor = Obsidian.text1
    properties[colors] = coresDoAstra()
    properties[shapes] = formasDoAstra()
    properties[shadows] = mapOf(
        raisedShadow to Shadow(radius = 12.dp, color = Color.Black, offset = DpOffset(0.dp, 4.dp), alpha = 0.28f),
        overlayShadow to Shadow(radius = 24.dp, color = Color.Black, offset = DpOffset(0.dp, 8.dp), alpha = 0.4f),
    )
    properties[indications] = mapOf(
        indicacaoPadraoDoKit to DegrauNaSuperficie,
        inverseIndication to VeuNoAcento,
    )
    properties[alphas] = mapOf(disabledAlpha to ALFA_DESLIGADO)
    properties[textSelectionColors] = mapOf(
        selecaoPadraoDoKit to TextSelectionColors(
            handleColor = Obsidian.accent,
            backgroundColor = Obsidian.accent.copy(alpha = SELECAO_DE_TEXTO),
        ),
    )
    extend { conteudo ->
        CompositionLocalProvider(
            LocalColorScheme provides ColorScheme.Dark,
            LocalInteractionMode provides InteractionMode.Pointer,
        ) {
            FocusVisibilityProvider { conteudo() }
        }
    }
}

private fun coresDoAstra() = mapOf(
    backgroundColor to Obsidian.base,
    onBackgroundColor to Obsidian.text1,
    panelColor to Obsidian.overlay,
    onPanelColor to Obsidian.text1,
    mutedColor to Obsidian.text2,
    primaryColor to Obsidian.accent,
    onPrimaryColor to Obsidian.textInv,
    secondaryColor to Obsidian.hover,
    onSecondaryColor to Obsidian.text1,
    controlColor to Obsidian.active,
    onControlColor to Obsidian.text3,
    thumbColor to Obsidian.text1,
    switchTrackColor to Obsidian.active,
    switchSelectedTrackColor to Obsidian.accent.copy(alpha = TRILHO_DA_CHAVE_LIGADA),
    switchThumbColor to Obsidian.text1,
    selectedControlColor to Obsidian.active,
    onSelectedControlColor to Obsidian.text1,
    destructiveColor to Obsidian.danger,
    onDestructiveColor to Obsidian.textInv,
    borderColor to Obsidian.borderDim,
    fieldColor to Obsidian.base,
    onFieldColor to Obsidian.text1,
    scrimColor to Obsidian.void.copy(alpha = ESCURO_ATRAS_DO_DIALOGO),
    ringColor to Obsidian.accent,
)

private fun formasDoAstra() = mapOf(
    smallShape to FormaDoKit,
    mediumShape to FormaDoKit,
    largeShape to FormaDoKit,
    buttonShape to FormaDoKit,
    dialogShape to FormaDoKit,
    sheetShape to RoundedCornerShape(topStart = RaioDoKit, topEnd = RaioDoKit),
    menuShape to FormaDoKit,
    fieldShape to FormaDoKit,
)

class FonteSemApertoNemFoco(val original: MutableInteractionSource) : MutableInteractionSource {
    override val interactions = original.interactions.filterNot { it is PressInteraction || it is FocusInteraction }

    override suspend fun emit(interaction: Interaction) = original.emit(interaction)

    override fun tryEmit(interaction: Interaction) = original.tryEmit(interaction)
}

private class DegrauDeSuperficie(
    private val corSobre: () -> Color,
    private val corApertado: () -> Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        val real = (interactionSource as? FonteSemApertoNemFoco)?.original ?: interactionSource
        return NoDoDegrau(real, corSobre, corApertado)
    }

    override fun equals(other: Any?) = other === this

    override fun hashCode() = System.identityHashCode(this)
}

private class NoDoDegrau(
    private val fonte: InteractionSource,
    private val corSobre: () -> Color,
    private val corApertado: () -> Color,
) : Modifier.Node(), DrawModifierNode {
    private val sobre = mutableSetOf<HoverInteraction.Enter>()
    private val apertos = mutableSetOf<PressInteraction.Press>()

    override fun onAttach() {
        coroutineScope.launch {
            fonte.interactions.collect { interacao ->
                when (interacao) {
                    is HoverInteraction.Enter -> sobre += interacao
                    is HoverInteraction.Exit -> sobre -= interacao.enter
                    is PressInteraction.Press -> apertos += interacao
                    is PressInteraction.Release -> apertos -= interacao.press
                    is PressInteraction.Cancel -> apertos -= interacao.press
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val cor = when {
            apertos.isNotEmpty() -> corApertado()
            sobre.isNotEmpty() -> corSobre()
            else -> Color.Unspecified
        }
        if (cor.isSpecified) drawRect(cor)
        drawContent()
    }
}
