package app.astra.desktop.voice

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

internal class SomPronto(private val formato: AudioFormat, private val gerar: () -> ByteArray) {
    private var clip: Clip? = null

    fun tocar() = naFila { preparado()?.apply { voltarAoInicio(); start() } }

    fun repetir() = naFila { preparado()?.apply { voltarAoInicio(); loop(Clip.LOOP_CONTINUOUSLY) } }

    fun parar() = naFila { clip?.stop() }

    private fun Clip.voltarAoInicio() {
        stop()
        framePosition = 0
    }

    private fun preparado(): Clip? {
        clip?.takeIf { it.isOpen }?.let { return it }
        val pcm = gerar()
        val novo = AudioSystem.getClip().apply { open(formato, pcm, 0, pcm.size) }
        clip = novo
        return novo
    }

    private fun naFila(tarefa: () -> Unit) {
        fila.execute { runCatching(tarefa) }
    }

    private companion object {
        val fila: ExecutorService = Executors.newSingleThreadExecutor { r ->
            Thread(r, "astra-som").apply { isDaemon = true }
        }
    }
}
