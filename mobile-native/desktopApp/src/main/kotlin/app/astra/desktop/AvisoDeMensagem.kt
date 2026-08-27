package app.astra.desktop

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import app.astra.desktop.ui.DesktopAvatar
import app.astra.desktop.ui.LIcon
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

data class AvisoNaTela(
    val id: Long,
    val quem: String,
    val onde: String,
    val trecho: String,
    val avatarUrl: String?,
    val abrir: (() -> Unit)?,
)

object AvisosNaTela {
    private const val LIMITE = 3

    val vivos = mutableStateListOf<AvisoNaTela>()
    private var proximoId = 0L

    fun mostrar(quem: String, onde: String, trecho: String, avatarUrl: String?, abrir: (() -> Unit)? = null) {
        while (vivos.size >= LIMITE) vivos.removeAt(0)
        vivos.add(AvisoNaTela(proximoId++, quem, onde, trecho, avatarUrl, abrir))
        tocarAvisoDeMensagem()
    }

    fun dispensar(id: Long) {
        vivos.removeAll { it.id == id }
    }
}

private val LARGURA = 340.dp
private val ALTURA = 84.dp
private val RESPIRO_ENTRE = 8.dp
private val MARGEM_DA_TELA = 16.dp

private const val SEGUNDOS_NA_TELA = 6

@Composable
fun AvisosDeMensagem() {
    AvisosNaTela.vivos.forEachIndexed { indice, aviso ->
        key(aviso.id) { JanelaDeAviso(aviso, indice) }
    }
}

@Composable
private fun JanelaDeAviso(aviso: AvisoNaTela, indice: Int) {
    val densidade = androidx.compose.ui.platform.LocalDensity.current

    val tela = remember {
        runCatching {
            val cfg = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration
            val bordas = Toolkit.getDefaultToolkit().getScreenInsets(cfg)
            val b = cfg.bounds
            (b.x + b.width - bordas.right) to (b.y + b.height - bordas.bottom)
        }.getOrNull()
    }

    val larguraPx = with(densidade) { LARGURA.roundToPx() }
    val alturaPx = with(densidade) { ALTURA.roundToPx() }
    val respiroPx = with(densidade) { RESPIRO_ENTRE.roundToPx() }
    val margemPx = with(densidade) { MARGEM_DA_TELA.roundToPx() }

    val (direita, baixo) = tela ?: return

    val x = direita - larguraPx - margemPx
    val y = baixo - margemPx - alturaPx - indice * (alturaPx + respiroPx)

    Window(
        onCloseRequest = { AvisosNaTela.dispensar(aviso.id) },
        state = rememberWindowState(
            position = WindowPosition(with(densidade) { x.toDp() }, with(densidade) { y.toDp() }),
            size = DpSize(LARGURA, ALTURA),
        ),
        undecorated = true,
        transparent = janelaAceitaTransparencia,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
        title = "",
    ) {
        CartaoDeAviso(aviso)
    }
}

@Composable
private fun CartaoDeAviso(aviso: AvisoNaTela) {
    val fonte = remember { MutableInteractionSource() }
    val sobMouse by fonte.collectIsHoveredAsState()

    LaunchedEffect(sobMouse) {
        if (sobMouse) return@LaunchedEffect
        delay(SEGUNDOS_NA_TELA * 1000L)
        AvisosNaTela.dispensar(aviso.id)
    }

    val realce by animateFloatAsState(if (sobMouse) 1f else 0f, tween(140), label = "realceDoAviso")

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .hoverable(fonte)
            .let {
                if (aviso.abrir == null) it
                else it.clickable(interactionSource = fonte, indication = null) {
                    aviso.abrir.invoke()
                    AvisosNaTela.dispensar(aviso.id)
                }
            },
    ) {
        Box(Modifier.fillMaxSize().background(Obsidian.hover.copy(alpha = realce * 0.55f)))

        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopAvatar(aviso.avatarUrl, aviso.quem, 38)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        aviso.quem,
                        style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (aviso.onde.isNotBlank()) {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            aviso.onde,
                            style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    aviso.trecho,
                    style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (sobMouse) {
                    val fonteX = remember { MutableInteractionSource() }
                    val sobreX by fonteX.collectIsHoveredAsState()
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sobreX) Obsidian.active else Color.Transparent)
                            .hoverable(fonteX)
                            .clickable(interactionSource = fonteX, indication = null) {
                                AvisosNaTela.dispensar(aviso.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        LIcon(Lucide.X, tint = Obsidian.text3, size = 12.dp, rotulo = "Dispensar o aviso")
                    }
                }
            }
        }
    }
}
