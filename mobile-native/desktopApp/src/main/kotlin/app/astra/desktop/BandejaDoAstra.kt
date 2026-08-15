package app.astra.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.shape.RoundedCornerShape
import app.astra.desktop.ui.clickable
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import java.awt.MouseInfo
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

// A BANDEJA COM MENU DESENHADO PELO ASTRA.
//
// O `Tray` do Compose Desktop é o `SystemTray` do AWT por baixo, e o menu dele é um
// `PopupMenu` do Win32: quem pinta é o Windows. Ele não aceita cor, fonte, canto,
// ícone nem espaçamento — não existe API pra estilizar, o objeto simplesmente não
// tem essas propriedades. Era por isso que o menu da bandeja era o único pedaço do
// app que não parecia o app.
//
// Aqui o `TrayIcon` é criado na mão (sem `PopupMenu`), e o clique-direito abre uma
// JANELA nossa — sem moldura, transparente, sempre no topo, no ponto do cursor —
// com o mesmo vocabulário visual do resto. É o que Discord e Spotify fazem, e pelo
// mesmo motivo.
//
// O QUE ISTO CUSTA, dito antes: a bandeja é o caminho por onde o app fica vivo em
// segundo plano E por onde os avisos saem. Os dois passam a ser código nosso — o
// `sendNotification` do Compose vira `TrayIcon.displayMessage`.

data class ItemDaBandeja(
    val rotulo: String,
    val perigo: Boolean = false,
    val aoClicar: () -> Unit,
)

// O que o resto do app segura pra mandar aviso. Classe e não função solta porque o
// ícone só existe depois que a bandeja monta, e quem avisa (o ShellScreen) é criado
// antes — o campo mutável resolve a ordem sem ninguém precisar esperar.
class Bandeja {
    internal var icone: TrayIcon? = null

    // Sem bandeja (SO sem suporte, ou criação recusada) isto é um silêncio
    // deliberado: o aviso visual dentro do app já aconteceu, e derrubar a mensagem
    // por causa do ícone seria trocar um detalhe por uma falha.
    fun avisar(titulo: String, corpo: String) {
        runCatching { icone?.displayMessage(titulo, corpo, TrayIcon.MessageType.NONE) }
    }
}

private val LARGURA = 190.dp
private val ALTURA_ITEM = 32.dp
private val RESPIRO = 8.dp

@Composable
fun BandejaComMenu(
    bandeja: Bandeja,
    dica: String,
    aoAtivar: () -> Unit,
    itens: () -> List<ItemDaBandeja>,
) {
    // Ponto do cursor quando o menu abriu. null = fechado.
    var menuEm by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val lista = if (menuEm != null) itens() else emptyList()

    DisposableEffect(Unit) {
        if (!SystemTray.isSupported()) return@DisposableEffect onDispose { }
        val imagem = runCatching {
            Toolkit.getDefaultToolkit().createImage(
                Bandeja::class.java.getResource("/astra-icon.png"),
            )
        }.getOrNull()
        val icone = TrayIcon(imagem, dica).apply { isImageAutoSize = true }
        icone.addMouseListener(object : MouseAdapter() {
            // mouseReleased e não mousePressed: no Windows o `isPopupTrigger` vem no
            // release, e testar só o press perderia metade dos cliques-direito.
            override fun mouseReleased(e: MouseEvent) = trata(e)
            override fun mousePressed(e: MouseEvent) = trata(e)

            private fun trata(e: MouseEvent) {
                if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                    // Segundo clique-direito FECHA. É a rede de segurança do menu:
                    // se por algum motivo ele não receber foco (e portanto não
                    // fechar sozinho ao perder), ainda há um jeito óbvio de sair
                    // dele sem escolher nada.
                    menuEm = if (menuEm != null) null else {
                        val p = runCatching { MouseInfo.getPointerInfo().location }.getOrNull()
                        Pair(p?.x ?: e.xOnScreen, p?.y ?: e.yOnScreen)
                    }
                } else if (e.clickCount >= 2 && e.button == MouseEvent.BUTTON1) {
                    menuEm = null
                    aoAtivar()
                }
            }
        })
        val ok = runCatching { SystemTray.getSystemTray().add(icone) }.isSuccess
        if (ok) bandeja.icone = icone
        onDispose {
            bandeja.icone = null
            runCatching { SystemTray.getSystemTray().remove(icone) }
        }
    }

    val em = menuEm ?: return
    val altura = ALTURA_ITEM * lista.size + RESPIRO * 2
    // O menu nasce ACIMA e à ESQUERDA do cursor: a bandeja fica no canto inferior
    // direito, então descer ou ir pra direita jogaria a janela pra fora da tela.
    // A conta em pixels de tela (e não em dp) porque a posição do cursor vem do SO.
    val d = androidx.compose.ui.platform.LocalDensity.current
    val larguraPx = with(d) { LARGURA.roundToPx() }
    val alturaPx = with(d) { altura.roundToPx() }
    val tela = runCatching { Toolkit.getDefaultToolkit().screenSize }.getOrNull()
    val x = (em.first - larguraPx / 2).coerceAtLeast(4)
        .coerceAtMost((tela?.width ?: (em.first + larguraPx)) - larguraPx - 4)
    val y = (em.second - alturaPx - 12).coerceAtLeast(4)

    Window(
        onCloseRequest = { menuEm = null },
        state = rememberWindowState(
            position = WindowPosition(with(d) { x.toDp() }, with(d) { y.toDp() }),
            size = DpSize(LARGURA, altura),
        ),
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        title = "",
        onKeyEvent = {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { menuEm = null; true } else false
        },
    ) {
        // FECHAR AO PERDER O FOCO é o comportamento que se espera de um menu, e é
        // também o único jeito de ele não ficar preso na tela quando a pessoa
        // desiste. O `alwaysOnTop` garante que ele apareça mesmo se o Windows negar
        // o primeiro plano; o pedido de foco abaixo é o que faz o fechar funcionar.
        DisposableEffect(Unit) {
            val ouvinte = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) {}
                override fun windowLostFocus(e: WindowEvent?) { menuEm = null }
            }
            window.addWindowFocusListener(ouvinte)
            runCatching { window.toFront(); window.requestFocus() }
            onDispose { window.removeWindowFocusListener(ouvinte) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                .padding(vertical = RESPIRO),
        ) {
            Column(Modifier.fillMaxWidth()) {
                lista.forEach { item ->
                    LinhaDaBandeja(item) { menuEm = null }
                }
            }
        }
    }
}

@Composable
private fun LinhaDaBandeja(item: ItemDaBandeja, fechar: () -> Unit) {
    val fonte = remember { MutableInteractionSource() }
    val hover by fonte.collectIsHoveredAsState()
    val cor = when {
        item.perigo && hover -> Obsidian.danger
        item.perigo -> Obsidian.danger.copy(alpha = 0.85f)
        hover -> Obsidian.text1
        else -> Obsidian.text2
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(ALTURA_ITEM)
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (hover) Obsidian.hover else Color.Transparent)
            .hoverable(fonte)
            .clickable(interactionSource = fonte, indication = null) {
                // Fecha ANTES de agir: "Sair" derruba o app, e uma janela ainda
                // aberta no meio do encerramento fica piscando no caminho.
                fechar()
                item.aoClicar()
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(item.rotulo, style = TextStyle(color = cor, fontSize = 13.sp))
    }
}
