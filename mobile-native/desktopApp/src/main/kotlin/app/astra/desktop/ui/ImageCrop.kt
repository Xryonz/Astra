package app.astra.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

// Recorte de imagem no estilo do Discord: em vez de guardar posição/zoom em
// metadado (e cada tela recortar de um jeito), o enquadramento e ASSADO na
// imagem que sobe. O modal mostra a imagem INTEIRA com uma máscara escurecida em
// volta da janela de corte; arrastar move nos dois eixos, a roda do mouse da
// zoom, e "aplicar" gera um data-uri já na proporcao final.
//
// Consequencia boa: quem exibe não precisa saber de nada — o banner assado em
// ProfileBannerAspect cai perfeito no ProfileBanner (Fit + posição 50 + 100%).
// Tradeoff (o Discord tem o mesmo): assar PERDE o original, entao reenquadrar
// depois recorta o recorte. Pra voltar atras, subir a imagem de novo.
//
// LIMITE HONESTO: gif/webp ANIMADO não pode ser assado (recortar exigiria
// recodificar a animação). Quem chama testa `isAnimated` e manda o animado pro
// caminho antigo (posição+zoom em metadado, animação preservada).

// De onde vem a imagem: arquivo recem-escolhido ou url/data-uri já salva.
sealed interface CropSource {
    data class Local(val file: File) : CropSource
    data class Remote(val url: String) : CropSource
}

// Imagem carregada pro recorte. Skia (e não ImageIO) porque o backend guarda
// PNG/JPEG convertidos em WebP — e o ImageIO do JDK não le WebP.
class CropImage(val img: SkiaImage, val hasAlpha: Boolean) {
    val w: Int get() = img.width
    val h: Int get() = img.height
    fun close() { runCatching { img.close() } }
}

object ImageCrop {
    // DOBRO DA TELA, de proposito. Estes numeros sao a resolucao que FICA salva —
    // depois deles nao existe volta, entao errar pra baixo e irreversivel.
    //
    // Eram 1280/512, calculados como "o tamanho que a tela desenha". A conta
    // esquecia a densidade: no Windows a 150% (o padrao de fabrica em quase todo
    // notebook novo) um banner de 840dp pede 1260 pixeis FISICOS, e o de 1280
    // chegava sem folga nenhuma; a 175% ou num monitor 4K ele era ESTICADO. Era
    // isso o "pixelado" — a imagem sendo ampliada na hora de desenhar.
    //
    // O dobro cobre 200% de escala sem nunca ampliar, que e o teto que o Windows
    // oferece. Custo: o arquivo salvo fica ~3x maior (a area quadruplica, o WebP
    // do servidor come a maior parte). Isso e espaco em bucket, nao memoria de
    // quem usa — o Coil3 decodifica no tamanho que vai desenhar, nao no do arquivo.
    const val BANNER_OUT_W = 2560
    const val AVATAR_OUT_W = 1024

    // Teto da FONTE em memoria enquanto o modal esta aberto. Precisa ficar ACIMA
    // do maior destino (2560), senao a fonte ja chega reduzida e o recorte so
    // reamplia o que foi jogado fora. 3200 da folga pra um zoom leve e mantem o
    // pior caso em ~41MB de raster (x2, porque o preview guarda a copia dele).
    private const val SRC_MAX = 3200
    // Teto do data-uri que sobe. Subiu junto com a resolucao: um banner 2560 com
    // transparencia sai em PNG e passa folgado dos 5MB antigos. O servidor aceita
    // 10MB nas rotas de perfil/constelacao (ver index.ts).
    private const val HARD_MAX = 10_000_000

    private val http by lazy { GlobalContext.get().get<OkHttpClient>(named("authed")) }
    private val plain by lazy { OkHttpClient() }

    // Animado por CONTAGEM DE FRAMES (exato) — usado no momento do upload.
    fun isAnimated(file: File): Boolean = runCatching {
        val codec = Codec.makeFromData(Data.makeFromBytes(file.readBytes()))
        val n = codec.frameCount
        runCatching { codec.close() }
        n > 1
    }.getOrDefault(false)

    // Animado por PISTA (url já salva): so gif, porque o backend converte
    // PNG/JPEG em .webp — tratar webp como animado tiraria o reenquadrar de quase
    // todo banner salvo. Webp animado cai no recorte e perde a animação (raro, e
    // o preco de não baixar a imagem so pra decidir).
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

    // Recorta o retangulo (sx,sy,sw,sh) EM PIXEIS DA FONTE e devolve data-uri com
    // `outW` de largura. A proporcao da saida sai de sh/sw, entao quem chama so
    // precisa manter a janela de corte na proporcao certa.
    fun bake(src: CropImage, sx: Float, sy: Float, sw: Float, sh: Float, outW: Int): Result<String> = runCatching {
        require(sw > 0f && sh > 0f) { "recorte vazio" }
        // NUNCA AMPLIAR NA HORA DE ASSAR. Se o pedaco escolhido tem menos pixeis
        // que o destino, esticar ate `outW` nao inventa detalhe — so assa borrao
        // no arquivo e multiplica o peso. Salvar no tamanho real da o MESMO
        // resultado na tela (quem desenha amplia de qualquer jeito, com filtro) e
        // um arquivo menor. Acontece com fonte pequena ou zoom alto.
        val larguraFinal = min(outW, max(1, sw.roundToInt()))
        val outH = max(1, (larguraFinal * (sh / sw)).roundToInt())

        // Interseção do recorte com a imagem: o pedaço pedido pode passar das
        // bordas (zoom abaixo do "cobre"). Fora da imagem fica TRANSPARENTE — o
        // gradiente do banner aparece atras.
        val ix0 = floor(sx).coerceIn(0f, src.w.toFloat())
        val iy0 = floor(sy).coerceIn(0f, src.h.toFloat())
        val ix1 = ceil(sx + sw).coerceIn(0f, src.w.toFloat())
        val iy1 = ceil(sy + sh).coerceIn(0f, src.h.toFloat())
        require(ix1 > ix0 && iy1 > iy0) { "recorte fora da imagem" }

        val k = larguraFinal / sw
        val surface = Surface.makeRasterN32Premul(larguraFinal, outH)
        // MITCHELL, e nao LINEAR. O bilinear le so 2x2 pixeis vizinhos: numa
        // reducao de 4x (a fonte vem capada em 2048 e o avatar sai em 512) ele
        // ignora quase toda a informacao e devolve serrilhado — era isso o
        // "pixelado", nao o tamanho final. O cubico do Mitchell amostra uma
        // vizinhanca maior e foi desenhado justamente pra reduzir.
        surface.canvas.drawImageRect(
            src.img,
            SkRect.makeLTRB(ix0, iy0, ix1, iy1),
            SkRect.makeLTRB((ix0 - sx) * k, (iy0 - sy) * k, (ix1 - sx) * k, (iy1 - sy) * k),
            SamplingMode.MITCHELL,
            null,
            true,
        )
        val out = surface.makeImageSnapshot()

        // Sobrou area vazia OU a fonte tinha transparencia -> PNG. Senao JPEG
        // (mesma regra do AvatarPicker: JPEG não carrega canal alfa).
        val gap = ix0 > sx + 0.5f || iy0 > sy + 0.5f || ix1 < sx + sw - 0.5f || iy1 < sy + sh - 0.5f
        val alpha = gap || src.hasAlpha
        // 95 e nao 92: o servidor ainda re-encoda isto em WebP, entao o JPEG daqui
        // e um INTERMEDIARIO — todo artefato que ele criar e herdado pelo arquivo
        // final e ainda serve de material pra segunda compressao errar em cima.
        // Perda dupla e o que mais estraga foto de pele e ceu.
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
        // makeFromEncoded joga uma excecao nativa crua quando o formato não e
        // reconhecido; a checagem aqui devolve algo que da pra ler na tela.
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
            // MITCHELL aqui tambem. Esta reducao acontece ANTES do recorte, entao o
            // serrilhado que o bilinear deixa e assado junto e nao sai mais — o
            // cubico do `bake` estaria so reamostrando um borrao.
            SamplingMode.MITCHELL,
            null,
            true,
        )
        val small = surface.makeImageSnapshot()
        runCatching { surface.close() }
        runCatching { img.close() }
        return CropImage(small, hasAlpha)
    }

    // Mesmo caminho do AnimatedImage: data-uri inline, /uploads (base + path) ou
    // http. Tenta autenticado (uploads do proprio backend exigem) e cai pra um
    // cliente sem auth (CDN/R2 publico recusa o header Authorization).
    private fun fetch(url: String): ByteArray? {
        if (url.startsWith("data:")) {
            val i = url.indexOf("base64,")
            // O backend aceita data-uri percent-encoded (sem base64); aqui só o
            // base64 era tratado, e o outro caso voltava null calado.
            if (i < 0) {
                val corpo = url.substringAfter(',', "")
                return runCatching { java.net.URLDecoder.decode(corpo, "UTF-8").toByteArray(Charsets.ISO_8859_1) }
                    .getOrNull()
            }
            return runCatching { Base64.getDecoder().decode(url.substring(i + 7)) }.getOrNull()
        }
        val abs = absoluteUrl(url)
        // Authorization SO pro nosso backend. O banner do perfil vive no R2 (o
        // persistDataUri troca o data-uri por uma URL de la), e mandar o Bearer
        // pra um host de terceiro (a) vaza o token pra fora e (b) costuma tomar
        // 400 — vários storages recusam requisição com dois mecanismos de auth.
        // Antes tentava o autenticado PRIMEIRO em toda URL, inclusive nessas.
        val nosso = abs.startsWith(AstraShared.BASE_URL.trimEnd('/'))
        val motivos = mutableListOf<String>()
        if (nosso) get(http, abs, motivos)?.let { return it }
        get(plain, abs, motivos)?.let { return it }
        // Motivo REAL na exceção (código HTTP, timeout, DNS) em vez de null mudo.
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

// Popup que cobre a janela inteira (offset zero) — o conteudo desenha o scrim e
// centraliza o cartao. Mesmo idioma dos outros modais do app.
private object CropOverlay : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

// Modal de recorte. `aspect` = largura/altura da janela de corte (1 pro avatar/
// ícone, ProfileBannerAspect/ServerBannerAspect pros banners); `round` desenha o
// furo redondo (avatar). `onApply` recebe o data-uri já assado.
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

    // pct = porcentagem do "cobre a janela", e 100 e o MINIMO (escolha do dono:
    // "igual Discord"). Assim a imagem sempre preenche a moldura — nunca sobra
    // borda — e sempre ha pedaco escondido pra revelar, entao arrastar funciona
    // nos dois eixos. O preco: não da pra ver a imagem inteira, você escolhe QUAL
    // pedaço aparece. E exatamente como o recortador do Discord se comporta.
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
            // A MENSAGEM REAL, não um "não deu" generico: "não deu pra ler essa
            // imagem" foi reportado tres vezes seguidas e nunca deu pra saber se
            // era download, formato ou tamanho — problemas em pontas opostas.
            .onFailure { e ->
                val motivo = e.message.orEmpty()
                // 404 não é "erro ao ler a imagem": é a imagem NÃO EXISTIR MAIS no
                // servidor. Some quando o arquivo foi gravado no disco da instância
                // (storage local) e a hospedagem reiniciou — o endereço continua
                // salvo no perfil, apontando pra um arquivo que evaporou. Mandar
                // "HTTP 404" pra quem só queria mexer no banner não ajuda ninguém a
                // fazer a única coisa que resolve: subir a imagem de novo.
                err = if ("404" in motivo) {
                    "essa imagem não está mais no servidor. escolha o arquivo de novo para subir outra vez."
                } else {
                    "não foi possível ler: " + (motivo.take(120).ifBlank { e::class.simpleName ?: "erro desconhecido" })
                }
            }
    }
    // `cur` capturado numa val: ler `loaded` dentro do onDispose pegaria o valor
    // ATUAL (a imagem recem-carregada) e fecharia ela viva.
    val cur = loaded
    DisposableEffect(cur) { onDispose { cur?.close() } }

    val cover = if (cur == null) 1f else max(cropW / cur.w, cropH / cur.h)
    // Limite do arrasto por eixo: a janela nunca sai da imagem (e, quando a
    // imagem e menor que a janela, ela nunca sai da janela). O zoom vem por
    // PARAMETRO de proposito: o bloco do `pointerInput` so reinicia quando a
    // chave muda, entao capturar o zoom da composição deixaria o limite velho
    // depois de dar zoom.
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
                ) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(StageW + 36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Obsidian.raised)
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(14.dp))
                    // Clique no cartao NAO fecha (so o scrim atras fecha).
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
                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
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
                            // Máscara = palco MENOS o furo: um Path so, um drawPath so.
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
                        // Centro do furo = centro do palco; `pan` desloca a imagem.
                        // Dividir por zoom leva de pixel de tela pra pixel da fonte.
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

// Trilha de zoom em % do "cobre a janela" (100..300). Espelha o ZoomTrack das
// configs; a escala e outra (aqui 100 = cobre, não 100 = tamanho original).
@Composable
private fun CropZoomTrack(pct: Int, onChange: (Int) -> Unit) {
    val f = ((pct - 100) / 200f).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("zoom", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp), modifier = Modifier.width(42.dp))
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
