package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
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
import app.astra.desktop.ui.theme.Text
import app.astra.shared.AstraShared
import coil3.compose.AsyncImage
import java.awt.Desktop
import java.net.URI

// Lightbox (F5): visualizador de imagem dentro do app — scroll da zoom, arrastar
// move, duplo-clique reseta, ESC/clique-fora fecha; acoes de abrir/copiar link.

// Abrir imagem no lightbox de qualquer profundidade (evita callback por 3 camadas).
val LocalOpenImage = staticCompositionLocalOf<(String) -> Unit> { {} }

fun absoluteUrl(url: String): String =
    if (url.startsWith("/")) AstraShared.BASE_URL.trimEnd('/') + url else url

private object FillWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Lightbox(url: String, onClose: () -> Unit) {
    val abs = remember(url) { absoluteUrl(url) }
    val clipboard = LocalClipboardManager.current
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Popup(
        popupPositionProvider = FillWindow,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { e ->
            if (e.key == Key.Escape && e.type == KeyEventType.KeyDown) {
                onClose(); true
            } else {
                false
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.84f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                )
                .onPointerEvent(PointerEventType.Scroll) { e ->
                    val delta = e.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
                    scale = (scale * if (delta < 0) 1.12f else 0.89f).coerceIn(0.4f, 6f)
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = abs,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    // Clique na imagem nao fecha; arrastar move; duplo-clique reseta.
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }, onTap = {})
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            offset += drag
                        }
                    },
            )
            Row(
                Modifier.align(Alignment.TopEnd).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                var copied by remember { mutableStateOf(false) }
                LightboxAction("abrir no navegador") {
                    runCatching { Desktop.getDesktop().browse(URI(abs)) }
                }
                LightboxAction(if (copied) "copiado ✓" else "copiar link") {
                    clipboard.setText(AnnotatedString(abs))
                    copied = true
                }
                LightboxAction("✕", onClose)
            }
        }
    }
}

@Composable
private fun LightboxAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Obsidian.raised.copy(alpha = 0.85f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
