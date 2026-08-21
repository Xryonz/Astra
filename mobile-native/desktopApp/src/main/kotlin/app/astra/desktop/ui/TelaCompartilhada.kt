package app.astra.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawBehind
import app.astra.desktop.voice.QuadroDeTela
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data as SkiaData
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.Shader
import org.jetbrains.skia.FilterTileMode

// A TELA DE OUTRA PESSOA, DESENHADA — o fim do caminho que começa na placa dela.
//
// O QUADRO CHEGA EM NV12, e é de propósito. O decodificador do Windows não oferece RGB, e
// converter no caminho custaria CPU: uma volta por pixel em 720p são 921 mil iterações
// por quadro, umas trinta vezes por segundo. Medido no papel: 10 a 18% de um núcleo só
// para trocar o arranjo das cores, numa máquina que já está decodificando.
//
// AQUI A PLACA FAZ ISSO DE GRAÇA. O NV12 sobe como duas imagens de um canal só — o brilho
// e a cor — e a conversão acontece no shader, um pixel por vez, em paralelo. O que a CPU
// paga é copiar 1,4 MB por quadro em vez de converter 921 mil pixels; a diferença é de
// uma ordem de grandeza, e a máquina fraca é justamente quem vai assistir.
//
// E A BANDA ENTRE OS PROCESSOS CAI JUNTO: NV12 é 1,5 byte por pixel contra 4 do BGRA. Em
// 720p são 1,3 MB por quadro em vez de 3,5 MB. A escolha do formato paga duas vezes.
//
// COMO O NV12 É ARRUMADO, porque o shader depende disso e não é óbvio:
//
//	brilho (Y)   uma amostra POR PIXEL — `altura` linhas de `passo` bytes
//	cor (UV)     uma amostra a cada 2x2 pixels, com U e V ALTERNADOS na mesma linha —
//	             `altura/2` linhas de `passo` bytes, logo depois do brilho
//
// Ou seja: a linha de cor tem o mesmo comprimento em BYTES da linha de brilho, mas
// metade dos pixels, porque cada pixel de cor ocupa dois bytes. É por isso que a conta de
// coluna no shader é `floor(x/2)*2` e não `x/2`.

private const val SKSL_NV12 = """
uniform shader brilho;
uniform shader cor;

half4 main(float2 p) {
    // O canal alfa porque as duas imagens sobem como ALPHA_8: um byte por amostra, e
    // o Skia o entrega no alfa.
    float y = brilho.eval(p).a;

    // A amostra de cor cobre um quadrado de 2x2 pixels. A linha é a metade da de
    // brilho; a coluna é o par (U,V) que começa em `floor(x/2)*2`.
    float2 c = float2(floor(p.x * 0.5) * 2.0, floor(p.y * 0.5));
    float u = cor.eval(float2(c.x + 0.5, c.y + 0.5)).a - 0.5;
    float v = cor.eval(float2(c.x + 1.5, c.y + 0.5)).a - 0.5;

    // BT.709, faixa ESTUDIO — que é o que o H.264 usa por padrão em alta definição, e
    // o que o compressor do Windows produz sem receber ordem em contrário. O brilho
    // vive entre 16 e 235 e a cor entre 16 e 240, então os dois são esticados de volta
    // antes da conta. Tratar como faixa cheia deixaria a imagem lavada; o contrário,
    // dura demais — é a diferença que se vê nos cinzas, não nas cores fortes.
    y = (y - 0.0627) * 1.1644;
    u = u * 1.1384;
    v = v * 1.1384;

    return half4(
        half(clamp(y + 1.5748 * v, 0.0, 1.0)),
        half(clamp(y - 0.1873 * u - 0.4681 * v, 0.0, 1.0)),
        half(clamp(y + 1.8556 * u, 0.0, 1.0)),
        1.0
    );
}
"""

/**
 * Desenha [quadro] preenchendo o espaço disponível, sem distorcer.
 *
 * NADA É DESENHADO quando o quadro é nulo — quem chama decide o que pôr no lugar. Este
 * componente não inventa estado vazio: ele sabe desenhar imagem, e só.
 */
@Composable
fun TelaCompartilhada(quadro: QuadroDeTela?, modifier: Modifier = Modifier) {
    // O EFEITO E O CONSTRUTOR VIVEM ENQUANTO O COMPONENTE VIVER. Compilar SkSL custa, e
    // recompilar por quadro seria pagar isso trinta vezes por segundo — o mesmo erro que
    // a aurora cometeu e que foi medido lá em 0,29 de núcleo com a tela parada.
    val efeito = remember { runCatching { RuntimeEffect.makeForShader(SKSL_NV12.trimIndent()) }.getOrNull() }
    val construtor = remember(efeito) { efeito?.let { RuntimeShaderBuilder(it) } }
    val tinta = remember { Paint() }

    // As peças nativas do quadro anterior, guardadas para serem FECHADAS na hora.
    //
    // Cada quadro cria duas imagens e um shader do Skia, e nenhum deles é memória da
    // JVM: some quando o coletor roda o limpador, em lote, muito depois. A 30 quadros por
    // segundo isso vira um engasgo periódico com cara de "a imagem cortou do nada" — foi
    // exatamente esse o defeito da aurora. Fechar o anterior antes de trocar torna a
    // liberação determinística.
    val anteriores = remember { arrayOfNulls<AutoCloseable>(5) }
    DisposableEffect(Unit) {
        onDispose {
            anteriores.forEach { runCatching { it?.close() } }
            anteriores.fill(null)
        }
    }

    Box(modifier) {
        if (quadro == null || construtor == null) return@Box
        Box(
            Modifier.fillMaxSize().drawBehind {
                val q = quadro
                if (q.largura <= 0 || q.altura <= 0 || size.width <= 0f || size.height <= 0f) return@drawBehind

                // O plano de brilho e o de cor são fatias do MESMO vetor, e viajam para o
                // Skia por `Data` com deslocamento — não por `copyOfRange`. A diferença
                // não é estilo: `copyOfRange` alocaria 1,4 MB na JVM por quadro, quarenta
                // megabytes por segundo de lixo, no app em que já se lutou para segurar a
                // memória. O `Data` copia uma vez, para dentro do Skia, e é a cópia que
                // liberta o rodízio de vetores do cano a seguir em frente.
                val bytesDoBrilho = q.passo * q.altura
                val bytesDaCor = q.passo * (q.altura / 2)
                if (q.dados.size < bytesDoBrilho + bytesDaCor) return@drawBehind

                val dadosDoBrilho = SkiaData.makeFromBytes(q.dados, 0, bytesDoBrilho)
                val dadosDaCor = SkiaData.makeFromBytes(q.dados, bytesDoBrilho, bytesDaCor)

                val brilho = runCatching {
                    SkiaImage.makeRaster(
                        ImageInfo(q.largura, q.altura, ColorType.ALPHA_8, ColorAlphaType.OPAQUE),
                        dadosDoBrilho,
                        q.passo,
                    )
                }.getOrNull()
                val cor = runCatching {
                    SkiaImage.makeRaster(
                        // LARGURA CHEIA E ALTURA PELA METADE: a linha de cor tem o mesmo
                        // comprimento em bytes da de brilho, mas cada pixel de cor ocupa
                        // dois (U e V). Declarar metade da largura aqui faria o shader
                        // ler a cor de um lugar que não existe.
                        ImageInfo(q.largura, q.altura / 2, ColorType.ALPHA_8, ColorAlphaType.OPAQUE),
                        dadosDaCor,
                        q.passo,
                    )
                }.getOrNull()
                runCatching { dadosDoBrilho.close() }
                runCatching { dadosDaCor.close() }
                if (brilho == null || cor == null) {
                    runCatching { brilho?.close() }
                    runCatching { cor?.close() }
                    return@drawBehind
                }

                // AMOSTRAGEM VIZINHA nos dois planos, e não linear. Interpolar aqui
                // misturaria amostras de cor de blocos 2x2 vizinhos ANTES da conversão,
                // e o resultado é franja colorida nas bordas de contraste — texto branco
                // sobre fundo escuro, que é o conteúdo mais comum de tela compartilhada.
                val vizinho = FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)
                val sB = brilho.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, vizinho)
                val sC = cor.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, vizinho)
                construtor.child("brilho", sB)
                construtor.child("cor", sC)
                val shader: Shader = construtor.makeShader()

                // A ESCALA VAI NO CANVAS, e o retângulo é desenhado no tamanho do quadro.
                // Assim as coordenadas que chegam ao shader são PIXELS DA IMAGEM, e as
                // contas de plano de cor fecham em número inteiro. Escalar o retângulo em
                // vez do canvas faria `p` chegar na escala da tela, e a conta de coluna
                // passaria a pular ou repetir amostras conforme o tamanho da janela.
                val escala = minOf(size.width / q.largura, size.height / q.altura)
                val larguraFinal = q.largura * escala
                val alturaFinal = q.altura * escala

                tinta.shader = shader
                drawIntoCanvas { tela ->
                    val c = tela.nativeCanvas
                    c.save()
                    c.translate((size.width - larguraFinal) / 2f, (size.height - alturaFinal) / 2f)
                    c.scale(escala, escala)
                    c.drawRect(Rect.makeWH(q.largura.toFloat(), q.altura.toFloat()), tinta)
                    c.restore()
                }

                // FECHA OS CINCO DO QUADRO ANTERIOR, e os cinco importam: as duas imagens,
                // os dois shaders que saem delas, e o shader do efeito. Esquecer os de
                // dentro é o vazamento silencioso — eles não aparecem em heap nenhum
                // porque não são memória da JVM.
                for (i in anteriores.indices) {
                    anteriores[i]?.let { runCatching { it.close() } }
                }
                anteriores[0] = shader
                anteriores[1] = sB
                anteriores[2] = sC
                anteriores[3] = brilho
                anteriores[4] = cor
            },
        )
    }
}
