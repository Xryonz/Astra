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

// Barra-titulo obsidiana da janela frameless: arrasta a janela, minimiza,
// maximiza/restaura e fecha — estilo Discord, pele Astra. Com sessão ativa,
// ganha lupa (busca) e sino (notificações, com badge).
@Composable
fun WindowScope.AstraTitleBar(
    state: WindowState,
    onClose: () -> Unit,
    showActions: Boolean = false,
    notifUnread: Int = 0,
    onOpenSearch: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenMissions: () -> Unit = {},
    onOpenDesejos: () -> Unit = {},
    // null = não há atualização pendente (o ponto nem nasce).
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
                // Aviso de atualização: um PONTO à esquerda da lupa, e o card se abre
                // na horizontal a partir dele (pedido do dono). Fica aqui e não no
                // rodapé porque a barra é onde já moram os avisos do app — o canto
                // inferior era um segundo lugar pra "olhe isto" sem nada que ligasse
                // um ao outro.
                atualizacao?.let { PontoDeAtualizacao(it) }
                TitleBarButton(Lucide.Search, "Buscar", onClick = onOpenSearch)
                TitleBarBell(notifUnread, onClick = onOpenNotifications)
                // Missoes por ultimo dos tres: e o menos frequente. Busca e sino sao
                // reacao a alguma coisa; missao e quando a pessoa QUER olhar.
                TitleBarButton(Lucide.Target, "Missões", onClick = onOpenMissions)
                // Desejos por ultimo: e o menos frequente dos quatro. Busca e sino sao
                // reacao; missao e progresso proprio; desejo e curiosidade sobre o que
                // os outros pediram — o unico que ninguem abre com pressa.
                TitleBarButton(Lucide.Sparkles, "Estrela dos desejos", onClick = onOpenDesejos)
            }
            TitleBarButton(Lucide.Minus, "Minimizar") { state.isMinimized = true }
            val maximizada = state.placement == WindowPlacement.Maximized
            // O rotulo acompanha o icone: o botao alterna, e anunciar "Maximizar"
            // com a janela ja maximizada seria o leitor de tela dizendo o contrario
            // do que o clique faz.
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

// Sino com badge de não-lidas (bolinha ambar com contagem). Mesma pegada do
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
        // A contagem entra no NOME, e nao so no badge: a bolinha ambar e a unica
        // pista de que ha algo novo, e ela e puramente visual.
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
            rotulo = rotulo,
        )
    }
}

// PONTO DE ATUALIZACAO + card horizontal.
//
// O card antigo morava no canto inferior direito com 240dp de largura e tres
// linhas. Aqui ele nao cabe em altura, entao vira uma FAIXA: uma linha so, mais
// larga, na altura da barra. Nada de conteudo se perdeu — o que era empilhado
// virou sequencia, que e o que uma barra comporta.
//
// O ponto vem ANTES do card (pedido do dono): fechado, o aviso ocupa 7dp e nao
// disputa com nada; aberto, ele se desenrola na horizontal a partir dali. Um card
// permanentemente aberto na barra seria uma tarja fixa dizendo a mesma coisa o dia
// inteiro — e aviso que nao se pode encolher vira moldura.
//
// Cresce PRA ESQUERDA (expandFrom = End) porque a direita esta ocupada pela lupa,
// pelo sino e pelos botoes da janela. Crescer pra cima deles cobriria controles em
// uso; a esquerda e o vazio da barra, que existe justamente pra isso.
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
        // O ponto PULSA so enquanto ha algo a fazer, e para quando o card esta
        // aberto: uma vez que voce olhou, o pisca-pisca so continuaria pedindo
        // atencao pra uma coisa que ja tem a sua.
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
