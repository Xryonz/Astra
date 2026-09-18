package app.astra.desktop.ui

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Image as SkiaImage
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAIOR_REDUCAO_POR_ETAPA = 2f

fun reduzirEmEtapas(origem: SkiaImage, larguraAlvo: Int, alturaAlvo: Int): Bitmap {
    var atual = origem
    var minhaImagem: SkiaImage? = null
    var minhaVez: Surface? = null

    while (atual.width > larguraAlvo * MAIOR_REDUCAO_POR_ETAPA &&
        atual.height > alturaAlvo * MAIOR_REDUCAO_POR_ETAPA
    ) {
        val meia = Surface.makeRasterN32Premul(
            max(larguraAlvo, atual.width / 2),
            max(alturaAlvo, atual.height / 2),
        )
        meia.canvas.drawImageRect(
            atual,
            Rect.makeWH(atual.width.toFloat(), atual.height.toFloat()),
            Rect.makeWH(meia.width.toFloat(), meia.height.toFloat()),
            SamplingMode.LINEAR,
            null,
            true,
        )
        val imagemAnterior = minhaImagem
        val superficieAnterior = minhaVez
        atual = meia.makeImageSnapshot()
        minhaImagem = atual
        minhaVez = meia
        imagemAnterior?.close()
        superficieAnterior?.close()
    }

    val destino = Bitmap().apply { allocN32Pixels(larguraAlvo, alturaAlvo) }
    Canvas(destino).use { pincel ->
        pincel.drawImageRect(
            atual,
            Rect.makeWH(atual.width.toFloat(), atual.height.toFloat()),
            Rect.makeWH(larguraAlvo.toFloat(), alturaAlvo.toFloat()),
            SamplingMode.MITCHELL,
            null,
            true,
        )
    }
    minhaImagem?.close()
    minhaVez?.close()
    destino.setImmutable()
    return destino
}

class DecodificadorNitido(
    private val fonte: ImageSource,
    private val opcoes: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val bytes = fonte.source().use { it.readByteArray() }
        val original = SkiaImage.makeFromEncoded(bytes)
        val larguraPedida = opcoes.size.width.pxOrElse { original.width }
        val alturaPedida = opcoes.size.height.pxOrElse { original.height }

        val fator = minOf(
            larguraPedida.toDouble() / original.width,
            alturaPedida.toDouble() / original.height,
        )
        if (fator >= 1.0 || fator <= 0.0) {
            val copia = Bitmap.makeFromImage(original).apply { setImmutable() }
            original.close()
            return DecodeResult(image = copia.asImage(), isSampled = false)
        }

        val largura = (original.width * fator).roundToInt().coerceAtLeast(1)
        val altura = (original.height * fator).roundToInt().coerceAtLeast(1)
        val reduzido = reduzirEmEtapas(original, largura, altura)
        original.close()
        return DecodeResult(image = reduzido.asImage(), isSampled = true)
    }

    class Fabrica : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder = DecodificadorNitido(result.source, options)
    }
}
