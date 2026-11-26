package app.astra.desktop.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Text
import com.composables.ui.components.DropdownMenu
import com.composables.ui.components.DropdownMenuAlignment
import com.composables.ui.components.DropdownMenuItem
import com.composables.ui.components.DropdownMenuItemStyle
import com.composables.ui.components.DropdownMenuPanel
import com.composables.ui.components.DropdownMenuPanelContentScope
import com.composables.ui.components.DropdownMenuSide
import com.composeunstyled.LocalContentColor

@Composable
internal fun MenuDoKit(
    aberto: Boolean,
    aoMudar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    lado: DropdownMenuSide = DropdownMenuSide.Bottom,
    alinhamento: DropdownMenuAlignment = DropdownMenuAlignment.Start,
    itens: @Composable DropdownMenuPanelContentScope.() -> Unit,
    ancora: @Composable () -> Unit,
) {
    val reduzir = LocalReduceMotion.current
    DropdownMenu(
        expanded = aberto,
        onExpandedChange = aoMudar,
        modifier = modifier,
        side = lado,
        alignment = alinhamento,
        panel = {
            DropdownMenuPanel(
                minWidth = 150.dp,
                maxWidth = 260.dp,
                enter = if (reduzir) EnterTransition.None else null,
                exit = if (reduzir) ExitTransition.None else null,
                content = itens,
            )
        },
        anchor = ancora,
    )
}

@Composable
internal fun DropdownMenuPanelContentScope.ItemDoMenu(
    rotulo: String,
    icone: ImageVector? = null,
    perigo: Boolean = false,
    marca: ImageVector? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        style = if (perigo) DropdownMenuItemStyle.Destructive else DropdownMenuItemStyle.Default,
        leading = icone?.let { { LIcon(it, tint = LocalContentColor.current, size = 15.dp) } },
        trailing = marca?.let { { LIcon(it, tint = LocalContentColor.current, size = 13.dp) } },
    ) {
        Text(
            rotulo,
            style = TextStyle(color = LocalContentColor.current, fontSize = 13.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
