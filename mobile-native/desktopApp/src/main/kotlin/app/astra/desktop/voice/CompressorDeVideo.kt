package app.astra.desktop.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

sealed interface PreparoDoVideo {
    data class Pronto(val arquivo: File) : PreparoDoVideo
    data class Recusado(val motivo: String) : PreparoDoVideo
}

private data class FichaDoVideo(val duracaoMs: Long, val largura: Int, val altura: Int)

object CompressorDeVideo {
    private const val LADO_MENOR = 720
    private const val KBPS = 2500
    private const val KBPS_DO_SOM = 128
    private const val KBPS_DE_VIDEO_LEVE = 3000
    private const val KBPS_MINIMO = 600
    private const val PREFIXO_DO_TEMPORARIO = "astra-video-"
    private const val SOBRA_ANTIGA_MS = 60 * 60 * 1000L

    private val leitor = Json { ignoreUnknownKeys = true }
    private val umDeCadaVez = Semaphore(1)
    private val varrido = AtomicBoolean(false)

    val disponivel: Boolean get() = LocalizadorDoSidecar.caminho != null

    suspend fun preparar(original: File, teto: Long, andamento: (Float) -> Unit): PreparoDoVideo {
        withContext(Dispatchers.IO) { varrerSobras() }
        val ficha = sondar(original)
            ?: return if (original.length() <= teto) PreparoDoVideo.Pronto(original)
            else PreparoDoVideo.Recusado("${original.name} não pôde ser comprimido. Envie um vídeo de até 25 MB.")

        val segundos = ficha.duracaoMs / 1000.0
        val kbpsAtual = if (segundos > 0) original.length() * 8 / 1000.0 / segundos else Double.MAX_VALUE
        val leve = min(ficha.largura, ficha.altura) <= LADO_MENOR && kbpsAtual <= KBPS_DE_VIDEO_LEVE
        if (leve && original.length() <= teto) return PreparoDoVideo.Pronto(original)
        val kbps = min(KBPS.toDouble(), kbpsAtual).toInt().coerceAtLeast(KBPS_MINIMO)
        val estimado = (kbps + KBPS_DO_SOM) * 1000L / 8 * ficha.duracaoMs / 1000
        if (estimado > teto) return longoDemais(original)

        val saida = withContext(Dispatchers.IO) {
            File.createTempFile(PREFIXO_DO_TEMPORARIO, ".mp4").apply { deleteOnExit() }
        }
        val comprimiu = try {
            umDeCadaVez.withPermit { comprimir(original, saida, kbps, andamento) }
        } catch (e: CancellationException) {
            saida.delete()
            throw e
        }
        return when {
            !comprimiu || saida.length() == 0L -> {
                saida.delete()
                if (original.length() <= teto) PreparoDoVideo.Pronto(original)
                else PreparoDoVideo.Recusado("${original.name} não pôde ser comprimido. Envie um vídeo de até 25 MB.")
            }
            saida.length() >= original.length() && original.length() <= teto -> {
                saida.delete()
                PreparoDoVideo.Pronto(original)
            }
            saida.length() > teto -> {
                saida.delete()
                longoDemais(original)
            }
            else -> PreparoDoVideo.Pronto(saida)
        }
    }

    private fun varrerSobras() {
        if (!varrido.compareAndSet(false, true)) return
        val limite = System.currentTimeMillis() - SOBRA_ANTIGA_MS
        File(System.getProperty("java.io.tmpdir")).listFiles { f ->
            f.name.startsWith(PREFIXO_DO_TEMPORARIO) && f.lastModified() < limite
        }?.forEach { it.delete() }
    }

    private fun longoDemais(original: File) = PreparoDoVideo.Recusado(
        "${original.name} passa de 25 MB mesmo comprimido. Corte para até 1 minuto e 20 segundos e envie de novo.",
    )

    private suspend fun sondar(arquivo: File): FichaDoVideo? {
        var ficha: FichaDoVideo? = null
        rodar(listOf("sondar-video", arquivo.absolutePath)) { linha ->
            if (linha["ev"]?.jsonPrimitive?.content == "ficha") {
                ficha = FichaDoVideo(
                    duracaoMs = linha["duracaoMs"]?.jsonPrimitive?.longOrNull ?: 0,
                    largura = linha["largura"]?.jsonPrimitive?.intOrNull ?: 0,
                    altura = linha["altura"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }
        }
        return ficha?.takeIf { it.duracaoMs > 0 && it.largura > 0 && it.altura > 0 }
    }

    private suspend fun comprimir(entrada: File, saida: File, kbps: Int, andamento: (Float) -> Unit): Boolean {
        var pronto = false
        rodar(
            listOf(
                "comprimir-video", entrada.absolutePath, saida.absolutePath,
                LADO_MENOR.toString(), kbps.toString(), KBPS_DO_SOM.toString(),
            ),
        ) { linha ->
            when (linha["ev"]?.jsonPrimitive?.content) {
                "andamento" -> linha["fracao"]?.jsonPrimitive?.floatOrNull?.let(andamento)
                "pronto" -> pronto = true
                "erro" -> VoiceLog.nota("[vídeo] não comprimiu ${entrada.name}: ${linha["msg"]?.jsonPrimitive?.content}")
            }
        }
        return pronto
    }

    private suspend fun rodar(argumentos: List<String>, aoLer: (JsonObject) -> Unit) {
        val exe = LocalizadorDoSidecar.caminho ?: return
        withContext(Dispatchers.IO) {
            val processo = ProcessBuilder(listOf(exe.absolutePath) + argumentos)
                .directory(exe.parentFile)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val vigia = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    processo.destroyForcibly()
                }
            }
            try {
                processo.inputStream.bufferedReader().useLines { linhas ->
                    linhas.forEach { texto ->
                        runCatching { leitor.parseToJsonElement(texto) as JsonObject }.getOrNull()?.let(aoLer)
                    }
                }
            } catch (e: IOException) {
                VoiceLog.nota("[vídeo] a leitura do astra-voz parou: ${e.message}")
            } finally {
                vigia.cancel()
                processo.destroyForcibly().waitFor()
            }
        }
    }
}
