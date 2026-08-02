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

// Imagens ANIMADAS no desktop (GIF, WebP animado). O Coil3 no JVM so decodifica o
// PRIMEIRO frame (não existe coil-gif-jvm nem AnimatedSkiaImageDecoder no JVM —
// coil-gif so pública variant Android). Entao a animação vem daqui: decodifica os
// frames na mao com o Codec do Skiko (que já vem junto do Compose Desktop) e roda
// um loop de frames no Compose. Estatico continua no Coil (sem regressao).
//
// AstraImage e drop-in do AsyncImage: enquanto não sabe se anima (ou se e estatico)
// mostra o Coil — que já pinta o 1o frame do gif, entao a troca pro animado não
// pisca. So tenta decodificar formatos que PODEM animar (gif/webp) pra não baixar
// duas vezes cada foto estatica.

private data class AnimatedFrames(
    val frames: List<ImageBitmap>,
    val durationsMs: List<Int>,
    val width: Int,
    val height: Int,
) {
    // Custo real em memoria (4 bytes por pixel por frame) — e o que o cache soma
    // pra respeitar o teto global.
    val bytes: Long get() = width.toLong() * height * 4 * frames.size
}

@Composable
fun AstraImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    // Enquadramento (banner do perfil usa BiasAlignment pra posição vertical).
    alignment: Alignment = Alignment.Center,
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
        // O NUMERO DO QUADRO NAO PODE SER LIDO AQUI EM CIMA.
        //
        // Antes era `Image(bitmap = a.frames[idx])`, com o `idx` lido no corpo do
        // composable. Ler um State na composicao significa: mudou o State,
        // recompoe e REMEDE o no inteiro. A ~15 quadros por segundo. Por imagem.
        // No grid do seletor de GIF, com uma duzia animando ao mesmo tempo, isso
        // e a interface inteira sendo remontada centenas de vezes por segundo pra
        // trocar uns pixels.
        //
        // Agora o quadro e lido DENTRO do desenho (QuadrosPainter.onDraw): mudou o
        // quadro, so redesenha. Mesmo conserto que tirou a travada do video de
        // chamada na 0.1.26.
        //
        // Por que Painter e nao Canvas: o Painter tem tamanho intrinseco, entao o
        // `Image` continua medindo e enquadrando exatamente como antes —
        // contentScale e alignment seguem funcionando de graca. Um Canvas nao tem
        // tamanho proprio e obrigaria a refazer Crop/Fit na mao em 8 lugares.
        //
        // Reduzir movimento: congela no 1o quadro (ainda mostra o gif, so não mexe).
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
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
        )
    }
}

// Pinta o quadro ATUAL de uma animacao, lendo o indice so na hora de desenhar.
//
// O tamanho intrinseco vem do primeiro quadro (todos tem o mesmo) — e o que faz o
// `Image` medir e enquadrar igualzinho a antes. Como ele nunca muda, trocar de
// quadro nao invalida layout nenhum: so o desenho.
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

// So gif/webp podem animar; png/jpeg nunca (evita baixar+decodificar toda foto).
private fun mightAnimate(url: String): Boolean {
    if (url.startsWith("data:")) {
        val head = url.substringBefore(',').lowercase()
        return "image/gif" in head || "image/webp" in head
    }
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return path.endsWith(".gif") || path.endsWith(".webp")
}

// Maior lado que um frame animado guarda em memoria. Banner/avatar nunca sao
// desenhados perto disso, entao reduzir não tira qualidade visivel — e e o que faz
// gif GRANDE continuar animando: na resolucao original, um gif de banner (ex.
// 1920x1080 = 8MB/frame) estourava o teto e virava "estatico" pra sempre.
private const val ANIM_MAX_DIM = 1024

// Decodifica os bytes em frames via Skiko, REDUZINDO frames grandes. null = 1 frame
// so (estatico -> Coil) ou não decodificou.
private fun decodeAnimated(bytes: ByteArray): AnimatedFrames? = runCatching {
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    val count = codec.frameCount
    if (count <= 1) return null // estatico: deixa o Coil pintar
    val info = codec.imageInfo
    val w = info.width
    val h = info.height
    if (w <= 0 || h <= 0) return null
    // Alvo depois da reducao (mantem proporcao). scale < 1 => desenha reduzido.
    val scale = minOf(1f, ANIM_MAX_DIM.toFloat() / maxOf(w, h))
    val tw = (w * scale).toInt().coerceAtLeast(1)
    val th = (h * scale).toInt().coerceAtLeast(1)
    // Teto de memoria (~48MB de bitmaps) contado JA no tamanho reduzido.
    val perFrame = tw.toLong() * th * 4
    val maxFrames = (48L * 1024 * 1024 / perFrame).toInt()
    if (maxFrames < 2) return null
    val n = minOf(count, maxFrames)
    val fi = codec.framesInfo
    val bmp = Bitmap().apply { allocPixels(info) }
    // So aloca a superficie de reducao quando ha o que reduzir.
    val surface = if (scale < 1f) Surface.makeRasterN32Premul(tw, th) else null
    val out = ArrayList<ImageBitmap>(n)
    val durs = ArrayList<Int>(n)
    for (i in 0 until n) {
        // Sequencial: o bitmap já carrega o frame i-1, cobrindo o disposal comum
        // (requiredFrame == i-1). makeFromBitmap copia (bitmap e mutavel) -> cada
        // frame vira um snapshot independente.
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
        durs += if (d <= 0) 100 else d // GIF com 0ms -> 100ms (o que os browsers fazem)
    }
    runCatching { bmp.close() }
    runCatching { surface?.close() }
    AnimatedFrames(out, durs, tw, th)
}.getOrNull()

// Cache de frames decodificados. O LRU e por BYTES, não por contagem: contando
// itens (12) com um teto de 48MB CADA, o pior caso eram ~576MB de frames vivos —
// o app inchava sozinho conforme você passava por avatares/banners animados.
// Agora o teto e GLOBAL e a conta e fechada: nunca passa de ANIM_CACHE_BYTES,
// independente de quantos gifs aparecerem.
private const val ANIM_CACHE_BYTES = 48L * 1024 * 1024

// Guarda também as URLs que deram ESTATICO, pra não baixar/decodificar de novo a
// cada scroll. Bytes vem por data-uri (inline), /uploads (base + path) ou http.
private object AnimatedImageStore {
    private val lock = Any()

    // accessOrder=true -> o primeiro da iteracao e o MENOS usado recentemente.
    private val cache = LinkedHashMap<String, AnimatedFrames>(16, 0.75f, true)
    private var cacheBytes = 0L

    // Chamado sob o lock: joga fora os menos usados ate caber no teto.
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
                cache.put(url, frames)?.let { cacheBytes -= it.bytes } // trocou: tira o antigo
                cacheBytes += frames.bytes
                trimLocked()
            }
        }
        return frames
    }

    // Cliente SEM auth pro fallback: quando o banner mora num CDN/R2 publico, mandar
    // o header Authorization pode ser recusado (o endpoint tenta interpretar como
    // assinatura) -> o fetch falhava, a animação nunca era decodificada e sobrava o
    // 1o frame do Coil. Era o "gif anima, ai reinicio o Astra e fica parado": antes de
    // salvar a imagem e data-uri (decodifica local), depois vira URL e passava por aqui.
    private val plain by lazy { OkHttpClient() }

    private fun fetchBytes(url: String): ByteArray? {
        if (url.startsWith("data:")) {
            val i = url.indexOf("base64,")
            if (i < 0) return null
            return runCatching { Base64.getDecoder().decode(url.substring(i + 7)) }.getOrNull()
        }
        val abs = if (url.startsWith("/")) AstraShared.BASE_URL.trimEnd('/') + url else url
        // Tenta autenticado (uploads do proprio backend exigem) e, se falhar, sem auth.
        return get(http, abs) ?: get(plain, abs)
    }

    private fun get(client: OkHttpClient, url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.bytes() else null
        }
    }.getOrNull()
}
