package app.astra.desktop.profile

import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max

object AvatarPicker {
    private const val AVATAR_DIM = 1024
    private const val GIF_MAX = 9_000_000
    private const val HARD_MAX = 10_000_000

    const val BANNER_DIM = 2560

    fun choose(title: String = "Escolher imagem"): File? {
        val dlg = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dlg.isVisible = true
        val dir = dlg.directory ?: return null
        val name = dlg.file ?: return null
        return File(dir, name)
    }

    data class Imagem(val dataUri: String, val largura: Int, val altura: Int)

    fun encode(file: File, dim: Int = AVATAR_DIM): Result<String> =
        encodeComMedidas(file, dim).map { it.dataUri }

    fun encodeComMedidas(file: File, dim: Int = AVATAR_DIM): Result<Imagem> = runCatching {
        val raw = file.readBytes()
        if (file.name.lowercase().endsWith(".gif") && raw.size <= GIF_MAX) {
            return@runCatching Imagem(dataUri("image/gif", raw), 0, 0)
        }
        val src = ImageIO.read(file) ?: error("formato de imagem não suportado")
        val fitted = fit(src, dim)
        val alpha = fitted.colorModel.hasAlpha()
        val out = ByteArrayOutputStream()
        if (alpha) ImageIO.write(fitted, "png", out) else escreverJpeg(fitted, out)
        val bytes = out.toByteArray()
        require(bytes.size <= HARD_MAX) { "imagem muito grande" }
        Imagem(dataUri(if (alpha) "image/png" else "image/jpeg", bytes), fitted.width, fitted.height)
    }

    fun zoomQueCobre(largura: Int, altura: Int, aspectoDaFaixa: Float): Int {
        if (largura <= 0 || altura <= 0) return 100
        val aspectoDaImagem = largura.toFloat() / altura
        val fator = max(aspectoDaFaixa / aspectoDaImagem, aspectoDaImagem / aspectoDaFaixa)
        return (fator * 100).toInt().coerceIn(100, ZOOM_MAX_BANNER)
    }

    const val ZOOM_MAX_BANNER = 300

    private fun fit(src: BufferedImage, dim: Int): BufferedImage {
        val maior = max(src.width, src.height)
        if (maior <= dim) return src

        val type = if (src.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        var atual = src
        var w = src.width
        var h = src.height

        while (max(w, h) / 2 > dim) {
            w = (w / 2).coerceAtLeast(1)
            h = (h / 2).coerceAtLeast(1)
            atual = desenhar(atual, w, h, type)
        }

        val escala = dim.toDouble() / max(w, h)
        return desenhar(
            atual,
            (w * escala).toInt().coerceAtLeast(1),
            (h * escala).toInt().coerceAtLeast(1),
            type,
        )
    }

    private fun desenhar(src: BufferedImage, w: Int, h: Int, type: Int): BufferedImage {
        val dst = BufferedImage(w, h, type)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return dst
    }

    private fun escreverJpeg(img: BufferedImage, out: ByteArrayOutputStream) {
        val escritor = ImageIO.getImageWritersByFormatName("jpg").next()
        val params = escritor.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = 0.95f
        }
        ImageIO.createImageOutputStream(out).use { saida ->
            escritor.output = saida
            escritor.write(null, IIOImage(img, null, null), params)
        }
        escritor.dispose()
    }

    private fun dataUri(mime: String, bytes: ByteArray) =
        "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
}
