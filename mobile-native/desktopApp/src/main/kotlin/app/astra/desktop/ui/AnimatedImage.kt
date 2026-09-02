package app.astra.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.astra.shared.AstraShared
import androidx.compose.ui.draw.drawBehind
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Image as SkiaImage
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import java.util.Base64
import kotlin.math.roundToInt

private data class AnimatedFrames(
    val frames: List<ImageBitmap>,
    val durationsMs: List<Int>,
    val width: Int,
    val height: Int,
) {
    val bytes: Long get() = width.toLong() * height * 4 * frames.size
}

@Composable
fun AstraImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    blurhash: String? = null,
    proporcaoBlur: Float = 1.5f,
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
        val indice = remember(a) { mutableIntStateOf(0) }
        if (!reduce && a.frames.size > 1) {
            LaunchedEffect(a) {
                var i = 0
                while (true) {
                    delay(a.durationsMs[i].coerceAtLeast(20).toLong())
                    i = (i + 1) % a.frames.size
                    indice.intValue = i
                }
            }
        }
        Image(
            painter = remember(a) { QuadrosPainter(a.frames, indice) },
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
        )
    } else {
        val borrao by lembrarBlurhash(blurhash, proporcaoBlur)
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier.drawBehind {
                val b = borrao ?: return@drawBehind
                drawImage(
                    image = b,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(b.width, b.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
            },
            alignment = alignment,
            contentScale = contentScale,
        )
    }
}

private class QuadrosPainter(
    private val quadros: List<ImageBitmap>,
    private val indice: IntState,
) : Painter() {
    override val intrinsicSize: Size =
        if (quadros.isEmpty()) Size.Unspecified
        else Size(quadros[0].width.toFloat(), quadros[0].height.toFloat())

    override fun DrawScope.onDraw() {
        if (quadros.isEmpty() || size.width <= 0f || size.height <= 0f) return
        val bmp = quadros[indice.intValue.coerceIn(0, quadros.lastIndex)]
        drawImage(
            image = bmp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bmp.width, bmp.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }
}

private fun mightAnimate(url: String): Boolean {
    if (url.startsWith("data:")) {
        val head = url.substringBefore(',').lowercase()
        return "image/gif" in head || "image/webp" in head
    }
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return path.endsWith(".gif") || path.endsWith(".webp")
}

private const val ANIM_MAX_DIM = 1024

private fun decodeAnimated(bytes: ByteArray): AnimatedFrames? = runCatching {
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    val count = codec.frameCount
    if (count <= 1) return null
    val info = codec.imageInfo
    val w = info.width
    val h = info.height
    if (w <= 0 || h <= 0) return null
    val scale = minOf(1f, ANIM_MAX_DIM.toFloat() / maxOf(w, h))
    val tw = (w * scale).toInt().coerceAtLeast(1)
    val th = (h * scale).toInt().coerceAtLeast(1)
    val perFrame = tw.toLong() * th * 4
    val maxFrames = (48L * 1024 * 1024 / perFrame).toInt()
    if (maxFrames < 2) return null
    val n = minOf(count, maxFrames)
    val fi = codec.framesInfo
    val bmp = Bitmap().apply { allocPixels(info) }
    val surface = if (scale < 1f) Surface.makeRasterN32Premul(tw, th) else null
    val out = ArrayList<ImageBitmap>(n)
    val durs = ArrayList<Int>(n)
    for (i in 0 until n) {
        codec.readPixels(bmp, i)
        val full = SkiaImage.makeFromBitmap(bmp)
        if (surface == null) {
            out += full.toComposeImageBitmap()
        } else {
            surface.canvas.clear(0)
            surface.canvas.drawImageRect(full, Rect.makeWH(tw.toFloat(), th.toFloat()))
            runCatching { full.close() }
            out += surface.makeImageSnapshot().toComposeImageBitmap()
        }
        val d = fi.getOrNull(i)?.duration ?: 100
        durs += if (d <= 0) 100 else d
    }
    runCatching { bmp.close() }
    runCatching { surface?.close() }
    AnimatedFrames(out, durs, tw, th)
}.getOrNull()

private const val ANIM_CACHE_BYTES = 48L * 1024 * 1024

private object AnimatedImageStore {
    private val lock = Any()

    private val cache = LinkedHashMap<String, AnimatedFrames>(16, 0.75f, true)
    private var cacheBytes = 0L

    private fun trimLocked() {
        if (cacheBytes <= ANIM_CACHE_BYTES) return
        val it = cache.entries.iterator()
        while (it.hasNext() && cacheBytes > ANIM_CACHE_BYTES) {
            cacheBytes -= it.next().value.bytes
            it.remove()
        }
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
            if (frames == null) {
                staticKeys[url] = true
            } else {
                cache.put(url, frames)?.let { cacheBytes -= it.bytes }
                cacheBytes += frames.bytes
                trimLocked()
            }
        }
        return frames
    }

    private val plain by lazy { OkHttpClient() }

    private fun fetchBytes(url: String): ByteArray? {
        if (url.startsWith("data:")) {
            val i = url.indexOf("base64,")
            if (i < 0) return null
            return runCatching { Base64.getDecoder().decode(url.substring(i + 7)) }.getOrNull()
        }
        val abs = if (url.startsWith("/")) AstraShared.BASE_URL.trimEnd('/') + url else url
        return doDiscoDoCoil(abs) ?: doDiscoDoCoil(url) ?: get(http, abs) ?: get(plain, abs)
    }

    private fun doDiscoDoCoil(chave: String): ByteArray? = runCatching {
        val disco = SingletonImageLoader.get(PlatformContext.INSTANCE).diskCache ?: return null
        disco.openSnapshot(chave)?.use { guardado ->
            disco.fileSystem.read(guardado.data) { readByteArray() }
        }
    }.getOrNull()

    private fun get(client: OkHttpClient, url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.bytes() else null
        }
    }.getOrNull()
}
