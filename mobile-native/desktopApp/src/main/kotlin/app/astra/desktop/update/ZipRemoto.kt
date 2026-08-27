package app.astra.desktop.update

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

internal data class EntradaDoZip(
    val nome: String,
    val metodo: Int,
    val crc: Long,
    val comprimido: Long,
    val cru: Long,
    val deslocamento: Long,
) {
    val ehPasta: Boolean get() = nome.endsWith("/")
}

internal data class DiretorioDoZip(
    val entradas: List<EntradaDoZip>,
    val ondeComecaOCentral: Long,
) {
    val fimDosDados: Map<String, Long> = run {
        val porDeslocamento = entradas.sortedBy { it.deslocamento }
        val fim = HashMap<String, Long>(entradas.size)
        for ((i, e) in porDeslocamento.withIndex()) {
            fim[e.nome] =
                if (i + 1 < porDeslocamento.size) porDeslocamento[i + 1].deslocamento
                else ondeComecaOCentral
        }
        fim
    }
}

private const val FIM_DO_CENTRAL = 0x06054b50
private const val ENTRADA_CENTRAL = 0x02014b50
private const val LOCALIZADOR_ZIP64 = 0x07064b50

private const val COMENTARIO_MAXIMO = 65_535
private const val RODAPE_FIXO = 22

internal class ZipRemoto(private val http: OkHttpClient, private val url: String) {

    private var tamanhoConhecido = -1L

    fun tamanho(): Long {
        if (tamanhoConhecido < 0) faixa(0, 1)
        return tamanhoConhecido
    }

    fun faixa(de: Long, ate: Long): ByteArray {
        if (ate <= de) return ByteArray(0)
        val req = Request.Builder().url(url)
            .header("User-Agent", "Astra-Desktop")
            .header("Range", "bytes=$de-${ate - 1}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code != 206) throw IOException("faixa recusada (HTTP ${resp.code})")
            resp.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull()?.let {
                tamanhoConhecido = it
            }
            val corpo = resp.body ?: throw IOException("faixa sem corpo")
            val bruto = corpo.bytes()
            if (bruto.size.toLong() != ate - de) {
                throw IOException("faixa veio com ${bruto.size} bytes, esperava ${ate - de}")
            }
            return bruto
        }
    }

    fun diretorio(): DiretorioDoZip? {
        val total = tamanho()
        if (total <= RODAPE_FIXO) return null
        val quanto = minOf(total, (COMENTARIO_MAXIMO + RODAPE_FIXO).toLong())
        val rodape = faixa(total - quanto, total)

        var fim = -1
        for (i in rodape.size - RODAPE_FIXO downTo 0) {
            if (lerInt(rodape, i) == FIM_DO_CENTRAL) { fim = i; break }
        }
        if (fim < 0) return null

        val quantas = lerCurto(rodape, fim + 10)
        val tamanhoCentral = lerInt(rodape, fim + 12).toLong() and 0xFFFFFFFFL
        val ondeComeca = lerInt(rodape, fim + 16).toLong() and 0xFFFFFFFFL

        if (quantas == 0xFFFF || tamanhoCentral == 0xFFFFFFFFL || ondeComeca == 0xFFFFFFFFL) {
            return null
        }
        for (i in 0..rodape.size - 4) {
            if (lerInt(rodape, i) == LOCALIZADOR_ZIP64) return null
        }
        if (ondeComeca + tamanhoCentral > total) return null

        val central = faixa(ondeComeca, ondeComeca + tamanhoCentral)
        val entradas = ArrayList<EntradaDoZip>(quantas)
        var p = 0
        while (p + 46 <= central.size) {
            if (lerInt(central, p) != ENTRADA_CENTRAL) break
            val metodo = lerCurto(central, p + 10)
            val crc = lerInt(central, p + 16).toLong() and 0xFFFFFFFFL
            val comprimido = lerInt(central, p + 20).toLong() and 0xFFFFFFFFL
            val cru = lerInt(central, p + 24).toLong() and 0xFFFFFFFFL
            val nomeLen = lerCurto(central, p + 28)
            val extraLen = lerCurto(central, p + 30)
            val comentarioLen = lerCurto(central, p + 32)
            val deslocamento = lerInt(central, p + 42).toLong() and 0xFFFFFFFFL

            if (comprimido == 0xFFFFFFFFL || cru == 0xFFFFFFFFL || deslocamento == 0xFFFFFFFFL) {
                return null
            }
            if (p + 46 + nomeLen > central.size) return null
            val nome = String(central, p + 46, nomeLen, Charsets.UTF_8)
            entradas.add(EntradaDoZip(nome, metodo, crc, comprimido, cru, deslocamento))
            p += 46 + nomeLen + extraLen + comentarioLen
        }
        if (entradas.size != quantas) return null
        return DiretorioDoZip(entradas, ondeComeca)
    }

    private fun lerInt(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or
            ((b[i + 1].toInt() and 0xFF) shl 8) or
            ((b[i + 2].toInt() and 0xFF) shl 16) or
            ((b[i + 3].toInt() and 0xFF) shl 24)

    private fun lerCurto(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
}
