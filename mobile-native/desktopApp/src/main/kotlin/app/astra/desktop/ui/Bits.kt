package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import app.astra.desktop.ui.theme.EaseOutStd
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.astra.desktop.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import coil3.compose.AsyncImage
import app.astra.desktop.net.RedeLog
import coil3.compose.AsyncImagePainter
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

val FormaDeBotao = RoundedCornerShape(8.dp)

@Composable
fun Modifier.clickScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
    formaDoFoco: Shape = RoundedCornerShape(8.dp),
): Modifier {
    val reduce = LocalReduceMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    var focado by remember { mutableStateOf(false) }
    val modoDeEntrada = LocalInputModeManager.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "clickScale",
    )
    val brilho by animateFloatAsState(
        targetValue = if (pressed && !reduce) 1f else 0f,
        animationSpec = tween(durationMillis = if (pressed) 90 else 320),
        label = "clickGlow",
    )
    val corDoFoco = Obsidian.accent
    return onFocusChanged { focado = it.isFocused }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .drawBehind {
            if (brilho <= 0.01f) return@drawBehind
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        corDoFoco.copy(alpha = 0.34f * brilho),
                        corDoFoco.copy(alpha = 0.10f * brilho),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = size.minDimension * 0.95f,
                ),
                radius = size.minDimension * 0.95f,
            )
        }
        .drawWithContent {
            drawContent()
            if (!focado || modoDeEntrada.inputMode != InputMode.Keyboard) return@drawWithContent
            val traco = Stroke(width = 2.dp.toPx())
            when (val contorno = formaDoFoco.createOutline(size, layoutDirection, this)) {
                is Outline.Rectangle -> drawRect(corDoFoco, style = traco)
                is Outline.Rounded -> drawPath(Path().apply { addRoundRect(contorno.roundRect) }, corDoFoco, style = traco)
                is Outline.Generic -> drawPath(contorno.path, corDoFoco, style = traco)
            }
        }
}

@Composable
fun CartaoInterno(
    modifier: Modifier = Modifier,
    fundo: Color = Obsidian.raised,
    borda: Boolean = true,
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val forma = RoundedCornerShape(8.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(forma)
            .background(fundo)
            .then(if (borda) Modifier.border(1.dp, Obsidian.borderDim, forma) else Modifier)
            .padding(padding),
        content = conteudo,
    )
}

@Composable
fun LIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Obsidian.text2,
    size: Dp = 16.dp,
    rotulo: String? = null,
) {
    Image(
        imageVector = icon,
        contentDescription = rotulo,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun DesktopAvatar(url: String?, name: String, sizeDp: Int) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clip(CircleShape).background(Obsidian.overlay),
            contentAlignment = Alignment.Center,
        ) {
            if (!url.isNullOrBlank() && !imagemMorreu(url)) {
                AsyncImage(
                    model = url,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onState = { lembrarQueMorreu(url, it) },
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    style = TextStyle(color = Obsidian.accent, fontSize = (sizeDp * 0.42f).sp),
                )
            }
        }
    }
}

val LocalReduceMotion = compositionLocalOf { false }

data class MinhaConta(val id: String? = null, val usuario: String? = null)
val LocalMinhaConta = staticCompositionLocalOf { MinhaConta() }

class MencaoClicavel {
    var abrir: (usuario: String) -> Unit = {}
}
val LocalMencaoClicavel = staticCompositionLocalOf { MencaoClicavel() }

val LocalWindowActive = compositionLocalOf { true }

val LocalJanelaNaTela = compositionLocalOf { true }

data class RenderPrefs(val auroraOctaves: Int = 3, val fpsCap: Int = 0)
val LocalRenderPrefs = staticCompositionLocalOf { RenderPrefs() }

val LocalMsgFontScale = staticCompositionLocalOf { 1f }
data class MsgDensity(val topDp: Int = 10, val groupedTopDp: Int = 2)
val LocalMsgDensity = staticCompositionLocalOf { MsgDensity() }

private const val CASCADE_MAX = 14

@Composable
fun CascadeIn(
    index: Int,
    listKey: Any?,
    stepMs: Long = 26L,
    startDelayMs: Long = 0L,
    translateY: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    val semMovimento = LocalReduceMotion.current
    val animate = index in 0 until CASCADE_MAX
    val enter = remember(listKey) { Animatable(if (animate && !semMovimento) 0f else 1f) }
    LaunchedEffect(listKey, semMovimento) {
        if (enter.value >= 1f) return@LaunchedEffect
        if (semMovimento) {
            enter.snapTo(1f)
            return@LaunchedEffect
        }
        delay(startDelayMs + index * stepMs)
        enter.animateTo(1f, tween(230, easing = EaseOutStd))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = enter.value
            translationY = (1f - enter.value) * translateY.toPx()
        },
    ) {
        content()
    }
}

@Composable
fun TypingDots(color: Color = Obsidian.text3, dotSize: Dp = 4.dp) {
    if (LocalReduceMotion.current) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { Box(Modifier.size(dotSize).clip(CircleShape).background(color)) }
        }
        return
    }
    val transition = rememberInfiniteTransition()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(3) { i ->
            val dy by transition.animateFloat(
                initialValue = 0f,
                targetValue = -3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(280, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 140),
                ),
            )
            Box(
                Modifier
                    .size(dotSize)
                    .graphicsLayer { translationY = dy * density }
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
fun PopIn(content: @Composable () -> Unit) {
    val semMovimento = LocalReduceMotion.current
    val vis = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = vis,
        enter = if (semMovimento) EnterTransition.None else scaleIn(
            animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
            initialScale = 0.4f,
        ) + fadeIn(tween(120)),
        exit = if (semMovimento) ExitTransition.None else
            scaleOut(tween(150), targetScale = 0.55f) + fadeOut(tween(130)),
    ) {
        content()
    }
}

private val urlsMortas = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > 512
}

internal fun imagemMorreu(url: String?): Boolean =
    url != null && synchronized(urlsMortas) { urlsMortas.containsKey(url) }

internal fun lembrarQueMorreu(url: String?, estado: AsyncImagePainter.State) {
    if (url == null || estado !is AsyncImagePainter.State.Error) return
    val novidade = synchronized(urlsMortas) { urlsMortas.put(url, true) == null }
    if (novidade) RedeLog.imagemMorreu(url)
}
