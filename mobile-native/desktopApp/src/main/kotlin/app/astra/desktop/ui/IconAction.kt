package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.LoaderCircle
import com.composables.icons.lucide.Lucide
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import kotlinx.coroutines.delay

// Acao virada em ICONE, com o nome dela aparecendo ao parar o mouse.
//
// Nasceu do trio do banner ("subir banner" / "reenquadrar" / "remover banner"), que
// ocupava a largura inteira do painel e ainda quebrava em duas linhas no botao mais
// comprido. Tres quadrados de 34dp fazem o mesmo trabalho em um terco do espaco.
//
// A DICA NAO E OPCIONAL. Icone sozinho e adivinhacao: um quadrado com uma seta pode
// ser "subir imagem", "exportar" ou "mover pra cima", e quem nao acertar de primeira
// vai clicar pra descobrir — num botao que APAGA o banner, descobrir clicando e caro
// demais. A dica so custa parar o mouse meio segundo.
private const val ESPERA_DICA_MS = 420L

@Composable
fun BotaoIcone(
    icone: ImageVector,
    dica: String,
    accent: Boolean = false,
    danger: Boolean = false,
    // Trabalhando: troca o glifo por um giro e recusa o clique. Sem isto, subir uma
    // imagem grande deixa o botao com cara de "nao aconteceu nada" e a pessoa clica
    // de novo — dois uploads da mesma foto.
    ocupado: Boolean = false,
    onClick: () -> Unit,
) {
    val interacao = remember { MutableInteractionSource() }
    val sobHover by interacao.collectIsHoveredAsState()
    var mostrarDica by remember { mutableStateOf(false) }

    // Espera antes de mostrar: sem isso, atravessar a fileira com o mouse acende as
    // tres dicas em sequencia e a tela pisca.
    LaunchedEffect(sobHover) {
        if (!sobHover) { mostrarDica = false; return@LaunchedEffect }
        delay(ESPERA_DICA_MS)
        mostrarDica = true
    }

    val corBase = when {
        danger -> Obsidian.danger
        accent -> Obsidian.accent
        else -> Obsidian.text2
    }
    val conteudo by animateColorAsState(
        if (sobHover) (if (danger) Obsidian.danger else Obsidian.text1) else corBase,
        tween(120),
    )
    val fundo by animateColorAsState(
        if (sobHover) (if (danger) Obsidian.danger.copy(alpha = 0.10f) else Obsidian.hover)
        else Color.Transparent,
        tween(120),
    )
    val borda = when {
        danger -> Obsidian.danger.copy(alpha = 0.45f)
        accent -> Obsidian.accentDim
        else -> Obsidian.borderDim
    }

    Box {
        Box(
            Modifier
                .size(34.dp)
                .clickScale(interacao)
                .clip(RoundedCornerShape(8.dp))
                .background(fundo)
                .border(1.dp, borda, RoundedCornerShape(8.dp))
                .hoverable(interacao)
                .clickable(interactionSource = interacao, indication = null, enabled = !ocupado, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (ocupado) {
                // Girando de verdade. Um LoaderCircle parado le como icone travado —
                // que e exatamente a impressao oposta da que ele deve passar.
                val giro = rememberInfiniteTransition(label = "ocupado")
                val angulo by giro.animateFloat(
                    0f, 360f,
                    infiniteRepeatable(tween(900, easing = LinearEasing)),
                    label = "angulo",
                )
                LIcon(
                    Lucide.LoaderCircle, tint = conteudo, size = 16.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = angulo },
                )
            } else {
                // A `dica` VIRA o nome acessivel. Ela ja e exatamente isso — o nome
                // da acao em portugues — e estava sendo escrita duas vezes no
                // codigo pra ser mostrada uma so, pro mouse. Quem usa leitor de
                // tela nao alcancava o texto que ja existia.
                LIcon(icone, tint = conteudo, size = 16.dp, rotulo = dica)
            }
        }
        if (mostrarDica && !ocupado) {
            val margem = with(LocalDensity.current) { 6.dp.roundToPx() }
            Popup(popupPositionProvider = remember(margem) { AbaixoCentralizado(margem) }) {
                Box(
                    Modifier
                        .popupReveal(originX = 0.5f, originY = 0f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(7.dp)),
                ) {
                    Text(
                        dica,
                        style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

// `calculatePosition` fala PIXEL, nao dp — e o `margem` chega ja convertido pelo
// chamador. Somar 6 cru dava ~4dp numa tela a 150% e ~3dp a 200%: o respiro
// encolhia justamente onde a tela e maior e a dica encosta no botao. Mesmo
// tropeco do cartao de perfil, que ja foi consertado uma vez.
private class AbaixoCentralizado(private val margem: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        // Abaixo por padrao; se nao couber, vira pra cima — a fileira do banner fica
        // perto do fim do painel e a dica sairia da janela.
        val abaixo = anchorBounds.bottom + margem
        val y = if (abaixo + popupContentSize.height <= windowSize.height) abaixo
        else (anchorBounds.top - popupContentSize.height - margem).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
