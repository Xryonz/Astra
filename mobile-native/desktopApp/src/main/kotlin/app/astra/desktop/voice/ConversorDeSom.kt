package app.astra.desktop.voice

import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

object ConversorDeSom {

    private const val SEGUNDOS_MAXIMOS = 300

    fun paraWav(entrada: File): File? {
        if (entrada.extension.equals("wav", ignoreCase = true)) return entrada

        return runCatching {
            AudioSystem.getAudioInputStream(entrada).use { origem ->
                val canais = origem.format.channels.coerceAtLeast(1)
                val taxa = origem.format.sampleRate
                val alvo = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    taxa,
                    16,
                    canais,
                    canais * 2,
                    taxa,
                    false,
                )

                AudioSystem.getAudioInputStream(alvo, origem).use { pcm ->
                    val tetoDeBytes = (taxa * canais * 2 * SEGUNDOS_MAXIMOS).toLong()
                    val bruto = pcm.readNBytes((tetoDeBytes + 1).toInt())
                    if (bruto.size > tetoDeBytes) {
                        error("som acima de ${SEGUNDOS_MAXIMOS / 60} minutos")
                    }

                    val quadros = bruto.size.toLong() / alvo.frameSize
                    if (quadros <= 0) error("arquivo sem áudio")

                    val saida = File.createTempFile("astra-som-", ".wav")
                    AudioInputStream(bruto.inputStream(), alvo, quadros).use {
                        AudioSystem.write(it, AudioFileFormat.Type.WAVE, saida)
                    }
                    saida
                }
            }
        }.getOrNull()
    }

    fun duracaoMs(wav: File): Int = runCatching {
        AudioSystem.getAudioInputStream(wav).use { s ->
            val quadros = s.frameLength
            val taxa = s.format.frameRate
            if (quadros <= 0 || taxa <= 0f) 0 else (quadros / taxa * 1000f).toInt()
        }
    }.getOrDefault(0)
}
