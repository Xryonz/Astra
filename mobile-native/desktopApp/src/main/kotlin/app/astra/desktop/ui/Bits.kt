package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import app.astra.desktop.ui.theme.EaseOutStd
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import app.astra.desktop.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import coil3.compose.AsyncImage

// Avatar circular com fallback de inicial — usado no shell e no chat.
@Composable
fun DesktopAvatar(url: String?, name: String, sizeDp: Int) {
    Box(
        modifier = Modifier.size(sizeDp.dp).clip(CircleShape).background(Obsidian.overlay),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                style = TextStyle(color = Obsidian.accent, fontSize = (sizeDp * 0.42f).sp),
            )
        }
    }
}

// Entrada em cascata (F6): itens de lista revelam um a um (fade + subida leve).
// GPU-only (alpha/translation em graphicsLayer). So os primeiros CASCADE_MAX
// indices animam — item que entra por scroll aparece pronto (LazyColumn recicla).
private const val CASCADE_MAX = 14

@Composable
fun CascadeIn(index: Int, listKey: Any?, content: @Composable () -> Unit) {
    val animate = index in 0 until CASCADE_MAX
    val enter = remember(listKey) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(listKey) {
        if (enter.value < 1f) {
            delay(index * 26L)
            enter.animateTo(1f, tween(230, easing = EaseOutStd))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = enter.value
            translationY = (1f - enter.value) * 10.dp.toPx()
        },
    ) {
        content()
    }
}

// Tres pontinhos em onda (bounce sequencial) — "digitando…" no chat e sidebar.
@Composable
fun TypingDots(color: Color = Obsidian.text3, dotSize: Dp = 4.dp) {
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
