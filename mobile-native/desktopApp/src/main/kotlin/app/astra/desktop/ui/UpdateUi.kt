package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Cinzel
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.update.UpdateService
import app.astra.desktop.update.UpdateState
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zed.rainxch.rikkaui.components.ui.progress.Progress
import zed.rainxch.rikkaui.components.ui.progress.ProgressAnimation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val prazoSemNoticias = 15_000L

private const val prazoDoReinicio = 25_000L

@Composable
fun UpdaterGate(updater: UpdateService, reduceMotion: Boolean, onDone: () -> Unit) {
    val st by updater.state.collectAsState()

    val duracaoDaBarra = 1200

    LaunchedEffect(st) {
        when (val s = st) {
            is UpdateState.Available -> updater.downloadAndStage(s)
            is UpdateState.UpToDate -> onDone()
            is UpdateState.Failed -> { delay(1300); onDone() }
            is UpdateState.Ready -> { delay(700); updater.restartToInstall() }
            else -> {}
        }
    }
    LaunchedEffect(st) {
        delay(if (st is UpdateState.Ready) prazoDoReinicio else prazoSemNoticias)
        onDone()
    }

    val entrance: State<Float>? = if (reduceMotion) null else {
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { started = true }
        animateFloatAsState(
            targetValue = if (started) 1f else 0f,
            animationSpec = tween(1100, easing = LinearEasing),
            label = "gateEntrance",
        )
    }

    val realProgress = (st as? UpdateState.Downloading)?.progress
    val syntheticFill = run {
        var go by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { go = true }
        animateFloatAsState(
            targetValue = if (go) 1f else 0f,
            animationSpec = tween(duracaoDaBarra, easing = LinearEasing),
            label = "gateFill",
        )
    }
    val barProgress = realProgress ?: syntheticFill.value
    val rotulo = when (val s = st) {
        is UpdateState.Available   -> Rotulo("nova versão ${s.version}")
        is UpdateState.Downloading -> Rotulo("baixando ${s.version}", baixando = true)
        is UpdateState.Ready       -> Rotulo("reiniciando para aplicar")
        is UpdateState.Failed      -> Rotulo(s.reason)
        else                       -> Rotulo(spaceWord(barProgress))
    }

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Obsidian.void)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        StarField(Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            Obsidian.accent.copy(alpha = 0.10f),
                            Obsidian.accent.copy(alpha = 0.03f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height * 0.34f),
                        radius = size.minDimension * 0.62f,
                    ),
                )
            },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 24.dp).fillMaxWidth(),
        ) {
            RotatingStarsLogo(reduceMotion, entrance = entrance, planetRes = "astra-glyph.png")
            Spacer(Modifier.height(18.dp))
            Text(
                "ASTRA",
                style = TextStyle(
                    color = Obsidian.text1,
                    fontSize = 22.sp,
                    fontFamily = Cinzel,
                    letterSpacing = 3.5.sp,
                ),
                modifier = Modifier.graphicsLayer {
                    val ms = (entrance?.value ?: 1f) * 2000f
                    alpha = ((ms - 1500f) / 500f).coerceIn(0f, 1f)
                },
            )
            Spacer(Modifier.height(16.dp))
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                ThinProgress(barProgress, rotulo, realProgress?.let { (it * 100).toInt() }, reduceMotion)
            }
        }
    }
}

@Composable
internal fun ThinProgress(progress: Float, rotulo: Rotulo, percent: Int?, reduceMotion: Boolean) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.dissolverNasBordas(ZONA_ESCURA)) {
            AnimatedContent(
                targetState = rotulo,
                transitionSpec = {
                    val dur = if (reduceMotion) 0 else 1
                    (
                        fadeIn(tween(240 * dur, delayMillis = 190 * dur)) +
                            slideInHorizontally(tween(340 * dur, delayMillis = 190 * dur, easing = EaseOutStd)) { -it / 3 }
                        ).togetherWith(
                        fadeOut(tween(200 * dur)) +
                            slideOutHorizontally(tween(260 * dur, easing = EaseOutStd)) { it / 3 },
                    ) using SizeTransform(clip = false)
                },
                label = "palavraEspacial",
            ) { r ->
                Row(
                    Modifier.padding(horizontal = ZONA_ESCURA),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        r.texto,
                        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, letterSpacing = 0.4.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (r.baixando) {
                        Spacer(Modifier.width(7.dp))
                        TypingDots(Obsidian.text3, dotSize = 3.dp)
                        Spacer(Modifier.width(9.dp))
                        Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                "${percent ?: 0}%",
                                style = TextStyle(color = Obsidian.accent, fontSize = 11.sp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Canvas(Modifier.fillMaxWidth(0.62f).height(2.dp)) {
            val h = size.height
            val cr = CornerRadius(h / 2f)
            drawRoundRect(color = Obsidian.raised, size = size, cornerRadius = cr)
            val w = (size.width * progress).coerceIn(0f, size.width)
            if (w > 0f) {
                drawRoundRect(color = Obsidian.accent, size = Size(w, h), cornerRadius = cr)
                drawCircle(color = Obsidian.accent.copy(alpha = 0.5f), radius = h * 1.6f, center = Offset(w, h / 2f))
            }
        }
    }
}

internal data class Rotulo(val texto: String, val baixando: Boolean = false)

internal val ZONA_ESCURA = 26.dp

internal fun Modifier.dissolverNasBordas(zona: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (size.width <= 0f) return@drawWithContent
        val f = (zona.toPx() / size.width).coerceIn(0f, 0.49f)
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                f to Color.Black,
                1f - f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

internal val SPACE_WORDS = listOf(
    "acordando o cosmos",
    "alinhando órbitas",
    "traçando a rota estelar",
    "calibrando constelações",
    "quase lá",
)

internal fun spaceWord(progress: Float): String =
    SPACE_WORDS[(progress * SPACE_WORDS.size).toInt().coerceIn(0, SPACE_WORDS.lastIndex)]

@Composable
internal fun RotatingStarsLogo(reduceMotion: Boolean, diameter: Dp = 150.dp, entrance: State<Float>? = null, planetRes: String = "astra-icon.png") {
    val accent = Obsidian.accent
    val twoPi = (2.0 * PI).toFloat()
    val phaseState = if (reduceMotion || !LocalWindowActive.current) null else {
        rememberInfiniteTransition(label = "orbit").animateFloat(
            initialValue = 0f,
            targetValue = twoPi,
            animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
            label = "phase",
        )
    }
    val count = 14
    val tilt = (-12.0 * PI / 180.0).toFloat()

    fun DrawScope.orbitPos(theta: Float): Offset {
        val half = size.minDimension / 2f
        val rx = half * 0.92f
        val ry = half * 0.30f
        val ex = rx * cos(theta)
        val ey = ry * sin(theta)
        return Offset(
            center.x + ex * cos(tilt) - ey * sin(tilt),
            center.y + ex * sin(tilt) + ey * cos(tilt),
        )
    }
    fun DrawScope.scatterPos(i: Int): Offset {
        val half = size.minDimension / 2f
        val ang = i * 2.4f
        val rad = half * (1.5f + 0.6f * ((i * 53) % 5) / 4f)
        return Offset(center.x + rad * cos(ang), center.y + rad * sin(ang))
    }
    fun DrawScope.drawRing(front: Boolean) {
        val phase = phaseState?.value ?: 0.9f
        val gather = ((entrance?.value ?: 1f) * 2000f / 1100f).coerceIn(0f, 1f)
            .let { FastOutSlowInEasing.transform(it) }
        repeat(count) { i ->
            val theta = phase + i * (twoPi / count)
            val depth = sin(theta)
            if ((depth > 0f) != front) return@repeat
            val target = orbitPos(theta)
            val p = if (gather >= 1f) target else lerp(scatterPos(i), target, gather)
            val t01 = (depth + 1f) / 2f
            val lead = i % 5 == 0
            val entryAlpha = if (gather >= 1f) 1f else 0.15f + 0.85f * gather
            drawCircle(
                color = accent.copy(alpha = (0.30f + 0.70f * t01) * entryAlpha),
                radius = (1.3f + 2.1f * t01).dp.toPx() * (if (lead) 1.35f else 1f),
                center = p,
            )
        }
    }
    fun DrawScope.drawConstellationLines() {
        val e = entrance?.value ?: return
        val phase = phaseState?.value ?: 0.9f
        val ms = e * 2000f
        val gather = (ms / 1100f).coerceIn(0f, 1f).let { FastOutSlowInEasing.transform(it) }
        val settle = ((ms - 1100f) / 600f).coerceIn(0f, 1f)
        val lineAlpha = gather * (1f - settle)
        if (lineAlpha <= 0f) return
        val pts = (0 until count).map { i ->
            val theta = phase + i * (twoPi / count)
            lerp(scatterPos(i), orbitPos(theta), gather)
        }
        for (i in 0 until count) {
            drawLine(
                color = accent.copy(alpha = 0.16f * lineAlpha),
                start = pts[i],
                end = pts[(i + 1) % count],
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            drawConstellationLines()
            drawRing(front = false)
        }
        Image(
            painter = painterResource(planetRes),
            contentDescription = null,
            modifier = Modifier
                .size(diameter * 0.52f)
                .graphicsLayer {
                    val ms = (entrance?.value ?: 1f) * 2000f
                    val a = ((ms - 1100f) / 600f).coerceIn(0f, 1f)
                    alpha = a
                    scaleX = 0.85f + 0.15f * a
                    scaleY = 0.85f + 0.15f * a
                },
        )
        Canvas(Modifier.size(diameter)) { drawRing(front = true) }
    }
}

@Composable
private fun PillButton(label: String, accent: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Text(
        label,
        style = TextStyle(color = if (accent) Obsidian.accent else Obsidian.text3, fontSize = 12.sp),
        modifier = Modifier
            .clickScale(src)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (accent) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private val LARGURA_AVISO = 240.dp

@Composable
fun BoxScope.UpdateBanner(updater: UpdateService) {
    val st by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }
    val show = !dismissed && (
        st is UpdateState.Available || st is UpdateState.Downloading || st is UpdateState.Ready
    )
    AnimatedVisibility(
        visible = show,
        enter = slideInVertically(tween(240, easing = EaseOutStd)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(180, easing = EaseOutStd)) { it } + fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp),
    ) {
        Column(
            Modifier
                .width(LARGURA_AVISO)
                .clip(RoundedCornerShape(10.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            when (val s = st) {
                is UpdateState.Available -> {
                    TituloDoAviso("Astra ${s.version} disponível", onClose = { dismissed = true })
                    Spacer(Modifier.height(10.dp))
                    AcaoDoAviso("atualizar") { scope.launch { updater.downloadAndStage(s) } }
                }
                is UpdateState.Downloading -> {
                    TituloDoAviso("baixando ${s.version} · ${(s.progress * 100).toInt()}%", onClose = null)
                    Spacer(Modifier.height(10.dp))
                    Progress(
                        s.progress,
                        Modifier.fillMaxWidth(),
                        Obsidian.accent,
                        Obsidian.overlay,
                        5.dp,
                        ProgressAnimation.Spring,
                    )
                }
                is UpdateState.Ready -> {
                    TituloDoAviso("${s.version} pronto — reinicie para aplicar", onClose = null)
                    Spacer(Modifier.height(10.dp))
                    AcaoDoAviso("reiniciar") { updater.restartToInstall() }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun TituloDoAviso(texto: String, onClose: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.Top) {
        LIcon(Lucide.ArrowUp, tint = Obsidian.accent, size = 15.dp)
        Spacer(Modifier.width(9.dp))
        Text(
            texto,
            style = TextStyle(color = Obsidian.text1, fontSize = 12.5.sp, lineHeight = 17.sp),
            modifier = Modifier.weight(1f),
        )
        if (onClose != null) {
            Spacer(Modifier.width(4.dp))
            BannerClose(onClose)
        }
    }
}

@Composable
private fun AcaoDoAviso(rotulo: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        PillButton(rotulo, accent = true, onClick = onClick)
    }
}

@Composable
private fun BannerClose(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(26.dp)
            .clickScale(src)
            .clip(RoundedCornerShape(7.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(Lucide.X, tint = Obsidian.text3, size = 14.dp, rotulo = "dispensar")
    }
}
