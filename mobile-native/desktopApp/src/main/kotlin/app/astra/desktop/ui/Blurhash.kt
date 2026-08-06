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

// BLURHASH — as cores borradas da imagem, em ~30 bytes.
//
// O servidor JA calculava isto em todo upload (routes/upload.ts) e o desktop nunca
// usou: a conta era feita, o dado viajava, e a tela mostrava um buraco cinza
// esperando a foto. Aqui ele vira pixel.
//
// Nao ha biblioteca de blurhash no JVM, entao a decodificacao e na mao. O formato e
// fechado e minusculo — 83 caracteres de alfabeto, uma DCT de no maximo 9x9
// componentes — entao "na mao" aqui sao 80 linhas, nao uma aventura.
//
// Decodifica em 32px de largura DE PROPOSITO: o resultado e um borrao, e borrao em
// alta resolucao e desperdicio. A GPU estica com filtro bilinear e o resultado e
// exatamente o degrade suave que se quer.

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

// sRGB -> linear e volta. Sem isso as cores saem lavadas: a media tem que ser feita
// no espaco linear, nao no que a tela mostra.
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

/** Decodifica o hash num ImageBitmap pequeno. Devolve null se o hash for invalido. */
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

    // Os cossenos em x e em y sao SEPARAVEIS: pre-calcular as duas tabelas troca
    // larg*alt*numX*numY chamadas de cos() por (larg*numX + alt*numY). Num anexo
    // 32x24 com 4x3 componentes isso e ~9000 cossenos virando ~200.
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
            // BGRA: e o layout que o Skia usa no ImageInfo abaixo.
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

// Cache pequeno: rolar o chat pra cima e pra baixo repassaria pelos mesmos anexos, e
// decodificar de novo a cada volta seria trabalho puro. 64 entradas cobrem uma tela
// cheia de imagens com folga; cada uma custa ~4 KB.
private const val TETO_CACHE = 64
private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, ImageBitmap>) = size > TETO_CACHE
}

@Composable
fun lembrarBlurhash(hash: String?, proporcao: Float): State<ImageBitmap?> {
    val estado = remember(hash) { mutableStateOf(hash?.let { synchronized(cache) { cache[it] } }) }
    LaunchedEffect(hash, proporcao) {
        if (hash.isNullOrBlank() || estado.value != null) return@LaunchedEffect
        // Fora da thread de UI: sao ~1000 pixels com uma somatoria cada, barato mas
        // nao de graca, e travar um quadro pra desenhar um borrao seria irônico.
        val bmp = withContext(Dispatchers.Default) { runCatching { decodificarBlurhash(hash, proporcao) }.getOrNull() }
        if (bmp != null) {
            synchronized(cache) { cache[hash] = bmp }
            estado.value = bmp
        }
    }
    return estado
}
