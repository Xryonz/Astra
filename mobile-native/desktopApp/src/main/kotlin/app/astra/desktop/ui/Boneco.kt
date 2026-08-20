package app.astra.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.astra.desktop.prefs.DesktopPrefs
import org.jetbrains.skia.Image as SkiaImage
import org.koin.core.context.GlobalContext
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

// O BONECO — o retrato que a pessoa monta, em vez do que ela fotografa.
//
// O CORPO vem do "Eris Esra's Character Template 4.1" (licença livre, crédito no
// README). Ele é um MANEQUIM: creme, careca, sem roupa. Vestir esse manequim
// inteiro custaria ~158 quadros POR PEÇA — 5 direções vezes 7 animações. Isso não
// é trabalho de uma pessoa, é trabalho de um estúdio.
//
// O QUE TORNA ISTO VIÁVEL foi uma medida, não uma ideia. A caixa de cada quadro da
// animação parada, de frente:
//
//	quadro 0:  x[8..23]  y[ 7..31]
//	quadro 1:  x[8..23]  y[ 8..31]
//	quadro 2:  x[8..23]  y[ 9..31]
//	quadro 3:  x[8..23]  y[ 7..31]
//
// O X NUNCA MUDA E A SILHUETA NUNCA MUDA. A animação inteira é um deslocamento
// vertical. Então um cabelo não são quatro desenhos: é UM desenho com quatro
// deslocamentos. Uma peça de roupa idem. Foi isso que levou o custo de 158 quadros
// para 1, e é por isso que este arquivo existe em vez de um pedido de socorro.
//
// A cabeça desce 0/1/2/0 e o tronco 0/1/1/0 — o pescoço absorve a diferença. Os
// dois números estão medidos linha a linha, não estimados.
//
// A COR sai de graça, pela mesma técnica que o gato já usa (`FolhasDoGato.repintar`):
// o sprite tem CINCO cores ao todo, então trocar a paleta troca o personagem. Um
// cabelo desenhado vezes dez cores são dez cabelos com um desenho só.
//
// E O QUE VIAJA NA REDE É A RECEITA, NÃO A IMAGEM. Uns 10 bytes de texto contra o
// data-URI de uma foto. Numa constelação de 200 pessoas isso é a diferença entre
// alguns quilobytes e alguns megabytes por abertura de lista.

/** O que a pessoa escolheu. Cabe num campo de texto e viaja no JSON que já existe. */
data class ReceitaDoBoneco(
    val pele: Int = 0,
    val cabelo: Int = 0,
    val corCabelo: Int = 0,
    val roupa: Int = 0,
    val corRoupa: Int = 0,
) {
    /** Formato posicional de propósito: um campo novo entra no fim e as receitas
     *  antigas continuam legíveis, caindo no padrão do que não existia. */
    fun texto(): String = "$pele.$cabelo.$corCabelo.$roupa.$corRoupa"

    companion object {
        val PADRAO = ReceitaDoBoneco()

        fun de(texto: String?): ReceitaDoBoneco {
            if (texto.isNullOrBlank()) return PADRAO
            val p = texto.split('.').map { it.trim().toIntOrNull() ?: 0 }
            fun em(i: Int, limite: Int) = (p.getOrNull(i) ?: 0).coerceIn(0, limite - 1)
            return ReceitaDoBoneco(
                pele = em(0, CatalogoDoBoneco.peles.size),
                cabelo = em(1, CatalogoDoBoneco.cabelos.size),
                corCabelo = em(2, CatalogoDoBoneco.coresDoCabelo.size),
                roupa = em(3, CatalogoDoBoneco.roupas.size),
                corRoupa = em(4, CatalogoDoBoneco.coresDaRoupa.size),
            )
        }
    }
}

/** Um tom: as cores que substituem as chaves, na ordem claro / médio / sombra. */
data class TomDoBoneco(val nome: String, val cores: List<Int>)

/** Uma peça. `arquivo` nulo é a ausência dela — careca, sem roupa. */
data class PecaDoBoneco(val nome: String, val arquivo: String?)

// O CATÁLOGO É DADO, NÃO CÓDIGO. Acrescentar um cabelo é soltar um PNG de 32x32 em
// `resources/boneco/` e uma linha aqui — nenhuma lógica muda. Foi desenhado assim
// porque a parte cara desta feature é arte, e arte chega depois do código.
object CatalogoDoBoneco {
    // As cinco cores do sprite original. As três primeiras são pele; a quarta é a
    // bochecha, que precisa acompanhar — rosa claro sobre pele escura vira mancha.
    val CHAVES_DA_PELE = listOf(0xFAF3E8, 0xE7D5C6, 0xB9B3A1, 0xD19DA7)

    // As chaves das peças. Cinza de propósito: cinza não é cor nenhuma, então
    // ninguém confunde a chave com uma escolha estética.
    val CHAVES_DA_PECA = listOf(0xF2F2F2, 0xA8A8A8, 0x5C5C5C)

    val peles = listOf(
        TomDoBoneco("Alabastro", listOf(0xFAF3E8, 0xE7D5C6, 0xB9B3A1, 0xD19DA7)),
        TomDoBoneco("Areia", listOf(0xF2D9BC, 0xDCBE9C, 0xB08F6E, 0xD69A94)),
        TomDoBoneco("Mel", listOf(0xE8C4A0, 0xD3A87F, 0xA9805C, 0xC98878)),
        TomDoBoneco("Âmbar", listOf(0xD2A074, 0xB37F55, 0x8A5C39, 0xB5766A)),
        TomDoBoneco("Castanha", listOf(0xA97048, 0x8C5836, 0x5F3A22, 0x8E5546)),
        TomDoBoneco("Ébano", listOf(0x6E4630, 0x563421, 0x372014, 0x6B3D33)),
    )

    val cabelos = listOf(
        PecaDoBoneco("Raspado", null),
        PecaDoBoneco("Curto", "cabelo-curto.png"),
        PecaDoBoneco("Chanel", "cabelo-medio.png"),
        PecaDoBoneco("Longo", "cabelo-longo.png"),
    )

    val coresDoCabelo = listOf(
        TomDoBoneco("Corvo", listOf(0x4A4550, 0x322E39, 0x1D1A23)),
        TomDoBoneco("Castanho", listOf(0x6B4A2F, 0x4A3220, 0x2E1F14)),
        TomDoBoneco("Avelã", listOf(0x8A6440, 0x66452A, 0x422B19)),
        TomDoBoneco("Trigo", listOf(0xC9A96E, 0xA8894F, 0x6E5730)),
        TomDoBoneco("Cobre", listOf(0xB5653A, 0x8C4A28, 0x5B2E19)),
        TomDoBoneco("Neve", listOf(0xEDEAF2, 0xC3BFCB, 0x86828E)),
        TomDoBoneco("Ametista", listOf(0x8E6FB8, 0x6B5090, 0x45325F)),
        TomDoBoneco("Anil", listOf(0x5A79B5, 0x41598C, 0x293759)),
    )

    val roupas = listOf(
        PecaDoBoneco("Nenhuma", null),
        PecaDoBoneco("Camiseta", "roupa-camiseta.png"),
        PecaDoBoneco("Manga longa", "roupa-manga.png"),
        PecaDoBoneco("Túnica", "roupa-tunica.png"),
    )

    val coresDaRoupa = listOf(
        TomDoBoneco("Ardósia", listOf(0x8E93A0, 0x5C6070, 0x3A3D49)),
        TomDoBoneco("Anil", listOf(0x7E96C4, 0x5A719C, 0x3A4A6B)),
        TomDoBoneco("Musgo", listOf(0x8AA37E, 0x5E7754, 0x3C4E35)),
        TomDoBoneco("Ferrugem", listOf(0xC4756B, 0x96524A, 0x63332E)),
        TomDoBoneco("Areia", listOf(0xD3BC96, 0xA8926C, 0x6E5D45)),
        TomDoBoneco("Ameixa", listOf(0x9A7BB0, 0x6E5482, 0x453353)),
        TomDoBoneco("Carvão", listOf(0x5A5A62, 0x3C3C44, 0x26262C)),
        TomDoBoneco("Marfim", listOf(0xEAE4D8, 0xC4BDAE, 0x827C70)),
    )
}

// A JANELA DE DESENHO. O sprite mora numa tela de 32x32 com folga em volta para as
// animações que não usamos; desenhar a tela inteira deixaria o boneco ocupando
// metade da caixa, com margem transparente que ninguém pediu. Este recorte é o que
// as peças de fato alcançam: a mecha mais larga e o cabelo mais longo cabem nele.
private const val JANELA_X = 7
private const val JANELA_Y = 6

/** Tamanho do boneco em pixels de arte. Quem reserva espaço para ele multiplica isto
 *  pela escala — nunca escolhe uma altura em `dp` e deixa a conta para o Compose. */
const val LARGURA_DO_BONECO = 18
const val ALTURA_DO_BONECO = 26

private const val QUADROS = 4
private const val FPS_DO_BONECO = 6

// Medidos linha a linha no sprite, não estimados: a cabeça desce dois pixels ao
// longo do ciclo, o tronco desce um, e o pescoço engole a diferença.
private val DESCIDA_DA_CABECA = intArrayOf(0, 1, 2, 0)
private val DESCIDA_DO_TRONCO = intArrayOf(0, 1, 1, 0)

private const val OPACO = 0xFF000000.toInt()

internal object FolhasDoBoneco {
    private val cru = HashMap<String, BufferedImage?>()

    // TETO NO CACHE, e ele não é zelo prematuro: quando o boneco entrar na lista de
    // membros, uma constelação grande instancia centenas de receitas diferentes numa
    // rolagem. Sem teto isso é um vazamento com cara de cache. 96 cobre qualquer
    // tela do app com folga, e cada entrada são 4 bitmaps de 32x32 (~16 KB).
    private const val TETO = 96
    private val prontos = object : LinkedHashMap<String, List<ImageBitmap>?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<ImageBitmap>?>) = size > TETO
    }

    @Synchronized
    fun quadros(r: ReceitaDoBoneco): List<ImageBitmap>? {
        val chave = r.texto()
        // `containsKey` e não `getOrPut`: o valor nulo é uma RESPOSTA (esta receita
        // não monta — recurso faltando, jar estranho), e getOrPut trataria nulo como
        // "ainda não perguntei", refazendo a tentativa a cada composição.
        if (prontos.containsKey(chave)) return prontos[chave]
        val feito = runCatching { montar(r) }.getOrNull()
        prontos[chave] = feito
        return feito
    }

    private fun montar(r: ReceitaDoBoneco): List<ImageBitmap> {
        val base = requireNotNull(ler("base.png")) { "o corpo do boneco sumiu" }
        val pele = CatalogoDoBoneco.peles[r.pele].cores
        val cabelo = CatalogoDoBoneco.cabelos[r.cabelo].arquivo?.let { ler(it) }
        val roupa = CatalogoDoBoneco.roupas[r.roupa].arquivo?.let { ler(it) }
        val tonsDoCabelo = CatalogoDoBoneco.coresDoCabelo[r.corCabelo].cores
        val tonsDaRoupa = CatalogoDoBoneco.coresDaRoupa[r.corRoupa].cores

        return (0 until QUADROS).map { q ->
            val tela = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
            // A ORDEM É A PILHA: corpo, roupa, cabelo. Cabelo por último porque ele
            // cai sobre a testa e sobre o ombro — inverter põe a franja atrás do rosto.
            colar(tela, base, q * 32, 0, CatalogoDoBoneco.CHAVES_DA_PELE, pele)
            roupa?.let { colar(tela, it, 0, DESCIDA_DO_TRONCO[q], CatalogoDoBoneco.CHAVES_DA_PECA, tonsDaRoupa) }
            cabelo?.let { colar(tela, it, 0, DESCIDA_DA_CABECA[q], CatalogoDoBoneco.CHAVES_DA_PECA, tonsDoCabelo) }
            assar(tela)
        }
    }

    /**
     * Copia [fonte] na [tela] trocando as cores-chave pela rampa, deslocando [dy]
     * linhas para baixo. Percorre pixel a pixel de propósito: é uma vez por receita,
     * em 1024 pixels, e o custo some no ruído de abrir a tela.
     */
    private fun colar(
        tela: BufferedImage,
        fonte: BufferedImage,
        origemX: Int,
        dy: Int,
        chaves: List<Int>,
        rampa: List<Int>,
    ) {
        for (y in 0 until 32) {
            val destinoY = y + dy
            if (destinoY !in 0 until 32) continue
            for (x in 0 until 32) {
                val p = fonte.getRGB(origemX + x, y)
                if (p ushr 24 == 0) continue
                val i = chaves.indexOf(p and 0xFFFFFF)
                tela.setRGB(x, destinoY, if (i >= 0) OPACO or rampa[i] else p)
            }
        }
    }

    // PNG no meio do caminho pelo mesmo motivo do gato: ler e escrever pixel avulso é
    // trivial no BufferedImage e chato no Skia, e o Skia é quem sabe desenhar.
    private fun assar(img: BufferedImage): ImageBitmap {
        val saida = ByteArrayOutputStream()
        ImageIO.write(img, "png", saida)
        return SkiaImage.makeFromEncoded(saida.toByteArray()).toComposeImageBitmap()
    }

    private fun ler(arquivo: String): BufferedImage? = cru.getOrPut(arquivo) {
        runCatching {
            FolhasDoBoneco::class.java.getResourceAsStream("/boneco/$arquivo")!!
                .use { ImageIO.read(ByteArrayInputStream(it.readBytes())) }
        }.getOrNull()
    }
}

/**
 * A receita guardada nesta máquina, ou nulo se a pessoa nunca montou um boneco.
 *
 * Nulo em vez do padrão de propósito: o padrão é o manequim cru — careca, sem roupa,
 * alabastro. Desenhá-lo para quem nunca entrou no Ateliê anunciaria como retrato uma
 * coisa que ninguém escolheu.
 */
@Composable
fun receitaLocalDoBoneco(): ReceitaDoBoneco? {
    val prefs = remember { GlobalContext.get().get<DesktopPrefs>() }
    val estado by prefs.state.collectAsState()
    return estado.boneco.takeIf { it.isNotBlank() }?.let { ReceitaDoBoneco.de(it) }
}

/**
 * Desenha o boneco de [receita] a [escala] pixels de tela por pixel de arte.
 *
 * A ESCALA É INTEIRA E É O CHAMADOR QUEM A DÁ, e isso não é preciosismo de API.
 * Receber uma altura em `dp` parece mais flexível e é uma armadilha: `dp` vira pixel
 * pela densidade da tela, então 156dp são 156 pixels a 100% e 195 a 125% — sete
 * pixels e meio de arte por pixel de tela. Meio pixel não existe, e o que aparece são
 * listras que o artista nunca desenhou, em algumas linhas sim e em outras não. Com a
 * escala explícita, a conta fecha em qualquer densidade.
 *
 * [animar] falso congela no primeiro quadro. Existe para a fita de escolha, onde vinte
 * bonecos balançando ao mesmo tempo seria ruído, não vida.
 */
@Composable
fun Boneco(
    receita: ReceitaDoBoneco,
    escala: Int,
    modifier: Modifier = Modifier,
    animar: Boolean = true,
) {
    val quadros = remember(receita) { FolhasDoBoneco.quadros(receita) } ?: return
    val parado = LocalReduceMotion.current || !LocalWindowActive.current || !animar

    var i by remember { mutableStateOf(0) }
    // Relógio próprio em vez de `rememberInfiniteTransition`: são seis trocas por
    // segundo, e uma transição infinita pediria quadro na taxa da tela para
    // interpolar um valor que só interessa em quatro degraus.
    LaunchedEffect(parado) {
        if (parado) { i = 0; return@LaunchedEffect }
        val inicio = withFrameNanos { it }
        while (true) {
            val agora = withFrameNanos { it }
            i = (((agora - inicio) / 1_000_000_000.0 * FPS_DO_BONECO).toInt()) % QUADROS
        }
    }

    val larguraPx = LARGURA_DO_BONECO * escala
    val alturaPx = ALTURA_DO_BONECO * escala
    val densidade = LocalDensity.current
    Canvas(
        modifier.size(
            with(densidade) { larguraPx.toDp() },
            with(densidade) { alturaPx.toDp() },
        ),
    ) {
        drawImage(
            image = quadros[i.coerceIn(0, QUADROS - 1)],
            srcOffset = IntOffset(JANELA_X, JANELA_Y),
            srcSize = IntSize(LARGURA_DO_BONECO, ALTURA_DO_BONECO),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(larguraPx, alturaPx),
            // Interpolar pixel art é borrar de propósito o que foi desenhado pixel a
            // pixel. Mesma regra do gato.
            filterQuality = FilterQuality.None,
        )
    }
}
