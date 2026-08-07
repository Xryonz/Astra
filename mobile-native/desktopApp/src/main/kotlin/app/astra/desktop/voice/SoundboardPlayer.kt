package app.astra.desktop.voice

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.Collections
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

// TOCADOR DA SOUNDBOARD.
//
// O servidor nunca manda audio — manda o aviso "tocou o som X, a URL e essa". Quem
// baixa e toca e cada cliente, localmente. Por isso todo mundo ouve o arquivo
// ORIGINAL, sem passar pelo Opus da voz (que e afinado pra fala e esmaga efeito).
//
// Cache em memoria por URL: numa call, o mesmo som toca dez vezes seguidas, e
// baixar dez vezes seria desperdicio de rede e atraso ate o som sair. O teto existe
// porque isto vive num app que fica aberto o dia inteiro.
object SoundboardPlayer {
    private const val TETO_CACHE = 24
    private const val TETO_BYTES = 4 * 1024 * 1024

    private val http = OkHttpClient()

    // LinkedHashMap com accessOrder = LRU pronto; synchronizedMap porque quem
    // escreve e a thread de download e quem le e a de reproducao.
    private val cache: MutableMap<String, ByteArray> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, ByteArray>): Boolean =
                size > TETO_CACHE
        },
    )

    fun tocar(url: String) {
        if (url.isBlank()) return
        thread(isDaemon = true, name = "astra-soundboard") {
            runCatching {
                val bytes = cache[url] ?: baixar(url)?.also { cache[url] = it } ?: return@runCatching
                reproduzir(bytes)
            }
        }
    }

    private fun baixar(url: String): ByteArray? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val corpo = r.body?.bytes() ?: return null
            // Teto de tamanho: som de soundboard e curto por definicao. Um arquivo
            // gigante aqui seria erro de upload ou sabotagem, e nos dois casos nao
            // vale estourar a memoria de quem so entrou na call.
            if (corpo.size > TETO_BYTES) null else corpo
        }
    }.getOrNull()

    private fun reproduzir(bytes: ByteArray) {
        // getAudioInputStream le o cabecalho, entao precisa de um stream que aceite
        // mark/reset — ByteArrayInputStream aceita.
        AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { entrada ->
            val original = entrada.format
            // Placas nao tocam qualquer formato. PCM_SIGNED 16 bits e o denominador
            // comum; a conversao e feita pelo proprio JDK quando necessaria.
            val alvo = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                original.sampleRate,
                16,
                original.channels,
                original.channels * 2,
                original.sampleRate,
                false,
            )
            val stream = if (AudioSystem.isConversionSupported(alvo, original)) {
                AudioSystem.getAudioInputStream(alvo, entrada)
            } else {
                entrada
            }
            val info = DataLine.Info(SourceDataLine::class.java, stream.format)
            if (!AudioSystem.isLineSupported(info)) return
            (AudioSystem.getLine(info) as SourceDataLine).apply {
                open(stream.format)
                start()
                val buf = ByteArray(8192)
                while (true) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    write(buf, 0, n)
                }
                drain()
                stop()
                close()
            }
        }
    }
}
