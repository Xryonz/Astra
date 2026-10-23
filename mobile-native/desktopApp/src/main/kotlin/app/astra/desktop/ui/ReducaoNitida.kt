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
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Image as SkiaImage
import kotlin.math.roundToInt

private val AMOSTRAGEM_COM_MIPMAPS = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

fun reduzirComMipmaps(origem: SkiaImage, larguraAlvo: Int, alturaAlvo: Int): Bitmap {
    val destino = Bitmap().apply { allocN32Pixels(larguraAlvo, alturaAlvo) }
    checkNotNull(destino.peekPixels()).use { pixels ->
        check(origem.scalePixels(pixels, AMOSTRAGEM_COM_MIPMAPS, false))
    }
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
        val reduzido = reduzirComMipmaps(original, largura, altura)
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
