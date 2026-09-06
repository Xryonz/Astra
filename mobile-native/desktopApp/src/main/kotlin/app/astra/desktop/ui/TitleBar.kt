package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import app.astra.desktop.ui.theme.Text
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.update.UpdateService
import app.astra.desktop.update.UpdateState
import kotlinx.coroutines.launch
import app.astra.desktop.ui.theme.Obsidian
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.X
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip

@Composable
fun WindowScope.AstraTitleBar(
    state: WindowState,
    onClose: () -> Unit,
    showActions: Boolean = false,
    notifUnread: Int = 0,
    onOpenSearch: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenMissions: () -> Unit = {},
    missoesProntas: Int = 0,
    onOpenDesejos: () -> Unit = {},
    atualizacao: UpdateService? = null,
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
                atualizacao?.let { PontoDeAtualizacao(it) }
                TitleBarButton(Lucide.Search, "Buscar", onClick = onOpenSearch)
                TitleBarBell(notifUnread, onClick = onOpenNotifications)
                TitleBarButton(
                    Lucide.Target,
                    if (missoesProntas > 0) "Missões — $missoesProntas para resgatar" else "Missões",
                    aviso = missoesProntas,
                    onClick = onOpenMissions,
                )
                TitleBarButton(Lucide.Sparkles, "Estrela dos desejos", onClick = onOpenDesejos)
            }
            TitleBarButton(Lucide.Minus, "Minimizar") { state.isMinimized = true }
            val maximizada = state.placement == WindowPlacement.Maximized
            TitleBarButton(
                if (maximizada) Lucide.Copy else Lucide.Square,
                if (maximizada) "Restaurar" else "Maximizar",
            ) {
                state.placement =
                    if (maximizada) WindowPlacement.Floating else WindowPlacement.Maximized
            }
            TitleBarButton(Lucide.X, "Fechar", hoverColor = Obsidian.danger, onClick = onClose)
        }
    }
}

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
        LIcon(
            Lucide.Bell,
            tint = if (unread > 0) Obsidian.text1 else Obsidian.text2,
            size = 15.dp,
            rotulo = if (unread > 0) "Notificações — $unread não lidas" else "Notificações",
        )
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
    rotulo: String,
    hoverColor: Color = Obsidian.hover,
    aviso: Int = 0,
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
            tint = when {
                hovered && hoverColor == Obsidian.danger -> Obsidian.text1
                aviso > 0 -> Obsidian.text1
                else -> Obsidian.text2
            },
            size = 15.dp,
            rotulo = rotulo,
        )
        if (aviso > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 10.dp)
                    .clip(CircleShape)
                    .background(Obsidian.accent)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    if (aviso > 9) "9+" else aviso.toString(),
                    style = TextStyle(color = Obsidian.textInv, fontSize = 8.sp),
                )
            }
        }
    }
}

@Composable
private fun PontoDeAtualizacao(updater: UpdateService) {
    val st by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    var aberto by remember { mutableStateOf(false) }

    val pendente = st is UpdateState.Available || st is UpdateState.Downloading || st is UpdateState.Ready
    if (!pendente) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            visible = aberto,
            enter = expandHorizontally(tween(220, easing = EaseOutStd), expandFrom = Alignment.End) +
                fadeIn(tween(180)),
            exit = shrinkHorizontally(tween(160, easing = EaseOutStd), shrinkTowards = Alignment.End) +
                fadeOut(tween(120)),
        ) {
            Row(
                Modifier
                    .padding(end = 8.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Obsidian.raised)
                    .border(1.dp, Obsidian.accent.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (val s = st) {
                    is UpdateState.Available -> {
                        Text(
                            "Astra ${s.version} disponível",
                            style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(10.dp))
                        AcaoDaFaixa("atualizar") { scope.launch { updater.downloadAndStage(s) } }
                    }
                    is UpdateState.Downloading -> Text(
                        "baixando ${s.version} · ${(s.progress * 100).toInt()}%",
                        style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
                        maxLines = 1,
                    )
                    is UpdateState.Ready -> {
                        Text(
                            "${s.version} pronto",
                            style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(10.dp))
                        AcaoDaFaixa("reiniciar") { updater.restartToInstall() }
                    }
                    else -> {}
                }
            }
        }
        val reduzir = LocalReduceMotion.current
        val brilho = if (reduzir || aberto) 1f else {
            val t = rememberInfiniteTransition(label = "updDot")
            t.animateFloat(
                0.45f, 1f,
                infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "updDotAlpha",
            ).value
        }
        val fonteDeInteracao = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(26.dp)
                .clickScale(fonteDeInteracao)
                .clickable(
                    interactionSource = fonteDeInteracao,
                    indication = null,
                    onClickLabel = if (aberto) "Fechar o aviso de atualização" else "Ver a atualização disponível",
                ) { aberto = !aberto },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Obsidian.accent.copy(alpha = brilho)),
            )
        }
    }
}

@Composable
private fun AcaoDaFaixa(rotulo: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Text(
        rotulo,
        style = TextStyle(
            color = if (hov) Obsidian.accent else Obsidian.text1,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        ),
        modifier = Modifier
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
    )
}
