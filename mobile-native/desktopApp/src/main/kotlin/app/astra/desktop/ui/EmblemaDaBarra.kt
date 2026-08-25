package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.astra.desktop.ui.theme.Obsidian
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.Taskbar
import java.awt.Window
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

private const val LADO = 64
private const val ANEL = 4f

@Composable
fun EmblemaDaBarra(janela: Window?, quantidade: Int) {
    val fundo = Obsidian.accent
    val tinta = Obsidian.textInv
    val contorno = Obsidian.void

    LaunchedEffect(janela, quantidade, fundo, tinta, contorno) {
        if (janela == null) return@LaunchedEffect
        val imagem = if (quantidade <= 0) null else desenhar(
            texto = rotulo(quantidade),
            fundo = Color(fundo.red, fundo.green, fundo.blue),
            tinta = Color(tinta.red, tinta.green, tinta.blue),
            contorno = Color(contorno.red, contorno.green, contorno.blue),
        )
        SwingUtilities.invokeLater {
            runCatching {
                if (!Taskbar.isTaskbarSupported()) return@runCatching
                val barra = Taskbar.getTaskbar()
                if (!barra.isSupported(Taskbar.Feature.ICON_BADGE_IMAGE_WINDOW)) return@runCatching
                barra.setWindowIconBadge(janela, imagem)
            }
        }
    }
}

private fun rotulo(n: Int): String = if (n > 99) "+" else n.toString()

private fun desenhar(texto: String, fundo: Color, tinta: Color, contorno: Color): BufferedImage {
    val img = BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    g.color = contorno
    g.fillOval(0, 0, LADO, LADO)
    g.color = fundo
    g.fillOval(ANEL.toInt(), ANEL.toInt(), LADO - 2 * ANEL.toInt(), LADO - 2 * ANEL.toInt())

    val corpo = if (texto.length >= 2) 34f else 44f
    g.font = Font(Font.SANS_SERIF, Font.BOLD, corpo.toInt())
    g.color = tinta
    val fm = g.fontMetrics
    val x = (LADO - fm.stringWidth(texto)) / 2f
    val y = (LADO + (fm.ascent - fm.descent)) / 2f
    g.drawString(texto, x, y)
    g.dispose()
    return img
}
