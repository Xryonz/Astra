package app.astra.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.FrameWindowScope
import java.awt.Frame
import java.awt.GraphicsConfiguration
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
