package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import app.astra.desktop.ui.theme.Text
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.X
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

// Barra-titulo obsidiana da janela frameless: arrasta a janela, minimiza,
// maximiza/restaura e fecha — estilo Discord, pele Astra. Com sessao ativa,
// ganha lupa (busca) e sino (notificacoes, com badge).
@Composable
fun WindowScope.AstraTitleBar(
    state: WindowState,
    onClose: () -> Unit,
    showActions: Boolean = false,
    notifUnread: Int = 0,
    onOpenSearch: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    WindowDraggableArea {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(Obsidian.void),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Astra",
                style = TextStyle(color = Obsidian.text3, fontSize = 13.sp, fontFamily = DmSerif),
                modifier = Modifier.padding(start = 14.dp),
            )
            Spacer(Modifier.weight(1f))
            if (showActions) {
                TitleBarButton(Lucide.Search, onClick = onOpenSearch)
                TitleBarBell(notifUnread, onClick = onOpenNotifications)
            }
            TitleBarButton(Lucide.Minus) { state.isMinimized = true }
            TitleBarButton(if (state.placement == WindowPlacement.Maximized) Lucide.Copy else Lucide.Square) {
                state.placement =
                    if (state.placement == WindowPlacement.Maximized) WindowPlacement.Floating
                    else WindowPlacement.Maximized
            }
            TitleBarButton(Lucide.X, hoverColor = Obsidian.danger, onClick = onClose)
        }
    }
}

// Sino com badge de nao-lidas (bolinha ambar com contagem). Mesma pegada do
// TitleBarButton, com o badge sobreposto no canto.
@Composable
private fun TitleBarBell(unread: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) Obsidian.hover else Color.Transparent, tween(120))
    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .clickScale(interaction)
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(Lucide.Bell, tint = if (unread > 0) Obsidian.text1 else Obsidian.text2, size = 15.dp)
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 10.dp)
                    .clip(CircleShape)
                    .background(Obsidian.accent)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    if (unread > 9) "9+" else unread.toString(),
                    style = TextStyle(color = Obsidian.textInv, fontSize = 8.sp),
                )
            }
        }
    }
}

@Composable
private fun TitleBarButton(
    icon: ImageVector,
    hoverColor: Color = Obsidian.hover,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) hoverColor else Color.Transparent, tween(120))
    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .clickScale(interaction)
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(
            icon = icon,
            tint = if (hovered && hoverColor == Obsidian.danger) Obsidian.text1 else Obsidian.text2,
            size = 15.dp,
        )
    }
}
