package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.EmojiDto
import app.astra.mobile.core.network.dto.GifResultDto
import app.astra.mobile.core.network.dto.ServerStickerDto
import com.composables.icons.lucide.ChartNoAxesColumn
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Paperclip
import com.composables.icons.lucide.Smile
import com.composables.icons.lucide.Sticker
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

internal enum class Seletor { EMOJI, GIF, FIGURINHA }

private class AcimaDoBotao(private val pelaDireita: Boolean) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (pelaDireita) anchorBounds.right - popupContentSize.width else anchorBounds.left
        return IntOffset(
            x = x.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)),
            y = (anchorBounds.top - popupContentSize.height - 8).coerceAtLeast(0),
        )
    }
}

private val AcimaPelaDireita = AcimaDoBotao(pelaDireita = true)
private val AcimaPelaEsquerda = AcimaDoBotao(pelaDireita = false)

@Composable
private fun MolduraDoCompositor(
    onClick: () -> Unit,
    conteudo: @Composable (aceso: Boolean) -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { conteudo(hov) }
}

@Composable
private fun IconeDoCompositor(
    glifo: String,
    tamanho: Int = 13,
    onClick: () -> Unit,
) {
    MolduraDoCompositor(onClick) { hov ->
        Text(
            glifo,
            style = TextStyle(color = if (hov) Obsidian.accent else Obsidian.text3, fontSize = tamanho.sp),
        )
    }
}

@Composable
private fun IconeVetorial(icone: ImageVector, onClick: () -> Unit) {
    MolduraDoCompositor(onClick) { hov ->
        LIcon(
            icone,
            tint = if (hov) Obsidian.accent else Obsidian.text3,
            size = 15.dp,
        )
    }
}

@Composable
internal fun ComposerPickerButton(
    tipo: Seletor,
    serverId: String? = null,
    onPickEmoji: (String) -> Unit = {},
    onPickGif: (GifResultDto) -> Unit = {},
    onPickSticker: (ServerStickerDto) -> Unit = {},
    emojisDaSala: List<EmojiDto> = emptyList(),
) {
    var open by remember { mutableStateOf(false) }
    Box {
        when (tipo) {
            Seletor.GIF -> IconeDoCompositor("GIF", tamanho = 9) { open = !open }
            Seletor.FIGURINHA -> IconeVetorial(Lucide.Sticker) { open = !open }
            Seletor.EMOJI -> IconeVetorial(Lucide.Smile) { open = !open }
        }
        if (open) {
            Popup(
                popupPositionProvider = AcimaPelaDireita,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                PopupReveal(originX = 1f, originY = 1f) {
                    when (tipo) {
                        Seletor.GIF -> GifPanel(onPick = { g -> open = false; onPickGif(g) })
                        Seletor.FIGURINHA -> if (serverId != null) {
                            StickerPanel(serverId, onPick = { f -> open = false; onPickSticker(f) })
                        }
                        Seletor.EMOJI -> ReactionPicker(onPick = onPickEmoji, personalizados = emojisDaSala)
                    }
                }
            }
        }
    }
}

@Composable
fun ComposerPlusButton(
    onPickFiles: (List<File>) -> Unit,
    onCriarEnquete: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconeDoCompositor("+", tamanho = 17) { open = !open }
        if (open) {
            Popup(
                popupPositionProvider = AcimaPelaEsquerda,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                PopupReveal(originX = 0f, originY = 1f) {
                    Column(
                        Modifier
                            .shadow(8.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Obsidian.overlay)
                            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                            .padding(5.dp)
                            .width(178.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        MenuRow(Lucide.Paperclip, "enviar um arquivo") {
                            open = false
                            val files = chooseFiles()
                            if (files.isNotEmpty()) onPickFiles(files)
                        }
                        if (onCriarEnquete != null) {
                            MenuRow(Lucide.ChartNoAxesColumn, "criar enquete") {
                                open = false
                                onCriarEnquete()
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun chooseFiles(): List<File> {
    val dlg = FileDialog(null as Frame?, "Enviar arquivo", FileDialog.LOAD)
    dlg.isMultipleMode = true
    dlg.isVisible = true
    return dlg.files?.toList().orEmpty()
}

@Composable
private fun MenuRow(icone: ImageVector, label: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (hov) Obsidian.hover else Color.Transparent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(icone, tint = if (hov) Obsidian.accent else Obsidian.text3, size = 14.dp)
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            style = TextStyle(
                color = if (hov) Obsidian.text1 else Obsidian.text2,
                fontSize = 12.sp,
                fontFamily = DmMono,
            ),
        )
    }
}
