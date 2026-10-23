package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.FrameWindowScope
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import java.awt.Container

@Composable
fun FrameWindowScope.MedindoCadaQuadro() {
    DisposableEffect(window) {
        val desfazer = medirCadaQuadro(window)
        onDispose { desfazer() }
    }
}

private fun medirCadaQuadro(janela: Container): () -> Unit {
    val camada = janela.camadaDoSkia() ?: return {}
    val original = camada.renderDelegate ?: return {}
    val medidor = cronometrandoQuadros(original)
    camada.renderDelegate = medidor
    return { if (camada.renderDelegate === medidor) camada.renderDelegate = original }
}

private fun cronometrandoQuadros(original: SkikoRenderDelegate) = object : SkikoRenderDelegate {
    override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
        val inicio = System.nanoTime()
        original.onRender(canvas, width, height, nanoTime)
        Quadros.anotarQuadro((System.nanoTime() - inicio) / 1_000_000.0)
    }
}

private fun Container.camadaDoSkia(): SkiaLayer? {
    for (filho in components) {
        if (filho is SkiaLayer) return filho
        if (filho is Container) filho.camadaDoSkia()?.let { return it }
    }
    return null
}
