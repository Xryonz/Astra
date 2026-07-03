package app.astra.mobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import app.astra.mobile.ui.theme.astraColors

/**
 * Grade oficial de emojis (androidx.emoji2-emojipicker: busca, categorias,
 * recentes — igual Gboard) no painel editorial do app (mesmo padrao do GifPicker:
 * scrim + folha inferior). View clássica embrulhada em AndroidView.
 */
@Composable
fun EmojiPickerSheet(
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val panelShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .imePadding(),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .clip(panelShape)
                .background(astraColors.raised)
                .border(1.dp, astraColors.borderMid, panelShape)
                .pointerInput(Unit) { detectTapGestures {} }
                .padding(top = 10.dp),
        ) {
            AndroidView(
                factory = { ctx ->
                    EmojiPickerView(ctx).apply { emojiGridColumns = 9 }
                },
                update = { view ->
                    view.setOnEmojiPickedListener { item -> onPick(item.emoji) }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
