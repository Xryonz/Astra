package app.astra.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow
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

@Composable
fun TelaCompartilhada(
    fonte: StateFlow<Map<String, QuadroDeTela>>,
    de: String,
    modifier: Modifier = Modifier,
) {
    val quadro = remember(de) { mutableStateOf(fonte.value[de]) }
    LaunchedEffect(fonte, de) {
        fonte.collect { quadro.value = it[de] }
    }
    val efeito = remember { runCatching { RuntimeEffect.makeForShader(SKSL_NV12.trimIndent()) }.getOrNull() }
    val construtor = remember(efeito) { efeito?.let { RuntimeShaderBuilder(it) } }
    val tinta = remember { Paint() }

    val anteriores = remember { arrayOfNulls<AutoCloseable>(5) }
    DisposableEffect(Unit) {
        onDispose {
            anteriores.forEach { runCatching { it?.close() } }
            anteriores.fill(null)
        }
    }

    Box(modifier) {
        if (construtor == null) return@Box
        Box(
            Modifier.fillMaxSize().drawBehind {
                val q = quadro.value ?: return@drawBehind
                if (q.largura <= 0 || q.altura <= 0 || size.width <= 0f || size.height <= 0f) return@drawBehind

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

                val vizinho = FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)
                val sB = brilho.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, vizinho)
                val sC = cor.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, vizinho)
                construtor.child("brilho", sB)
                construtor.child("cor", sC)
                val shader: Shader = construtor.makeShader()

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
