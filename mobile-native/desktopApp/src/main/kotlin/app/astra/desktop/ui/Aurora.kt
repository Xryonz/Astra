package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import app.astra.desktop.ui.theme.Obsidian
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

private const val ESCALA_DO_QUADRO = 0.35f

private fun fbmSigmaRel(octaves: Int): Double {
    var sumSq = 0.0
    var sumLin = 0.0
    var a = 0.5
    repeat(octaves) {
        sumSq += a * a
        sumLin += a
        a *= 0.5
    }
    return sqrt(sumSq) / sumLin
}

private fun auroraSksl(octaves: Int): String {
    val ref = 1.0 - Math.pow(0.5, 3.0)
    val inv = ref / (1.0 - Math.pow(0.5, octaves.toDouble()))
    val steep = 12.5 * (fbmSigmaRel(3) / fbmSigmaRel(octaves))
    return """
uniform float uTime;
uniform float2 uSize;

float hashn(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}
float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hashn(i), hashn(i + float2(1.0, 0.0)), u.x),
               mix(hashn(i + float2(0.0, 1.0)), hashn(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int k = 0; k < $octaves; k++) {
        v += a * vnoise(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v * $inv;
}
float softAbs(float d) { return sqrt(d * d + 6e-5); }

float curtain(float2 uv, float yC, float seed, float2 flow) {
    float n = fbm(float2(uv.x * 2.6 + seed, seed * 3.1) + flow);
    float d = uv.y - (yC + (n - 0.5) * 0.30);
    float kd = mix(10.0, 34.0, smoothstep(0.006, -0.006, d));
    float body = exp(-softAbs(d) * kd);
    float r = fbm(float2(uv.x * 11.0 - seed, 2.7 + seed) + flow * 1.6);
    float rays = 0.30 + 0.70 / (1.0 + exp(-$steep * (r - 0.54)));
    return body * rays * (0.5 + n);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float ang = uTime * 0.1;
    float2 flow1 = float2(cos(ang), sin(ang)) * 1.6;
    float2 flow2 = float2(cos(ang * 2.0 + 2.1), sin(ang * 2.0 + 2.1)) * 1.1;
    float c1 = curtain(uv, 0.24, 0.0, flow1);
    float c2 = curtain(uv, 0.46, 5.3, flow2) * 0.6;
    float fall = 1.0 - smoothstep(0.05, 0.9, uv.y);
    float aur = (c1 + c2) * fall * 0.22;

    float beam1 = exp(-softAbs(uv.x * 0.8 - uv.y * 0.45 - 0.18 - 0.45 * sin(ang * 2.0 + 0.7)) * 5.5);
    float beam2 = exp(-softAbs(uv.x * 0.6 + uv.y * 0.55 - 0.45 - 0.50 * sin(ang * 3.0 + 3.9)) * 7.0) * 0.6;
    float beams = beam1 + beam2;
    aur *= 1.0 + 0.45 * beams;
    aur += beams * fall * 0.02;

    aur *= 0.85 + 0.15 * sin(ang * 3.0 + 0.9);

    float wf = fbm(uv * float2(1.3, 2.0) + flow1 * 0.35 + float2(7.7, 3.3));
    aur *= 0.88 + 0.24 * wf;
    float3 tom = mix(float3(0.94, 0.99, 1.05), float3(1.06, 1.00, 0.94), wf);

    float knee = 0.22;
    float ceiling = 0.30;
    float over = max(aur - knee, 0.0);
    float lum = min(aur, knee) + (ceiling - knee) * (1.0 - exp(-over / (ceiling - knee)));
    float3 col = tom * lum / $ESCALA_DO_QUADRO;

    col += (hashn(fragCoord) - 0.5) / 255.0;
    return half4(col, 1.0);
}
"""
}

private const val TOCADOR_SKSL = """
uniform shader quadroA;
uniform shader quadroB;
uniform float uMistura;
uniform float3 uTom;
uniform float3 uVoid;
uniform float2 uEscala;

float hashn(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 p) {
    float2 q = p * uEscala;
    float3 a = float3(quadroA.eval(q).rgb);
    float3 b = float3(quadroB.eval(q).rgb);
    float3 c = uVoid + uTom * mix(a, b, uMistura);
    c += (hashn(p) - 0.5) / 255.0;
    return half4(half3(c), 1.0);
}
"""

private const val AURORA_LOOP = 62.831853f

private const val AURORA_STILL = 12f

private const val QUADROS_DA_VOLTA = 128

private const val QUADROS_DA_POSE = 2

private const val LARGURA_DO_QUADRO = 320

private const val ALTURA_DO_QUADRO = 180

private const val PASSO_MAXIMO_S = 0.25f

private const val INTERVALO_NO_PROCESSADOR_MS = 83L

private const val PULSO_MAXIMO = 0.15f

private const val TETO_DA_AURORA_QPS = 30

private class ClipeDaAurora {
    private val quadros = arrayOfNulls<Image>(QUADROS_DA_VOLTA)
    private val amostradores = arrayOfNulls<Shader>(QUADROS_DA_VOLTA)
    private var fechado = false

    var paradoPronto by mutableStateOf(false)
        private set

    var pronto by mutableStateOf(false)
        private set

    var geracao by mutableIntStateOf(0)
        private set

    fun quadro(i: Int): Image? = quadros[i]

    fun amostrador(i: Int): Shader? {
        amostradores[i]?.let { return it }
        val imagem = quadros[i] ?: return null
        return imagem.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, null)
            .also { amostradores[i] = it }
    }

    suspend fun preparar(oitavas: Int, primeiro: Int, naTela: () -> Boolean, emMovimento: () -> Boolean) {
        snapshotFlow { naTela() }.first { it }
        val efeito = foraDaTela {
            runCatching { RuntimeEffect.makeForShader(auroraSksl(oitavas).trimIndent()) }.getOrNull()
        } ?: return
        if (!currentCoroutineContext().isActive || fechado) {
            efeito.close()
            return
        }
        val construtor = RuntimeShaderBuilder(efeito)
        val pintor = Paint()
        val superficie = Surface.makeRasterN32Premul(LARGURA_DO_QUADRO, ALTURA_DO_QUADRO)
        try {
            for (passo in 0 until QUADROS_DA_VOLTA) {
                if (passo >= QUADROS_DA_POSE) snapshotFlow { naTela() && emMovimento() }.first { it }
                if (!currentCoroutineContext().isActive || fechado) return
                val i = (primeiro + passo) % QUADROS_DA_VOLTA
                val imagem = foraDaTela {
                    desenharQuadro(construtor, pintor, superficie, i * AURORA_LOOP / QUADROS_DA_VOLTA)
                } ?: return
                if (!currentCoroutineContext().isActive || fechado) {
                    imagem.close()
                    return
                }
                trocar(i, imagem)
                if (passo == QUADROS_DA_POSE - 1) {
                    paradoPronto = true
                    geracao++
                }
            }
            pronto = true
            geracao++
        } finally {
            superficie.close()
            pintor.close()
            construtor.close()
            efeito.close()
        }
    }

    private fun trocar(i: Int, imagem: Image) {
        amostradores[i]?.close()
        amostradores[i] = null
        quadros[i]?.close()
        quadros[i] = imagem
    }

    fun fechar() {
        fechado = true
        for (i in 0 until QUADROS_DA_VOLTA) {
            amostradores[i]?.close()
            amostradores[i] = null
            quadros[i]?.close()
            quadros[i] = null
        }
    }
}

private suspend fun <T : AutoCloseable> foraDaTela(calculo: () -> T?): T? {
    var feito: T? = null
    try {
        return withContext(NonCancellable + Dispatchers.Default) { calculo().also { feito = it } }
    } catch (e: CancellationException) {
        feito?.close()
        throw e
    }
}

private fun desenharQuadro(construtor: RuntimeShaderBuilder, pintor: Paint, superficie: Surface, tempo: Float): Image {
    construtor.uniform("uTime", tempo)
    construtor.uniform("uSize", LARGURA_DO_QUADRO.toFloat(), ALTURA_DO_QUADRO.toFloat())
    val shader = construtor.makeShader()
    pintor.shader = shader
    superficie.canvas.drawRect(Rect.makeWH(LARGURA_DO_QUADRO.toFloat(), ALTURA_DO_QUADRO.toFloat()), pintor)
    pintor.shader = null
    shader.close()
    return superficie.makeImageSnapshot()
}

private fun tetoDaAurora(tetoDaInterface: Int): Int =
    if (tetoDaInterface in 1..TETO_DA_AURORA_QPS) tetoDaInterface else TETO_DA_AURORA_QPS

private fun posicaoNaVolta(tempo: Float): Float = tempo / AURORA_LOOP * QUADROS_DA_VOLTA

private fun indiceDoTempo(tempo: Float): Int = floor(posicaoNaVolta(tempo)).toInt().mod(QUADROS_DA_VOLTA)

@Composable
fun Modifier.auroraBackground(pulse: () -> Float = { 0f }): Modifier {
    val oitavas = LocalRenderPrefs.current.auroraOctaves
    val accent = Obsidian.accent
    val voidC = Obsidian.void
    val relogio = remember { floatArrayOf(AURORA_STILL) }
    val clipe = remember { ClipeDaAurora() }
    val naTela = rememberUpdatedState(LocalJanelaNaTela.current)
    val emMovimento = rememberUpdatedState(!LocalReduceMotion.current)
    LaunchedEffect(oitavas) {
        clipe.preparar(oitavas, indiceDoTempo(relogio[0]), { naTela.value }, { emMovimento.value })
    }
    DisposableEffect(clipe) { onDispose { clipe.fechar() } }
    val peloProcessador = DesenhoDaJanela.peloProcessador
    val tempo = relogioDaAurora(relogio, clipe.pronto, peloProcessador)
    return if (peloProcessador) auroraPeloProcessador(clipe, tempo, pulse, accent, voidC)
    else auroraNaPlaca(clipe, tempo, pulse, accent, voidC)
}

@Composable
private fun relogioDaAurora(relogio: FloatArray, pronto: Boolean, peloProcessador: Boolean): State<Float> {
    val parado = LocalReduceMotion.current
    val ativa = rememberUpdatedState(LocalWindowActive.current)
    val teto = rememberUpdatedState(LocalRenderPrefs.current.fpsCap)
    return produceState(relogio[0], parado, pronto, peloProcessador) {
        value = relogio[0]
        if (parado || !pronto) return@produceState
        while (true) {
            snapshotFlow { ativa.value }.first { it }
            var anterior = withFrameNanos { it }
            while (ativa.value) {
                val inicio = System.nanoTime()
                withFrameNanos { agora ->
                    val passo = ((agora - anterior) / 1_000_000_000f).coerceAtMost(PASSO_MAXIMO_S)
                    relogio[0] = (relogio[0] + passo) % AURORA_LOOP
                    anterior = agora
                    value = relogio[0]
                }
                if (peloProcessador) {
                    delay((INTERVALO_NO_PROCESSADOR_MS - (System.nanoTime() - inicio) / 1_000_000).coerceAtLeast(0))
                } else {
                    esperarPeloTeto(tetoDaAurora(teto.value), inicio)
                }
            }
        }
    }
}

@Composable
private fun Modifier.auroraNaPlaca(
    clipe: ClipeDaAurora,
    tempo: State<Float>,
    pulse: () -> Float,
    accent: Color,
    voidC: Color,
): Modifier {
    val efeito = remember { runCatching { RuntimeEffect.makeForShader(TOCADOR_SKSL.trimIndent()) }.getOrNull() }
        ?: return this.drawBehind { drawRect(voidC) }
    val construtor = remember(efeito) { RuntimeShaderBuilder(efeito) }
    val pintor = remember { Paint() }
    val ultimo = remember { arrayOfNulls<Shader>(1) }
    val usados = remember(accent, voidC) { arrayOfNulls<Shader>(2) }
    val chave = remember(accent, voidC) { FloatArray(5) { Float.NaN } }
    DisposableEffect(construtor) {
        onDispose {
            ultimo[0]?.close()
            ultimo[0] = null
            pintor.close()
            construtor.close()
            efeito.close()
        }
    }
    return drawBehind {
        Quadros.cronometrar("aurora") {
            if (size.width <= 0f || size.height <= 0f || !clipe.paradoPronto) {
                drawRect(voidC)
                return@cronometrar
            }
            val posicao = posicaoNaVolta(tempo.value)
            val atual = floor(posicao).toInt().mod(QUADROS_DA_VOLTA)
            val quadroA = clipe.amostrador(atual)
            val quadroB = clipe.amostrador((atual + 1) % QUADROS_DA_VOLTA) ?: quadroA
            if (quadroA == null || quadroB == null) {
                drawRect(voidC)
                return@cronometrar
            }
            val mistura = posicao - floor(posicao)
            val brilho = 1f + PULSO_MAXIMO * pulse().coerceIn(0f, 1f)
            val geracao = clipe.geracao.toFloat()
            val mesmo = ultimo[0] != null && usados[0] === quadroA && usados[1] === quadroB &&
                chave[0] == geracao && chave[1] == mistura && chave[2] == brilho &&
                chave[3] == size.width && chave[4] == size.height
            if (!mesmo) {
                usados[0] = quadroA; usados[1] = quadroB
                chave[0] = geracao; chave[1] = mistura; chave[2] = brilho
                chave[3] = size.width; chave[4] = size.height
                val tom = brilho * ESCALA_DO_QUADRO
                construtor.child("quadroA", quadroA)
                construtor.child("quadroB", quadroB)
                construtor.uniform("uMistura", mistura)
                construtor.uniform("uTom", accent.red * tom, accent.green * tom, accent.blue * tom)
                construtor.uniform("uVoid", voidC.red, voidC.green, voidC.blue)
                construtor.uniform("uEscala", LARGURA_DO_QUADRO / size.width, ALTURA_DO_QUADRO / size.height)
                ultimo[0]?.close()
                ultimo[0] = construtor.makeShader()
            }
            pintor.shader = ultimo[0]
            drawIntoCanvas { it.nativeCanvas.drawRect(Rect.makeWH(size.width, size.height), pintor) }
        }
    }
}

@Composable
private fun Modifier.auroraPeloProcessador(
    clipe: ClipeDaAurora,
    tempo: State<Float>,
    pulse: () -> Float,
    accent: Color,
    voidC: Color,
): Modifier {
    val pequena = remember { Surface.makeRasterN32Premul(LARGURA_DO_QUADRO, ALTURA_DO_QUADRO) }
    val misturador = remember { Paint() }
    val pintor = remember { Paint().apply { isDither = true } }
    val mistura = remember { arrayOfNulls<Image>(1) }
    val chave = remember { FloatArray(3) { Float.NaN } }
    val filtro = remember(accent, voidC) { arrayOfNulls<ColorFilter>(1) }
    val brilhoDoFiltro = remember(accent, voidC) { floatArrayOf(Float.NaN) }
    DisposableEffect(Unit) {
        onDispose {
            mistura[0]?.close()
            mistura[0] = null
            misturador.close()
            pintor.close()
            pequena.close()
        }
    }
    DisposableEffect(filtro) { onDispose { filtro[0]?.close() } }
    return drawBehind {
        Quadros.cronometrar("aurora") {
            if (size.width <= 0f || size.height <= 0f || !clipe.paradoPronto) {
                drawRect(voidC)
                return@cronometrar
            }
            val posicao = posicaoNaVolta(tempo.value)
            val atual = floor(posicao).toInt().mod(QUADROS_DA_VOLTA)
            val quadroA = clipe.quadro(atual)
            if (quadroA == null) {
                drawRect(voidC)
                return@cronometrar
            }
            val quadroB = clipe.quadro((atual + 1) % QUADROS_DA_VOLTA) ?: quadroA
            val fracao = posicao - floor(posicao)
            val geracao = clipe.geracao.toFloat()
            val mesma = mistura[0] != null && chave[0] == geracao && chave[1] == atual.toFloat() && chave[2] == fracao
            if (!mesma) {
                chave[0] = geracao; chave[1] = atual.toFloat(); chave[2] = fracao
                misturador.setAlphaf(1f)
                pequena.canvas.drawImage(quadroA, 0f, 0f, misturador)
                misturador.setAlphaf(fracao)
                pequena.canvas.drawImage(quadroB, 0f, 0f, misturador)
                mistura[0]?.close()
                mistura[0] = pequena.makeImageSnapshot()
            }
            val brilho = 1f + PULSO_MAXIMO * Snapshot.withoutReadObservation { pulse() }.coerceIn(0f, 1f)
            if (brilho != brilhoDoFiltro[0]) {
                val antigo = filtro[0]
                filtro[0] = filtroDeCor(accent, voidC, brilho * ESCALA_DO_QUADRO)
                pintor.colorFilter = filtro[0]
                antigo?.close()
                brilhoDoFiltro[0] = brilho
            }
            val pronta = mistura[0] ?: return@cronometrar
            drawIntoCanvas {
                it.nativeCanvas.drawImageRect(
                    pronta,
                    Rect.makeWH(LARGURA_DO_QUADRO.toFloat(), ALTURA_DO_QUADRO.toFloat()),
                    Rect.makeWH(size.width, size.height),
                    SamplingMode.LINEAR,
                    pintor,
                    true,
                )
            }
        }
    }
}

private fun filtroDeCor(accent: Color, voidC: Color, tom: Float): ColorFilter =
    ColorFilter.makeMatrix(
        ColorMatrix(
            accent.red * tom, 0f, 0f, 0f, voidC.red,
            0f, accent.green * tom, 0f, 0f, voidC.green,
            0f, 0f, accent.blue * tom, 0f, voidC.blue,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
