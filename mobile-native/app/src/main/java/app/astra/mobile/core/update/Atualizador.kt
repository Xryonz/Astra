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
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

private const val REPOSITORIO = "Xryonz/Astra"
private val PREFIXO = BuildConfig.ETIQUETA_DE_ATUALIZACAO
private val ETIQUETAS = "https://api.github.com/repos/$REPOSITORIO/git/matching-refs/tags/$PREFIXO"
private val SO_NUMEROS = Regex("""\d+(\.\d+)*""")

private val NOME_POR_PROCESSADOR = mapOf(
    "arm64-v8a" to "android",
    "armeabi-v7a" to "android-32bits",
)

@Serializable
private data class Referencia(val ref: String = "")

data class VersaoNova(
    val versao: String,
    val endereco: String,
    val pagina: String,
)

class PacoteCorrompido : IOException()

object Atualizador {

    private val leitor = Json { ignoreUnknownKeys = true }

    private val cliente by lazy {
        OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.MINUTES)
            .build()
    }

    fun procurar(versaoInstalada: String): VersaoNova? {
        val pedido = Request.Builder()
            .url(ETIQUETAS)
            .header("Accept", "application/vnd.github+json")
            .build()

        val corpo = cliente.newCall(pedido).execute().use { resposta ->
            if (!resposta.isSuccessful) throw IOException("GitHub respondeu ${resposta.code}")
            resposta.body?.string().orEmpty()
        }

        val versao = leitor.decodeFromString<List<Referencia>>(corpo)
            .map { it.ref.removePrefix("refs/tags/$PREFIXO") }
            .filter { it.matches(SO_NUMEROS) }
            .maxWithOrNull(::comparar)
            ?: return null
        if (!ehMaisNova(versao, versaoInstalada)) return null

        val nome = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { NOME_POR_PROCESSADOR[it] } ?: return null
        val etiqueta = PREFIXO + versao
        return VersaoNova(
            versao = versao,
            endereco = "https://github.com/$REPOSITORIO/releases/download/$etiqueta/Astra-$versao-$nome.apk.gz",
            pagina = "https://github.com/$REPOSITORIO/releases/tag/$etiqueta",
        )
    }

    fun baixar(nova: VersaoNova, pasta: File, aoAvancar: (Float) -> Unit = {}): File {
        val pronto = File(pasta, "${nova.versao}.apk")
        if (pronto.exists()) return pronto
        pasta.mkdirs()

        val parcial = File(pasta, "${nova.versao}.apk.gz.parcial")
        val jaBaixado = if (parcial.exists()) parcial.length() else 0L
        val pedido = Request.Builder()
            .url(nova.endereco)
            .apply { if (jaBaixado > 0) header("Range", "bytes=$jaBaixado-") }
            .build()

        cliente.newCall(pedido).execute().use { resposta ->
            if (resposta.code != 416) {
                if (!resposta.isSuccessful) throw IOException("download respondeu ${resposta.code}")
                val corpo = resposta.body ?: throw IOException("download sem corpo")
                val continuando = resposta.code == 206
                val inicio = if (continuando) jaBaixado else 0L
                val tamanho = corpo.contentLength().takeIf { it > 0 }?.let { it + inicio }
                FileOutputStream(parcial, continuando).use { saida ->
                    corpo.byteStream().use { copiarContando(it, saida, inicio, tamanho, aoAvancar) }
                }
            }
        }

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
        if (!descomprimindo.renameTo(pronto)) throw IOException("não foi possível guardar o pacote")
        return pronto
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
