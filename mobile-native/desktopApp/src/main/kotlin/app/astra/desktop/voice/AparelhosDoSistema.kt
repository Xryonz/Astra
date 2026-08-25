package app.astra.desktop.voice

import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine

object AudioDevices {

    fun inputs(): List<String> = nomesComLinha(TargetDataLine::class.java)

    fun outputs(): List<String> = nomesComLinha(SourceDataLine::class.java)

    private fun nomesComLinha(classe: Class<*>): List<String> = runCatching {
        AudioSystem.getMixerInfo().filter { info ->
            runCatching {
                val mixer = AudioSystem.getMixer(info)
                val linhas = if (classe == TargetDataLine::class.java) mixer.targetLineInfo else mixer.sourceLineInfo
                linhas.any { it.lineClass == classe }
            }.getOrDefault(false)
        }.map { it.name }.distinct()
    }.getOrDefault(emptyList())
}
