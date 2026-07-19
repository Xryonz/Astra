package app.astra.desktop.profile

import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.max

// Avatar do Astra NAO usa o endpoint de upload: vive como DATA-URI na coluna
// avatarUrl (mesmo padrao do mobile e do web; o Coil do desktop ja resolve
// data-uri, ver Main.kt). O backend recusa acima de 5MB, entao reduzimos pra
// AVATAR_DIM antes de codificar. GIF pequeno passa CRU pra nao matar a animacao.
object AvatarPicker {
    private const val AVATAR_DIM = 512
    private const val GIF_MAX = 4_500_000
    private const val HARD_MAX = 5_000_000

    // Abre o seletor nativo do SO. Bloqueia (modal) — chamar da thread de UI e o
    // comportamento normal de um file dialog. null = o usuario cancelou.
    fun choose(): File? {
        val dlg = FileDialog(null as Frame?, "Escolher avatar", FileDialog.LOAD)
        dlg.isVisible = true
        val dir = dlg.directory ?: return null
        val name = dlg.file ?: return null
        return File(dir, name)
    }

    // Le, reduz e codifica. Pesado -> rodar fora da thread de UI.
    fun encode(file: File): Result<String> = runCatching {
        val raw = file.readBytes()
        if (file.name.lowercase().endsWith(".gif") && raw.size <= GIF_MAX) {
            return@runCatching dataUri("image/gif", raw)
        }
        val src = ImageIO.read(file) ?: error("formato de imagem nao suportado")
        val fitted = fit(src, AVATAR_DIM)
        // JPEG nao aceita canal alfa; so vai pra PNG quem realmente tem transparencia.
        val alpha = fitted.colorModel.hasAlpha()
        val out = ByteArrayOutputStream()
        ImageIO.write(fitted, if (alpha) "png" else "jpg", out)
        val bytes = out.toByteArray()
        require(bytes.size <= HARD_MAX) { "imagem muito grande" }
        dataUri(if (alpha) "image/png" else "image/jpeg", bytes)
    }

    private fun fit(src: BufferedImage, dim: Int): BufferedImage {
        val scale = dim.toDouble() / max(src.width, src.height)
        val w = if (scale < 1) (src.width * scale).toInt().coerceAtLeast(1) else src.width
        val h = if (scale < 1) (src.height * scale).toInt().coerceAtLeast(1) else src.height
        val type = if (src.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val dst = BufferedImage(w, h, type)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return dst
    }

    private fun dataUri(mime: String, bytes: ByteArray) =
        "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
}
