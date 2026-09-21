package app.astra.mobile.core.update

import android.os.Build
import app.astra.mobile.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

private const val REPOSITORIO = "Xryonz/Astra"
private val PREFIXO = BuildConfig.ETIQUETA_DE_ATUALIZACAO
private val ETIQUETAS = "https://api.github.com/repos/$REPOSITORIO/git/matching-refs/tags/$PREFIXO"
private val ANUNCIO = "https://github.com/$REPOSITORIO/releases/download/${BuildConfig.PUBLICACAO_FIXA}/versao.txt"
private const val CABECALHO_DO_ANUNCIO = "astra-android 1"
private const val PREFIXO_DA_VERSAO = "versao "
private const val SEPARADOR = "  "
private const val TAMANHO_DA_IMPRESSAO = 64
private val SO_NUMEROS = Regex("""\d+(\.\d+)*""")

private const val TENTATIVAS_DE_BAIXAR = 4
private val PAUSAS_MS = longArrayOf(2_000L, 5_000L, 10_000L)
private const val FOLGA_DE_ESPACO = 3L
private const val ESPACO_MINIMO = 20L * 1024 * 1024

private val NOME_POR_PROCESSADOR = mapOf(
    "arm64-v8a" to "android",
    "armeabi-v7a" to "android-32bits",
)

@Serializable
private data class Referencia(val ref: String = "")

private data class Anuncio(val versao: String, val impressoes: Map<String, String>)

data class VersaoNova(
    val versao: String,
    val endereco: String,
    val pagina: String,
    val impressao: String? = null,
)

class PacoteCorrompido : IOException()

class SemEspaco : IOException()

object Atualizador {

    private val leitor = Json { ignoreUnknownKeys = true }

    private val cliente by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .build()
    }

    fun procurar(versaoInstalada: String): VersaoNova? {
        val anuncio = runCatching { lerAnuncio() }.getOrNull()
        val versao = anuncio?.versao ?: versaoPelasEtiquetas() ?: return null
        if (!ehMaisNova(versao, versaoInstalada)) return null

        val nome = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { NOME_POR_PROCESSADOR[it] } ?: return null
        val etiqueta = PREFIXO + versao
        return VersaoNova(
            versao = versao,
            endereco = "https://github.com/$REPOSITORIO/releases/download/$etiqueta/Astra-$versao-$nome.apk.gz",
            pagina = "https://github.com/$REPOSITORIO/releases/tag/$etiqueta",
            impressao = anuncio?.impressoes?.get("Astra-$versao-$nome.apk"),
        )
    }

    private fun lerAnuncio(): Anuncio? {
        val pedido = Request.Builder().url(ANUNCIO).build()
        val corpo = cliente.newCall(pedido).execute().use { resposta ->
            if (!resposta.isSuccessful) return null
            resposta.body?.string().orEmpty()
        }
        val linhas = corpo.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (linhas.getOrNull(0) != CABECALHO_DO_ANUNCIO) return null
        val versao = linhas.getOrNull(1)?.removePrefix(PREFIXO_DA_VERSAO)?.takeIf { it.matches(SO_NUMEROS) }
            ?: return null
        val impressoes = linhas.drop(2).mapNotNull { linha ->
            val impressao = linha.substringBefore(SEPARADOR).lowercase()
            val arquivo = linha.substringAfter(SEPARADOR, "").trim()
            if (impressao.length == TAMANHO_DA_IMPRESSAO && arquivo.isNotEmpty()) arquivo to impressao else null
        }.toMap()
        return Anuncio(versao, impressoes)
    }

    private fun versaoPelasEtiquetas(): String? {
        val pedido = Request.Builder()
            .url(ETIQUETAS)
            .header("Accept", "application/vnd.github+json")
            .build()

        val corpo = cliente.newCall(pedido).execute().use { resposta ->
            if (!resposta.isSuccessful) throw IOException("GitHub respondeu ${resposta.code}")
            resposta.body?.string().orEmpty()
        }

        return leitor.decodeFromString<List<Referencia>>(corpo)
            .map { it.ref.removePrefix("refs/tags/$PREFIXO") }
            .filter { it.matches(SO_NUMEROS) }
            .maxWithOrNull(::comparar)
    }

    fun baixar(nova: VersaoNova, pasta: File, aoAvancar: (Float) -> Unit = {}): File {
        val pronto = File(pasta, "${nova.versao}.apk")
        if (pronto.exists()) return pronto
        pasta.mkdirs()

        val parcial = File(pasta, "${nova.versao}.apk.gz.parcial")
        var tentativa = 0
        while (true) {
            val antes = if (parcial.exists()) parcial.length() else 0L
            try {
                baixarOQueFalta(nova, pasta, parcial, aoAvancar)
                break
            } catch (e: SemEspaco) {
                throw e
            } catch (e: IOException) {
                if (parcial.exists() && parcial.length() > antes) tentativa = 0
                tentativa++
                if (tentativa >= TENTATIVAS_DE_BAIXAR) throw e
                Thread.sleep(PAUSAS_MS[(tentativa - 1).coerceAtMost(PAUSAS_MS.lastIndex)])
            }
        }

        if (pasta.usableSpace < parcial.length() * FOLGA_DE_ESPACO + ESPACO_MINIMO) throw SemEspaco()
        val descomprimindo = File(pasta, "${nova.versao}.apk.tmp")
        try {
            GZIPInputStream(parcial.inputStream().buffered()).use { entrada ->
                descomprimindo.outputStream().use { entrada.copyTo(it) }
            }
        } catch (e: IOException) {
            parcial.delete()
            descomprimindo.delete()
            throw PacoteCorrompido()
        }
        parcial.delete()
        if (nova.impressao != null && impressaoDe(descomprimindo) != nova.impressao) {
            descomprimindo.delete()
            throw PacoteCorrompido()
        }
        if (!descomprimindo.renameTo(pronto)) throw IOException("não foi possível guardar o pacote")
        return pronto
    }

    private fun baixarOQueFalta(nova: VersaoNova, pasta: File, parcial: File, aoAvancar: (Float) -> Unit) {
        val jaBaixado = if (parcial.exists()) parcial.length() else 0L
        val pedido = Request.Builder()
            .url(nova.endereco)
            .apply { if (jaBaixado > 0) header("Range", "bytes=$jaBaixado-") }
            .build()

        cliente.newCall(pedido).execute().use { resposta ->
            if (resposta.code == 416) return
            if (!resposta.isSuccessful) throw IOException("download respondeu ${resposta.code}")
            val corpo = resposta.body ?: throw IOException("download sem corpo")
            val continuando = resposta.code == 206
            val inicio = if (continuando) jaBaixado else 0L
            val restante = corpo.contentLength().takeIf { it > 0 }
            val tamanho = restante?.let { it + inicio }
            if (tamanho != null && pasta.usableSpace < tamanho * FOLGA_DE_ESPACO + ESPACO_MINIMO) throw SemEspaco()
            FileOutputStream(parcial, continuando).use { saida ->
                corpo.byteStream().use { copiarContando(it, saida, inicio, tamanho, aoAvancar) }
            }
            if (tamanho != null && parcial.length() != tamanho) {
                throw IOException("download incompleto (${parcial.length()}/$tamanho)")
            }
        }
    }

    private fun impressaoDe(arquivo: File): String {
        val resumo = MessageDigest.getInstance("SHA-256")
        arquivo.inputStream().buffered().use { entrada ->
            val balde = ByteArray(64 * 1024)
            while (true) {
                val lidos = entrada.read(balde)
                if (lidos < 0) break
                resumo.update(balde, 0, lidos)
            }
        }
        return resumo.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copiarContando(
        entrada: InputStream,
        saida: OutputStream,
        inicio: Long,
        tamanho: Long?,
        aoAvancar: (Float) -> Unit,
    ) {
        val pedaco = ByteArray(64 * 1024)
        var copiado = inicio
        var ultimoPorCento = -1L
        while (true) {
            val lidos = entrada.read(pedaco)
            if (lidos < 0) break
            saida.write(pedaco, 0, lidos)
            copiado += lidos
            if (tamanho != null) {
                val porCento = copiado * 100 / tamanho
                if (porCento != ultimoPorCento) {
                    ultimoPorCento = porCento
                    aoAvancar(porCento.coerceAtMost(100) / 100f)
                }
            }
        }
    }

    fun ehMaisNova(candidata: String, instalada: String): Boolean = comparar(candidata, instalada) > 0

    private fun comparar(a: String, b: String): Int {
        val x = emNumeros(a)
        val y = emNumeros(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val diferenca = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (diferenca != 0) return diferenca
        }
        return 0
    }

    private fun emNumeros(versao: String): List<Int> =
        versao.trim().removePrefix("v").split('.').map { pedaco ->
            pedaco.takeWhile(Char::isDigit).toIntOrNull() ?: 0
        }
}
