package app.astra.mobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.ui.theme.EaseOutSoft
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun Lightbox(images: List<Attachment>, startIndex: Int, onDismiss: () -> Unit) {
    val att = images.getOrNull(startIndex) ?: return
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(260, easing = EaseOutSoft)) }

    val close: () -> Unit = {
        scope.launch {
            progress.animateTo(0f, tween(180, easing = EaseOutSoft))
            onDismiss()
        }
    }
    BackHandler(onBack = close)

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress.value }
            .background(Color.Black.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = att.url,
            contentDescription = att.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (scale > 1.02f) { scale = 1f; offset = Offset.Zero } else close()
                        },
                        onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                }
                .graphicsLayer {
                    val open = 0.85f + 0.15f * progress.value
                    scaleX = scale * open
                    scaleY = scale * open
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
