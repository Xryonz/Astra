package app.astra.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import org.jetbrains.skia.Image as SkiaImage
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

// O BONECO — o retrato que a pessoa monta, em vez do que ela fotografa.
//
// DOIS ACERVOS, e a razão é de licença, não de gosto:
//
//   EMBUTIDO (`/boneco/`) — o Character Template do Eris Esra, com os cabelos e as
//   roupas desenhados aqui. Licença que permite uso comercial e só proíbe revenda,
//   então pode viver num repositório público. É o que qualquer pessoa que clonar o
//   Astra recebe, e é o que garante que clonar e compilar funcione.
//
//   PRIVADO (`/boneco-privado/`) — o pack da CozyFae, que é um sistema paper-doll de
//   verdade: base, olhos, cabelo, blusa e calça em camadas próprias, com quadros
//   próprios. Arte melhor, e licença que PROÍBE redistribuir os arquivos. Ele não
//   entra no Git (ver `.gitignore`); quem tem a licença o reconstrói com
//   `tools/preparar-boneco-privado.ps1`.
//
// Quando o privado existe, ele manda. Quando não existe, o embutido atende. Nada no
// resto do app precisa saber qual dos dois está no ar.
//
// O QUE TORNOU O ACERVO EMBUTIDO VIÁVEL foi uma medida, não uma ideia. O template do
// Eris Esra é um MANEQUIM — creme, careca, sem roupa — e vesti-lo por inteiro custaria
// ~158 quadros POR PEÇA (5 direções × 7 animações). Mas a caixa de cada quadro da
// animação parada, de frente, é:
//
//	quadro 0:  x[8..23]  y[7..31]
//	quadro 1:  x[8..23]  y[8..31]
//	quadro 2:  x[8..23]  y[9..31]
//	quadro 3:  x[8..23]  y[7..31]
//
// O X NUNCA MUDA E A SILHUETA NUNCA MUDA: a animação inteira é um deslocamento
// vertical. Então um cabelo não são quatro desenhos, é UM desenho com quatro
// deslocamentos — 158 quadros viraram 1.
//
// E É ESSA MESMA IDEIA QUE UNIFICA OS DOIS ACERVOS, numa linha só de código: cada
// camada lê o quadro `q % (largura do arquivo / célula)`. A peça de um quadro só lê
// sempre o quadro 0 e anda pelo deslocamento; a peça com quadros próprios lê o seu e
// não anda. Nenhum `if` de acervo dentro do laço de desenho.
//
// A COR sai de graça nos dois, pela técnica que o gato já usa
// (`FolhasDoGato.repintar`): os sprites têm meia dúzia de cores, então trocar a
// paleta troca o personagem. Um cabelo desenhado vezes oito cores são oito cabelos.
//
// E O QUE FICA GUARDADO É A RECEITA, NÃO A IMAGEM: uns dez bytes de texto contra o
// data-URI de uma foto.

/** O que a pessoa escolheu. Cabe num campo de texto e viaja no JSON que já existe. */
data class ReceitaDoBoneco(
    val pele: Int = 0,
    val cabelo: Int = 0,
    val corCabelo: Int = 0,
    val roupa: Int = 0,
    val corRoupa: Int = 0,
    // Daqui pra baixo só o acervo privado usa; o embutido não tem estas camadas.
    val corOlhos: Int = 0,
    val calca: Int = 0,
    val corCalca: Int = 0,
) {
    /**
     * Formato posicional de propósito: campo novo entra no FIM, e receita antiga
     * continua legível caindo no padrão do que ainda não existia. Foi assim que
     * olhos e calça entraram sem invalidar nada que já estivesse gravado.
     */
    fun texto(): String = "$pele.$cabelo.$corCabelo.$roupa.$corRoupa.$corOlhos.$calca.$corCalca"

    companion object {
        val PADRAO = ReceitaDoBoneco()

        fun de(texto: String?): ReceitaDoBoneco {
            if (texto.isNullOrBlank()) return PADRAO
            val p = texto.split('.').map { it.trim().toIntOrNull() ?: 0 }
            val a = CatalogoDoBoneco.ativo
            // Limitar pelo acervo ATIVO, e não pelo que estava no ar quando gravou:
            // trocar de acervo muda o tamanho das listas, e um índice fora da lista
            // seria um estouro na hora de desenhar o cartão de alguém.
            fun em(i: Int, limite: Int) = if (limite <= 0) 0 else (p.getOrNull(i) ?: 0).coerceIn(0, limite - 1)
            return ReceitaDoBoneco(
                pele = em(0, a.peles.size),
                cabelo = em(1, a.cabelos.size),
                corCabelo = em(2, a.coresDoCabelo.size),
                roupa = em(3, a.roupas.size),
                corRoupa = em(4, a.coresDaRoupa.size),
                corOlhos = em(5, a.coresDosOlhos.size),
                calca = em(6, a.calcas.size),
                corCalca = em(7, a.coresDaCalca.size),
            )
        }
    }
}

/** Um tom: as cores que substituem as chaves, na ordem em que as chaves estão. */
data class TomDoBoneco(val nome: String, val cores: List<Int>)

/** Uma peça. `arquivo` nulo é a ausência dela — careca, sem roupa, sem calça. */
data class PecaDoBoneco(val nome: String, val arquivo: String?)

/** Uma camada já resolvida para uma receita: que arquivo, com que paleta, e o
 *  deslocamento por quadro (zeros quando o arquivo traz os próprios quadros). */
internal class Camada(
    val arquivo: String,
    val chaves: List<Int>,
    val rampa: List<Int>,
    val dy: IntArray = SEM_DESLOCAMENTO,
) {
    companion object { val SEM_DESLOCAMENTO = IntArray(0) }
}

/**
 * Um conjunto de arte com sua própria anatomia. As listas de peças e cores são DADO:
 * acrescentar um cabelo é soltar um PNG na pasta e uma linha aqui, sem tocar em
 * lógica — o que importa porque a parte cara desta feature é arte, e arte chega
 * depois do código.
 *
 * Lista vazia significa "este acervo não tem esta camada", e é assim que o Ateliê
 * sabe quais fitas desenhar sem perguntar qual acervo está no ar.
 */
class AcervoDoBoneco internal constructor(
    internal val pasta: String,
    internal val quadros: Int,
    internal val fps: Int,
    internal val celula: Int,
    internal val janelaX: Int,
    internal val janelaY: Int,
    internal val largura: Int,
    internal val altura: Int,
    val peles: List<TomDoBoneco>,
    val cabelos: List<PecaDoBoneco>,
    val coresDoCabelo: List<TomDoBoneco>,
    val roupas: List<PecaDoBoneco>,
    val coresDaRoupa: List<TomDoBoneco>,
    val coresDosOlhos: List<TomDoBoneco> = emptyList(),
    val calcas: List<PecaDoBoneco> = emptyList(),
    val coresDaCalca: List<TomDoBoneco> = emptyList(),
    internal val camadas: (ReceitaDoBoneco) -> List<Camada>,
)

object CatalogoDoBoneco {
    // --- ACERVO EMBUTIDO: o manequim do Eris Esra, vestido à mão. -------------

    // As cinco cores do sprite. As três primeiras são pele; a quarta é a bochecha,
    // que precisa acompanhar — rosa claro sobre pele escura vira mancha.
    private val CHAVES_DA_PELE = listOf(0xFAF3E8, 0xE7D5C6, 0xB9B3A1, 0xD19DA7)

    // Cinza de propósito nas peças desenhadas aqui: cinza não é cor nenhuma, então
    // ninguém confunde a chave de troca com uma escolha estética.
    private val CHAVES_DA_PECA = listOf(0xF2F2F2, 0xA8A8A8, 0x5C5C5C)

    // Medidos linha a linha: a cabeça desce dois pixels no ciclo, o tronco desce um,
    // e o pescoço engole a diferença.
    private val DESCIDA_DA_CABECA = intArrayOf(0, 1, 2, 0)
    private val DESCIDA_DO_TRONCO = intArrayOf(0, 1, 1, 0)

    // OS CATÁLOGOS SÃO DECLARADOS ANTES DO ACERVO, e não dentro dele, porque a lambda
    // `camadas` precisa lê-los para resolver uma receita. Escritos inline, ela leria
    // o acervo enquanto o acervo ainda estava sendo construído — inicialização
    // circular, que em Kotlin aparece como uma cascata de "cannot infer type".
    private val EMBUTIDO_PELES = listOf(
        TomDoBoneco("Alabastro", listOf(0xFAF3E8, 0xE7D5C6, 0xB9B3A1, 0xD19DA7)),
        TomDoBoneco("Areia", listOf(0xF2D9BC, 0xDCBE9C, 0xB08F6E, 0xD69A94)),
        TomDoBoneco("Mel", listOf(0xE8C4A0, 0xD3A87F, 0xA9805C, 0xC98878)),
        TomDoBoneco("Âmbar", listOf(0xD2A074, 0xB37F55, 0x8A5C39, 0xB5766A)),
        TomDoBoneco("Castanha", listOf(0xA97048, 0x8C5836, 0x5F3A22, 0x8E5546)),
        TomDoBoneco("Ébano", listOf(0x6E4630, 0x563421, 0x372014, 0x6B3D33)),
    )
    private val EMBUTIDO_CABELOS = listOf(
        PecaDoBoneco("Raspado", null),
        PecaDoBoneco("Curto", "cabelo-curto.png"),
        PecaDoBoneco("Chanel", "cabelo-medio.png"),
        PecaDoBoneco("Longo", "cabelo-longo.png"),
    )
    private val EMBUTIDO_CORES_CABELO = listOf(
        TomDoBoneco("Corvo", listOf(0x4A4550, 0x322E39, 0x1D1A23)),
        TomDoBoneco("Castanho", listOf(0x6B4A2F, 0x4A3220, 0x2E1F14)),
        TomDoBoneco("Avelã", listOf(0x8A6440, 0x66452A, 0x422B19)),
        TomDoBoneco("Trigo", listOf(0xC9A96E, 0xA8894F, 0x6E5730)),
        TomDoBoneco("Cobre", listOf(0xB5653A, 0x8C4A28, 0x5B2E19)),
        TomDoBoneco("Neve", listOf(0xEDEAF2, 0xC3BFCB, 0x86828E)),
        TomDoBoneco("Ametista", listOf(0x8E6FB8, 0x6B5090, 0x45325F)),
        TomDoBoneco("Anil", listOf(0x5A79B5, 0x41598C, 0x293759)),
    )
    private val EMBUTIDO_ROUPAS = listOf(
        PecaDoBoneco("Nenhuma", null),
        PecaDoBoneco("Camiseta", "roupa-camiseta.png"),
        PecaDoBoneco("Manga longa", "roupa-manga.png"),
        PecaDoBoneco("Túnica", "roupa-tunica.png"),
    )
    private val EMBUTIDO_CORES_ROUPA = listOf(
        TomDoBoneco("Ardósia", listOf(0x8E93A0, 0x5C6070, 0x3A3D49)),
        TomDoBoneco("Anil", listOf(0x7E96C4, 0x5A719C, 0x3A4A6B)),
        TomDoBoneco("Musgo", listOf(0x8AA37E, 0x5E7754, 0x3C4E35)),
        TomDoBoneco("Ferrugem", listOf(0xC4756B, 0x96524A, 0x63332E)),
        TomDoBoneco("Areia", listOf(0xD3BC96, 0xA8926C, 0x6E5D45)),
        TomDoBoneco("Ameixa", listOf(0x9A7BB0, 0x6E5482, 0x453353)),
        TomDoBoneco("Carvão", listOf(0x5A5A62, 0x3C3C44, 0x26262C)),
        TomDoBoneco("Marfim", listOf(0xEAE4D8, 0xC4BDAE, 0x827C70)),
    )

    private val EMBUTIDO = AcervoDoBoneco(
        pasta = "boneco",
        quadros = 4,
        fps = 6,
        celula = 32,
        janelaX = 7, janelaY = 6, largura = 18, altura = 26,
        peles = EMBUTIDO_PELES,
        cabelos = EMBUTIDO_CABELOS,
        coresDoCabelo = EMBUTIDO_CORES_CABELO,
        roupas = EMBUTIDO_ROUPAS,
        coresDaRoupa = EMBUTIDO_CORES_ROUPA,
        camadas = { r ->
            buildList {
                add(Camada("base.png", CHAVES_DA_PELE, EMBUTIDO_PELES[r.pele].cores))
                EMBUTIDO_ROUPAS[r.roupa].arquivo?.let {
                    add(Camada(it, CHAVES_DA_PECA, EMBUTIDO_CORES_ROUPA[r.corRoupa].cores, DESCIDA_DO_TRONCO))
                }
                // O cabelo por ÚLTIMO: ele cai sobre a testa e sobre o ombro, e
                // inverter a ordem põe a franja atrás do rosto.
                EMBUTIDO_CABELOS[r.cabelo].arquivo?.let {
                    add(Camada(it, CHAVES_DA_PECA, EMBUTIDO_CORES_CABELO[r.corCabelo].cores, DESCIDA_DA_CABECA))
                }
            }
        },
    )

    // --- ACERVO PRIVADO: o paper-doll da CozyFae. -----------------------------
    //
    // Aqui não há tabela de deslocamento: cada camada traz os próprios dois quadros,
    // já alinhados na mesma célula de 32x32. É o acervo que a unificação do `colar`
    // existe para atender sem um `if`.

    private val CHAVES_PELE_COZY = listOf(0xFACFB4, 0xF0AB8B, 0xE18F6C, 0x591D19)
    private val CHAVES_OLHOS_COZY = listOf(0xCCE1FF, 0x3C5E8B, 0x253A5E)
    private val CHAVES_CABELO_COZY = listOf(0xBE772B, 0x61312C, 0x471E1E, 0x261414)
    private val CHAVES_BLUSA_COZY = listOf(0xF5EBDF, 0xA3897A)
    private val CHAVES_CALCA_COZY = listOf(0x3C5E8B, 0x253A5E, 0x172038)

    private val PRIVADO_PELES = listOf(
        TomDoBoneco("Porcelana", listOf(0xF5DCC8, 0xE8C4A8, 0xD2A184, 0x4A2420)),
        TomDoBoneco("Areia", listOf(0xFACFB4, 0xF0AB8B, 0xE18F6C, 0x591D19)),
        TomDoBoneco("Mel", listOf(0xE8B48C, 0xD4926A, 0xB4744E, 0x4A2018)),
        TomDoBoneco("Âmbar", listOf(0xC98F63, 0xAC7147, 0x8A5432, 0x3E1C14)),
        TomDoBoneco("Castanha", listOf(0x9A6642, 0x7E4E2E, 0x5E381E, 0x2E1410)),
        TomDoBoneco("Ébano", listOf(0x6B4430, 0x543120, 0x3A2014, 0x1E0E0A)),
    )
    private val PRIVADO_CABELOS = listOf(
        PecaDoBoneco("Raspado", null),
        PecaDoBoneco("Curto", "cabelo-curto.png"),
        PecaDoBoneco("Médio", "cabelo-medio.png"),
    )
    private val PRIVADO_CORES_CABELO = listOf(
        TomDoBoneco("Corvo", listOf(0x5A5464, 0x3E3948, 0x2E2A36, 0x1C1A22)),
        TomDoBoneco("Castanho", listOf(0xBE772B, 0x61312C, 0x471E1E, 0x261414)),
        TomDoBoneco("Avelã", listOf(0xC99A5E, 0x7A5436, 0x5C3C26, 0x3A2416)),
        TomDoBoneco("Trigo", listOf(0xE8CE96, 0xB99A5E, 0x8E7440, 0x5E4A26)),
        TomDoBoneco("Cobre", listOf(0xD88A46, 0xA85428, 0x7E3A1C, 0x4E220E)),
        TomDoBoneco("Neve", listOf(0xF2EEF6, 0xC6C0D0, 0x9A93A8, 0x6A6478)),
        TomDoBoneco("Ametista", listOf(0xC49AE0, 0x8A63AC, 0x664680, 0x402A52)),
        TomDoBoneco("Anil", listOf(0x8AA8DC, 0x5A78AC, 0x405684, 0x283654)),
    )
    private val PRIVADO_ROUPAS = listOf(
        PecaDoBoneco("Nenhuma", null),
        PecaDoBoneco("Blusa", "blusa.png"),
    )
    private val PRIVADO_CORES_ROUPA = listOf(
        TomDoBoneco("Marfim", listOf(0xF5EBDF, 0xA3897A)),
        TomDoBoneco("Ardósia", listOf(0x9AA0AE, 0x646A7A)),
        TomDoBoneco("Anil", listOf(0x7E9AC8, 0x4C6490)),
        TomDoBoneco("Musgo", listOf(0x8CAE7E, 0x566E4C)),
        TomDoBoneco("Ferrugem", listOf(0xC87E6E, 0x8E4E42)),
        TomDoBoneco("Ameixa", listOf(0xA886C0, 0x6E5286)),
        TomDoBoneco("Carvão", listOf(0x5E5E68, 0x3A3A44)),
        TomDoBoneco("Açafrão", listOf(0xE0B860, 0xA8823A)),
    )
    private val PRIVADO_CORES_OLHOS = listOf(
        TomDoBoneco("Céu", listOf(0xCCE1FF, 0x3C5E8B, 0x253A5E)),
        TomDoBoneco("Mel", listOf(0xF0D9A8, 0xA87B3C, 0x6B4A1E)),
        TomDoBoneco("Musgo", listOf(0xC8E0B0, 0x6B8F4E, 0x3E5B2C)),
        TomDoBoneco("Brasa", listOf(0xE8C0A8, 0xA86848, 0x6B3A26)),
        TomDoBoneco("Ardósia", listOf(0xD8DCE4, 0x78808E, 0x464C58)),
        TomDoBoneco("Ametista", listOf(0xDCC8EC, 0x8A6BA8, 0x543E68)),
    )
    private val PRIVADO_CALCAS = listOf(
        PecaDoBoneco("Nenhuma", null),
        PecaDoBoneco("Calça", "calca.png"),
    )
    private val PRIVADO_CORES_CALCA = listOf(
        TomDoBoneco("Índigo", listOf(0x3C5E8B, 0x253A5E, 0x172038)),
        TomDoBoneco("Carvão", listOf(0x5A5A64, 0x3C3C44, 0x24242A)),
        TomDoBoneco("Terra", listOf(0x8A6848, 0x5E4630, 0x3A2A1C)),
        TomDoBoneco("Musgo", listOf(0x6E8A5E, 0x4A6040, 0x2C3A26)),
        TomDoBoneco("Vinho", listOf(0x8A4A56, 0x5E303A, 0x3A1C24)),
        TomDoBoneco("Areia", listOf(0xC4A87E, 0x8E7654, 0x5A4A34)),
    )

    private val PRIVADO = AcervoDoBoneco(
        pasta = "boneco-privado",
        quadros = 2,
        fps = 3,
        celula = 32,
        janelaX = 9, janelaY = 7, largura = 14, altura = 20,
        peles = PRIVADO_PELES,
        cabelos = PRIVADO_CABELOS,
        coresDoCabelo = PRIVADO_CORES_CABELO,
        roupas = PRIVADO_ROUPAS,
        coresDaRoupa = PRIVADO_CORES_ROUPA,
        coresDosOlhos = PRIVADO_CORES_OLHOS,
        calcas = PRIVADO_CALCAS,
        coresDaCalca = PRIVADO_CORES_CALCA,
        camadas = { r ->
            buildList {
                add(Camada("base.png", CHAVES_PELE_COZY, PRIVADO_PELES[r.pele].cores))
                PRIVADO_CALCAS[r.calca].arquivo?.let {
                    add(Camada(it, CHAVES_CALCA_COZY, PRIVADO_CORES_CALCA[r.corCalca].cores))
                }
                PRIVADO_ROUPAS[r.roupa].arquivo?.let {
                    add(Camada(it, CHAVES_BLUSA_COZY, PRIVADO_CORES_ROUPA[r.corRoupa].cores))
                }
                // Olhos ANTES do cabelo: a franja passa por cima do olhar, não o
                // contrário. Calça antes da blusa pelo mesmo motivo — a barra da
                // blusa cobre o cós.
                add(Camada("olhos.png", CHAVES_OLHOS_COZY, PRIVADO_CORES_OLHOS[r.corOlhos].cores))
                PRIVADO_CABELOS[r.cabelo].arquivo?.let {
                    add(Camada(it, CHAVES_CABELO_COZY, PRIVADO_CORES_CABELO[r.corCabelo].cores))
                }
            }
        },
    )

    /**
     * O acervo em uso. O privado ganha quando está presente; senão o embutido, que
     * está sempre. Decidido UMA vez, no primeiro acesso: trocar de acervo no meio da
     * sessão significaria que uma receita já lida com uma lista poderia estourar a
     * outra.
     */
    val ativo: AcervoDoBoneco by lazy {
        val temPrivado = CatalogoDoBoneco::class.java.getResource("/${PRIVADO.pasta}/base.png") != null
        if (temPrivado) PRIVADO else EMBUTIDO
    }

    // Atalhos para a UI, que não deve precisar saber qual acervo está no ar.
    val peles get() = ativo.peles
    val cabelos get() = ativo.cabelos
    val coresDoCabelo get() = ativo.coresDoCabelo
    val roupas get() = ativo.roupas
    val coresDaRoupa get() = ativo.coresDaRoupa
    val coresDosOlhos get() = ativo.coresDosOlhos
    val calcas get() = ativo.calcas
    val coresDaCalca get() = ativo.coresDaCalca
}

private const val OPACO = 0xFF000000.toInt()

internal object FolhasDoBoneco {
    private val cru = HashMap<String, BufferedImage?>()

    // TETO NO CACHE, e não é zelo prematuro: quando o boneco entrar na lista de
    // membros, uma constelação grande instancia centenas de receitas numa rolagem.
    // Sem teto isso é um vazamento com cara de cache. 96 cobre qualquer tela do app
    // com folga, e cada entrada são poucos bitmaps de 32x32.
    private const val TETO = 96
    private val prontos = object : LinkedHashMap<String, List<ImageBitmap>?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<ImageBitmap>?>) = size > TETO
    }

    @Synchronized
    fun quadros(r: ReceitaDoBoneco): List<ImageBitmap>? {
        val chave = r.texto()
        // `containsKey` e não `getOrPut`: nulo aqui é uma RESPOSTA (esta receita não
        // monta — recurso faltando, jar estranho), e `getOrPut` trataria nulo como
        // "ainda não perguntei", refazendo a tentativa a cada composição.
        if (prontos.containsKey(chave)) return prontos[chave]
        val feito = runCatching { montar(r) }.getOrNull()
        prontos[chave] = feito
        return feito
    }

    private fun montar(r: ReceitaDoBoneco): List<ImageBitmap> {
        val a = CatalogoDoBoneco.ativo
        val camadas = a.camadas(r)
        return (0 until a.quadros).map { q ->
            val tela = BufferedImage(a.celula, a.celula, BufferedImage.TYPE_INT_ARGB)
            camadas.forEach { c -> ler(a.pasta, c.arquivo)?.let { colar(tela, it, q, c, a.celula) } }
            assar(tela)
        }
    }

    /**
     * Cola uma camada no quadro [quadro], trocando as cores-chave pela rampa.
     *
     * AQUI MORA A UNIFICAÇÃO DOS DOIS ACERVOS. Quantos quadros a camada tem sai da
     * LARGURA DO ARQUIVO dividida pela célula: a peça desenhada sobre o manequim tem
     * um quadro só, então lê sempre o mesmo e anda pelo deslocamento; a peça do
     * paper-doll tem os seus, então lê o que corresponde e não anda. Um caminho de
     * código, dois acervos.
     *
     * Percorre pixel a pixel de propósito: é uma vez por receita, em mil pixels, e o
     * custo some no ruído de abrir a tela.
     */
    private fun colar(tela: BufferedImage, fonte: BufferedImage, quadro: Int, c: Camada, celula: Int) {
        val quadrosNoArquivo = (fonte.width / celula).coerceAtLeast(1)
        val origemX = (quadro % quadrosNoArquivo) * celula
        val dy = c.dy.getOrElse(quadro) { 0 }
        for (y in 0 until celula) {
            val destinoY = y + dy
            if (destinoY !in 0 until celula) continue
            for (x in 0 until celula) {
                val p = fonte.getRGB(origemX + x, y)
                if (p ushr 24 == 0) continue
                val i = c.chaves.indexOf(p and 0xFFFFFF)
                tela.setRGB(x, destinoY, if (i >= 0 && i < c.rampa.size) OPACO or c.rampa[i] else p)
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

    private fun ler(pasta: String, arquivo: String): BufferedImage? = cru.getOrPut("$pasta/$arquivo") {
        runCatching {
            FolhasDoBoneco::class.java.getResourceAsStream("/$pasta/$arquivo")!!
                .use { ImageIO.read(ByteArrayInputStream(it.readBytes())) }
        }.getOrNull()
    }
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
    val acervo = CatalogoDoBoneco.ativo
    val quadros = remember(receita) { FolhasDoBoneco.quadros(receita) } ?: return
    val parado = LocalReduceMotion.current || !LocalWindowActive.current || !animar

    var i by remember { mutableStateOf(0) }
    // Relógio próprio em vez de `rememberInfiniteTransition`: são poucas trocas por
    // segundo, e uma transição infinita pediria quadro na taxa da tela para
    // interpolar um valor que só interessa em degraus inteiros.
    LaunchedEffect(parado, acervo) {
        if (parado) { i = 0; return@LaunchedEffect }
        val inicio = withFrameNanos { it }
        while (true) {
            val agora = withFrameNanos { it }
            i = (((agora - inicio) / 1_000_000_000.0 * acervo.fps).toInt()) % acervo.quadros
        }
    }

    val larguraPx = acervo.largura * escala
    val alturaPx = acervo.altura * escala
    val densidade = LocalDensity.current
    Canvas(
        modifier.size(
            with(densidade) { larguraPx.toDp() },
            with(densidade) { alturaPx.toDp() },
        ),
    ) {
        drawImage(
            image = quadros[i.coerceIn(0, quadros.size - 1)],
            srcOffset = IntOffset(acervo.janelaX, acervo.janelaY),
            srcSize = IntSize(acervo.largura, acervo.altura),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(larguraPx, alturaPx),
            // Interpolar pixel art é borrar de propósito o que foi desenhado pixel a
            // pixel. Mesma regra do gato.
            filterQuality = FilterQuality.None,
        )
    }
}
