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

// Avatar do Astra NAO usa o endpoint de upload: vive como DATA-URI na coluna
// avatarUrl (mesmo padrao do mobile e do web; o Coil do desktop já resolve
// data-uri, ver Main.kt). O backend recusa acima de 5MB, entao reduzimos pra
// AVATAR_DIM antes de codificar. GIF pequeno passa CRU pra não matar a animação.
object AvatarPicker {
    private const val AVATAR_DIM = 512
    private const val GIF_MAX = 4_500_000
    private const val HARD_MAX = 5_000_000

    // Abre o seletor nativo do SO. Bloqueia (modal) — chamar da thread de UI e o
    // comportamento normal de um file dialog. null = o usuário cancelou.
    // Banner e mais largo que o avatar -> mais resolucao antes de virar data-uri.
    const val BANNER_DIM = 1280

    fun choose(title: String = "Escolher imagem"): File? {
        val dlg = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dlg.isVisible = true
        val dir = dlg.directory ?: return null
        val name = dlg.file ?: return null
        return File(dir, name)
    }

    // Le, reduz e codifica. Pesado -> rodar fora da thread de UI.
    fun encode(file: File, dim: Int = AVATAR_DIM): Result<String> = runCatching {
        val raw = file.readBytes()
        if (file.name.lowercase().endsWith(".gif") && raw.size <= GIF_MAX) {
            return@runCatching dataUri("image/gif", raw)
        }
        val src = ImageIO.read(file) ?: error("formato de imagem não suportado")
        val fitted = fit(src, dim)
        // JPEG não aceita canal alfa; so vai pra PNG quem realmente tem transparencia.
        val alpha = fitted.colorModel.hasAlpha()
        val out = ByteArrayOutputStream()
        if (alpha) ImageIO.write(fitted, "png", out) else escreverJpeg(fitted, out)
        val bytes = out.toByteArray()
        require(bytes.size <= HARD_MAX) { "imagem muito grande" }
        dataUri(if (alpha) "image/png" else "image/jpeg", bytes)
    }

    // REDUZ EM ETAPAS, metade por vez. Era isto que deixava a foto "pixelada".
    //
    // Uma foto de 4000px caindo direto pra 512 e uma reducao de 8x, e a
    // interpolacao bilinear le so 2x2 pixeis vizinhos: 98% da informacao nunca e
    // olhada, e o que sobra vira serrilhado e granulado. Nao e culpa do tamanho
    // final — 512 e de sobra pro maior avatar do app —, e do salto.
    //
    // Cortar pela metade de cada vez faz cada etapa ser uma reducao de 2x, onde
    // 2x2 pixeis sao EXATAMENTE a vizinhanca certa. E o "media de area" feito na
    // mao, e a diferenca aparece na hora numa foto com textura.
    private fun fit(src: BufferedImage, dim: Int): BufferedImage {
        val maior = max(src.width, src.height)
        if (maior <= dim) return src

        val type = if (src.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        var atual = src
        var w = src.width
        var h = src.height

        // Etapas de metade enquanto ainda falta mais de 2x pro destino.
        while (max(w, h) / 2 > dim) {
            w = (w / 2).coerceAtLeast(1)
            h = (h / 2).coerceAtLeast(1)
            atual = desenhar(atual, w, h, type)
        }

        // Etapa final, exata.
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

    // JPEG com qualidade EXPLICITA. O ImageIO.write(…, "jpg", …) usa o padrao do
    // JDK, que e 0.75 — visivel como bloco em volta de contorno e em area de cor
    // chapada. 0.92 e o mesmo numero que o recorte (ImageCrop) ja usava; ficar nos
    // dois em 0.92 e o que faz a foto sair igual pelos dois caminhos.
    private fun escreverJpeg(img: BufferedImage, out: ByteArrayOutputStream) {
        val escritor = ImageIO.getImageWritersByFormatName("jpg").next()
        val params = escritor.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = 0.92f
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
