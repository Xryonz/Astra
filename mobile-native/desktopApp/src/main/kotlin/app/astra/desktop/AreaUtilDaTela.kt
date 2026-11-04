package app.astra.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import java.awt.Frame
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener

@Composable
fun FrameWindowScope.MaximizadaDentroDaAreaUtil() {
    DisposableEffect(window) {
        val janela = window
        val aoMover = object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent) = janela.limitarMaximizadaAAreaUtil()
        }
        val aoTrocarDeTela = PropertyChangeListener { janela.limitarMaximizadaAAreaUtil() }
        janela.limitarMaximizadaAAreaUtil()
        janela.addComponentListener(aoMover)
        janela.addPropertyChangeListener("graphicsConfiguration", aoTrocarDeTela)
        onDispose {
            janela.removeComponentListener(aoMover)
            janela.removePropertyChangeListener("graphicsConfiguration", aoTrocarDeTela)
        }
    }
}

fun Frame.limitarMaximizadaAAreaUtil() {
    graphicsConfiguration?.let { maximizedBounds = areaUtil(it) }
}

private val TAMANHO_PREFERIDO_DA_JANELA = DpSize(1280.dp, 820.dp)
private val FOLGA_ATE_A_BORDA = 24.dp

fun tamanhoInicialDaJanela(): DpSize {
    val tela = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    }.getOrNull() ?: return TAMANHO_PREFERIDO_DA_JANELA
    val area = areaUtil(tela)
    return DpSize(
        minOf(TAMANHO_PREFERIDO_DA_JANELA.width, area.width.dp - FOLGA_ATE_A_BORDA * 2),
        minOf(TAMANHO_PREFERIDO_DA_JANELA.height, area.height.dp - FOLGA_ATE_A_BORDA * 2),
    )
}

private fun areaUtil(tela: GraphicsConfiguration): Rectangle {
    val limites = tela.bounds
    val bordas = Toolkit.getDefaultToolkit().getScreenInsets(tela)
    return Rectangle(
        limites.x + bordas.left,
        limites.y + bordas.top,
        limites.width - bordas.left - bordas.right,
        limites.height - bordas.top - bordas.bottom,
    )
}
