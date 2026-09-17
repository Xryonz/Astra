package app.astra.desktop.update

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

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

internal class FaixaRecusada(motivo: String) : IOException(motivo)

internal class DownloadInterrompido : IOException("download interrompido")

private class EnderecoVencido : IOException("o endereco do arquivo venceu")

private data class FimDoCentral(val quantas: Int, val tamanho: Long, val ondeComeca: Long)

private const val FIM_DO_CENTRAL = 0x06054b50
private const val ENTRADA_CENTRAL = 0x02014b50
private const val LOCALIZADOR_ZIP64 = 0x07064b50

private const val COMENTARIO_MAXIMO = 65_535
private const val RODAPE_FIXO = 22
internal const val MAIOR_RODAPE = COMENTARIO_MAXIMO + RODAPE_FIXO

private const val HTTP_PARCIAL = 206
private const val HTTP_MUITOS_PEDIDOS = 429

private const val TENTATIVAS_SEM_AVANCO = 4
private const val PAUSA_INICIAL_MS = 1_000L

internal class ZipRemoto(private val http: OkHttpClient, private val url: String) {

    @Volatile private var tamanhoConhecido = -1L
    @Volatile private var enderecoFinal: String? = null

    fun tamanho(): Long {
        if (tamanhoConhecido < 0) faixa(0, 1)
        return tamanhoConhecido
    }

    fun faixa(de: Long, ate: Long): ByteArray {
        if (ate <= de) return ByteArray(0)
        val bruto = ByteArray((ate - de).toInt())
        preencher(de, ate, bruto, 0)
        return bruto
    }

    fun preencher(
        de: Long,
        ate: Long,
        destino: ByteArray,
        noDestino: Int,
        continuar: () -> Boolean = { true },
        aoReceber: (Int) -> Unit = {},
    ) {
        var recebidos = 0L
        var semAvanco = 0
        while (recebidos < ate - de) {
            if (!continuar()) throw DownloadInterrompido()
            val antes = recebidos
            try {
                pedirFaixa(de + recebidos, ate, destino, noDestino + recebidos.toInt(), continuar) { n ->
                    recebidos += n
                    aoReceber(n)
                }
            } catch (e: FaixaRecusada) {
                throw e
            } catch (e: DownloadInterrompido) {
                throw e
            } catch (e: EnderecoVencido) {
                continue
            } catch (e: IOException) {
                if (recebidos > antes) {
                    semAvanco = 0
                    continue
                }
                if (++semAvanco >= TENTATIVAS_SEM_AVANCO) throw e
                Thread.sleep(PAUSA_INICIAL_MS shl (semAvanco - 1))
            }
        }
    }

    private fun pedirFaixa(
        de: Long,
        ate: Long,
        destino: ByteArray,
        noDestino: Int,
        continuar: () -> Boolean,
        aoLer: (Int) -> Unit,
    ) {
        val resolvido = enderecoFinal
        val req = Request.Builder().url(resolvido ?: url)
            .header("User-Agent", "Astra-Desktop")
            .header("Range", "bytes=$de-${ate - 1}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code != HTTP_PARCIAL) {
                if (resolvido != null && resp.code in 400..499) {
                    enderecoFinal = null
                    throw EnderecoVencido()
                }
                if (resp.code >= 500 || resp.code == HTTP_MUITOS_PEDIDOS) throw IOException("HTTP ${resp.code}")
                throw FaixaRecusada("faixa recusada (HTTP ${resp.code})")
            }
            val intervalo = resp.header("Content-Range").orEmpty()
            if (!intervalo.startsWith("bytes $de-")) throw FaixaRecusada("faixa trocada ($intervalo)")
            intervalo.substringAfter('/', "").toLongOrNull()?.let { tamanhoConhecido = it }
            if (resolvido == null) enderecoFinal = resp.request.url.toString()
            val corpo = resp.body ?: throw IOException("faixa sem corpo")
            val esperado = (ate - de).toInt()
            var lidos = 0
            corpo.byteStream().use { entrada ->
                while (lidos < esperado) {
                    if (!continuar()) throw DownloadInterrompido()
                    val n = entrada.read(destino, noDestino + lidos, esperado - lidos)
                    if (n < 0) break
                    lidos += n
                    aoLer(n)
                }
            }
            if (lidos != esperado) throw IOException("faixa veio com $lidos bytes, esperava $esperado")
        }
    }

    fun diretorio(): DiretorioDoZip? {
        val total = tamanho()
        if (total <= RODAPE_FIXO) return null
        val rodapeDe = total - minOf(total, MAIOR_RODAPE.toLong())
        return lerDiretorio(total, faixa(rodapeDe, total)) { de, ate -> faixa(de, ate) }
    }
}

internal fun lerDiretorioLocal(arquivo: File): DiretorioDoZip? = runCatching {
    RandomAccessFile(arquivo, "r").use { raf ->
        val total = raf.length()
        if (total <= RODAPE_FIXO) return@use null
        fun ler(de: Long, ate: Long): ByteArray {
            val bruto = ByteArray((ate - de).toInt())
            raf.seek(de)
            raf.readFully(bruto)
            return bruto
        }
        lerDiretorio(total, ler(total - minOf(total, MAIOR_RODAPE.toLong()), total), ::ler)
    }
}.getOrNull()

internal fun lerDiretorio(
    total: Long,
    rodape: ByteArray,
    buscar: (Long, Long) -> ByteArray,
): DiretorioDoZip? {
    val fim = lerFimDoCentral(rodape) ?: return null
    if (fim.ondeComeca + fim.tamanho > total) return null
    val rodapeDe = total - rodape.size
    val central = if (fim.ondeComeca >= rodapeDe) {
        val de = (fim.ondeComeca - rodapeDe).toInt()
        rodape.copyOfRange(de, de + fim.tamanho.toInt())
    } else {
        buscar(fim.ondeComeca, fim.ondeComeca + fim.tamanho)
    }
    val entradas = lerEntradas(central, fim.quantas) ?: return null
    return DiretorioDoZip(entradas, fim.ondeComeca)
}

private fun lerFimDoCentral(rodape: ByteArray): FimDoCentral? {
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
    return FimDoCentral(quantas, tamanhoCentral, ondeComeca)
}

private fun lerEntradas(central: ByteArray, quantas: Int): List<EntradaDoZip>? {
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
    return entradas
}

internal fun lerInt(b: ByteArray, i: Int): Int =
    (b[i].toInt() and 0xFF) or
        ((b[i + 1].toInt() and 0xFF) shl 8) or
        ((b[i + 2].toInt() and 0xFF) shl 16) or
        ((b[i + 3].toInt() and 0xFF) shl 24)

internal fun lerCurto(b: ByteArray, i: Int): Int =
    (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
