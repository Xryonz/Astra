package app.astra.desktop

import app.astra.shared.AstraShared
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@Serializable
private data class Relato(
    val instalacao: String,
    val versao: String,
    val so: String,
    val placa: String,
    val tipo: String,
    val mensagem: String,
    val rastro: String,
    val arranque: String,
)

object RelatoDeFalha {

    private const val ARQUIVO_DA_INSTALACAO = "instalacao.txt"
    private const val ROTA = "api/falhas"
    private const val TETO_DO_TEXTO = 12_000
    private const val TETO_CURTO = 200
    private const val TETO_DA_MENSAGEM = 2_000
    private const val MAXIMO_POR_SESSAO = 3
    private const val PRAZO_S = 12L
    private const val DIARIO_DA_TROCA = "troca.log"
    private const val TIPO_DA_TROCA = "atualizacao"

    @Volatile private var enviados = 0

    private val json = Json { encodeDefaults = true }

    private val cliente by lazy {
        OkHttpClient.Builder()
            .callTimeout(PRAZO_S, TimeUnit.SECONDS)
            .build()
    }

    private val instalacao: String by lazy {
        val arquivo = File(CrashLog.dataDir(), ARQUIVO_DA_INSTALACAO)
        runCatching { arquivo.readText().trim() }.getOrNull()?.takeIf { it.length in 8..64 }
            ?: UUID.randomUUID().toString().also { runCatching { arquivo.writeText(it) } }
    }

    fun contar(erro: Throwable, rastroCompleto: String) {
        val versao = System.getProperty("astra.version") ?: return
        synchronized(this) {
            if (enviados >= MAXIMO_POR_SESSAO) return
            enviados++
        }
        thread(isDaemon = true, name = "astra-relato-de-falha") {
            runCatching { enviar(montar(versao, erro, rastroCompleto)) }
        }
    }

    fun contarTroca(versaoAlvo: String) {
        val versao = System.getProperty("astra.version") ?: return
        val diario = runCatching { File(CrashLog.dataDir(), DIARIO_DA_TROCA).readText() }.getOrDefault("")
        thread(isDaemon = true, name = "astra-relato-de-troca") {
            runCatching {
                enviar(
                    Relato(
                        instalacao = instalacao,
                        versao = versao,
                        so = semNome("${System.getProperty("os.name")} ${System.getProperty("os.version")}").take(TETO_CURTO),
                        placa = "",
                        tipo = TIPO_DA_TROCA,
                        mensagem = semNome("a troca para $versaoAlvo não se completou").take(TETO_DA_MENSAGEM),
                        rastro = semNome(diario.ifBlank { "sem diário da troca" }).take(TETO_DO_TEXTO),
                        arranque = "",
                    ),
                )
            }
        }
    }

    private fun montar(versao: String, erro: Throwable, rastroCompleto: String) = Relato(
        instalacao = instalacao,
        versao = versao,
        so = semNome("${System.getProperty("os.name")} ${System.getProperty("os.version")}").take(TETO_CURTO),
        placa = semNome(Placas.daTela?.nome.orEmpty()).take(TETO_CURTO),
        tipo = erro::class.qualifiedName.orEmpty().ifBlank { "Throwable" }.take(TETO_CURTO),
        mensagem = semNome(erro.message.orEmpty()).take(TETO_DA_MENSAGEM),
        rastro = semNome(rastroCompleto).take(TETO_DO_TEXTO),
        arranque = semNome(trilhaDoArranque()).take(TETO_DO_TEXTO),
    )

    private fun trilhaDoArranque(): String =
        runCatching { File(CrashLog.dataDir(), "arranque.txt").readText() }.getOrDefault("")

    private fun semNome(texto: String): String {
        val nome = System.getProperty("user.name").orEmpty()
        if (nome.isBlank()) return texto
        return texto.replace(nome, "···", ignoreCase = true)
    }

    private fun enviar(relato: Relato) {
        val corpo = json.encodeToString(Relato.serializer(), relato)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val pedido = Request.Builder()
            .url(AstraShared.BASE_URL.trimEnd('/') + "/" + ROTA)
            .post(corpo)
            .build()
        cliente.newCall(pedido).execute().use { }
    }
}
