package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import kotlin.math.cos
import kotlin.math.pow

private const val ALFABETO =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

private const val LARGURA_DECODE = 32

private fun decodificar83(s: String): Int {
    var valor = 0
    for (c in s) {
        val i = ALFABETO.indexOf(c)
        if (i < 0) return -1
        valor = valor * 83 + i
    }
    return valor
}

private fun paraLinear(v: Int): Float {
    val f = v / 255f
    return if (f <= 0.04045f) f / 12.92f else ((f + 0.055f) / 1.055f).pow(2.4f)
}

private fun paraSrgb(v: Float): Int {
    val f = v.coerceIn(0f, 1f)
    val s = if (f <= 0.0031308f) f * 12.92f else 1.055f * f.pow(1f / 2.4f) - 0.055f
    return (s * 255f + 0.5f).toInt().coerceIn(0, 255)
}

private fun sinalPow(v: Float, exp: Float): Float =
    (if (v < 0) -1f else 1f) * kotlin.math.abs(v).pow(exp)

fun decodificarBlurhash(hash: String, proporcao: Float): ImageBitmap? {
    if (hash.length < 6) return null

    val bandeira = decodificar83(hash.substring(0, 1))
    if (bandeira < 0) return null
    val numX = bandeira % 9 + 1
    val numY = bandeira / 9 + 1
    if (hash.length != 4 + 2 * numX * numY) return null

    val maxQuant = decodificar83(hash.substring(1, 2))
    if (maxQuant < 0) return null
    val maximo = (maxQuant + 1) / 166f

    val cores = Array(numX * numY) { FloatArray(3) }

    val dc = decodificar83(hash.substring(2, 6))
    if (dc < 0) return null
    cores[0][0] = paraLinear(dc shr 16)
    cores[0][1] = paraLinear((dc shr 8) and 255)
    cores[0][2] = paraLinear(dc and 255)

    for (i in 1 until numX * numY) {
        val ac = decodificar83(hash.substring(4 + i * 2, 6 + i * 2))
        if (ac < 0) return null
        cores[i][0] = sinalPow((ac / (19 * 19) - 9) / 9f, 2f) * maximo
        cores[i][1] = sinalPow(((ac / 19) % 19 - 9) / 9f, 2f) * maximo
        cores[i][2] = sinalPow((ac % 19 - 9) / 9f, 2f) * maximo
    }

    val larg = LARGURA_DECODE
    val alt = (larg / proporcao.coerceIn(0.2f, 5f)).toInt().coerceIn(4, 96)

    val cosX = Array(larg) { x -> FloatArray(numX) { i -> cos(Math.PI * x * i / larg).toFloat() } }
    val cosY = Array(alt) { y -> FloatArray(numY) { j -> cos(Math.PI * y * j / alt).toFloat() } }

    val pixels = ByteArray(larg * alt * 4)
    for (y in 0 until alt) {
        for (x in 0 until larg) {
            var r = 0f; var g = 0f; var b = 0f
            for (j in 0 until numY) {
                val cy = cosY[y][j]
                for (i in 0 until numX) {
                    val base = cosX[x][i] * cy
                    val c = cores[i + j * numX]
                    r += c[0] * base; g += c[1] * base; b += c[2] * base
                }
            }
            val p = (y * larg + x) * 4
            pixels[p]     = paraSrgb(b).toByte()
            pixels[p + 1] = paraSrgb(g).toByte()
            pixels[p + 2] = paraSrgb(r).toByte()
            pixels[p + 3] = 255.toByte()
        }
    }

    return SkiaImage.makeRaster(
        imageInfo = ImageInfo.makeS32(larg, alt, ColorAlphaType.OPAQUE),
        bytes = pixels,
        rowBytes = larg * 4,
    ).toComposeImageBitmap()
}

private const val TETO_CACHE = 64
private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, ImageBitmap>) = size > TETO_CACHE
}

@Composable
fun lembrarBlurhash(hash: String?, proporcao: Float): State<ImageBitmap?> {
    val estado = remember(hash) { mutableStateOf(hash?.let { synchronized(cache) { cache[it] } }) }
    LaunchedEffect(hash, proporcao) {
        if (hash.isNullOrBlank() || estado.value != null) return@LaunchedEffect
        val bmp = withContext(Dispatchers.Default) { runCatching { decodificarBlurhash(hash, proporcao) }.getOrNull() }
        if (bmp != null) {
            synchronized(cache) { cache[hash] = bmp }
            estado.value = bmp
        }
    }
    return estado
}
