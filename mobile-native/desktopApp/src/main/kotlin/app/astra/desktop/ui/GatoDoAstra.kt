package app.astra.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.Obsidian
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.Image as SkiaImage

// O GATO DO ASTRA — o "pet" que estava anotado como uma palavra só no ESTADO.md.
//
// Ele anda livre por cima da interface inteira (escolha do dono) e é PIXEL ART:
// sprites do pacote "Cat 2D Pixel Art", do Mattz Art (xzany). A licença do pacote
// viaja junto da arte, em `resources/pet/LICENCA-cat-2d-pixel-art.txt`.
//
// TRÊS REGRAS QUE NÃO SE QUEBRAM, porque ele passa por cima de tudo:
//
// 1. **Não intercepta ponteiro.** Um bicho que anda sobre a conversa e engole
//    cliques é um bug com pelo. O `pointerInput` vazio abaixo existe pra deixar
//    isso explícito no código, e a camada inteira é irmã do conteúdo, nunca pai.
// 2. **Dorme quando ninguém vê.** Janela oculta ou minimizada e ele para de vez —
//    não é só invisível, é sem quadro nenhum. Gato de enfeite não tem o direito de
//    custar bateria enquanto o app está na bandeja.
// 3. **Reduzir movimento tira ele da tela.** Não "diminui": tira. O recurso inteiro
//    é movimento contínuo, e é justamente isso que a pessoa desligou. Fingir que
//    obedece com uma animação mais lenta seria pior que ignorar.
//
// TENSÃO COM A NORMA DO APP, dita em voz alta em vez de escondida: a norma diz
// "movimento é sinal, não enfeite; repouso deliberadamente quieto". Um gato que
// caminha é enfeite contínuo por definição, e contraria isso. O acordo é o ritmo:
// ele passa a MAIOR PARTE do tempo parado (pausas de 4 a 13 segundos) e caminha em
// trechos curtos, para não virar um piscar de canto de olho que ensina o olho a
// ignorar o resto do app. Mais interruptor próprio, para quem discordar do acordo.

enum class PetEvento { MENSAGEM, CALL }

object Pet {
    private val _evento = MutableSharedFlow<PetEvento>(extraBufferCapacity = 4)
    val evento = _evento.asSharedFlow()

    fun mensagemNova() { _evento.tryEmit(PetEvento.MENSAGEM) }
    fun entrouEmCall() { _evento.tryEmit(PetEvento.CALL) }
}

// ---------------------------------------------------------------------------
// GEOMETRIA DAS FOLHAS — medida no arquivo, não estimada.
//
// Cada folha é uma tira horizontal de quadros de 80x64. O gato ocupa só o miolo:
// varrendo o alfa de todos os 42 quadros das quatro folhas, o conteúdo cabe em
// x 7..64 e y 16..49. Recortar nessa caixa ÚNICA (a mesma para toda animação) é o
// que mantém o alinhamento de graça — cada quadro continua no lugar exato em que o
// artista o desenhou, só sem a margem vazia.
//
// As patas repousam em y=47 do quadro, ou seja, na linha 31 do recorte. É por isso
// que a âncora do desenho é o PÉ e não o centro: com o pé fixo, o pulo sobe de
// verdade em vez de o bicho inteiro escorregar pra cima.
private const val QUADRO_W = 80
private const val CORTE_X = 7
private const val CORTE_Y = 16
private const val CORTE_W = 58
private const val CORTE_H = 34
private const val CORTE_PES = 31

// `velocidade` está em LARGURAS DE GATO POR SEGUNDO, não em pixels. Amarrado assim,
// o passo casa com a passada em qualquer escala e em qualquer monitor: dobrar o
// tamanho do bicho dobra a distância que ele cobre, e o pé nunca patina no chão.
private enum class Anim(
    val arquivo: String,
    val quadros: Int,
    val fps: Int,
    val velocidade: Float,
) {
    PARADO("gato_parado.png", 8, 8, 0f),
    ANDANDO("gato_andando.png", 12, 12, 1.05f),
    CORRENDO("gato_correndo.png", 8, 14, 3.0f),
    PULO("gato_pulo.png", 3, 9, 0f),
}

// Carrega as quatro folhas UMA vez, na primeira aparição do gato, e nunca mais.
// São 12 KB de PNG somados; decodificados viram ~700 KB de bitmap, o que é menos
// que um avatar de banner e some junto com o processo.
//
// `getOrNull` de propósito: se a folha faltar (recurso removido, jar estranho), o
// gato cai pro desenho vetorial mais abaixo em vez de derrubar a tela inteira. Pet
// quebrado não pode ser motivo de crash de app de conversa.
private object FolhasDoGato {
    val folhas: Map<Anim, ImageBitmap>? by lazy {
        runCatching {
            Anim.entries.associateWith { anim ->
                val bytes = requireNotNull(
                    FolhasDoGato::class.java.getResourceAsStream("/pet/" + anim.arquivo),
                ) { "sprite ausente: " + anim.arquivo }.use { it.readBytes() }
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            }
        }.getOrNull()
    }
}

private const val FPS = 30
private val LARGURA_VETOR = 34.dp

@Composable
fun GatoDoAstra(ligado: Boolean) {
    val reduzir = LocalReduceMotion.current
    val janelaAtiva = LocalWindowActive.current
    // As três condições numa só: `ligado` é a escolha, `reduzir` é a necessidade e
    // `janelaAtiva` é a economia. Qualquer uma delas falsa e nem a camada existe —
    // sair da composição é mais barato que desenhar nada.
    if (!ligado || reduzir || !janelaAtiva) return

    val folhas = FolhasDoGato.folhas

    // Pixel art só fica nítida em MÚLTIPLO INTEIRO de pixel físico: em 2,5x metade
    // das colunas do sprite ocupa 2 pixels e a outra metade 3, e o bicho ganha uma
    // listra que o artista não desenhou. Por isso a escala é um inteiro derivado da
    // densidade da tela, e não um valor em dp — assim o gato tem mais ou menos o
    // mesmo tamanho aparente em 100% e em 200% de escala do Windows, sempre nítido.
    val densidade = LocalDensity.current.density
    val mult = (2f * densidade).roundToInt().coerceIn(2, 6)
    val larguraPx = (CORTE_W * mult).toFloat()
    val alturaPx = (CORTE_H * mult).toFloat()
    val pesPx = (CORTE_PES * mult).toFloat()

    var area by remember { mutableStateOf(IntSize.Zero) }
    var x by remember { mutableStateOf(-1f) }
    var y by remember { mutableStateOf(-1f) }
    var alvoX by remember { mutableStateOf(0f) }
    var alvoY by remember { mutableStateOf(0f) }
    var anim by remember { mutableStateOf(Anim.PARADO) }
    var tempoNaAnim by remember { mutableStateOf(0f) }
    var olhandoPraDireita by remember { mutableStateOf(false) }
    var espera by remember { mutableStateOf(2f) }
    var pulosRestantes by remember { mutableStateOf(0) }
    var piscada by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        Pet.evento.collect { ev ->
            // Reagir a evento vale mais que continuar o passeio: o gato para o que
            // estava fazendo e pula. É a única hora em que ele compete por atenção,
            // e é justamente quando a atenção já foi chamada por outra coisa.
            pulosRestantes = if (ev == PetEvento.CALL) 2 else 1
            anim = Anim.PULO
            tempoNaAnim = 0f
        }
    }

    LaunchedEffect(area, mult) {
        if (area.width <= 0) return@LaunchedEffect
        if (x < 0f) { x = area.width * 0.5f; y = area.height * 0.72f }
        while (true) {
            val inicio = System.nanoTime()
            val dt = 1f / FPS

            piscada += dt
            tempoNaAnim += dt

            when (anim) {
                Anim.PARADO -> {
                    espera -= dt
                    if (espera <= 0f) {
                        // Alvo em qualquer lugar, menos a faixa de cima: ali moram a
                        // barra de título e o cabeçalho do canal, e um gato parado em
                        // cima do nome da conversa atrapalha a leitura da única linha
                        // que diz onde você está.
                        val margem = larguraPx * 0.5f
                        alvoX = margem + Random.nextFloat() * (area.width - margem * 2f).coerceAtLeast(1f)
                        alvoY = area.height * (0.35f + Random.nextFloat() * 0.55f)
                        olhandoPraDireita = alvoX > x
                        // Correr é raro e só pra longe. Um gato que corre sempre vira
                        // ansiedade na tela; um que corre de vez em quando vira graça.
                        val longe = abs(alvoX - x) > area.width * 0.45f
                        anim = if (longe && Random.nextFloat() < 0.3f) Anim.CORRENDO else Anim.ANDANDO
                        tempoNaAnim = 0f
                    }
                }

                Anim.ANDANDO, Anim.CORRENDO -> {
                    val dx = alvoX - x
                    val dy = alvoY - y
                    val dist = hypot(dx, dy)
                    val v = anim.velocidade * larguraPx * dt
                    if (dist <= v) {
                        x = alvoX
                        y = alvoY
                        anim = Anim.PARADO
                        tempoNaAnim = 0f
                        // Pausa LONGA de propósito (4 a 13 segundos). Um gato que
                        // anda sem parar vira ruído periférico; um que passa a maior
                        // parte do tempo parado vira presença.
                        espera = 4f + Random.nextFloat() * 9f
                    } else {
                        x += dx / dist * v
                        y += dy / dist * v
                    }
                }

                Anim.PULO -> {
                    // Único one-shot: quando o último quadro passa, ou emenda outro
                    // pulo (call) ou volta a ficar parado, com pausa curta — ele
                    // acabou de reagir, então continuar o passeio na hora seria negar
                    // a própria reação.
                    if (tempoNaAnim >= Anim.PULO.quadros.toFloat() / Anim.PULO.fps) {
                        pulosRestantes -= 1
                        tempoNaAnim = 0f
                        if (pulosRestantes <= 0) {
                            anim = Anim.PARADO
                            espera = 2.5f + Random.nextFloat() * 3f
                        }
                    }
                }
            }
            esperarPeloTeto(FPS, inicio)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { area = it }
            // NÃO CONSOME NADA. Um `pointerInput` que só suspende para sempre não
            // registra gesto nenhum, então clique, hover e rolagem atravessam a
            // camada e chegam na conversa por baixo.
            .pointerInput(Unit) { },
    ) {
        if (area.width <= 0 || x < 0f) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val folha = folhas?.get(anim)
            if (folha == null) {
                // Reserva: o gato de traço. Vale quando o sprite não carregou, e é
                // barato manter — quem desliga a arte ainda vê um gato.
                translate(x, y) {
                    desenharGato(
                        esc = LARGURA_VETOR.toPx() / 34f,
                        paraDireita = olhandoPraDireita,
                        andando = anim == Anim.ANDANDO || anim == Anim.CORRENDO,
                        passo = tempoNaAnim * 7f,
                        olhoFechado = (piscada % 4.2f) < 0.13f,
                        animacaoDeEvento = if (anim == Anim.PULO) 1f else 0f,
                        pelo = Obsidian.text2,
                        detalhe = Obsidian.accent,
                    )
                }
                return@Canvas
            }

            val i = (tempoNaAnim * anim.fps).toInt().let {
                if (anim == Anim.PULO) it.coerceIn(0, anim.quadros - 1) else it % anim.quadros
            }
            val esq = (x - larguraPx / 2f).roundToInt()
            val topo = (y - pesPx).roundToInt()

            // O gato do pacote olha pra ESQUERDA. Andando pra direita, a folha é
            // espelhada no eixo do próprio bicho — de graça, e sem duplicar arte.
            scale(
                scaleX = if (olhandoPraDireita) -1f else 1f,
                scaleY = 1f,
                pivot = Offset(x, y),
            ) {
                drawImage(
                    image = folha,
                    srcOffset = IntOffset(i * QUADRO_W + CORTE_X, CORTE_Y),
                    srcSize = IntSize(CORTE_W, CORTE_H),
                    dstOffset = IntOffset(esq, topo),
                    dstSize = IntSize(larguraPx.toInt(), alturaPx.toInt()),
                    // SEM suavização. O padrão do Compose interpola, e interpolar
                    // pixel art é borrar de propósito o que o artista desenhou
                    // pixel a pixel. É o ponto inteiro do estilo.
                    filterQuality = FilterQuality.None,
                )
            }
        }
    }
}

// O gato, em traço — a RESERVA de quando o sprite não carrega. Corpo e cabeça são
// curvas fechadas; patas e cauda são linha.
//
// Ele é desenhado olhando pra DIREITA e espelhado quando anda pra esquerda — meia
// figura pela metade do trabalho, e o espelho é exato porque nenhuma parte do
// desenho depende de qual lado é qual.
private fun DrawScope.desenharGato(
    esc: Float,
    paraDireita: Boolean,
    andando: Boolean,
    passo: Float,
    olhoFechado: Boolean,
    animacaoDeEvento: Float,
    pelo: Color,
    detalhe: Color,
) {
    val lado = if (paraDireita) 1f else -1f
    // Sobe e desce meio pixel por passada: é o que faz o caminhar parecer caminhar
    // em vez de deslizar. Parado, some.
    val balanco = if (andando) sin(passo * 2f) * 0.8f * esc else 0f
    // Ao reagir, ele estica pra cima (olhando pro que aconteceu).
    val esticar = animacaoDeEvento * 3f * esc
    val traco = Stroke(width = 1.6f * esc)

    translate(0f, balanco - esticar) {
        // ---- cauda: sobe e ondula; ao reagir, levanta de vez ----
        val cauda = Path().apply {
            moveTo(-11f * esc * lado, -6f * esc)
            val altura = if (animacaoDeEvento > 0f) -20f else -12f
            cubicTo(
                -19f * esc * lado, -8f * esc,
                -20f * esc * lado, altura * esc + sin(passo) * 2f * esc,
                -14f * esc * lado, (altura - 4f) * esc,
            )
        }
        drawPath(cauda, pelo, style = traco)

        // ---- corpo ----
        val corpo = Path().apply {
            moveTo(-12f * esc * lado, -5f * esc)
            cubicTo(
                -13f * esc * lado, -12f * esc,
                2f * esc * lado, -13f * esc,
                4f * esc * lado, -7f * esc,
            )
            lineTo(4f * esc * lado, -1f * esc)
            lineTo(-12f * esc * lado, -1f * esc)
            close()
        }
        drawPath(corpo, pelo.copy(alpha = 0.22f))
        drawPath(corpo, pelo, style = traco)

        // ---- patas: alternam quando anda ----
        val passoFrente = if (andando) sin(passo) * 2.4f * esc else 0f
        val passoTras = if (andando) sin(passo + 3.14f) * 2.4f * esc else 0f
        for ((baseX, desloc) in listOf(
            (2f * esc * lado) to passoFrente,
            (-2f * esc * lado) to passoTras,
            (-8f * esc * lado) to passoFrente,
            (-11f * esc * lado) to passoTras,
        )) {
            drawLine(
                pelo,
                Offset(baseX, -1f * esc),
                Offset(baseX + desloc, 3f * esc),
                strokeWidth = 1.5f * esc,
            )
        }

        // ---- cabeça ----
        val cabecaX = 8f * esc * lado
        val cabecaY = -11f * esc
        drawCircle(pelo.copy(alpha = 0.22f), radius = 5.2f * esc, center = Offset(cabecaX, cabecaY))
        drawCircle(pelo, radius = 5.2f * esc, center = Offset(cabecaX, cabecaY), style = traco)

        // orelhas: dois triângulos. Ao reagir elas se levantam (o gato "escutou").
        val perk = animacaoDeEvento * 1.4f * esc
        for (dir in listOf(-1f, 1f)) {
            val ox = cabecaX + dir * 3.2f * esc
            val orelha = Path().apply {
                moveTo(ox - 2f * esc, cabecaY - 3.4f * esc)
                lineTo(ox + 0.4f * esc * dir, cabecaY - 7.4f * esc - perk)
                lineTo(ox + 2f * esc, cabecaY - 3.4f * esc)
                close()
            }
            drawPath(orelha, pelo.copy(alpha = 0.22f))
            drawPath(orelha, pelo, style = traco)
        }

        // olhos: o accent do tema entra AQUI e em nenhum outro lugar do bicho —
        // pouca área, muito significado. É o que faz o gato parecer vivo, e é a
        // regra 60-30-10 aplicada a um desenho de 34dp.
        val olhoY = cabecaY - 0.6f * esc
        for (dir in listOf(-1f, 1f)) {
            val ox = cabecaX + dir * 2f * esc
            if (olhoFechado) {
                drawLine(
                    detalhe,
                    Offset(ox - 1.1f * esc, olhoY),
                    Offset(ox + 1.1f * esc, olhoY),
                    strokeWidth = 1.2f * esc,
                )
            } else {
                drawCircle(detalhe, radius = 1.15f * esc, center = Offset(ox, olhoY))
            }
        }

        // focinho
        drawLine(
            pelo,
            Offset(cabecaX + 3.4f * esc * lado, cabecaY + 2f * esc),
            Offset(cabecaX + 5.2f * esc * lado, cabecaY + 2.6f * esc),
            strokeWidth = 1.2f * esc,
        )

        // ---- faíscas do evento: some junto com a reação ----
        if (animacaoDeEvento > 0f) {
            val a = (animacaoDeEvento).coerceIn(0f, 1f)
            for (i in 0 until 3) {
                val ang = (i * 2.4f) + animacaoDeEvento * 2f
                val r = (7f + i * 2.5f) * esc
                drawCircle(
                    detalhe.copy(alpha = a * 0.75f),
                    radius = 0.9f * esc,
                    center = Offset(
                        cabecaX + cos(ang) * r,
                        cabecaY - 8f * esc + sin(ang) * r * 0.5f - abs(sin(ang)) * 2f * esc,
                    ),
                )
            }
        }
    }
}
