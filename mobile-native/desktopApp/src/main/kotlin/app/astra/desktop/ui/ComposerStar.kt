package app.astra.desktop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
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
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

// Estrela do compositor: UM botao no lugar dos dois soltos (emoji e GIF). Clicar
// gira a estrela e abre um menu PRA CIMA com as duas opções; escolher troca o
// conteudo do MESMO popup pelo seletor correspondente.
//
// Um popup so (maquina de estados) em vez de popup dentro de popup: no desktop
// cada Popup focavel e uma janela de verdade, e empilhar duas rouba o foco da
// primeira — o mesmo tipo de armadilha que já congelou a aurora quando ela era
// gateada por foco.
private enum class StarPane { MENU, EMOJI, GIF }

// Ancora o painel ACIMA do botao, alinhado pela direita (o botao vive no canto
// direito do compositor). Clampa pra não sair da janela.
private object StarAbove : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = (anchorBounds.right - popupContentSize.width).coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)),
        y = (anchorBounds.top - popupContentSize.height - 8).coerceAtLeast(0),
    )
}

@Composable
fun ComposerStarButton(
    onPickEmoji: (String) -> Unit,
    onPickGif: (GifResultDto) -> Unit,
    onPickFiles: (List<File>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var pane by remember { mutableStateOf(StarPane.MENU) }
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val reduce = LocalReduceMotion.current

    // A animação do clique: a estrela gira um oitavo de volta e cresce. Mola com
    // pouco amortecimento pra dar o "tec" de mola sem virar brinquedo. Nada de
    // animação continua — comeca no clique e termina.
    val spin by animateFloatAsState(
        targetValue = if (open) 45f else 0f,
        animationSpec = if (reduce) tween(0) else spring(
            dampingRatio = 0.42f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "starSpin",
    )
    val scale by animateFloatAsState(
        targetValue = if (open) 1.18f else if (hov) 1.08f else 1f,
        animationSpec = if (reduce) tween(0) else spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "starScale",
    )

    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (open || hov) Obsidian.hover else Color.Transparent)
                .hoverable(src)
                .clickable(interactionSource = src, indication = null) {
                    // Reabrir sempre cai no menu: quem fechou no seletor de GIF não
                    // quer voltar direto pra ele na próxima mensagem.
                    if (!open) pane = StarPane.MENU
                    open = !open
                },
            contentAlignment = Alignment.Center,
        ) {
            // O MESMO glifo da rail dos sussurros e do palco vazio — a estrela já e
            // a marca do app, entao o botao não introduz simbolo novo.
            // graphicsLayer: gira/escala na composicao GPU, sem relayout do texto.
            Text(
                "✦",
                style = TextStyle(
                    color = if (open || hov) Obsidian.accent else Obsidian.text3,
                    fontSize = 17.sp,
                ),
                modifier = Modifier.graphicsLayer {
                    rotationZ = spin
                    scaleX = scale
                    scaleY = scale
                },
            )
        }
        if (open) {
            Popup(
                popupPositionProvider = StarAbove,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                PopupReveal(originX = 1f, originY = 1f) {
                    when (pane) {
                        StarPane.MENU -> StarMenu(
                            onEmoji = { pane = StarPane.EMOJI },
                            onGif = { pane = StarPane.GIF },
                            // Arquivo abre o seletor NATIVO do SO (não um pane): fecha o
                            // popup antes pra o dialog modal não brigar por foco com a
                            // janela do Popup. Reusa o mesmo pipeline do arrastar-e-soltar
                            // (vm.addFiles -> anexo pendente no composer).
                            onFile = {
                                open = false
                                val files = chooseFiles()
                                if (files.isNotEmpty()) onPickFiles(files)
                            },
                        )
                        // Emoji fica aberto pra escolher varios (mesmo comportamento de
                        // antes); GIF fecha porque escolher JA ENVIA.
                        StarPane.EMOJI -> ReactionPicker(onPick = onPickEmoji)
                        StarPane.GIF -> GifPanel(onPick = { g ->
                            open = false
                            onPickGif(g)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun StarMenu(onEmoji: () -> Unit, onGif: () -> Unit, onFile: () -> Unit) {
    Column(
        Modifier
            .shadow(8.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(5.dp)
            // Largura FIXA: dentro do Popup o fillMaxWidth das linhas resolve pro
            // máximo disponível (= janela inteira). Sem teto, a Column esticava a
            // largura toda do app. Fixo = menu compacto ancorado sob o botao.
            .width(150.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StarMenuRow("☺", "emoji", onEmoji)
        StarMenuRow("▦", "GIF", onGif)
        StarMenuRow("▤", "arquivo", onFile)
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
private fun StarMenuRow(glyph: String, label: String, onClick: () -> Unit) {
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
        Text(
            glyph,
            style = TextStyle(color = if (hov) Obsidian.accent else Obsidian.text3, fontSize = 13.sp),
        )
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
