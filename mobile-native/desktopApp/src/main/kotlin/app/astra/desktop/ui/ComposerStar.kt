package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

// Botoes do compositor.
//
// Antes existia UMA estrela ✦ que abria um menu com emoji/GIF/arquivo. Virou o
// contrario (pedido do dono, padrao Discord): os seletores ficam A MOSTRA na
// barra e o '+' passa a ser o menu do que "cria coisa". A estrela foi removida
// junto — deixar um botao sem chamador so serviria pra confundir depois.
//
// Regra que continua valendo: UM Popup por botao, nunca popup dentro de popup.
// No desktop cada Popup focavel e uma janela de verdade, e empilhar duas rouba o
// foco da primeira.
internal enum class Seletor { EMOJI, GIF, FIGURINHA }

// Ancora o painel ACIMA do botao. O lado importa: alinhar SEMPRE pela direita
// funcionava quando o unico botao era a estrela, no canto direito do compositor.
// O '+' mora no canto ESQUERDO — alinhado pela direita, o menu era empurrado
// pra fora da barra, sobrando pra esquerda do botao. Cada botao pede a borda em
// que ele encosta. Clampa pra não sair da janela nos dois casos.
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

// A moldura comum dos botoes: quadrado de 28, SEM fundo — no hover a borda e o
// glifo acendem no accent, sem preencher (estilo pedido pelo dono).
//
// comBorda = false no '+' (tambem pedido do dono). Faz sentido alem do gosto: a
// borda agrupa os botoes que ABREM UM PAINEL (emoji, GIF). O '+' abre um menu e
// a seta envia — acoes de outra natureza, e ficarem sem moldura separa os grupos
// sem precisar de linha divisoria.
@Composable
private fun MolduraDoCompositor(
    onClick: () -> Unit,
    comBorda: Boolean = true,
    conteudo: @Composable (aceso: Boolean) -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (comBorda) Modifier.border(
                    1.dp,
                    if (hov) Obsidian.accentDim else Obsidian.borderDim,
                    RoundedCornerShape(8.dp),
                ) else Modifier,
            )
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { conteudo(hov) }
}

@Composable
private fun IconeDoCompositor(
    glifo: String,
    tamanho: Int = 13,
    comBorda: Boolean = true,
    onClick: () -> Unit,
) {
    MolduraDoCompositor(onClick, comBorda) { hov ->
        Text(
            glifo,
            style = TextStyle(color = if (hov) Obsidian.accent else Obsidian.text3, fontSize = tamanho.sp),
        )
    }
}

// Icone de TRAÇO (Lucide), nao glifo de texto.
//
// O emoji era o caractere "☺": a fonte de emoji do Windows sequestra esse ponto
// de codigo e desenha a bolinha amarela PREENCHIDA — cor propria, fundo proprio,
// nada a ver com o resto da barra. Nenhum ajuste de cor resolve, porque quem
// pinta e a fonte, nao nos. Icone vetorial de traco obedece o tint.
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

// Abre DIRETO no painel pedido (emoji, GIF ou figurinha) — sem passar por menu.
//
// serverId so importa pra figurinha: elas pertencem a uma constelacao, e em
// sussurro nao ha de onde tirar. Quem chama nao oferece o botao la (ChatView).
@Composable
internal fun ComposerPickerButton(
    tipo: Seletor,
    serverId: String? = null,
    onPickEmoji: (String) -> Unit = {},
    onPickGif: (GifResultDto) -> Unit = {},
    onPickSticker: (ServerStickerDto) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box {
        // "GIF" continua sendo TEXTO de proposito: e uma sigla, nao um desenho —
        // e todo cliente de chat escreve GIF em vez de tentar desenhar um.
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
                    // Emoji fica aberto pra escolher varios; GIF e figurinha
                    // fecham porque escolher JA ENVIA.
                    when (tipo) {
                        Seletor.GIF -> GifPanel(onPick = { g -> open = false; onPickGif(g) })
                        Seletor.FIGURINHA -> if (serverId != null) {
                            StickerPanel(serverId, onPick = { f -> open = false; onPickSticker(f) })
                        }
                        Seletor.EMOJI -> ReactionPicker(onPick = onPickEmoji)
                    }
                }
            }
        }
    }
}

// O '+' agora e menu, nao atalho de anexo.
//
// onCriarEnquete = null em sussurro: enquete so existe em canal no backend, e
// oferecer um item que sempre falha e pior que nao ter o item.
@Composable
fun ComposerPlusButton(
    onPickFiles: (List<File>) -> Unit,
    onCriarEnquete: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconeDoCompositor("+", tamanho = 17, comBorda = false) { open = !open }
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
                            // Fecha ANTES: o dialog nativo e modal e brigaria por
                            // foco com a janela do Popup.
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

// Seletor nativo do SO, multi-arquivo. Modal (bloqueia) — padrao de file dialog.
// Vazio = cancelou. Internal: o '+' do composer (ChatView) reusa o mesmo seletor.
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
