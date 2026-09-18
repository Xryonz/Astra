package app.astra.mobile.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private const val REPOSITORIO = "Xryonz/Astra"
private const val PREFIXO_DA_ETIQUETA = "android-v"
private const val LISTA_DE_VERSOES =
    "https://api.github.com/repos/$REPOSITORIO/releases?per_page=20"

@Serializable
private data class PublicacaoDoGitHub(
    @SerialName("tag_name") val etiqueta: String = "",
    @SerialName("html_url") val pagina: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ArquivoPublicado> = emptyList(),
)

@Serializable
private data class ArquivoPublicado(
    val name: String = "",
    @SerialName("browser_download_url") val endereco: String = "",
    val size: Long = 0,
)

data class VersaoNova(
    val versao: String,
    val endereco: String,
    val tamanho: Long,
    val pagina: String,
)

object Atualizador {

    private val leitor = Json { ignoreUnknownKeys = true }

    private val cliente by lazy {
        OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.MINUTES)
            .build()
    }

    suspend fun procurar(versaoInstalada: String): VersaoNova? = withContext(Dispatchers.IO) {
        val pedido = Request.Builder()
            .url(LISTA_DE_VERSOES)
            .header("Accept", "application/vnd.github+json")
            .build()

        val corpo = runCatching {
            cliente.newCall(pedido).execute().use { resposta ->
                if (resposta.isSuccessful) resposta.body?.string() else null
            }
        }.getOrNull() ?: return@withContext null

        val publicacoes = runCatching {
            leitor.decodeFromString<List<PublicacaoDoGitHub>>(corpo)
        }.getOrNull().orEmpty()

        val doAndroid = publicacoes.firstOrNull {
            !it.draft && !it.prerelease && it.etiqueta.startsWith(PREFIXO_DA_ETIQUETA)
        } ?: return@withContext null

        val versao = doAndroid.etiqueta.removePrefix(PREFIXO_DA_ETIQUETA)
        if (!ehMaisNova(versao, versaoInstalada)) return@withContext null

        val pacote = doAndroid.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: return@withContext null

        VersaoNova(versao, pacote.endereco, pacote.size, doAndroid.pagina)
    }

    suspend fun baixar(
        nova: VersaoNova,
        destino: File,
        aoAndar: (Float) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val pedido = Request.Builder().url(nova.endereco).build()
        runCatching {
            cliente.newCall(pedido).execute().use { resposta ->
                val corpo = resposta.body
                if (!resposta.isSuccessful || corpo == null) return@use null

                val total = if (nova.tamanho > 0) nova.tamanho else corpo.contentLength()
                destino.parentFile?.mkdirs()
                destino.outputStream().use { saida ->
                    corpo.byteStream().use { entrada ->
                        val balde = ByteArray(64 * 1024)
                        var recebidos = 0L
                        while (true) {
                            val lidos = entrada.read(balde)
                            if (lidos < 0) break
                            saida.write(balde, 0, lidos)
                            recebidos += lidos
                            if (total > 0) {
                                aoAndar((recebidos.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                destino
            }
        }.getOrNull()
    }

    fun ehMaisNova(candidata: String, instalada: String): Boolean {
        val nova = emNumeros(candidata)
        val atual = emNumeros(instalada)
        for (i in 0 until maxOf(nova.size, atual.size)) {
            val a = nova.getOrElse(i) { 0 }
            val b = atual.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun emNumeros(versao: String): List<Int> =
        versao.trim().removePrefix("v").split('.').map { pedaco ->
            pedaco.takeWhile(Char::isDigit).toIntOrNull() ?: 0
        }
}
