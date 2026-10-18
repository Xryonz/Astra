package app.astra.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage
import java.io.File
import javax.imageio.ImageIO

object AreaDeTransferencia {
    private val sistema get() = Toolkit.getDefaultToolkit().systemClipboard

    fun temAlgoParaAnexar(): Boolean = runCatching {
        val area = sistema
        area.isDataFlavorAvailable(DataFlavor.javaFileListFlavor) ||
            (area.isDataFlavorAvailable(DataFlavor.imageFlavor) && !area.isDataFlavorAvailable(DataFlavor.stringFlavor))
    }.getOrDefault(false)

    suspend fun anexosColados(): List<File> = withContext(Dispatchers.IO) {
        runCatching {
            val conteudo = sistema.getContents(null) ?: return@runCatching emptyList()
            when {
                conteudo.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> arquivos(conteudo)
                imagemSemTexto(conteudo) ->
                    listOfNotNull((conteudo.getTransferData(DataFlavor.imageFlavor) as? Image)?.let(::emPng))
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun arquivos(conteudo: Transferable): List<File> =
        (conteudo.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>).orEmpty().filterIsInstance<File>()

    private fun imagemSemTexto(conteudo: Transferable): Boolean =
        conteudo.isDataFlavorSupported(DataFlavor.imageFlavor) && !conteudo.isDataFlavorSupported(DataFlavor.stringFlavor)

    private fun emPng(imagem: Image): File? {
        val maior = (imagem as? MultiResolutionImage)?.resolutionVariants?.maxByOrNull { it.getWidth(null) } ?: imagem
        val largura = maior.getWidth(null)
        val altura = maior.getHeight(null)
        if (largura <= 0 || altura <= 0) return null
        val pronta = maior as? BufferedImage ?: BufferedImage(largura, altura, BufferedImage.TYPE_INT_ARGB).also { quadro ->
            val pincel = quadro.createGraphics()
            pincel.drawImage(maior, 0, 0, null)
            pincel.dispose()
        }
        val pasta = File(System.getProperty("java.io.tmpdir"), PASTA_DAS_COLADAS).apply { mkdirs() }
        val arquivo = File(pasta, "imagem-colada-${System.currentTimeMillis()}.png")
        if (!ImageIO.write(pronta, "png", arquivo)) return null
        arquivo.deleteOnExit()
        return arquivo
    }

    private const val PASTA_DAS_COLADAS = "astra-coladas"
}
