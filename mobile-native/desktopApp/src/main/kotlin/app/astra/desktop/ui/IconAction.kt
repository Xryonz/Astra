package app.astra.desktop.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.FormaDoKit
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import com.composables.icons.lucide.LoaderCircle
import com.composables.icons.lucide.Lucide
import com.composables.ui.components.ButtonStyle
import com.composables.ui.components.Tooltip
import com.composables.ui.components.TooltipPanel
import com.composables.ui.components.TooltipSide
import com.composeunstyled.LocalContentColor

private const val ESPERA_DICA_MS = 420L
private const val VOLTA_DO_GIRO_MS = 900

@Composable
fun BotaoIcone(
    icone: ImageVector,
    dica: String,
    accent: Boolean = false,
    danger: Boolean = false,
    ocupado: Boolean = false,
    onClick: () -> Unit,
) {
    val interacao = remember { MutableInteractionSource() }
    val sobHover by interacao.collectIsHoveredAsState()

    val corBase = when {
        danger -> Obsidian.danger
        accent -> Obsidian.accent
        else -> Obsidian.text2
    }
    val conteudo by animateColorAsState(
        if (sobHover) (if (danger) Obsidian.danger else Obsidian.text1) else corBase,
        tween(120),
    )

    DicaDoKit(dica, ligada = !ocupado, lado = TooltipSide.Bottom) {
        BotaoIconeDoKit(
            onClick = onClick,
            estilo = ButtonStyle.Outlined,
            ligado = !ocupado,
            toque = interacao,
        ) {
            if (ocupado) {
                val giro = faseDeEnfeite(VOLTA_DO_GIRO_MS, repouso = 0f)
                LIcon(
                    Lucide.LoaderCircle, tint = conteudo, size = 16.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = giro.value * 360f },
                )
            } else {
                LIcon(icone, tint = conteudo, size = 16.dp, rotulo = dica)
            }
        }
    }
}

@Composable
fun DicaDoKit(
    dica: String,
    ligada: Boolean = true,
    lado: TooltipSide = TooltipSide.Top,
    alvo: @Composable () -> Unit,
) {
    val reduzir = LocalReduceMotion.current
    Tooltip(
        enabled = ligada,
        side = lado,
        sideOffset = 6.dp,
        hoverDelayMillis = ESPERA_DICA_MS,
        panel = {
            TooltipPanel(
                shape = FormaDoKit,
                enter = if (reduzir) EnterTransition.None else null,
                exit = if (reduzir) ExitTransition.None else null,
            ) {
                Text(dica, style = TextStyle(color = LocalContentColor.current, fontSize = 11.sp))
            }
        },
        anchor = alvo,
    )
}
