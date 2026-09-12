package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import kotlin.math.roundToInt

enum class Marco {
    CONSTELACOES,
    CANAIS,
    ESCREVER,
    VOZ,
    SINO,
    BUSCA,
    CEU,
}

internal data class PassoDoTour(
    val marco: Marco,
    val titulo: String,
    val texto: String,
)

private val ESTREIA = listOf(
    PassoDoTour(
        Marco.CONSTELACOES,
        "As constelações",
        "Cada esfera desta coluna é uma comunidade sua. É por aqui que se troca de lugar, e o " +
            "ponto aceso avisa onde apareceu coisa nova.",
    ),
    PassoDoTour(
        Marco.CANAIS,
        "Canais e sussurros",
        "Dentro de uma constelação, cada canal é um assunto. Canal de voz não tem botão de " +
            "entrar: clicar já coloca você na chamada.",
    ),
    PassoDoTour(
        Marco.ESCREVER,
        "Onde se escreve",
        "Enter envia; Shift com Enter quebra a linha. O sinal de mais guarda arquivo, imagem e " +
            "enquete.",
    ),
    PassoDoTour(
        Marco.VOZ,
        "A sua voz",
        "Microfone e escuta moram aqui, e é esta faixa que mostra o estado da chamada enquanto " +
            "você conversa em outro canal.",
    ),
    PassoDoTour(
        Marco.CEU,
        "O céu do Astra",
        "Este fundo é desenhado ao vivo pela placa de vídeo. Ele vem desligado de fábrica para " +
            "poupar máquina modesta — mas se a sua dá conta, é assim que o Astra foi desenhado " +
            "para ser visto.",
    ),
)

internal val DICAS = mapOf(
    Marco.SINO to PassoDoTour(
        Marco.SINO,
        "Onde o que você perdeu se acumula",
        "Chegou algo enquanto você estava longe. O sino guarda menções, respostas e convites, e " +
            "some quando você lê.",
    ),
    Marco.BUSCA to PassoDoTour(
        Marco.BUSCA,
        "Procurar em tudo de uma vez",
        "Com várias constelações, achar aquela mensagem vira o problema. A lupa procura em todas " +
            "ao mesmo tempo, e Ctrl com K abre ela de onde você estiver.",
    ),
)

object Tour {

    var ativo by mutableStateOf(false)
        private set

    internal var roteiro by mutableStateOf<List<PassoDoTour>>(emptyList())
        private set

    internal var indice by mutableStateOf(0)
        private set

    internal val alvos = mutableStateMapOf<Marco, Rect>()

    internal var aoFechar: (() -> Unit)? = null

    internal val passo: PassoDoTour? get() = roteiro.getOrNull(indice)

    fun estreia(aoTerminar: () -> Unit) = tocar(ESTREIA, aoTerminar)

    fun dica(marco: Marco, aoTerminar: () -> Unit) {
        val unica = DICAS[marco] ?: return
        tocar(listOf(unica), aoTerminar)
    }

    private fun tocar(passos: List<PassoDoTour>, aoTerminar: () -> Unit) {
        if (ativo) return
        roteiro = passos
        indice = 0
        aoFechar = aoTerminar
        ativo = true
    }

    internal fun avancar() {
        if (indice + 1 < roteiro.size) indice++ else encerrar()
    }

    internal fun encerrar() {
        ativo = false
        roteiro = emptyList()
        indice = 0
        alvos.clear()
        aoFechar?.invoke()
        aoFechar = null
    }
}

@Composable
fun Modifier.marcoDoTour(marco: Marco): Modifier =
    if (!Tour.ativo) {
        this
    } else {
        this.onGloballyPositioned {
            val agora = it.boundsInWindow()
            if (Tour.alvos[marco] != agora) Tour.alvos[marco] = agora
        }
    }

private val LARGURA_DO_CARTAO = 300.dp
private val RESPIRO_DO_RECORTE = 6.dp
private val DISTANCIA_DO_CARTAO = 16.dp
private const val ESCURIDAO = 0.74f
private const val VIAGEM_MS = 280

@Composable
fun TourNaTela(prefs: DesktopPrefs) {
    if (!Tour.ativo) return
    val passo = Tour.passo ?: return
    val paradinho = LocalReduceMotion.current
    val densidade = LocalDensity.current

    var origem by remember { mutableStateOf(Offset.Zero) }
    var tamanho by remember { mutableStateOf(Size.Zero) }

    val ehCeu = passo.marco == Marco.CEU
    var ceuAntes by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(ehCeu) {
        if (!ehCeu) return@LaunchedEffect
        ceuAntes = prefs.state.value.auroraEnabled
        prefs.setAuroraEnabled(true)
    }

    val destino = if (ehCeu) null else Tour.alvos[passo.marco]?.translate(-origem)
    val desenhado = remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(passo.marco, destino) {
        val para = destino
        val de = desenhado.value
        if (para == null) { desenhado.value = null; return@LaunchedEffect }
        if (de == null || paradinho) { desenhado.value = para; return@LaunchedEffect }
        Animatable(0f).animateTo(1f, tween(VIAGEM_MS, easing = EaseOutStd)) {
            desenhado.value = lerp(de, para, value)
        }
    }

    val buraco = desenhado.value
    val respiro = with(densidade) { RESPIRO_DO_RECORTE.toPx() }
    val cantoDoBuraco = with(densidade) { 12.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origem = it.boundsInWindow().topLeft
                tamanho = Size(it.size.width.toFloat(), it.size.height.toFloat())
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .semCursorDeClique(),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(Obsidian.void.copy(alpha = if (ehCeu) 0f else ESCURIDAO))
            buraco ?: return@Canvas
            val aberto = Rect(
                buraco.left - respiro,
                buraco.top - respiro,
                buraco.right + respiro,
                buraco.bottom + respiro,
            )
            drawRoundRect(
                color = Color.Black,
                topLeft = aberto.topLeft,
                size = aberto.size,
                cornerRadius = CornerRadius(cantoDoBuraco),
                blendMode = BlendMode.Clear,
            )
            drawRoundRect(
                color = Obsidian.accent,
                topLeft = aberto.topLeft,
                size = aberto.size,
                cornerRadius = CornerRadius(cantoDoBuraco),
                style = Stroke(width = 2f),
            )
        }

        val larguraDoCartao = with(densidade) { LARGURA_DO_CARTAO.toPx() }
        val folga = with(densidade) { DISTANCIA_DO_CARTAO.toPx() }
        val posicao = remember(buraco, tamanho) {
            cantoDoCartao(buraco, tamanho, larguraDoCartao, folga)
        }

        Box(
            Modifier
                .offset { IntOffset(posicao.x.roundToInt(), posicao.y.roundToInt()) }
                .width(LARGURA_DO_CARTAO),
        ) {
            CartaoDoTour(
                passo = passo,
                ultimo = Tour.indice == Tour.roteiro.lastIndex,
                ehCeu = ehCeu,
                aoAvancar = { Tour.avancar() },
                aoPular = { Tour.encerrar() },
                aoRecusarCeu = {
                    prefs.setAuroraEnabled(ceuAntes ?: false)
                    Tour.encerrar()
                },
            )
        }
    }
}

private fun cantoDoCartao(buraco: Rect?, tela: Size, largura: Float, folga: Float): Offset {
    if (buraco == null || tela == Size.Zero) {
        return Offset((tela.width - largura) / 2f, tela.height * 0.42f)
    }
    val y = (buraco.top).coerceIn(folga, (tela.height - folga * 12f).coerceAtLeast(folga))
    val aDireita = buraco.right + folga
    if (aDireita + largura + folga <= tela.width) return Offset(aDireita, y)
    val aEsquerda = buraco.left - folga - largura
    if (aEsquerda >= folga) return Offset(aEsquerda, y)
    val centrado = ((tela.width - largura) / 2f).coerceAtLeast(folga)
    return Offset(centrado, (buraco.bottom + folga).coerceAtMost(tela.height - folga * 10f))
}

@Composable
private fun CartaoDoTour(
    passo: PassoDoTour,
    ultimo: Boolean,
    ehCeu: Boolean,
    aoAvancar: () -> Unit,
    aoPular: () -> Unit,
    aoRecusarCeu: () -> Unit,
) {
    val forma = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .widthIn(max = LARGURA_DO_CARTAO)
            .clip(forma)
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderMid, forma)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .semCursorDeClique()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            passo.titulo,
            style = TextStyle(color = Obsidian.text1, fontSize = 16.sp, fontFamily = DmSerif),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            passo.texto,
            style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 18.sp),
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ehCeu) {
                BotaoDoTour("prefiro liso", destaque = false, onClick = aoRecusarCeu)
                Spacer(Modifier.width(8.dp))
                BotaoDoTour("fica assim", destaque = true, onClick = aoAvancar)
            } else {
                BotaoDoTour("pular", destaque = false, onClick = aoPular)
                Spacer(Modifier.width(8.dp))
                BotaoDoTour(if (ultimo) "entendi" else "próximo", destaque = true, onClick = aoAvancar)
            }
        }
    }
}

@Composable
private fun BotaoDoTour(rotulo: String, destaque: Boolean, onClick: () -> Unit) {
    val fonte = remember { MutableInteractionSource() }
    val sobre by fonte.collectIsHoveredAsState()
    val cor = if (destaque) Obsidian.accent else Obsidian.text3
    Box(
        Modifier
            .clickScale(fonte)
            .clip(FormaDeBotao)
            .background(if (sobre) cor.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (destaque) Obsidian.accentDim else Obsidian.borderDim, FormaDeBotao)
            .hoverable(fonte)
            .clickable(interactionSource = fonte, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            rotulo,
            style = TextStyle(
                color = cor,
                fontSize = 12.sp,
                fontWeight = if (destaque) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}

internal fun chaveDoTour(userId: String?) = "tourVisto:" + (userId ?: "anon")

internal fun chaveDaDica(marco: Marco, userId: String?) =
    "dica:" + marco.name + ":" + (userId ?: "anon")
