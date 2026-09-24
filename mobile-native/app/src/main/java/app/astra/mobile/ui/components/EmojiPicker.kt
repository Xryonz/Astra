package app.astra.mobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import app.astra.mobile.core.network.dto.GifResultDto
import app.astra.mobile.core.network.dto.ServerStickerDto
import app.astra.mobile.feature.gif.presentation.PainelDeGifs
import app.astra.mobile.feature.stickers.presentation.PainelDeFigurinhas
import app.astra.mobile.ui.theme.astraColors

enum class AbaDeExpressao { EMOJIS, FIGURINHAS, GIFS }

@Composable
fun EmojiPickerSheet(
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    orbitaId: String? = null,
    abaInicial: AbaDeExpressao = AbaDeExpressao.EMOJIS,
    aoFigurinha: ((ServerStickerDto) -> Unit)? = null,
    aoGif: ((GifResultDto) -> Unit)? = null,
) {
    BackHandler(onBack = onClose)
    val panelShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

    val abas = remember(orbitaId, aoFigurinha, aoGif) {
        buildList {
            add(AbaDeExpressao.EMOJIS)
            if (aoFigurinha != null && !orbitaId.isNullOrBlank()) add(AbaDeExpressao.FIGURINHAS)
            if (aoGif != null) add(AbaDeExpressao.GIFS)
        }
    }
    var aba by remember { mutableStateOf(abaInicial.takeIf { it in abas } ?: AbaDeExpressao.EMOJIS) }

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
                .fillMaxHeight(if (abas.size > 1) 0.62f else 0.55f)
                .clip(panelShape)
                .background(astraColors.raised)
                .border(1.dp, astraColors.borderMid, panelShape)
                .pointerInput(Unit) { detectTapGestures {} }
                .padding(top = 10.dp),
        ) {
            if (abas.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    abas.forEach { opcao ->
                        val ativa = opcao == aba
                        val forma = RoundedCornerShape(12.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(forma)
                                .background(if (ativa) astraColors.accentDim else astraColors.base)
                                .border(1.dp, if (ativa) astraColors.accent else astraColors.border, forma)
                                .clickable { aba = opcao }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = rotuloDaAba(opcao),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (ativa) astraColors.accent else astraColors.text2,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when (aba) {
                AbaDeExpressao.EMOJIS -> AndroidView(
                    factory = { ctx -> EmojiPickerView(ctx).apply { emojiGridColumns = 9 } },
                    update = { view -> view.setOnEmojiPickedListener { item -> onPick(item.emoji) } },
                    modifier = Modifier.fillMaxSize(),
                )
                AbaDeExpressao.FIGURINHAS -> PainelDeFigurinhas(
                    orbitaId = orbitaId.orEmpty(),
                    aoEscolher = { fig -> aoFigurinha?.invoke(fig); onClose() },
                    modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp),
                )
                AbaDeExpressao.GIFS -> PainelDeGifs(
                    aoEscolher = { gif -> aoGif?.invoke(gif); onClose() },
                    modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp),
                )
            }
        }
    }
}

private fun rotuloDaAba(aba: AbaDeExpressao) = when (aba) {
    AbaDeExpressao.EMOJIS -> "Emojis"
    AbaDeExpressao.FIGURINHAS -> "Figurinhas"
    AbaDeExpressao.GIFS -> "GIFs"
}
