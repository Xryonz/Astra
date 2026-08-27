package app.astra.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSans
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.voice.Sfx
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.Image as SkiaImage

enum class PetEvento { MENSAGEM, CALL }

object Pet {
    private val _evento = MutableSharedFlow<PetEvento>(extraBufferCapacity = 4)
    val evento = _evento.asSharedFlow()

    fun mensagemNova() { _evento.tryEmit(PetEvento.MENSAGEM) }
    fun entrouEmCall() { _evento.tryEmit(PetEvento.CALL) }
}

object PisoDoPet {
    var caixa by mutableStateOf(Rect.Zero)
}

class Passo(
    val arquivo: String,
    val linha: Int,
    val quadros: Int,
    val fps: Int,
    val velocidade: Float,
)

enum class Bicho(
    val rotulo: String,
    val quadroW: Int,
    val cx: Int, val cy: Int, val cw: Int, val ch: Int, val pes: Int,
    val escala: Int,
    val olhaParaDireita: Boolean,
    val base: IntArray,
    val destino: IntArray,
    val passos: Map<Anim, Passo>,
) {
    TRAVESSO(
        "Travesso", 32, 3, 1, 24, 31, 30, 2, olhaParaDireita = true,
        intArrayOf(0xFFFFFF, 0xB6C5CD, 0x869EAC, 0x688697), intArrayOf(0, 1, 2, 3),
        mapOf(
            Anim.PARADO to Passo("gato_travesso_parado.png", 0, 3, 4, 0f),
            Anim.ANDANDO to Passo("gato_travesso_andando.png", 0, 3, 8, 1.0f),
            Anim.PULO to Passo("gato_travesso_pulo.png", 0, 1, 1, 0f),
            Anim.CARINHO to Passo("gato_travesso_carinho.png", 0, 3, 6, 0f),
        ),
    ),

    SATIRO(
        "Sátiro", 32, 2, 3, 28, 26, 23, 2, olhaParaDireita = false,
        intArrayOf(0xAD2F45, 0x781D4F, 0x4F1D4C), intArrayOf(0, 1, 2),
        mapOf(
            Anim.PARADO to Passo("satiro.png", 0, 6, 7, 0f),
            Anim.ANDANDO to Passo("satiro.png", 1, 8, 10, 1.0f),
            Anim.ATAQUE to Passo("satiro.png", 9, 10, 12, 0f),
        ),
    ),

    SIMPLES(
        "Simples", 32, 7, 14, 18, 18, 17, 2, olhaParaDireita = true,
        intArrayOf(0xE0E0E0, 0xB5B5B5), intArrayOf(0, 2),
        mapOf(
            Anim.PARADO to Passo("gato_simples.png", 0, 4, 5, 0f),
            Anim.ANDANDO to Passo("gato_simples.png", 4, 8, 10, 1.0f),
            Anim.PULO to Passo("gato_simples.png", 8, 7, 11, 0f),
            Anim.CARINHO to Passo("gato_simples.png", 2, 4, 6, 0f),
        ),
    ),
    ;

    companion object {
        val disponiveis = listOf(SIMPLES)

        fun de(nome: String?): Bicho = disponiveis.firstOrNull { it.name == nome } ?: SIMPLES
    }
}

enum class Anim(val rotulo: String) {
    PARADO("parado"), ANDANDO("andando"), PULO("pulo"),
    CARINHO("carinho"),
    FESTA("exibição"),
    RECOLHE("cansaço"),
    ATAQUE("ataque"),
}

val Bicho.escadaDeCarinho: List<Anim>
    get() = listOf(Anim.CARINHO, Anim.FESTA, Anim.ATAQUE).filter { it in passos }

val Bicho.gestoDeSusto: Anim?
    get() = if (Anim.PULO in passos) Anim.PULO else escadaDeCarinho.firstOrNull()

private const val LIMITE_DE_CARINHO = 3

private val RAMPA_BASE = intArrayOf(0xF6CA9F, 0xE69C69, 0xBF6F4A, 0x8A4836)
private const val OPACO = 0xFF000000.toInt()

enum class Pelagem(val rotulo: String, val rampa: IntArray) {
    LARANJA("Laranja", intArrayOf(0xF6CA9F, 0xE69C69, 0xBF6F4A, 0x8A4836)),
    CINZA("Cinza", intArrayOf(0xDEE4F1, 0xC6CFE1, 0xA2AABB, 0x757B87)),
    CARVAO("Carvão", intArrayOf(0x757280, 0x676478, 0x545163, 0x3D3B48)),
    BRANCO("Branco", intArrayOf(0xFFF9F1, 0xF5ECE0, 0xCEC6BA, 0x99938A)),
    CHOCOLATE("Chocolate", intArrayOf(0xB18576, 0xA66750, 0x8A4F39, 0x63392A)),
    CARAMELO("Caramelo", intArrayOf(0xF6E2B2, 0xE6C985, 0xBFA464, 0x8A7648)),
    LILAS("Lilás", intArrayOf(0xDCC9E2, 0xCAAFD4, 0xA78DB0, 0x79667F)),
    ;

    val amostra: Color get() = Color(0xFF000000.toInt() or rampa[1])

    companion object {
        fun de(nome: String?): Pelagem = entries.firstOrNull { it.name == nome } ?: LARANJA
    }
}

internal object FolhasDoGato {
    private val cache = mutableMapOf<Pair<Bicho, Pelagem>, Map<Anim, ImageBitmap>?>()

    @Synchronized
    fun folhas(bicho: Bicho, pelagem: Pelagem): Map<Anim, ImageBitmap>? =
        cache.getOrPut(bicho to pelagem) {
            runCatching {
                val porArquivo = mutableMapOf<String, ImageBitmap>()
                bicho.passos.mapValues { (_, passo) ->
                    porArquivo.getOrPut(passo.arquivo) {
                        val bytes = requireNotNull(
                            FolhasDoGato::class.java.getResourceAsStream("/pet/" + passo.arquivo),
                        ) { "sprite ausente: " + passo.arquivo }.use { it.readBytes() }
                        val img = ImageIO.read(ByteArrayInputStream(bytes))
                            ?: error("PNG ilegível: " + passo.arquivo)
                        repintar(img, bicho, pelagem).toComposeImageBitmap()
                    }
                }
            }.getOrNull()
        }

    private fun repintar(src: BufferedImage, bicho: Bicho, pelagem: Pelagem): SkiaImage {
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val p = src.getRGB(x, y)
                val i = bicho.base.indexOf(p and 0xFFFFFF)
                if (i >= 0) src.setRGB(x, y, (p and OPACO) or pelagem.rampa[bicho.destino[i]])
            }
        }
        val saida = ByteArrayOutputStream()
        ImageIO.write(src, "png", saida)
        return SkiaImage.makeFromEncoded(saida.toByteArray())
    }
}

private const val FPS = 30
private val LARGURA_VETOR = 34.dp

@Composable
fun GatoDoAstra(
    ligado: Boolean,
    bichoId: String = Bicho.SIMPLES.name,
    pelagem: String = Pelagem.LARANJA.name,
    nome: String = "",
) {
    val reduzir = LocalReduceMotion.current
    val janelaAtiva = LocalWindowActive.current
    if (!ligado || reduzir || !janelaAtiva) return

    val bicho = Bicho.de(bichoId)
    val cor = Pelagem.de(pelagem)
    val folhas = remember(bicho, cor) { FolhasDoGato.folhas(bicho, cor) }
    val medidor = rememberTextMeasurer()

    val densidadeLocal = LocalDensity.current
    val densidade = densidadeLocal.density
    val mult = (bicho.escala * densidade).roundToInt().coerceAtLeast(1)
    val larguraPx = (bicho.cw * mult).toFloat()
    val alturaPx = (bicho.ch * mult).toFloat()
    val pesPx = (bicho.pes * mult).toFloat()

    var origem by remember { mutableStateOf(Offset.Zero) }
    val piso = PisoDoPet.caixa.translate(-origem.x, -origem.y)
    var x by remember { mutableStateOf(-1f) }
    var alvoX by remember { mutableStateOf(0f) }
    var anim by remember { mutableStateOf(Anim.PARADO) }
    var tempoNaAnim by remember { mutableStateOf(0f) }
    var olhandoPraDireita by remember { mutableStateOf(false) }
    var espera by remember { mutableStateOf(2f) }
    var pulosRestantes by remember { mutableStateOf(0) }
    var piscada by remember { mutableStateOf(0f) }
    var caricias by remember { mutableStateOf(0) }
    var ultimaCaricia by remember { mutableStateOf(0L) }
    var deMalAte by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        Pet.evento.collect { ev ->
            val susto = bicho.gestoDeSusto ?: return@collect
            pulosRestantes = if (ev == PetEvento.CALL) 2 else 1
            anim = susto
            tempoNaAnim = 0f
        }
    }

    val limiteEsq = piso.left + larguraPx * 0.5f
    val limiteDir = piso.right - larguraPx * 0.5f

    LaunchedEffect(piso, mult) {
        if (piso.width <= larguraPx) return@LaunchedEffect
        if (x < 0f) x = piso.center.x
        x = x.coerceIn(limiteEsq, limiteDir)
        alvoX = alvoX.coerceIn(limiteEsq, limiteDir)
        while (true) {
            val inicio = System.nanoTime()
            val dt = 1f / FPS

            piscada += dt
            tempoNaAnim += dt

            when (anim) {
                Anim.PARADO -> {
                    espera -= dt
                    if (espera <= 0f) {
                        alvoX = limiteEsq + Random.nextFloat() * (limiteDir - limiteEsq)
                        anim = Anim.ANDANDO
                        tempoNaAnim = 0f
                    }
                }

                Anim.ANDANDO -> {
                    val dx = alvoX - x
                    val v = (bicho.passos[anim]?.velocidade ?: 1f) * larguraPx * dt
                    if (abs(dx) <= v) {
                        x = alvoX
                        anim = Anim.PARADO
                        tempoNaAnim = 0f
                        espera = 4f + Random.nextFloat() * 9f
                    } else {
                        olhandoPraDireita = dx > 0f
                        x += if (dx > 0f) v else -v
                    }
                }

                Anim.CARINHO, Anim.FESTA, Anim.RECOLHE, Anim.ATAQUE -> {
                    val c = bicho.passos[anim]
                    if (c == null || tempoNaAnim >= c.quadros.toFloat() / c.fps) {
                        val recolhido = anim == Anim.RECOLHE
                        anim = Anim.PARADO
                        tempoNaAnim = 0f
                        espera = if (recolhido) 5f + Random.nextFloat() * 3f
                        else 2f + Random.nextFloat() * 2.5f
                    }
                }

                Anim.PULO -> {
                    val p = bicho.passos[Anim.PULO]
                    if (p == null || tempoNaAnim >= p.quadros.toFloat() / p.fps) {
                        pulosRestantes -= 1
                        tempoNaAnim = 0f
                        if (pulosRestantes <= 0) {
                            anim = Anim.PARADO
                            espera = 2.5f + Random.nextFloat() * 3f
                        }
                    }
                }
            }
            esperarPeloTeto(FPS, inicio)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origem = it.positionInWindow() },
    ) {
        if (piso.width <= larguraPx || x < 0f) return@Box
        val y = piso.top

        val sobHover = remember { MutableInteractionSource() }
        val comMouse by sobHover.collectIsHoveredAsState()

        Box(
            Modifier
                .offset { IntOffset((x - larguraPx / 2f).roundToInt(), (y - pesPx).roundToInt()) }
                .size(with(densidadeLocal) { larguraPx.toDp() }, with(densidadeLocal) { alturaPx.toDp() })
                .hoverable(sobHover)
                .clickable(interactionSource = sobHover, indication = null) {
                    val agora = System.currentTimeMillis()
                    if (agora < deMalAte) return@clickable

                    if (agora - ultimaCaricia > 4000) caricias = 0
                    ultimaCaricia = agora
                    caricias += 1

                    if (caricias >= LIMITE_DE_CARINHO) {
                        deMalAte = agora + 6000
                        caricias = 0
                        tempoNaAnim = 0f

                        val recolhe = bicho.passos[Anim.RECOLHE]
                        if (recolhe != null) {
                            anim = Anim.RECOLHE
                        } else {
                            alvoX = if (x < piso.center.x) limiteDir else limiteEsq
                            anim = Anim.ANDANDO
                        }
                    } else {
                        val escada = bicho.escadaDeCarinho
                        anim = escada.getOrNull(caricias - 1) ?: escada.lastOrNull() ?: Anim.CARINHO
                        tempoNaAnim = 0f
                        Sfx.carinho()
                    }
                }
                .semantics { contentDescription = if (nome.isBlank()) "Companheiro do Astra" else nome },
        )

        Canvas(Modifier.fillMaxSize()) {
            val folha = folhas?.get(anim)
            if (folha == null) {
                translate(x, y) {
                    desenharGato(
                        esc = LARGURA_VETOR.toPx() / 34f,
                        paraDireita = olhandoPraDireita,
                        andando = anim == Anim.ANDANDO,
                        passo = tempoNaAnim * 7f,
                        olhoFechado = (piscada % 4.2f) < 0.13f,
                        animacaoDeEvento = if (anim == Anim.PULO) 1f else 0f,
                        pelo = Obsidian.text2,
                        detalhe = Obsidian.accent,
                    )
                }
                return@Canvas
            }

            val passo = bicho.passos[anim] ?: return@Canvas
            val i = (tempoNaAnim * passo.fps).toInt().let {
                if (anim == Anim.PULO) it.coerceIn(0, passo.quadros - 1) else it % passo.quadros
            }
            val esq = (x - larguraPx / 2f).roundToInt()
            val topo = (y - pesPx).roundToInt()

            scale(
                scaleX = if (olhandoPraDireita != bicho.olhaParaDireita) -1f else 1f,
                scaleY = 1f,
                pivot = Offset(x, y),
            ) {
                drawImage(
                    image = folha,
                    srcOffset = IntOffset(
                        i * bicho.quadroW + bicho.cx,
                        passo.linha * bicho.quadroW + bicho.cy,
                    ),
                    srcSize = IntSize(bicho.cw, bicho.ch),
                    dstOffset = IntOffset(esq, topo),
                    dstSize = IntSize(larguraPx.toInt(), alturaPx.toInt()),
                    filterQuality = FilterQuality.None,
                )
            }

            if (nome.isNotBlank() && comMouse) {
                val opacidade = 1f
                val texto = medidor.measure(
                    nome,
                    style = TextStyle(
                        fontFamily = DmSans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Obsidian.text1.copy(alpha = opacidade),
                    ),
                )
                val nx = x - texto.size.width / 2f
                val ny = y - alturaPx - texto.size.height - 4f
                drawRoundRect(
                    color = Obsidian.overlay.copy(alpha = opacidade * 0.92f),
                    topLeft = Offset(nx - 7f, ny - 3f),
                    size = Size(texto.size.width + 14f, texto.size.height + 6f),
                    cornerRadius = CornerRadius(8f, 8f),
                )
                drawText(texto, topLeft = Offset(nx, ny))
            }
        }
    }
}

private fun DrawScope.desenharGato(
    esc: Float,
    paraDireita: Boolean,
    andando: Boolean,
    passo: Float,
    olhoFechado: Boolean,
    animacaoDeEvento: Float,
    pelo: Color,
    detalhe: Color,
) {
    val lado = if (paraDireita) 1f else -1f
    val balanco = if (andando) sin(passo * 2f) * 0.8f * esc else 0f
    val esticar = animacaoDeEvento * 3f * esc
    val traco = Stroke(width = 1.6f * esc)

    translate(0f, balanco - esticar) {
        val cauda = Path().apply {
            moveTo(-11f * esc * lado, -6f * esc)
            val altura = if (animacaoDeEvento > 0f) -20f else -12f
            cubicTo(
                -19f * esc * lado, -8f * esc,
                -20f * esc * lado, altura * esc + sin(passo) * 2f * esc,
                -14f * esc * lado, (altura - 4f) * esc,
            )
        }
        drawPath(cauda, pelo, style = traco)

        val corpo = Path().apply {
            moveTo(-12f * esc * lado, -5f * esc)
            cubicTo(
                -13f * esc * lado, -12f * esc,
                2f * esc * lado, -13f * esc,
                4f * esc * lado, -7f * esc,
            )
            lineTo(4f * esc * lado, -1f * esc)
            lineTo(-12f * esc * lado, -1f * esc)
            close()
        }
        drawPath(corpo, pelo.copy(alpha = 0.22f))
        drawPath(corpo, pelo, style = traco)

        val passoFrente = if (andando) sin(passo) * 2.4f * esc else 0f
        val passoTras = if (andando) sin(passo + 3.14f) * 2.4f * esc else 0f
        for ((baseX, desloc) in listOf(
            (2f * esc * lado) to passoFrente,
            (-2f * esc * lado) to passoTras,
            (-8f * esc * lado) to passoFrente,
            (-11f * esc * lado) to passoTras,
        )) {
            drawLine(
                pelo,
                Offset(baseX, -1f * esc),
                Offset(baseX + desloc, 3f * esc),
                strokeWidth = 1.5f * esc,
            )
        }

        val cabecaX = 8f * esc * lado
        val cabecaY = -11f * esc
        drawCircle(pelo.copy(alpha = 0.22f), radius = 5.2f * esc, center = Offset(cabecaX, cabecaY))
        drawCircle(pelo, radius = 5.2f * esc, center = Offset(cabecaX, cabecaY), style = traco)

        val perk = animacaoDeEvento * 1.4f * esc
        for (dir in listOf(-1f, 1f)) {
            val ox = cabecaX + dir * 3.2f * esc
            val orelha = Path().apply {
                moveTo(ox - 2f * esc, cabecaY - 3.4f * esc)
                lineTo(ox + 0.4f * esc * dir, cabecaY - 7.4f * esc - perk)
                lineTo(ox + 2f * esc, cabecaY - 3.4f * esc)
                close()
            }
            drawPath(orelha, pelo.copy(alpha = 0.22f))
            drawPath(orelha, pelo, style = traco)
        }

        val olhoY = cabecaY - 0.6f * esc
        for (dir in listOf(-1f, 1f)) {
            val ox = cabecaX + dir * 2f * esc
            if (olhoFechado) {
                drawLine(
                    detalhe,
                    Offset(ox - 1.1f * esc, olhoY),
                    Offset(ox + 1.1f * esc, olhoY),
                    strokeWidth = 1.2f * esc,
                )
            } else {
                drawCircle(detalhe, radius = 1.15f * esc, center = Offset(ox, olhoY))
            }
        }

        drawLine(
            pelo,
            Offset(cabecaX + 3.4f * esc * lado, cabecaY + 2f * esc),
            Offset(cabecaX + 5.2f * esc * lado, cabecaY + 2.6f * esc),
            strokeWidth = 1.2f * esc,
        )

        if (animacaoDeEvento > 0f) {
            val a = (animacaoDeEvento).coerceIn(0f, 1f)
            for (i in 0 until 3) {
                val ang = (i * 2.4f) + animacaoDeEvento * 2f
                val r = (7f + i * 2.5f) * esc
                drawCircle(
                    detalhe.copy(alpha = a * 0.75f),
                    radius = 0.9f * esc,
                    center = Offset(
                        cabecaX + cos(ang) * r,
                        cabecaY - 8f * esc + sin(ang) * r * 0.5f - abs(sin(ang)) * 2f * esc,
                    ),
                )
            }
        }
    }
}
