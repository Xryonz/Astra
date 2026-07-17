package app.astra.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.astra.shared.AstraShared
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image as SkiaImage
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import java.util.Base64

// Imagens ANIMADAS no desktop (GIF, WebP animado). O Coil3 no JVM so decodifica o
// PRIMEIRO frame (nao existe coil-gif-jvm nem AnimatedSkiaImageDecoder no JVM —
// coil-gif so publica variant Android). Entao a animacao vem daqui: decodifica os
// frames na mao com o Codec do Skiko (que ja vem junto do Compose Desktop) e roda
// um loop de frames no Compose. Estatico continua no Coil (sem regressao).
//
// AstraImage e drop-in do AsyncImage: enquanto nao sabe se anima (ou se e estatico)
// mostra o Coil — que ja pinta o 1o frame do gif, entao a troca pro animado nao
// pisca. So tenta decodificar formatos que PODEM animar (gif/webp) pra nao baixar
// duas vezes cada foto estatica.

private data class AnimatedFrames(
    val frames: List<ImageBitmap>,
    val durationsMs: List<Int>,
    val width: Int,
    val height: Int,
)

@Composable
fun AstraImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val reduce = LocalReduceMotion.current
    var anim by remember(url) { mutableStateOf(url?.let { AnimatedImageStore.cached(it) }) }

    LaunchedEffect(url) {
        if (anim != null || url.isNullOrBlank()) return@LaunchedEffect
        if (!mightAnimate(url) || AnimatedImageStore.isKnownStatic(url)) return@LaunchedEffect
        val frames = withContext(Dispatchers.IO) { AnimatedImageStore.loadOrDecode(url) }
        if (frames != null) anim = frames
    }

    val a = anim
    if (a != null && a.frames.isNotEmpty()) {
        // Reduzir movimento: congela no 1o frame (ainda mostra o gif, so nao mexe).
        var idx by remember(a) { mutableStateOf(0) }
        if (!reduce && a.frames.size > 1) {
            LaunchedEffect(a) {
                var i = 0
                while (true) {
                    delay(a.durationsMs[i].coerceAtLeast(20).toLong())
                    i = (i + 1) % a.frames.size
                    idx = i
                }
            }
        }
        Image(
            bitmap = a.frames[idx.coerceIn(0, a.frames.lastIndex)],
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

// So gif/webp podem animar; png/jpeg nunca (evita baixar+decodificar toda foto).
private fun mightAnimate(url: String): Boolean {
    if (url.startsWith("data:")) {
        val head = url.substringBefore(',').lowercase()
        return "image/gif" in head || "image/webp" in head
    }
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return path.endsWith(".gif") || path.endsWith(".webp")
}

// Decodifica os bytes em frames via Skiko. null = 1 frame so (estatico -> Coil),
// nao decodificou, ou grande demais pro teto de memoria.
private fun decodeAnimated(bytes: ByteArray): AnimatedFrames? = runCatching {
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    val count = codec.frameCount
    if (count <= 1) return null // estatico: deixa o Coil pintar
    val info = codec.imageInfo
    val w = info.width
    val h = info.height
    if (w <= 0 || h <= 0) return null
    // Teto por imagem (~24MB de bitmaps): imagem gigante nao vira animacao (cai no
    // Coil estatico) pra nao estourar a RAM.
    val perFrame = w.toLong() * h * 4
    val maxFrames = (24L * 1024 * 1024 / perFrame).toInt()
    if (maxFrames < 2) return null
    val n = minOf(count, maxFrames)
    val fi = codec.framesInfo
    val bmp = Bitmap().apply { allocPixels(info) }
    val out = ArrayList<ImageBitmap>(n)
    val durs = ArrayList<Int>(n)
    for (i in 0 until n) {
        // Sequencial: o bitmap ja carrega o frame i-1, cobrindo o disposal comum
        // (requiredFrame == i-1). makeFromBitmap copia (bitmap e mutavel) -> cada
        // frame vira um snapshot independente.
        codec.readPixels(bmp, i)
        out += SkiaImage.makeFromBitmap(bmp).toComposeImageBitmap()
        val d = fi.getOrNull(i)?.duration ?: 100
        durs += if (d <= 0) 100 else d // GIF com 0ms -> 100ms (o que os browsers fazem)
    }
    runCatching { bmp.close() }
    AnimatedFrames(out, durs, w, h)
}.getOrNull()

// Cache de frames decodificados (LRU por contagem — cada gif custa uns MB). Guarda
// tambem as URLs que deram ESTATICO, pra nao baixar/decodificar de novo a cada
// scroll. Bytes vem por data-uri (inline), /uploads (base + path) ou http direto.
private object AnimatedImageStore {
    private val lock = Any()

    private val cache = object : LinkedHashMap<String, AnimatedFrames>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AnimatedFrames>) = size > 12
    }
    private val staticKeys = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > 512
    }

    private val http by lazy { GlobalContext.get().get<OkHttpClient>(named("authed")) }

    fun cached(url: String): AnimatedFrames? = synchronized(lock) { cache[url] }
    fun isKnownStatic(url: String): Boolean = synchronized(lock) { staticKeys.containsKey(url) }

    suspend fun loadOrDecode(url: String): AnimatedFrames? {
        synchronized(lock) {
            cache[url]?.let { return it }
            if (staticKeys.containsKey(url)) return null
        }
        val bytes = fetchBytes(url) ?: return null
        val frames = decodeAnimated(bytes)
        synchronized(lock) {
            if (frames == null) staticKeys[url] = true else cache[url] = frames
        }
        return frames
    }

    private fun fetchBytes(url: String): ByteArray? {
        if (url.startsWith("data:")) {
            val i = url.indexOf("base64,")
            if (i < 0) return null
            return runCatching { Base64.getDecoder().decode(url.substring(i + 7)) }.getOrNull()
        }
        val abs = if (url.startsWith("/")) AstraShared.BASE_URL.trimEnd('/') + url else url
        return runCatching {
            http.newCall(Request.Builder().url(abs).build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        }.getOrNull()
    }
}
