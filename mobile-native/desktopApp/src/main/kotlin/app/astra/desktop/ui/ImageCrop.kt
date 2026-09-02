package app.astra.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.Obsidian
import app.astra.shared.AstraShared
import app.astra.desktop.ui.theme.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import java.io.File
import java.util.Base64
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Rect as SkRect
import app.astra.desktop.ui.theme.Tipo

sealed interface CropSource {
    data class Local(val file: File) : CropSource
    data class Remote(val url: String) : CropSource
}

class CropImage(val img: SkiaImage, val hasAlpha: Boolean) {
    val w: Int get() = img.width
    val h: Int get() = img.height
    fun close() { runCatching { img.close() } }
}

object ImageCrop {
    const val BANNER_OUT_W = 2560
    const val AVATAR_OUT_W = 1024

    private const val SRC_MAX = 3200
    private const val HARD_MAX = 10_000_000

    private val http by lazy { GlobalContext.get().get<OkHttpClient>(named("authed")) }
    private val plain by lazy { OkHttpClient() }

    fun isAnimated(file: File): Boolean = runCatching {
        val codec = Codec.makeFromData(Data.makeFromBytes(file.readBytes()))
        val n = codec.frameCount
        runCatching { codec.close() }
        n > 1
    }.getOrDefault(false)

    fun isAnimated(url: String?): Boolean {
        val u = url ?: return false
        if (u.startsWith("data:")) return "image/gif" in u.substringBefore(',').lowercase()
        return u.substringBefore('?').substringBefore('#').lowercase().endsWith(".gif")
    }

    fun load(source: CropSource): Result<CropImage> = runCatching {
        val bytes = when (source) {
            is CropSource.Local -> source.file.readBytes()
            is CropSource.Remote -> fetch(source.url)
                ?: error("a imagem salva está num formato que não sei ler")
        }
        decode(bytes)
    }

    fun bake(src: CropImage, sx: Float, sy: Float, sw: Float, sh: Float, outW: Int): Result<String> = runCatching {
        require(sw > 0f && sh > 0f) { "recorte vazio" }
        val larguraFinal = min(outW, max(1, sw.roundToInt()))
        val outH = max(1, (larguraFinal * (sh / sw)).roundToInt())

        val ix0 = floor(sx).coerceIn(0f, src.w.toFloat())
        val iy0 = floor(sy).coerceIn(0f, src.h.toFloat())
        val ix1 = ceil(sx + sw).coerceIn(0f, src.w.toFloat())
        val iy1 = ceil(sy + sh).coerceIn(0f, src.h.toFloat())
        require(ix1 > ix0 && iy1 > iy0) { "recorte fora da imagem" }

        val k = larguraFinal / sw
        val surface = Surface.makeRasterN32Premul(larguraFinal, outH)
        surface.canvas.drawImageRect(
            src.img,
            SkRect.makeLTRB(ix0, iy0, ix1, iy1),
            SkRect.makeLTRB((ix0 - sx) * k, (iy0 - sy) * k, (ix1 - sx) * k, (iy1 - sy) * k),
            SamplingMode.MITCHELL,
            null,
            true,
        )
        val out = surface.makeImageSnapshot()

        val gap = ix0 > sx + 0.5f || iy0 > sy + 0.5f || ix1 < sx + sw - 0.5f || iy1 < sy + sh - 0.5f
        val alpha = gap || src.hasAlpha
        val data = out.encodeToData(if (alpha) EncodedImageFormat.PNG else EncodedImageFormat.JPEG, 95)
            ?: error("não foi possível gerar a imagem")
        val bytes = data.bytes
        runCatching { data.close() }
        runCatching { out.close() }
        runCatching { surface.close() }
        require(bytes.size <= HARD_MAX) { "imagem muito grande" }
        "data:${if (alpha) "image/png" else "image/jpeg"};base64," + Base64.getEncoder().encodeToString(bytes)
    }

    private fun decode(bytes: ByteArray): CropImage {
        require(bytes.isNotEmpty()) { "a imagem veio vazia" }
        val img = SkiaImage.makeFromEncoded(bytes)
        val hasAlpha = img.imageInfo.colorInfo.alphaType != ColorAlphaType.OPAQUE
        val m = max(img.width, img.height)
        if (m <= SRC_MAX) return CropImage(img, hasAlpha)
        val s = SRC_MAX.toFloat() / m
        val w = (img.width * s).toInt().coerceAtLeast(1)
        val h = (img.height * s).toInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(
            img,
            SkRect.makeWH(img.width.toFloat(), img.height.toFloat()),
            SkRect.makeWH(w.toFloat(), h.toFloat()),
            SamplingMode.MITCHELL,
            null,
            true,
        )
        val small = surface.makeImageSnapshot()
        runCatching { surface.close() }
        runCatching { img.close() }
        return CropImage(small, hasAlpha)
    }

    private fun fetch(url: String): ByteArray? {
        if (url.startsWith("data:")) {
            val i = url.indexOf("base64,")
            if (i < 0) {
                val corpo = url.substringAfter(',', "")
                return runCatching { java.net.URLDecoder.decode(corpo, "UTF-8").toByteArray(Charsets.ISO_8859_1) }
                    .getOrNull()
            }
            return runCatching { Base64.getDecoder().decode(url.substring(i + 7)) }.getOrNull()
        }
        val abs = absoluteUrl(url)
        val nosso = abs.startsWith(AstraShared.BASE_URL.trimEnd('/'))
        val motivos = mutableListOf<String>()
        if (nosso) get(http, abs, motivos)?.let { return it }
        get(plain, abs, motivos)?.let { return it }
        throw java.io.IOException(motivos.joinToString(" / ").ifBlank { "sem resposta" })
    }

    private fun get(client: OkHttpClient, url: String, motivos: MutableList<String>): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.bytes()
            else { motivos += "HTTP ${resp.code}"; null }
        }
    }.onFailure { motivos += (it.message ?: it::class.simpleName ?: "falhou") }.getOrNull()
}

private val StageW = 324.dp
private val StageH = 210.dp
private val StagePad = 14.dp

private object CropOverlay : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CropDialog(
    source: CropSource,
    aspect: Float,
    round: Boolean,
    title: String,
    outW: Int,
    onApply: (String) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<CropImage?>(null) }
    var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var pct by remember { mutableStateOf(100) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    val d = LocalDensity.current
    val stageW = with(d) { StageW.toPx() }
    val stageH = with(d) { StageH.toPx() }
    val pad = with(d) { StagePad.toPx() }
    val cropW = min(stageW - pad * 2, (stageH - pad * 2) * aspect)
    val cropH = cropW / aspect

    LaunchedEffect(source) {
        val r = withContext(Dispatchers.IO) { ImageCrop.load(source) }
        r.onSuccess { loaded = it; bmp = it.img.toComposeImageBitmap(); pct = 100; pan = Offset.Zero }
            .onFailure { e ->
                val motivo = e.message.orEmpty()
                err = if ("404" in motivo) {
                    "essa imagem não está mais no servidor. escolha o arquivo de novo para subir outra vez."
                } else {
                    "não foi possível ler: " + (motivo.take(120).ifBlank { e::class.simpleName ?: "erro desconhecido" })
                }
            }
    }
    val cur = loaded
    DisposableEffect(cur) { onDispose { cur?.close() } }

    val cover = if (cur == null) 1f else max(cropW / cur.w, cropH / cur.h)
    fun clampPan(p: Offset, z: Float): Offset {
        val i = cur ?: return Offset.Zero
        val lx = abs(i.w * z - cropW) / 2f
        val ly = abs(i.h * z - cropH) / 2f
        return Offset(p.x.coerceIn(-lx, lx), p.y.coerceIn(-ly, ly))
    }

    Popup(
        popupPositionProvider = CropOverlay,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Obsidian.void.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClose() }
                .semCursorDeClique(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(StageW + 36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Obsidian.raised)
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .padding(18.dp),
            ) {
                Text(
                    title,
                    style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "arraste para enquadrar · a roda do mouse aproxima.",
                    style = Tipo.apoio,
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .width(StageW)
                        .height(StageH)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Obsidian.void),
                    contentAlignment = Alignment.Center,
                ) {
                    val img = bmp
                    val i = cur
                    if (img == null || i == null) {
                        Text(
                            err ?: "carregando…",
                            style = TextStyle(
                                color = if (err != null) Obsidian.danger else Obsidian.text3,
                                fontSize = 12.sp,
                            ),
                        )
                    } else {
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .pointerHoverIcon(PointerIcon.Hand)
                                .onPointerEvent(PointerEventType.Scroll) { e ->
                                    val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
                                    pct = (pct + if (dy < 0) 8 else -8).coerceIn(100, 300)
                                    pan = clampPan(pan, cover * pct / 100f)
                                }
                                .pointerInput(i) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        pan = clampPan(pan + drag, cover * pct / 100f)
                                    }
                                },
                        ) {
                            val zoom = cover * pct / 100f
                            val dw = i.w * zoom
                            val dh = i.h * zoom
                            drawImage(
                                image = img,
                                dstOffset = IntOffset(
                                    (size.width / 2f + pan.x - dw / 2f).roundToInt(),
                                    (size.height / 2f + pan.y - dh / 2f).roundToInt(),
                                ),
                                dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
                            )
                            val hole = Rect(
                                Offset((size.width - cropW) / 2f, (size.height - cropH) / 2f),
                                Size(cropW, cropH),
                            )
                            val holePath = Path().apply {
                                if (round) addOval(hole)
                                else addRoundRect(RoundRect(hole, CornerRadius(8.dp.toPx())))
                            }
                            val stagePath = Path().apply { addRect(Rect(Offset.Zero, size)) }
                            drawPath(
                                Path.combine(PathOperation.Difference, stagePath, holePath),
                                Obsidian.void.copy(alpha = 0.68f),
                            )
                            drawPath(
                                holePath,
                                Obsidian.accent.copy(alpha = 0.85f),
                                style = Stroke(1.5.dp.toPx()),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                CropZoomTrack(pct) { pct = it; pan = clampPan(pan, cover * it / 100f) }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CropButton("cancelar", accent = false) { onClose() }
                    CropButton(if (busy) "aplicando…" else "aplicar", accent = true) {
                        val i = cur ?: return@CropButton
                        if (busy) return@CropButton
                        busy = true
                        val zoom = cover * pct / 100f
                        val sx = i.w / 2f - (cropW / 2f + pan.x) / zoom
                        val sy = i.h / 2f - (cropH / 2f + pan.y) / zoom
                        val sw = cropW / zoom
                        val sh = cropH / zoom
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { ImageCrop.bake(i, sx, sy, sw, sh, outW) }
                            busy = false
                            r.onSuccess { onApply(it); onClose() }
                                .onFailure { err = "não foi possível recortar essa imagem" }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropZoomTrack(pct: Int, onChange: (Int) -> Unit) {
    val f = ((pct - 100) / 200f).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("zoom", style = Tipo.apoio, modifier = Modifier.width(42.dp))
        Box(
            Modifier
                .weight(1f)
                .height(22.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val x = (change.position.x / size.width).coerceIn(0f, 1f)
                        onChange((100 + x * 200).toInt())
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Obsidian.void.copy(alpha = 0.6f)))
            Box(Modifier.fillMaxWidth(f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Obsidian.accent))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (f > 0f) Spacer(Modifier.weight(f))
                Box(Modifier.size(14.dp).clip(CircleShape).background(Obsidian.accent).border(2.dp, Obsidian.raised, CircleShape))
                if (f < 1f) Spacer(Modifier.weight(1f - f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("$pct%", style = TextStyle(color = Obsidian.text2, fontSize = 11.sp))
    }
}

@Composable
private fun CropButton(label: String, accent: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(color = if (accent) Obsidian.accent else Obsidian.text2, fontSize = 13.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (accent) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
