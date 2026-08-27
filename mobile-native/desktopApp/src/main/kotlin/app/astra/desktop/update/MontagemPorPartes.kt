package app.astra.desktop.update

import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.Inflater

private const val GUARDADO = 0
private const val ENCOLHIDO = 8

private const val MAIOR_PEDIDO = 8L * 1024 * 1024
private const val VAO_QUE_VALE_PULAR = 96L * 1024

private const val TETO_NA_MEMORIA = 64L * 1024 * 1024

internal data class Montagem(
    val reaproveitados: Int,
    val baixados: Int,
    val bytesBaixados: Long,
    val bytesTotais: Long,
)

internal class MontagemPorPartes(http: OkHttpClient, url: String) {

    private val zip = ZipRemoto(http, url)

    fun montar(deOndeVem: File, paraOnde: File, aoAndar: (Float) -> Unit): Montagem? {
        val dir = zip.diretorio() ?: return null
        val arquivos = dir.entradas.filterNot { it.ehPasta }
        if (arquivos.isEmpty()) return null
        if (arquivos.any { it.metodo != GUARDADO && it.metodo != ENCOLHIDO }) return null

        val prefixo = prefixoDaRaiz(arquivos) ?: return null

        val alvos = arquivos.mapNotNull { e ->
            val rel = e.nome.removePrefix(prefixo)
            if (rel.isBlank() || rel.contains("..")) null else e to rel
        }
        if (alvos.isEmpty()) return null

        val reaproveitar = ArrayList<Pair<File, File>>()
        val baixar = ArrayList<Pair<EntradaDoZip, String>>()
        for ((e, rel) in alvos) {
            val jaTenho = File(deOndeVem, rel)
            if (jaTenho.isFile && jaTenho.length() == e.cru && crcDoArquivo(jaTenho) == e.crc) {
                reaproveitar.add(jaTenho to File(paraOnde, rel))
            } else {
                baixar.add(e to rel)
            }
        }

        val totalBaixar = baixar.sumOf { (e, _) -> e.comprimido }
        if (totalBaixar >= zip.tamanho()) return null
        if (baixar.any { (e, _) -> e.comprimido > TETO_NA_MEMORIA }) return null

        paraOnde.mkdirs()
        for ((origem, destino) in reaproveitar) {
            destino.parentFile?.mkdirs()
            if (!ligarOuCopiar(origem, destino)) return null
        }

        var andados = 0L
        aoAndar(if (totalBaixar == 0L) 1f else 0f)
        for (bloco in agrupar(baixar, dir)) {
            val bruto = zip.faixa(bloco.de, bloco.ate)
            for ((e, rel) in bloco.entradas) {
                val destino = File(paraOnde, rel)
                destino.parentFile?.mkdirs()
                val dentro = (e.deslocamento - bloco.de).toInt()
                if (!escrever(bruto, dentro, e, destino)) return null
                andados += e.comprimido
                if (totalBaixar > 0) aoAndar((andados.toFloat() / totalBaixar).coerceIn(0f, 1f))
            }
        }
        aoAndar(1f)

        return Montagem(reaproveitar.size, baixar.size, totalBaixar, zip.tamanho())
    }

    private class Bloco(val de: Long, val ate: Long, val entradas: List<Pair<EntradaDoZip, String>>)

    private fun agrupar(
        baixar: List<Pair<EntradaDoZip, String>>,
        dir: DiretorioDoZip,
    ): List<Bloco> {
        val ordenado = baixar.sortedBy { (e, _) -> e.deslocamento }
        val blocos = ArrayList<Bloco>()
        var atuais = ArrayList<Pair<EntradaDoZip, String>>()
        var de = 0L
        var ate = 0L

        fun fechar() {
            if (atuais.isNotEmpty()) blocos.add(Bloco(de, ate, atuais))
            atuais = ArrayList()
        }

        for (par in ordenado) {
            val (e, _) = par
            val fimDele = dir.fimDosDados[e.nome] ?: (e.deslocamento + 30 + e.comprimido + 4096)
            if (atuais.isEmpty()) {
                de = e.deslocamento
                ate = fimDele
                atuais.add(par)
                continue
            }
            val esticado = maxOf(ate, fimDele)
            val vao = e.deslocamento - ate
            if (vao <= VAO_QUE_VALE_PULAR && esticado - de <= MAIOR_PEDIDO) {
                ate = esticado
                atuais.add(par)
            } else {
                fechar()
                de = e.deslocamento
                ate = fimDele
                atuais.add(par)
            }
        }
        fechar()
        return blocos
    }

    private fun escrever(bruto: ByteArray, dentro: Int, e: EntradaDoZip, destino: File): Boolean {
        if (dentro < 0 || dentro + 30 > bruto.size) return false
        val nomeLen = (bruto[dentro + 26].toInt() and 0xFF) or ((bruto[dentro + 27].toInt() and 0xFF) shl 8)
        val extraLen = (bruto[dentro + 28].toInt() and 0xFF) or ((bruto[dentro + 29].toInt() and 0xFF) shl 8)
        val inicio = dentro + 30 + nomeLen + extraLen
        val fim = inicio + e.comprimido.toInt()
        if (inicio < 0 || fim > bruto.size) return false

        val confere = CRC32()
        FileOutputStream(destino).buffered().use { saida ->
            if (e.metodo == GUARDADO) {
                saida.write(bruto, inicio, e.comprimido.toInt())
                confere.update(bruto, inicio, e.comprimido.toInt())
            } else {
                val soltador = Inflater(true)
                soltador.setInput(bruto, inicio, e.comprimido.toInt())
                val balde = ByteArray(64 * 1024)
                try {
                    while (!soltador.finished()) {
                        val n = soltador.inflate(balde)
                        if (n == 0 && (soltador.needsInput() || soltador.needsDictionary())) break
                        saida.write(balde, 0, n)
                        confere.update(balde, 0, n)
                    }
                } catch (_: Exception) {
                    return false
                } finally {
                    soltador.end()
                }
            }
        }
        if (confere.value != e.crc) {
            destino.delete()
            return false
        }
        return true
    }

    private fun ligarOuCopiar(origem: File, destino: File): Boolean = try {
        runCatching { Files.createLink(destino.toPath(), origem.toPath()) }
            .getOrElse { origem.copyTo(destino, overwrite = true) }
        destino.exists()
    } catch (_: IOException) {
        false
    }

    private fun crcDoArquivo(f: File): Long {
        val crc = CRC32()
        f.inputStream().buffered().use { entrada ->
            val balde = ByteArray(64 * 1024)
            while (true) {
                val n = entrada.read(balde)
                if (n < 0) break
                crc.update(balde, 0, n)
            }
        }
        return crc.value
    }

    private fun prefixoDaRaiz(arquivos: List<EntradaDoZip>): String? {
        val exe = arquivos.firstOrNull { it.nome == "Astra.exe" || it.nome.endsWith("/Astra.exe" ) }
            ?: return null
        return exe.nome.removeSuffix("Astra.exe")
    }
}
