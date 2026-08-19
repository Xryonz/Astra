package app.astra.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import app.astra.desktop.ui.DesktopAvatar
import app.astra.desktop.ui.LIcon
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

// O AVISO DE MENSAGEM — a janela que o Astra desenha no canto quando chega mensagem
// com o app fechado ou em segundo plano.
//
// POR QUE NÃO O BALÃO DO WINDOWS. Ele saía por `TrayIcon.displayMessage`, e essa API
// aceita título, texto e um tipo de ícone. Só isso. Não existe parâmetro de imagem —
// não é que a foto de quem mandou ficasse feia, é que ela era IMPOSSÍVEL. E era a
// primeira pergunta de quem ouve o aviso: quem me chamou? Reconhecer um rosto é
// instantâneo; ler um nome é uma tarefa, ainda que curta.
//
// A janela própria também resolve o segundo defeito de graça: o balão do Windows não
// é clicável de forma confiável (o clique vai pro ícone da bandeja, não pro aviso).
// Aqui, clicar abre a conversa — que é o que a pessoa quer fazer em quase todo aviso
// que ela decide não ignorar.
//
// ELA NÃO PEDE FOCO, E ISSO É A REGRA MAIS IMPORTANTE DESTE ARQUIVO. O menu da
// bandeja pede (`window.requestFocus()`), e tem que pedir: é um menu, ele precisa
// fechar quando você clica fora. Um aviso é o contrário — ele chega enquanto você
// está fazendo outra coisa, e roubar o foco significa engolir a tecla que a pessoa
// estava digitando em outro programa. `focusable = false` é o que garante isso, e não
// é detalhe de polimento: é a diferença entre um aviso e uma interrupção.

// Um aviso vivo na tela.
data class AvisoNaTela(
    val id: Long,
    // Quem mandou. É o que vai grande, porque é a primeira pergunta.
    val quem: String,
    // Onde: "#geral · Constelação" para canal, vazio para sussurro (que já se explica
    // pelo rosto — sussurro não tem "onde", tem só "quem").
    val onde: String,
    val trecho: String,
    val avatarUrl: String?,
    // Nulo = aviso sem destino (o de teste, por exemplo). Sem isto o cartão fingiria
    // ser clicável e não faria nada, que é pior do que não convidar ao clique.
    val abrir: (() -> Unit)?,
)

// Quem segura os avisos vivos. Objeto e não estado de tela porque quem CRIA aviso é o
// ShellScreen (que só existe com sessão aberta) e quem DESENHA é o `application`, que
// existe sempre — inclusive com a janela principal escondida na bandeja, que é
// justamente quando o aviso mais importa.
object AvisosNaTela {
    // Quantos cabem empilhados antes de o canto da tela virar parede. Três é o que o
    // Windows também mostra, e por um motivo bom: acima disso ninguém lê, só vê
    // movimento — e um aviso que não se lê é interrupção pura.
    private const val LIMITE = 3

    val vivos = mutableStateListOf<AvisoNaTela>()
    private var proximoId = 0L

    fun mostrar(quem: String, onde: String, trecho: String, avatarUrl: String?, abrir: (() -> Unit)? = null) {
        // O MAIS ANTIGO SAI, e não "o novo é descartado". Mensagem velha importa menos
        // que mensagem nova, e descartar a nova deixaria a pilha congelada mostrando
        // três avisos que a pessoa já ignorou.
        while (vivos.size >= LIMITE) vivos.removeAt(0)
        vivos.add(AvisoNaTela(proximoId++, quem, onde, trecho, avatarUrl, abrir))
        // O SOM SAI DAQUI, junto do cartão, pelo mesmo motivo que ele saía junto do
        // balão: quem decide QUANDO avisar é o ShellScreen, e um som com regra própria
        // acabaria tocando sem nada na tela, ou com o app na frente — barulho sem
        // referente. Não precisa checar modo transmissão: quem transmite nunca chega
        // aqui, o caminho discreto desvia antes.
        tocarAvisoDeMensagem()
    }

    fun dispensar(id: Long) {
        vivos.removeAll { it.id == id }
    }
}

private val LARGURA = 340.dp
private val ALTURA = 84.dp
private val RESPIRO_ENTRE = 8.dp
private val MARGEM_DA_TELA = 16.dp

// Quanto tempo o aviso fica. Seis segundos é o suficiente pra ler duas linhas sem
// pressa; abaixo disso quem olhou tarde perde, e acima disso a pilha entope.
private const val SEGUNDOS_NA_TELA = 6

@Composable
fun AvisosDeMensagem() {
    // Cada aviso é uma JANELA. Uma só, alta, com os três dentro, seria menos código —
    // e teria um buraco: a janela precisaria cobrir a altura da pilha cheia o tempo
    // todo, e essa área transparente engole clique de quem estiver embaixo. Janelas
    // separadas ocupam exatamente o retângulo que desenham.
    AvisosNaTela.vivos.forEachIndexed { indice, aviso ->
        key(aviso.id) { JanelaDeAviso(aviso, indice) }
    }
}

@Composable
private fun JanelaDeAviso(aviso: AvisoNaTela, indice: Int) {
    val densidade = androidx.compose.ui.platform.LocalDensity.current

    // A ÁREA ÚTIL da tela, e não a tela inteira: `screenSize` inclui o espaço da barra
    // de tarefas, e ancorar por ela poria o aviso por baixo dela. O `getScreenInsets`
    // é o que devolve o que sobra.
    val tela = remember {
        runCatching {
            val cfg = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration
            val bordas = Toolkit.getDefaultToolkit().getScreenInsets(cfg)
            val b = cfg.bounds
            (b.x + b.width - bordas.right) to (b.y + b.height - bordas.bottom)
        }.getOrNull()
    }

    val larguraPx = with(densidade) { LARGURA.roundToPx() }
    val alturaPx = with(densidade) { ALTURA.roundToPx() }
    val respiroPx = with(densidade) { RESPIRO_ENTRE.roundToPx() }
    val margemPx = with(densidade) { MARGEM_DA_TELA.roundToPx() }

    // Canto inferior direito, empilhando pra CIMA — a convenção do Windows, e a mesma
    // do balão que este aviso substitui. Sem informação de tela (ambiente sem monitor,
    // por exemplo) o aviso simplesmente não aparece: uma janela em coordenada chutada
    // é pior que aviso nenhum.
    val (direita, baixo) = tela ?: return

    val x = direita - larguraPx - margemPx
    val y = baixo - margemPx - alturaPx - indice * (alturaPx + respiroPx)

    Window(
        onCloseRequest = { AvisosNaTela.dispensar(aviso.id) },
        state = rememberWindowState(
            position = WindowPosition(with(densidade) { x.toDp() }, with(densidade) { y.toDp() }),
            size = DpSize(LARGURA, ALTURA),
        ),
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        // NÃO ROUBA O FOCO. Ver o comentário no topo do arquivo — é o que separa
        // "avisar" de "atrapalhar".
        focusable = false,
        title = "",
    ) {
        CartaoDeAviso(aviso)
    }
}

@Composable
private fun CartaoDeAviso(aviso: AvisoNaTela) {
    val fonte = remember { MutableInteractionSource() }
    val sobMouse by fonte.collectIsHoveredAsState()

    // O RELÓGIO PARA COM O MOUSE EM CIMA. Quem levou o ponteiro até ali está lendo, e
    // sumir debaixo do olho é a forma mais irritante de um aviso falhar. Sai de novo
    // assim que o mouse sai — o `LaunchedEffect` reinicia a contagem, o que é a
    // escolha certa: quem terminou de ler ganha o tempo inteiro para decidir clicar.
    LaunchedEffect(sobMouse) {
        if (sobMouse) return@LaunchedEffect
        delay(SEGUNDOS_NA_TELA * 1000L)
        AvisosNaTela.dispensar(aviso.id)
    }

    val realce by animateFloatAsState(if (sobMouse) 1f else 0f, tween(140), label = "realceDoAviso")

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .hoverable(fonte)
            .let {
                if (aviso.abrir == null) it
                else it.clickable(interactionSource = fonte, indication = null) {
                    aviso.abrir.invoke()
                    AvisosNaTela.dispensar(aviso.id)
                }
            },
    ) {
        // Realce de hover por cima, e não trocando a cor de fundo: o fundo já é a
        // superfície mais alta da rampa, e subir mais um degrau apagaria a borda.
        Box(Modifier.fillMaxSize().background(Obsidian.hover.copy(alpha = realce * 0.55f)))

        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopAvatar(aviso.avatarUrl, aviso.quem, 38, externalHover = sobMouse)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        aviso.quem,
                        style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (aviso.onde.isNotBlank()) {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            aviso.onde,
                            style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    aviso.trecho,
                    style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // O FECHAR só aparece com o mouse em cima. Ele é a saída de emergência de
            // quem não quer esperar os seis segundos, e desenhá-lo sempre poria um X
            // permanente competindo com o rosto pela atenção do canto do olho.
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (sobMouse) {
                    val fonteX = remember { MutableInteractionSource() }
                    val sobreX by fonteX.collectIsHoveredAsState()
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sobreX) Obsidian.active else Color.Transparent)
                            .hoverable(fonteX)
                            .clickable(interactionSource = fonteX, indication = null) {
                                AvisosNaTela.dispensar(aviso.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        LIcon(Lucide.X, tint = Obsidian.text3, size = 12.dp, rotulo = "Dispensar o aviso")
                    }
                }
            }
        }
    }
}
