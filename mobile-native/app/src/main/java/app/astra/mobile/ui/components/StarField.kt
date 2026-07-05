package app.astra.mobile.ui.components

import android.content.Context
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun lcg(seed: Int): () -> Float {
    var s = seed
    return {
        s = (s * 1103515245 + 12345) and 0x7fffffff
        s / 0x7fffffff.toFloat()
    }
}

private data class BgStar(val x: Float, val y: Float, val r: Float)
private data class Twinkle(val x: Float, val y: Float, val r: Float, val freq: Float, val phase: Float)
private data class MeteorDef(val startX: Float, val stagger: Float, val mx: Float)

private val BG_STARS: List<BgStar> = buildList {
    val rnd = lcg(424242)
    repeat(70) { add(BgStar(rnd(), rnd(), if (rnd() > 0.5f) 1.4f else 1.0f)) }
}
private val TWINKLES: List<Twinkle> = buildList {
    val rnd = lcg(99883)
    repeat(14) {
        add(Twinkle(rnd(), rnd(), 1.5f + rnd() * 1.0f, (1 + (rnd() * 3f).toInt()).toFloat(), rnd() * 6.2832f))
    }
}
private val METEORS: List<MeteorDef> = buildList {
    val rnd = lcg(13579)
    repeat(3) { i -> add(MeteorDef(0.3f + rnd() * 0.6f, i / 3f, -(0.25f + rnd() * 0.45f))) }
}

// Parallax: acelerometro com dois low-pass (rapido - lento) = responde a MUDANCA
// de inclinacao e re-centra sozinho em ~5s, entao funciona deitado ou em pe.
// So escreve MutableState lido na draw-phase: re-desenha sem recompor.
@Composable
private fun rememberParallaxTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) {
            tilt.value = Offset.Zero
            onDispose {}
        } else {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            var fastX = 0f; var fastY = 0f; var slowX = 0f; var slowY = 0f
            var primed = false
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    val x = e.values[0]; val y = e.values[1]
                    if (!primed) {
                        fastX = x; fastY = y; slowX = x; slowY = y; primed = true
                    }
                    fastX += (x - fastX) * 0.18f
                    fastY += (y - fastY) * 0.18f
                    slowX += (x - slowX) * 0.012f
                    slowY += (y - slowY) * 0.012f
                    tilt.value = Offset(
                        ((fastX - slowX) / 3f).coerceIn(-1f, 1f),
                        ((fastY - slowY) / 3f).coerceIn(-1f, 1f),
                    )
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            val register = {
                accel?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> register()
                    Lifecycle.Event.ON_PAUSE -> {
                        sm.unregisterListener(listener)
                        tilt.value = Offset.Zero
                        primed = false
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) register()
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                sm.unregisterListener(listener)
            }
        }
    }
    return tilt
}

@Composable
fun StarField(
    modifier: Modifier = Modifier,
    color: Color = astraColors.accent,
    tilt: State<Offset>? = null,
) {
    if (!LocalAppPrefs.current.starsOn) {
        StarFieldStatic(modifier, color)
        return
    }
    val inf = rememberInfiniteTransition(label = "starfield")

    val drift by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "drift",
    )

    val tau by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(5_000, easing = LinearEasing)), label = "tau",
    )

    val meteorT by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "meteor",
    )

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tv = tilt?.value ?: Offset.Zero
        // Camadas em profundidade: estrelas de fundo mexem menos que as brilhantes.
        val dxBg = sin(drift) * 14.dp.toPx() - tv.x * 5.dp.toPx()
        val dyBg = (cos(drift) - 1f) * 9.dp.toPx() - tv.y * 5.dp.toPx()
        val dxTw = sin(drift) * 14.dp.toPx() - tv.x * 10.dp.toPx()
        val dyTw = (cos(drift) - 1f) * 9.dp.toPx() - tv.y * 10.dp.toPx()

        BG_STARS.forEach { s ->
            drawCircle(color, s.r.dp.toPx(), Offset(s.x * w + dxBg, s.y * h + dyBg), alpha = 0.34f)
        }

        TWINKLES.forEach { t ->
            val a = 0.25f + 0.7f * ((sin(tau * t.freq + t.phase) + 1f) / 2f)
            val c = Offset(t.x * w + dxTw, t.y * h + dyTw)
            drawCircle(color, t.r.dp.toPx() * 2.4f, c, alpha = a * 0.16f)
            drawCircle(color, t.r.dp.toPx(), c, alpha = a)
        }

        METEORS.forEach { m ->
            val local = (meteorT + m.stagger) % 1f
            if (local < 0.12f) {
                val p = local / 0.12f
                val headX = (m.startX + m.mx * p) * w
                val headY = (-0.1f + 1.2f * p) * h
                val tailX = headX - m.mx * 0.06f * w
                val tailY = headY - 0.06f * h
                val alpha = sin(p * PI).toFloat().coerceIn(0f, 1f)
                drawLine(color, Offset(tailX, tailY), Offset(headX, headY), 1.5.dp.toPx(), StrokeCap.Round, alpha = alpha * 0.7f)
                drawCircle(color, 2.2.dp.toPx(), Offset(headX, headY), alpha = alpha)
            }
        }
    }
}

@Composable
private fun StarFieldStatic(modifier: Modifier = Modifier, color: Color = astraColors.accent) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        BG_STARS.forEach { s ->
            drawCircle(color, s.r.dp.toPx(), Offset(s.x * w, s.y * h), alpha = 0.34f)
        }
        TWINKLES.forEach { t ->
            drawCircle(color, t.r.dp.toPx(), Offset(t.x * w, t.y * h), alpha = 0.55f)
        }
    }
}

// Aurora AGSL v2: cortinas organicas por ruido fractal (FBM), nao mais senos.
// O tempo anda num CIRCULO no espaco de ruido (cos/sin * raio), entao o loop de
// 60s fecha perfeito sem salto. Ainda barato: value-noise ALU-only, sem textura.
// Extras: tilt (parallax por sensor) desloca o uv; tap no fundo vazio = pulso de
// glow + anel que expande (rippleAge < 0 desliga o branch).
private const val AURORA_AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float3 accent;
uniform float2 tilt;
uniform float2 touchPos;
uniform float touchGlow;
uniform float rippleAge;

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
    // 3 oitavas (era 4): -25% de ALU por pixel, diferenca visual imperceptivel
    // no alpha ~0.16 em que a aurora vive.
    for (int k = 0; k < 3; k++) {
        v += a * vnoise(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}
float curtain(float2 uv, float yC, float seed, float2 flow) {
    float n = fbm(float2(uv.x * 2.6 + seed, seed * 3.1) + flow);
    float d = uv.y - (yC + (n - 0.5) * 0.30);
    float body = exp(-abs(d) * (d < 0.0 ? 26.0 : 9.0));
    float rays = 0.55 + 0.45 * fbm(float2(uv.x * 7.0 - seed, 2.7 + seed) + flow * 1.4);
    return body * rays * (0.5 + n);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    uv += tilt * 0.02;
    float ang = iTime * 0.1;
    float2 flow1 = float2(cos(ang), sin(ang)) * 1.6;
    float2 flow2 = float2(cos(ang * 2.0 + 2.1), sin(ang * 2.0 + 2.1)) * 1.1;
    float c1 = curtain(uv, 0.24, 0.0, flow1);
    float c2 = curtain(uv, 0.46, 5.3, flow2) * 0.6;
    float fall = 1.0 - smoothstep(0.05, 0.9, uv.y);
    float aur = (c1 + c2) * fall * 0.16;

    // Efeito de toque COMPACTO (pedido do user): glow ~do tamanho do dedo e
    // anel que expande pouco (~9% da tela) e morre rapido (~0.6s).
    float2 asp = float2(iResolution.x / iResolution.y, 1.0);
    float dT = distance(uv * asp, touchPos * asp);
    float fx = touchGlow * exp(-dT * 28.0) * 0.30;
    if (rippleAge >= 0.0) {
        float r = rippleAge * 0.16;
        fx += exp(-abs(dT - r) * 60.0) * exp(-rippleAge * 4.5) * 0.35;
    }

    float a = min(aur + fx, 0.30);
    return half4(accent * a, a);
}
"""

private const val TIME_LOOP = 62.831853f // 20*PI: fecha o circulo do fbm E os sin

// Trilha do cometa (arrasto no ceu vazio): pontos recentes do dedo que a cauda
// liga, esvaindo em ~0.5s. Vive no mesmo Box pai do gesto de toque.
private const val TRAIL_LIFE_MS = 520L
private const val TRAIL_MAX = 64
private data class TrailPoint(val pos: Offset, val bornMs: Long)

// Estado do efeito de toque. Vive no CosmicBackdrop porque o GESTO e detectado
// no Box pai (hit-path de toda a UI); o canvas da aurora fica atras do conteudo
// e nunca receberia toques em telas cobertas por listas (chat, DMs, servidores).
private class TouchFx {
    val uv = mutableStateOf(Offset.Zero)
    val glow = Animatable(0f)
    val ripple = Animatable(3f)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AuroraShader(
    color: Color,
    tilt: State<Offset>,
    fx: TouchFx,
    modifier: Modifier = Modifier,
) {
    val shader = remember { RuntimeShader(AURORA_AGSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    val inf = rememberInfiniteTransition(label = "aurora")
    val time by inf.animateFloat(
        0f, TIME_LOOP,
        infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "aurora-t",
    )

    val r = color.red; val g = color.green; val b = color.blue
    // MEIA RESOLUCAO: o AGSL custa por pixel. O canvas e MEDIDO na metade do
    // tamanho e composto com scale 2x a partir de um buffer offscreen -> o
    // shader calcula ~25% dos pixels. O upscale bilinear e invisivel aqui
    // (aurora e gradiente desfocado); estrelas/conteudo seguem em res cheia.
    // O shader e independente de resolucao (uv = fragCoord/iResolution), entao
    // toque/tilt continuam alinhados com a tela sem ajuste.
    Canvas(
        modifier
            .layout { measurable, constraints ->
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val half = measurable.measure(
                    Constraints.fixed((w / 2).coerceAtLeast(1), (h / 2).coerceAtLeast(1)),
                )
                layout(w, h) { half.place(0, 0) }
            }
            .graphicsLayer {
                scaleX = 2f
                scaleY = 2f
                transformOrigin = TransformOrigin(0f, 0f)
                // Sem Offscreen o RenderNode reexecutaria o draw ja transformado
                // (= shader por pixel FINAL, ganho zero). Com ele, rasteriza no
                // tamanho do layout e escala a textura.
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        // Fase de desenho (como o StarField): le estados animados sem recompor.
        val tv = tilt.value
        val age = fx.ripple.value
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("accent", r, g, b)
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("tilt", tv.x, tv.y)
        shader.setFloatUniform("touchPos", fx.uv.value.x, fx.uv.value.y)
        shader.setFloatUniform("touchGlow", fx.glow.value)
        shader.setFloatUniform("rippleAge", if (age >= 3f) -1f else age)
        drawRect(brush)
    }
}

// Fundo cosmico REAL: uma unica instancia global no AstraApp, atras do NavHost
// (1 shader + 1 starfield + 1 sensor pro app todo; transicoes de tela deslizam
// o conteudo sobre o ceu parado, estilo Discord/iOS). Overlays que cobrem tudo
// (ex.: ProfileSheet) usam este direto pra ficarem opacos.
@Composable
fun CosmicBackdrop(
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val prefs = LocalAppPrefs.current
    val auroraShown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && prefs.auroraOn
    // Parallax so quando algo anima (respeita reduceMotion via getters starsOn/auroraOn).
    val tilt = rememberParallaxTilt(enabled = auroraShown || prefs.starsOn)
    val fx = remember { TouchFx() }
    val trail = remember { mutableStateListOf<TrailPoint>() }
    val scope = rememberCoroutineScope()
    // Deteccao no Box PAI (esta no hit-path de toda tela, ao contrario do canvas
    // atras do conteudo): "tap no vazio" = gesto que NENHUM filho consumiu (nao
    // foi click, scroll nem campo de texto) e que nao arrastou alem do slop.
    val touchMod = if (interactive && auroraShown && prefs.skyTouchOn) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.isConsumed) return@awaitEachGesture
                val slop = viewConfiguration.touchSlop
                var tapped = false
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent()
                    val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.size > 1) break
                    if (!dragging) {
                        if (ch.isConsumed) break
                        if ((ch.position - down.position).getDistance() > slop) {
                            // Virou arrasto no ceu vazio: abre a trilha do cometa.
                            dragging = true
                            val t = System.currentTimeMillis()
                            trail.add(TrailPoint(down.position, t))
                            trail.add(TrailPoint(ch.position, t))
                        }
                    } else {
                        if (ch.isConsumed) break // um filho pegou (scroll) -> encerra
                        trail.add(TrailPoint(ch.position, System.currentTimeMillis()))
                        while (trail.size > TRAIL_MAX) trail.removeAt(0)
                    }
                    if (!ch.pressed) {
                        if (!dragging) tapped = true
                        break
                    }
                }
                if (!tapped) return@awaitEachGesture
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                fx.uv.value = Offset(down.position.x / w, down.position.y / h)
                scope.launch {
                    fx.glow.snapTo(0f)
                    fx.glow.animateTo(1f, tween(90))
                    fx.glow.animateTo(0f, tween(450))
                }
                scope.launch {
                    fx.ripple.snapTo(0f)
                    fx.ripple.animateTo(3f, tween(3000, easing = LinearEasing))
                }
            }
        }
    } else {
        Modifier
    }
    Box(modifier.fillMaxSize().background(astraColors.void).then(touchMod)) {
        // Aurora so em Android 13+ (RuntimeShader) e com o toggle ligado (que ja
        // inclui o mestre reduceMotion). Senao, fallback = void + StarField.
        if (auroraShown) {
            AuroraShader(astraColors.accent, tilt = tilt, fx = fx)
        }
        StarField(tilt = tilt)
        CometTrail(trail, astraColors.accent)
        content()
    }
}

// Cauda do cometa: liga os pontos recentes do dedo. Cada segmento pega alpha e
// largura por IDADE (esvai em ~0.5s) e por POSICAO (cauda fina/apagada, cabeca
// grossa/brilhante) + um ponto com glow na cabeca — estilo dos meteoros. O
// ticker so gira enquanto ha pontos vivos (ceu parado = zero custo).
@Composable
private fun CometTrail(points: SnapshotStateList<TrailPoint>, color: Color) {
    val active = points.isNotEmpty()
    val frame = remember { mutableStateOf(0L) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (points.isNotEmpty()) {
            withFrameMillis { frame.value = it }
            val now = System.currentTimeMillis()
            points.removeAll { now - it.bornMs > TRAIL_LIFE_MS }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        frame.value // assina o frame: redesenha a cada quadro enquanto vivo
        val now = System.currentTimeMillis()
        val n = points.size
        if (n == 0) return@Canvas
        val last = n - 1
        for (i in 1..last) {
            val a = points[i]
            val b = points[i - 1]
            val ageF = (1f - (now - a.bornMs).toFloat() / TRAIL_LIFE_MS).coerceIn(0f, 1f)
            if (ageF <= 0f) continue
            val posF = if (last == 0) 1f else i.toFloat() / last
            val alpha = ageF * (0.10f + 0.55f * posF)
            val width = (0.8f + 2.4f * posF).dp.toPx() * ageF
            drawLine(color, b.pos, a.pos, width, StrokeCap.Round, alpha = alpha)
        }
        val head = points[last]
        val headF = (1f - (now - head.bornMs).toFloat() / TRAIL_LIFE_MS).coerceIn(0f, 1f)
        drawCircle(color, 7.dp.toPx(), head.pos, alpha = headF * 0.18f)
        drawCircle(color, 3.2.dp.toPx(), head.pos, alpha = headF)
    }
}

// Compat: as telas continuam usando CosmicBackground como container, mas o ceu
// agora e global (CosmicBackdrop no AstraApp) — aqui e so um Box transparente.
// `interactive` e ignorado: o toque reativo vive no backdrop global.
@Suppress("UNUSED_PARAMETER")
@Composable
fun CosmicBackground(
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), content = content)
}
