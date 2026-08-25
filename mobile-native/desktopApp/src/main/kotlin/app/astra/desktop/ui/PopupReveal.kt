package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import app.astra.desktop.ui.theme.EaseOutStd

private const val REVEAL_MS = 150

@Composable
fun PopupReveal(
    originX: Float = 0f,
    originY: Float = 0f,
    initialScale: Float = 0.94f,
    content: @Composable () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val entered = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = entered,
        enter = if (reduce) {
            fadeIn(tween(0))
        } else {
            fadeIn(tween(REVEAL_MS, easing = EaseOutStd)) +
                scaleIn(
                    tween(REVEAL_MS, easing = EaseOutStd),
                    initialScale = initialScale,
                    transformOrigin = TransformOrigin(originX, originY),
                )
        },
    ) {
        content()
    }
}

@Composable
fun Modifier.popupReveal(
    originX: Float = 0f,
    originY: Float = 0f,
    initialScale: Float = 0.94f,
): Modifier {
    val reduce = LocalReduceMotion.current
    val p = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (p.value < 1f) p.animateTo(1f, tween(REVEAL_MS, easing = EaseOutStd))
    }
    return this.graphicsLayer {
        alpha = p.value
        val s = initialScale + (1f - initialScale) * p.value
        scaleX = s
        scaleY = s
        transformOrigin = TransformOrigin(originX, originY)
    }
}
