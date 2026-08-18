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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSans
import app.astra.desktop.ui.theme.Obsidian
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
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

// O CHÃO do gato: a borda de cima do cartão do usuário, no rodapé da barra lateral.
//
// Ele andava solto pela tela inteira, e isso era pior do que parecia no papel. Bicho
// flutuando no meio de uma conversa não lê como bicho, lê como adesivo colado no
// vidro — falta o chão que diz "ele está APOIADO em alguma coisa". Uma prateleira
// resolve as duas coisas de uma vez: dá peso ao gato e tira ele de cima do texto.
//
// O `UserFooter` publica a própria caixa aqui; quem desenha o gato lê. É um ponto de
// encontro em vez de um `CompositionLocal` porque são dois pontos distantes da mesma
// árvore, e passar isso de mão em mão atravessaria meia dúzia de telas que não têm
// nada com o assunto.
object PisoDoPet {
    var caixa by mutableStateOf(Rect.Zero)
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
// Um QUADRO de uma animação, num dos dois bichos. `linha` só é usada por quem guarda
// tudo numa grade só; quem tem um arquivo por animação deixa em 0.
class Passo(
    val arquivo: String,
    val linha: Int,
    val quadros: Int,
    val fps: Int,
    // Em LARGURAS DE BICHO POR SEGUNDO, não em pixels. Amarrado assim, o passo casa
    // com a passada em qualquer escala: dobrar o tamanho dobra a distância coberta,
    // e o pé nunca patina no chão.
    val velocidade: Float,
)

// OS DOIS GATOS. As folhas vêm de artistas diferentes, com geometria e paleta
// diferentes, então tudo que difere está aqui como DADO — o desenho e a máquina de
// estados não sabem qual bicho estão animando.
//
// `escala` é o multiplicador base, e ele existe porque os dois têm tamanhos MUITO
// diferentes na folha: o do Mattz ocupa 58px, o do Elthen só 18. Sem multiplicador
// próprio, um sairia do tamanho de um botão ao lado do outro.
//
// O ALVO é a foto do usuário, ali do lado — uns 34dp. Um bicho de estimação tem que
// caber no canto do olho; grande demais ele deixa de ser companhia e vira obstáculo
// em cima da conversa. O do Mattz fica em 1x (a folha já nasce nesse tamanho, e 1:1
// é o melhor que pixel art pode ficar); o do Elthen precisa de 2x pra chegar perto.
//
// `base` são as cores de pelo da folha e `destino` diz em que degrau da
// `Pelagem.rampa` cada uma cai. O do Mattz tem quatro degraus e usa os quatro; o do
// Elthen só tem dois, e eles vão pro degrau claro e pro escuro (0 e 2) pra manter
// contraste — mandar os dois pra degraus vizinhos achataria o bicho.
enum class Bicho(
    val rotulo: String,
    val quadroW: Int,
    val cx: Int, val cy: Int, val cw: Int, val ch: Int, val pes: Int,
    val escala: Int,
    val base: IntArray,
    val destino: IntArray,
    val passos: Map<Anim, Passo>,
) {
    MALHADO(
        "Malhado", 80, 7, 16, 58, 34, 31, 1,
        intArrayOf(0xF6CA9F, 0xE69C69, 0xBF6F4A, 0x8A4836), intArrayOf(0, 1, 2, 3),
        mapOf(
            Anim.PARADO to Passo("gato_parado.png", 0, 8, 8, 0f),
            Anim.ANDANDO to Passo("gato_andando.png", 0, 12, 12, 1.05f),
            Anim.CORRENDO to Passo("gato_correndo.png", 0, 8, 14, 3.0f),
            Anim.PULO to Passo("gato_pulo.png", 0, 3, 9, 0f),
        ),
    ),

    // Grade 8x10 de 32px, uma linha por animação. Conteúdo medido em x 7..24 e
    // y 14..31 — gato de 18px, patas na linha 17 do recorte.
    SIMPLES(
        "Simples", 32, 7, 14, 18, 18, 17, 2,
        intArrayOf(0xE0E0E0, 0xB5B5B5), intArrayOf(0, 2),
        mapOf(
            Anim.PARADO to Passo("gato_simples.png", 0, 4, 5, 0f),
            Anim.ANDANDO to Passo("gato_simples.png", 4, 8, 10, 1.0f),
            Anim.CORRENDO to Passo("gato_simples.png", 9, 8, 14, 2.6f),
            Anim.PULO to Passo("gato_simples.png", 8, 7, 11, 0f),
        ),
    ),
    ;

    companion object {
        fun de(nome: String?): Bicho = entries.firstOrNull { it.name == nome } ?: MALHADO
    }
}

enum class Anim { PARADO, ANDANDO, CORRENDO, PULO }

// PELAGEM — troca de cor do jeito que pixel art pede: remapeando a rampa que o
// artista desenhou, cor por cor, e não jogando um filtro por cima.
//
// A folha inteira tem 12 cores. Só QUATRO são pelo (`RAMPA_BASE`, do mais claro ao
// mais escuro); as outras oito são o peito branco, as patinhas cinza, o rosa do
// focinho e da orelha, e o azul dos olhos. Trocar só as quatro é o que faz o gato
// continuar sendo um gato quando muda de cor: filtro de matiz mexeria nos olhos e
// no focinho junto, e o bicho viraria uma mancha monocromática.
//
// Cada pelagem foi gerada girando matiz e saturação sobre a rampa original e
// PRESERVANDO o valor de cada degrau — ou seja, a sombra continua exatamente onde o
// artista pôs. Foram revisadas a olho antes de entrar aqui.
//
// Não existe "preto": preto de verdade some no fundo escuro do app. `CARVAO` é o
// mais escuro que ainda se enxerga, e chamá-lo de preto seria mentir no rótulo.
private val RAMPA_BASE = intArrayOf(0xF6CA9F, 0xE69C69, 0xBF6F4A, 0x8A4836)
private const val OPACO = 0xFF000000.toInt()

enum class Pelagem(val rotulo: String, val rampa: IntArray) {
    LARANJA("Laranja", intArrayOf(0xF6CA9F, 0xE69C69, 0xBF6F4A, 0x8A4836)),
    CINZA("Cinza", intArrayOf(0xDEE4F1, 0xC6CFE1, 0xA2AABB, 0x757B87)),
    CARVAO("Carvão", intArrayOf(0x757280, 0x676478, 0x545163, 0x3D3B48)),
    BRANCO("Branco", intArrayOf(0xFFF9F1, 0xF5ECE0, 0xCEC6BA, 0x99938A)),
    CHOCOLATE("Chocolate", intArrayOf(0xB18576, 0xA66750, 0x8A4F39, 0x63392A)),
    CARAMELO("Caramelo", intArrayOf(0xF6E2B2, 0xE6C985, 0xBFA464, 0x8A7648)),
    LILAS("Lilás", intArrayOf(0xDCC9E2, 0xCAAFD4, 0xA78DB0, 0x79667F)),
    ;

    // A cor que representa a pelagem no seletor: o degrau do meio, que é o que o
    // olho lê como "a cor do bicho" — o mais claro engana pra branco em todas.
    val amostra: Color get() = Color(0xFF000000.toInt() or rampa[1])

    companion object {
        fun de(nome: String?): Pelagem = entries.firstOrNull { it.name == nome } ?: LARANJA
    }
}

// Carrega as quatro folhas UMA vez POR PELAGEM, sob demanda, e nunca mais. São 12 KB
// de PNG somados; decodificados viram ~700 KB de bitmap por pelagem. O cache é por
// pelagem porque trocar de cor no meio da sessão não pode custar recarregar arquivo,
// e voltar pra anterior tem que ser instantâneo.
//
// `getOrNull` de propósito: se a folha faltar (recurso removido, jar estranho), o
// gato cai pro desenho vetorial mais abaixo em vez de derrubar a tela inteira. Pet
// quebrado não pode ser motivo de crash de app de conversa.
private object FolhasDoGato {
    private val cache = mutableMapOf<Pair<Bicho, Pelagem>, Map<Anim, ImageBitmap>?>()

    @Synchronized
    fun folhas(bicho: Bicho, pelagem: Pelagem): Map<Anim, ImageBitmap>? =
        cache.getOrPut(bicho to pelagem) {
            runCatching {
                // Um arquivo pode servir a várias animações (o gato Simples guarda
                // tudo numa grade só), então decodifica-se cada arquivo UMA vez e o
                // resultado é compartilhado entre as animações que o usam.
                val porArquivo = mutableMapOf<String, ImageBitmap>()
                bicho.passos.mapValues { (_, passo) ->
                    porArquivo.getOrPut(passo.arquivo) {
                        val bytes = requireNotNull(
                            FolhasDoGato::class.java.getResourceAsStream("/pet/" + passo.arquivo),
                        ) { "sprite ausente: " + passo.arquivo }.use { it.readBytes() }
                        val img = ImageIO.read(ByteArrayInputStream(bytes))
                            ?: error("PNG ilegível: " + passo.arquivo)
                        repintar(img, bicho, pelagem).toComposeImageBitmap()
                    }
                }
            }.getOrNull()
        }

    // Repinta pela rampa e reencoda em PNG, porque ler e escrever pixel avulso é
    // trivial no BufferedImage e chato no Skia. Roda uma vez por bicho e pelagem: o
    // custo (poucos milhares de pixels) some no ruído de abrir a tela.
    private fun repintar(src: BufferedImage, bicho: Bicho, pelagem: Pelagem): SkiaImage {
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val p = src.getRGB(x, y)
                val i = bicho.base.indexOf(p and 0xFFFFFF)
                if (i >= 0) src.setRGB(x, y, (p and OPACO) or pelagem.rampa[bicho.destino[i]])
            }
        }
        val saida = ByteArrayOutputStream()
        ImageIO.write(src, "png", saida)
        return SkiaImage.makeFromEncoded(saida.toByteArray())
    }
}

private const val FPS = 30
private val LARGURA_VETOR = 34.dp

@Composable
fun GatoDoAstra(
    ligado: Boolean,
    bichoId: String = Bicho.MALHADO.name,
    pelagem: String = Pelagem.LARANJA.name,
    nome: String = "",
) {
    val reduzir = LocalReduceMotion.current
    val janelaAtiva = LocalWindowActive.current
    // As três condições numa só: `ligado` é a escolha, `reduzir` é a necessidade e
    // `janelaAtiva` é a economia. Qualquer uma delas falsa e nem a camada existe —
    // sair da composição é mais barato que desenhar nada.
    if (!ligado || reduzir || !janelaAtiva) return

    val bicho = Bicho.de(bichoId)
    val cor = Pelagem.de(pelagem)
    val folhas = remember(bicho, cor) { FolhasDoGato.folhas(bicho, cor) }
    val medidor = rememberTextMeasurer()

    // Pixel art só fica nítida em MÚLTIPLO INTEIRO de pixel físico: em 2,5x metade
    // das colunas do sprite ocupa 2 pixels e a outra metade 3, e o bicho ganha uma
    // listra que o artista não desenhou. Por isso a escala é um inteiro derivado da
    // densidade da tela, e não um valor em dp — assim o gato tem mais ou menos o
    // mesmo tamanho aparente em 100% e em 200% de escala do Windows, sempre nítido.
    val densidade = LocalDensity.current.density
    val mult = (bicho.escala * densidade).roundToInt().coerceAtLeast(1)
    val larguraPx = (bicho.cw * mult).toFloat()
    val alturaPx = (bicho.ch * mult).toFloat()
    val pesPx = (bicho.pes * mult).toFloat()

    var origem by remember { mutableStateOf(Offset.Zero) }
    // A prateleira em coordenadas DESTA camada. A caixa publicada é da janela; esta
    // camada pode não começar no canto dela, então descontar a origem é o que impede
    // o gato de andar deslocado do cartão.
    val piso = PisoDoPet.caixa.translate(-origem.x, -origem.y)
    var x by remember { mutableStateOf(-1f) }
    var alvoX by remember { mutableStateOf(0f) }
    var anim by remember { mutableStateOf(Anim.PARADO) }
    var tempoNaAnim by remember { mutableStateOf(0f) }
    var olhandoPraDireita by remember { mutableStateOf(false) }
    var espera by remember { mutableStateOf(2f) }
    var pulosRestantes by remember { mutableStateOf(0) }
    var piscada by remember { mutableStateOf(0f) }
    // O NOME só aparece quando o bicho reage, e some sozinho.
    //
    // A ideia natural seria mostrar no hover. Não dá, e a razão está na regra 1 lá em
    // cima: pra saber que o mouse está sobre o gato eu teria que registrar gesto na
    // camada dele, e essa camada cobre a conversa inteira. Um pet que engole clique
    // vale menos que um pet sem nome à mostra. Então o nome vem junto da reação — que
    // é quando você olhou pra ele de qualquer forma.
    var nomeVisivel by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        Pet.evento.collect { ev ->
            // Reagir a evento vale mais que continuar o passeio: o gato para o que
            // estava fazendo e pula. É a única hora em que ele compete por atenção,
            // e é justamente quando a atenção já foi chamada por outra coisa.
            pulosRestantes = if (ev == PetEvento.CALL) 2 else 1
            anim = Anim.PULO
            tempoNaAnim = 0f
            nomeVisivel = 2.4f
        }
    }

    // Os limites do passeio: meio gato de folga em cada ponta, pra ele não sair
    // metade fora da prateleira ao chegar no fim dela.
    val limiteEsq = piso.left + larguraPx * 0.5f
    val limiteDir = piso.right - larguraPx * 0.5f

    LaunchedEffect(piso, mult) {
        if (piso.width <= larguraPx) return@LaunchedEffect
        if (x < 0f) x = piso.center.x
        x = x.coerceIn(limiteEsq, limiteDir)
        while (true) {
            val inicio = System.nanoTime()
            val dt = 1f / FPS

            piscada += dt
            tempoNaAnim += dt
            if (nomeVisivel > 0f) nomeVisivel = (nomeVisivel - dt).coerceAtLeast(0f)

            when (anim) {
                Anim.PARADO -> {
                    espera -= dt
                    if (espera <= 0f) {
                        alvoX = limiteEsq + Random.nextFloat() * (limiteDir - limiteEsq)
                        olhandoPraDireita = alvoX > x
                        // Correr é raro e só pra longe. Um gato que corre sempre vira
                        // ansiedade na tela; um que corre de vez em quando vira graça.
                        val longe = abs(alvoX - x) > (limiteDir - limiteEsq) * 0.55f
                        anim = if (longe && Random.nextFloat() < 0.3f) Anim.CORRENDO else Anim.ANDANDO
                        tempoNaAnim = 0f
                    }
                }

                Anim.ANDANDO, Anim.CORRENDO -> {
                    val dx = alvoX - x
                    val v = (bicho.passos[anim]?.velocidade ?: 1f) * larguraPx * dt
                    if (abs(dx) <= v) {
                        x = alvoX
                        anim = Anim.PARADO
                        tempoNaAnim = 0f
                        // Pausa LONGA de propósito (4 a 13 segundos). Um gato que
                        // anda sem parar vira ruído periférico; um que passa a maior
                        // parte do tempo parado vira presença.
                        espera = 4f + Random.nextFloat() * 9f
                    } else {
                        x += if (dx > 0f) v else -v
                    }
                }

                Anim.PULO -> {
                    // Único one-shot: quando o último quadro passa, ou emenda outro
                    // pulo (call) ou volta a ficar parado, com pausa curta — ele
                    // acabou de reagir, então continuar o passeio na hora seria negar
                    // a própria reação.
                    val p = bicho.passos[Anim.PULO]
                    if (p == null || tempoNaAnim >= p.quadros.toFloat() / p.fps) {
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
            .onGloballyPositioned { origem = it.positionInWindow() },
        // NÃO HÁ MODIFICADOR DE PONTEIRO AQUI, e é obrigatório que continue assim.
        //
        // Já teve: um `pointerInput(Unit) { }` vazio, com um comentário jurando que
        // bloco vazio não registra gesto. Mentira — o NÓ existe e entra no teste de
        // acerto de qualquer jeito, e como esta camada cobre a tela toda ela virou o
        // alvo de tudo: o app inteiro ficou sem clique. Um `Box` sem modificador de
        // ponteiro simplesmente não é alvo, e é assim que os eventos atravessam.
    ) {
        if (piso.width <= larguraPx || x < 0f) return@Box
        // O pé apoia na BORDA DE CIMA do cartão. Um pixel a mais e ele flutua; um a
        // menos e ele afunda — e as duas coisas o olho pega na hora.
        val y = piso.top
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

            val passo = bicho.passos[anim] ?: return@Canvas
            val i = (tempoNaAnim * passo.fps).toInt().let {
                if (anim == Anim.PULO) it.coerceIn(0, passo.quadros - 1) else it % passo.quadros
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
                    srcOffset = IntOffset(
                        i * bicho.quadroW + bicho.cx,
                        passo.linha * bicho.quadroW + bicho.cy,
                    ),
                    srcSize = IntSize(bicho.cw, bicho.ch),
                    dstOffset = IntOffset(esq, topo),
                    dstSize = IntSize(larguraPx.toInt(), alturaPx.toInt()),
                    // SEM suavização. O padrão do Compose interpola, e interpolar
                    // pixel art é borrar de propósito o que o artista desenhou
                    // pixel a pixel. É o ponto inteiro do estilo.
                    filterQuality = FilterQuality.None,
                )
            }

            // O nome vai FORA do `scale`: espelhar o gato não pode espelhar a
            // escrita. Some com uma curva, não com um corte — os últimos 0,6s são
            // o esmaecimento, e o resto é leitura tranquila.
            if (nome.isNotBlank() && nomeVisivel > 0f) {
                val opacidade = (nomeVisivel / 0.6f).coerceAtMost(1f)
                val texto = medidor.measure(
                    nome,
                    style = TextStyle(
                        fontFamily = DmSans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Obsidian.text1.copy(alpha = opacidade),
                    ),
                )
                val nx = x - texto.size.width / 2f
                val ny = y - alturaPx - texto.size.height - 4f
                drawRoundRect(
                    color = Obsidian.overlay.copy(alpha = opacidade * 0.92f),
                    topLeft = Offset(nx - 7f, ny - 3f),
                    size = Size(texto.size.width + 14f, texto.size.height + 6f),
                    cornerRadius = CornerRadius(8f, 8f),
                )
                drawText(texto, topLeft = Offset(nx, ny))
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
