package app.astra.mobile.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

@Composable
private fun Modifier.afundandoCom(fonte: MutableInteractionSource): Modifier =
    indication(fonte, LocalIndication.current)

@Composable
fun ItemDeMenu(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
) {
    val fonte = remember { MutableInteractionSource() }
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier.afundandoCom(fonte),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = fonte,
    )
}

@Composable
fun BotaoDeTexto(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val fonte = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.afundandoCom(fonte),
        enabled = enabled,
        interactionSource = fonte,
        content = content,
    )
}

@Composable
fun BotaoCheio(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit,
) {
    val fonte = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.afundandoCom(fonte),
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = fonte,
        content = content,
    )
}

@Composable
fun BotaoContornado(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val fonte = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.afundandoCom(fonte),
        interactionSource = fonte,
        content = content,
    )
}
