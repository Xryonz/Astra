package app.astra.desktop.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.FonteSemApertoNemFoco
import app.astra.desktop.ui.theme.FormaDoKit
import app.astra.desktop.ui.theme.Obsidian
import com.composables.ui.components.Button
import com.composables.ui.components.ButtonSize
import com.composables.ui.components.ButtonStyle
import com.composables.ui.components.IconButton

private val RespiroDoBotaoDoKit = PaddingValues(horizontal = 14.dp)

@Composable
private fun Modifier.comportamentoDoAstra(
    toque: MutableInteractionSource,
    ligado: Boolean,
    foco: FocusRequester?,
): Modifier = this
    .then(if (foco != null) Modifier.focusRequester(foco) else Modifier)
    .clickScale(toque, formaDoFoco = FormaDoKit)
    .pointerHoverIcon(if (ligado) PointerIcon.Hand else PointerIcon.Default)

@Composable
private fun rememberFonteDoKit(toque: MutableInteractionSource) = remember(toque) { FonteSemApertoNemFoco(toque) }

@Composable
internal fun BotaoDoKit(
    onClick: () -> Unit,
    estilo: ButtonStyle,
    modifier: Modifier = Modifier,
    ligado: Boolean = true,
    foco: FocusRequester? = null,
    toque: MutableInteractionSource = remember { MutableInteractionSource() },
    conteudo: @Composable RowScope.() -> Unit,
) {
    val fonteDoKit = rememberFonteDoKit(toque)
    Button(
        onClick = onClick,
        modifier = modifier.comportamentoDoAstra(toque, ligado, foco),
        enabled = ligado,
        style = estilo,
        buttonSize = ButtonSize.Small,
        contentPadding = RespiroDoBotaoDoKit,
        interactionSource = fonteDoKit,
        content = conteudo,
    )
}

@Composable
internal fun BotaoDePerigoDoKit(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ligado: Boolean = true,
    foco: FocusRequester? = null,
    conteudo: @Composable RowScope.() -> Unit,
) {
    val toque = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.comportamentoDoAstra(toque, ligado, foco),
        enabled = ligado,
        style = ButtonStyle.Outlined,
        buttonSize = ButtonSize.Small,
        contentPadding = RespiroDoBotaoDoKit,
        interactionSource = rememberFonteDoKit(toque),
        contentColor = Obsidian.danger,
        content = conteudo,
    )
}

@Composable
internal fun BotaoIconeDoKit(
    onClick: () -> Unit,
    estilo: ButtonStyle,
    modifier: Modifier = Modifier,
    ligado: Boolean = true,
    toque: MutableInteractionSource = remember { MutableInteractionSource() },
    conteudo: @Composable () -> Unit,
) {
    val fonteDoKit = rememberFonteDoKit(toque)
    IconButton(
        onClick = onClick,
        modifier = modifier.comportamentoDoAstra(toque, ligado, null),
        enabled = ligado,
        style = estilo,
        buttonSize = ButtonSize.Small,
        interactionSource = fonteDoKit,
        content = conteudo,
    )
}
