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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian

// Barra-titulo obsidiana da janela frameless: arrasta a janela, minimiza,
// maximiza/restaura e fecha — estilo Discord, pele Astra.
@Composable
fun WindowScope.AstraTitleBar(
    state: WindowState,
    onClose: () -> Unit,
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
            TitleBarButton("–") { state.isMinimized = true }
            TitleBarButton(if (state.placement == WindowPlacement.Maximized) "❐" else "□") {
                state.placement =
                    if (state.placement == WindowPlacement.Maximized) WindowPlacement.Floating
                    else WindowPlacement.Maximized
            }
            TitleBarButton("✕", hoverColor = Obsidian.danger, onClick = onClose)
        }
    }
}

@Composable
private fun TitleBarButton(
    glyph: String,
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
            .background(bg)
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = TextStyle(
                color = if (hovered && hoverColor == Obsidian.danger) Obsidian.text1 else Obsidian.text2,
                fontSize = 13.sp,
            ),
        )
    }
}
