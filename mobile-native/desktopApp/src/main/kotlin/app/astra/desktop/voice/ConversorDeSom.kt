package app.astra.desktop.voice

import java.io.File
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioSystem

// Converte qualquer arquivo de audio pra WAV, usando o ffmpeg que o app JA
// empacota pra transmissao de tela (FfmpegLocator).
//
// POR QUE CONVERTER, se o pedido foi "nao perder qualidade": decodificar um MP3 e
// uma operacao EXATA — o WAV guarda exatamente o que saiu do decodificador, amostra
// por amostra. Perda so existe quando se RE-ENCODA (MP3 -> MP3, ou MP3 -> AAC), e
// nao e o que acontece aqui. O arquivo fica maior, so isso.
//
// E converter compensa porque o JDK toca WAV sozinho: aceitar MP3 direto exigiria
// embarcar um decodificador so pra isso, com mais um formato pra dar problema em
// maquina estranha.
object ConversorDeSom {
    private const val TIMEOUT_S = 30L

    // Ja e WAV? Devolve o proprio arquivo — reconverter nao acrescentaria nada e
    // ainda gastaria um processo.
    fun paraWav(entrada: File): File? {
        if (entrada.extension.equals("wav", ignoreCase = true)) return entrada

        val ffmpeg = FfmpegLocator.path ?: return null
        val saida = File.createTempFile("astra-som-", ".wav")
        saida.delete() // o ffmpeg recusa sobrescrever sem -y; mais limpo apagar antes

        val proc = runCatching {
            ProcessBuilder(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin",
                "-i", entrada.absolutePath,
                // PCM 16 bits: e o que placa de som toca sem conversao extra, e o
                // que o javax.sound abre sem depender de codec do sistema.
                "-c:a", "pcm_s16le",
                saida.absolutePath,
            ).redirectErrorStream(true).start()
        }.getOrNull() ?: return null

        // Drena a saida: sem isso o buffer do pipe enche e o ffmpeg trava pra sempre.
        runCatching { proc.inputStream.use { it.readBytes() } }
        val terminou = runCatching { proc.waitFor(TIMEOUT_S, TimeUnit.SECONDS) }.getOrDefault(false)
        if (!terminou) {
            runCatching { proc.destroyForcibly() }
            saida.delete()
            return null
        }
        return if (saida.isFile && saida.length() > 0) saida else null.also { saida.delete() }
    }

    // Duracao lida UMA vez, no cadastro. Depois disso ela viaja no DTO — abrir o
    // arquivo do bucket so pra saber quanto dura seria uma requisicao por som toda
    // vez que alguem abre o painel.
    fun duracaoMs(wav: File): Int = runCatching {
        AudioSystem.getAudioInputStream(wav).use { s ->
            val quadros = s.frameLength
            val taxa = s.format.frameRate
            if (quadros <= 0 || taxa <= 0f) 0 else (quadros / taxa * 1000f).toInt()
        }
    }.getOrDefault(0)
}
